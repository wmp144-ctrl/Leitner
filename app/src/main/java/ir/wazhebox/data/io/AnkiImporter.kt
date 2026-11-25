package ir.wazhebox.data.io

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import io.airlift.compress.zstd.ZstdInputStream
import ir.wazhebox.core.time.StudyClock
import ir.wazhebox.data.db.CardEntity
import ir.wazhebox.data.db.DeckEntity
import ir.wazhebox.data.db.TagEntity
import ir.wazhebox.data.db.WazheBoxDatabase
import ir.wazhebox.data.db.WordEntity
import ir.wazhebox.data.db.WordTagEntity
import ir.wazhebox.domain.grading.TextNormalizer
import ir.wazhebox.domain.model.CardState
import ir.wazhebox.domain.model.Direction
import ir.wazhebox.domain.model.WordSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** What an .apkg import produced, so the screen can tell the three cases apart. */
sealed interface AnkiImportResult {
    /**
     * [report] carries the counts; when it was a dry run the numbers are what
     * *would* happen and no row has been written yet.
     */
    data class Imported(val report: ImportReport, val decks: Int) : AnkiImportResult
    /** The archive holds neither a SQLite collection nor a readable zstd one. */
    data object Unsupported : AnkiImportResult
    data object Corrupt : AnkiImportResult
}

/**
 * Text handling for Anki notes. Kept pure so it is unit-testable without SQLite.
 */
object AnkiText {

    private val soundTag = Regex("\\[sound:[^\\]]*]")
    private val clozeMarkup = Regex("\\{\\{c\\d+::(.*?)(?:::[^}]*)?}}", RegexOption.DOT_MATCHES_ALL)
    private val htmlBreak = Regex("(?i)<br\\s*/?>|</?(?:div|p|li|tr)\\b[^>]*>")
    private val htmlTag = Regex("(?i)</?(?:b|strong|i|em|u|span|div|p|li|ul|ol|font|a|sub|sup|small|big|table|tr|td|img)[^>]*>")
    private val anyTag = Regex("<[^>]+>")

    fun stripHtml(raw: String): String {
        val withBreaks = raw
            .replace(soundTag, "")
            .replace(htmlBreak, "\n")
            .replace(htmlTag, "")
            .replace(anyTag, "")
        return withBreaks
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("\u00A0", " ")
            .lines()
            .map { it.trim().replace(Regex("[ \\t]+"), " ") }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()
    }

    fun splitFields(flds: String): List<String> = flds.split('\u001F')

    fun isCloze(front: String): Boolean = clozeMarkup.containsMatchIn(front)

    /** Every cloze deletion's answer, in order. */
    fun clozeAnswers(front: String): List<String> =
        clozeMarkup.findAll(front).map { stripHtml(it.groupValues[1]) }.filter { it.isNotEmpty() }.toList()

    /**
     * The sentence with each deletion filled back in (hints dropped), so the
     * imported example can be blanked again when the cloze card is shown.
     */
    fun renderCloze(front: String): String =
        stripHtml(clozeMarkup.replace(front) { it.groupValues[1] })

    fun parseTags(raw: String?): List<String> =
        raw.orEmpty().trim().split(' ').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /** The first line is the word itself; anything below belongs to the meaning. */
    fun firstLine(text: String): String = text.lineSequence().firstOrNull()?.trim().orEmpty()
}

/** zstd decoding stays free of Android types, so it is testable on the JVM. */
object AnkiArchive {
    fun decompressZstd(source: java.io.InputStream, target: java.io.OutputStream) {
        ZstdInputStream(source).use { input -> input.copyTo(target) }
    }
}

private data class AnkiNote(
    val id: Long,
    val fields: List<String>,
    val tags: List<String>,
)

private data class AnkiCollection(
    val deckNames: Map<Long, String>,
    val deckOfNote: Map<Long, Long>,
    val notes: List<AnkiNote>,
)

/**
 * Reads an Anki `.apkg` (a zip with an SQLite collection inside) and writes its
 * notes into the app's own database. Media files are ignored: the app has no
 * image pipeline, and an offline study session only needs the text.
 *
 * Modern Anki stores the collection zstd-compressed as `collection.anki21b`,
 * which is decompressed here so a fresh export imports like an old one.
 */
@Singleton
class AnkiImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: WazheBoxDatabase,
    private val clock: StudyClock,
) {

    /** [apply] false only counts what the archive holds; nothing is written. */
    suspend fun import(file: File, apply: Boolean = true): AnkiImportResult =
        withContext(Dispatchers.IO) {
            val workDir = File(context.cacheDir, "anki-${System.currentTimeMillis()}")
            workDir.mkdirs()
            try {
                when (val extracted = extractCollection(file, workDir)) {
                    null -> AnkiImportResult.Corrupt
                    ExtractedCollection.UNSUPPORTED -> AnkiImportResult.Unsupported
                    is ExtractedCollection.Found -> {
                        val collection = readCollection(extracted.file)
                            ?: return@withContext AnkiImportResult.Corrupt
                        importNotes(collection, apply)
                    }
                }
            } catch (t: Throwable) {
                AnkiImportResult.Corrupt
            } finally {
                workDir.deleteRecursively()
            }
        }

    private sealed interface ExtractedCollection {
        data class Found(val file: File) : ExtractedCollection
        data object UNSUPPORTED : ExtractedCollection
    }

    /**
     * A plain SQLite collection is preferred when the archive carries both. An
     * archive with only `collection.anki21b` is decompressed with the bundled
     * zstd decoder; if that fails the caller reports the file as corrupt.
     */
    private fun extractCollection(apkg: File, workDir: File): ExtractedCollection? {
        var plain: File? = null
        var compressed: File? = null
        ZipInputStream(FileInputStream(apkg)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when (entry.name) {
                    "collection.anki21", "collection.anki2" -> {
                        val name = if (entry.name.endsWith("anki21")) "collection.anki21" else "collection.anki2"
                        plain = writeEntry(zip, File(workDir, name))
                    }
                    "collection.anki21b" -> {
                        compressed = writeEntry(zip, File(workDir, "collection.anki21b"))
                    }
                }
                zip.closeEntry()
            }
        }
        plain?.let { if (it.length() > 0) return ExtractedCollection.Found(it) }
        val zstd = compressed ?: return null
        val sqlite = File(workDir, "collection.anki21b.sqlite")
        return runCatching {
            FileInputStream(zstd).use { input ->
                sqlite.outputStream().use { output -> AnkiArchive.decompressZstd(input, output) }
            }
            if (sqlite.length() == 0L) ExtractedCollection.UNSUPPORTED
            else ExtractedCollection.Found(sqlite)
        }.getOrElse { ExtractedCollection.UNSUPPORTED }
    }

    private fun writeEntry(zip: ZipInputStream, target: File): File {
        target.outputStream().use { out -> zip.copyTo(out) }
        return target
    }

    private fun readCollection(file: File): AnkiCollection? = runCatching {
        val sqlite = SQLiteDatabase.openDatabase(
            file.path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        try {
            val deckNames = readDeckNames(sqlite)
            val deckOfNote = mutableMapOf<Long, Long>()
            sqlite.rawQuery("SELECT nid, did FROM cards", null).use { cursor ->
                while (cursor.moveToNext()) {
                    deckOfNote.putIfAbsent(cursor.getLong(0), cursor.getLong(1))
                }
            }
            val notes = mutableListOf<AnkiNote>()
            sqlite.rawQuery("SELECT id, flds, tags FROM notes", null).use { cursor ->
                while (cursor.moveToNext()) {
                    notes += AnkiNote(
                        id = cursor.getLong(0),
                        fields = AnkiText.splitFields(cursor.getString(1).orEmpty()),
                        tags = AnkiText.parseTags(cursor.getString(2)),
                    )
                }
            }
            AnkiCollection(deckNames, deckOfNote, notes)
        } finally {
            sqlite.close()
        }
    }.getOrNull()

    private fun readDeckNames(sqlite: SQLiteDatabase): Map<Long, String> {
        val json = runCatching {
            sqlite.rawQuery("SELECT decks FROM col LIMIT 1", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: return emptyMap()
        return runCatching {
            val root = Json.parseToJsonElement(json).jsonObject
            root.mapNotNull { (id, value) ->
                val name = (value as? JsonObject)?.get("name")?.jsonPrimitive?.content
                val deckId = id.toLongOrNull()
                if (deckId != null && !name.isNullOrBlank()) deckId to name else null
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private suspend fun importNotes(collection: AnkiCollection, apply: Boolean): AnkiImportResult {
        var added = 0
        var duplicates = 0
        var missingTerm = 0
        var missingMeaning = 0
        val now = clock.now().toEpochMilliseconds()
        val batch = now
        val deckIds = mutableMapOf<String, Long>()
        val deckNamesUsed = mutableSetOf<String>()
        val rejected = mutableListOf<RejectedRow>()

        suspend fun run() {
            collection.notes.forEachIndexed { index, note ->
                val front = note.fields.getOrNull(0).orEmpty()
                val back = note.fields.getOrNull(1).orEmpty()
                val cloze = AnkiText.isCloze(front)
                val term = if (cloze) {
                    AnkiText.clozeAnswers(front).firstOrNull().orEmpty()
                } else {
                    AnkiText.firstLine(AnkiText.stripHtml(front))
                }
                val sentence = if (cloze) AnkiText.renderCloze(front).takeIf { it.isNotBlank() } else null
                val meaning = AnkiText.stripHtml(back)

                when {
                    term.isBlank() -> {
                        missingTerm++
                        rejected += RejectedRow(index + 1, "", "missing_term")
                        return@forEachIndexed
                    }
                    meaning.isBlank() && sentence == null -> {
                        missingMeaning++
                        rejected += RejectedRow(index + 1, term, "missing_meaning")
                        return@forEachIndexed
                    }
                }

                val deckName = collection.deckNames[collection.deckOfNote[note.id]]
                    ?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_DECK
                deckNamesUsed += deckName

                val deckId: Long? = if (apply) {
                    deckIds.getOrPut(deckName) {
                        db.deckDao().byName(deckName)?.id ?: db.deckDao().insert(
                            DeckEntity(
                                name = deckName,
                                description = "واردشده از Anki",
                                createdAt = now,
                                updatedAt = now,
                            ),
                        )
                    }
                } else {
                    db.deckDao().byName(deckName)?.id
                }

                val norm = TextNormalizer.normalizeKey(term)
                if (deckId != null && db.wordDao().findByTermNorm(deckId, norm) != null) {
                    duplicates++
                    rejected += RejectedRow(index + 1, term, "duplicate")
                    return@forEachIndexed
                }
                if (!apply || deckId == null) {
                    // Dry run: nothing is written, so this row only counts.
                    added++
                    return@forEachIndexed
                }

                val wordId = db.wordDao().insert(
                    WordEntity(
                        deckId = deckId,
                        term = term,
                        termNorm = norm,
                        meaningFa = meaning.ifBlank { term },
                        exampleEn = sentence,
                        source = WordSource.ANKI.name,
                        createdAt = now,
                        updatedAt = now,
                        importBatch = batch,
                    ),
                )
                db.cardDao().insert(
                    CardEntity(
                        wordId = wordId,
                        direction = Direction.EN_FA.name,
                        state = CardState.NEW.name,
                        dueAt = now,
                        createdAt = now,
                    ),
                )
                if (cloze && sentence != null) {
                    db.cardDao().insertIgnore(
                        listOf(
                            CardEntity(
                                wordId = wordId,
                                direction = Direction.CLOZE.name,
                                state = CardState.NEW.name,
                                dueAt = now,
                                createdAt = now,
                            ),
                        ),
                    )
                }
                note.tags.forEach { tag ->
                    db.tagDao().insert(TagEntity(name = tag))
                    db.tagDao().byName(tag)?.let { entity ->
                        db.tagDao().link(WordTagEntity(wordId = wordId, tagId = entity.id))
                    }
                }
                added++
            }
        }

        if (apply) db.withTransaction { run() } else run()

        return AnkiImportResult.Imported(
            report = ImportReport(
                rowsRead = collection.notes.size,
                added = added,
                duplicates = duplicates,
                missingMeaning = missingMeaning,
                missingTerm = missingTerm,
                rejected = rejected,
                planned = !apply,
                batchId = if (apply) batch else null,
            ),
            decks = deckNamesUsed.size,
        )
    }

    private companion object {
        const val DEFAULT_DECK = "دک من"
    }
}
