package ir.wazhebox.data.io

import androidx.room.withTransaction
import ir.wazhebox.core.time.StudyClock
import ir.wazhebox.data.db.CardEntity
import ir.wazhebox.data.db.DeckEntity
import ir.wazhebox.data.db.ReviewEntity
import ir.wazhebox.data.db.WazheBoxDatabase
import ir.wazhebox.data.db.WordEntity
import ir.wazhebox.data.repo.DeckRepositoryImpl
import ir.wazhebox.data.repo.StatsRepositoryImpl
import ir.wazhebox.data.settings.SettingsRepository
import ir.wazhebox.domain.grading.TextNormalizer
import ir.wazhebox.domain.model.CardState
import ir.wazhebox.domain.model.Direction
import ir.wazhebox.domain.model.WordSource
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class RejectedRow(val line: Int, val term: String, val reason: String)

data class ImportReport(
    val rowsRead: Int = 0,
    val added: Int = 0,
    val duplicates: Int = 0,
    val missingMeaning: Int = 0,
    val missingTerm: Int = 0,
    val invalid: Int = 0,
    val tagsCreated: Int = 0,
    val unknownColumns: List<String> = emptyList(),
    val rejected: List<RejectedRow> = emptyList(),
) {
    fun rejectedCsv(): String {
        val rows = mutableListOf<List<String?>>()
        rows += listOf<String?>("line", "term", "reason")
        rejected.forEach { rows += listOf<String?>(it.line.toString(), it.term, it.reason) }
        return CsvCodec.buildDocument(rows)
    }
}

data class BackupPreview(val decks: Int, val words: Int, val reviews: Int)

sealed interface BackupResult {
    data class Success(val preview: BackupPreview) : BackupResult
    data object Corrupt : BackupResult
    data object NewerVersion : BackupResult
}

/** JSON backup format (PRD §14.2). */
@Serializable
data class BackupFile(
    val format: String = "wazhebox.backup",
    val version: Int = 1,
    val exported_at: String = "",
    val app_version: String = "1.0.0",
    val decks: List<BackupDeck> = emptyList(),
    val words: List<BackupWord> = emptyList(),
    val cards: List<BackupCard> = emptyList(),
    val reviews: List<BackupReview> = emptyList(),
    val settings: BackupSettings = BackupSettings(),
)

@Serializable
data class BackupDeck(val id: Long, val name: String, val description: String? = null, val is_default: Boolean = false)

@Serializable
data class BackupWord(
    val id: Long,
    val deck_id: Long,
    val term: String,
    val meaning_fa: String,
    val pos: String? = null,
    val ipa: String? = null,
    val example_en: String? = null,
    val example_fa: String? = null,
    val notes: String? = null,
    val tags: List<String> = emptyList(),
    val created_at: Long = 0,
)

@Serializable
data class BackupCard(
    val id: Long,
    val word_id: Long,
    val direction: String,
    val state: String,
    val due_at: Long,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val step_index: Int = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val last_review_at: Long? = null,
)

@Serializable
data class BackupReview(
    val card_id: Long,
    val reviewed_at: Long,
    val rating: Int,
    val verdict: String? = null,
    val elapsed_days: Double = 0.0,
    val scheduled_days: Double = 0.0,
    val prev_state: String = "REVIEW",
    val next_state: String = "REVIEW",
)

@Serializable
data class BackupSettings(
    val daily_new_limit: Int = 10,
    val daily_review_limit: Int = 200,
    val desired_retention: Double = 0.9,
)

interface DataTransferRepository {
    suspend fun exportCsv(): String
    suspend fun exportBackupJson(): String
    suspend fun importCsv(text: String): ImportReport
    suspend fun previewBackup(text: String): BackupResult
    suspend fun restoreBackup(text: String, merge: Boolean): BackupResult
}

/**
 * CSV / JSON transfer. Validation happens before any write, and a restore runs
 * inside a single transaction so a corrupt file can never leave half data
 * (PRD §14.2).
 */
@Singleton
class DataTransferRepositoryImpl @Inject constructor(
    private val db: WazheBoxDatabase,
    private val settingsRepository: SettingsRepository,
    private val deckRepository: DeckRepositoryImpl,
    private val clock: StudyClock,
) : DataTransferRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private suspend fun currentSettings() = settingsRepository.settings.first()

    override suspend fun exportCsv(): String {
        val deck = deckRepository.ensureDefaultDeck()
        val rows = mutableListOf<List<String?>>()
        rows += listOf(
            "term", "meaning_fa", "pos", "ipa", "example_en", "example_fa",
            "tags", "box", "due_at", "reps", "lapses", "created_at",
        )
        val wordDao = db.wordDao()
        wordDao.allWithCards().forEach { row ->
            if (row.word.deckId != deck.id) return@forEach
            val card = row.cards.firstOrNull { it.direction == Direction.EN_FA.name }
            rows += listOf(
                row.word.term,
                row.word.meaningFa,
                row.word.pos,
                row.word.ipa,
                row.word.exampleEn,
                row.word.exampleFa,
                db.tagDao().tagsForWord(row.word.id).joinToString(","),
                card?.let { ir.wazhebox.domain.model.BoxMapper.boxOf(CardState.valueOf(it.state), it.stability).toString() },
                card?.dueAt?.toString(),
                card?.reps?.toString(),
                card?.lapses?.toString(),
                row.word.createdAt.toString(),
            )
        }
        return CsvCodec.buildDocument(rows)
    }

    override suspend fun exportBackupJson(): String {
        val wordDao = db.wordDao()
        val rows = wordDao.allWithCards()
        val tagsByWord = rows.associate { it.word.id to db.tagDao().tagsForWord(it.word.id) }
        val decks = db.deckDao().observeAll().first().map {
            BackupDeck(id = it.id, name = it.name, description = it.description, is_default = it.isDefault)
        }
        val words = rows.map { row ->
            BackupWord(
                id = row.word.id,
                deck_id = row.word.deckId,
                term = row.word.term,
                meaning_fa = row.word.meaningFa,
                pos = row.word.pos,
                ipa = row.word.ipa,
                example_en = row.word.exampleEn,
                example_fa = row.word.exampleFa,
                notes = row.word.notes,
                tags = tagsByWord[row.word.id].orEmpty(),
                created_at = row.word.createdAt,
            )
        }
        val cards = rows.flatMap { row ->
            row.cards.map { card ->
                BackupCard(
                    id = card.id,
                    word_id = card.wordId,
                    direction = card.direction,
                    state = card.state,
                    due_at = card.dueAt,
                    stability = card.stability,
                    difficulty = card.difficulty,
                    step_index = card.stepIndex,
                    reps = card.reps,
                    lapses = card.lapses,
                    last_review_at = card.lastReviewAt,
                )
            }
        }
        val settings = currentSettings()
        val backup = BackupFile(
            exported_at = Instant.fromEpochMilliseconds(clock.now().toEpochMilliseconds()).toString(),
            decks = decks,
            words = words,
            cards = cards,
            reviews = emptyList(),
            settings = BackupSettings(
                daily_new_limit = settings.dailyNewLimit,
                daily_review_limit = settings.dailyReviewLimit,
                desired_retention = settings.desiredRetention,
            ),
        )
        return json.encodeToString(backup)
    }

    override suspend fun importCsv(text: String): ImportReport {
        val deck = deckRepository.ensureDefaultDeck()
        val delimiter = CsvCodec.detectDelimiter(text)
        val parsed = CsvCodec.parse(text, delimiter).filter { (_, cells) -> cells.any { it.isNotBlank() } }
        if (parsed.isEmpty()) return ImportReport()

        val headerCells = parsed.first().second.map { it.trim().lowercase() }
        val hasHeader = headerCells.any { CsvCodec.HEADER_ALIASES.containsKey(it) }
        val mapping = if (hasHeader) {
            headerCells.map { CsvCodec.HEADER_ALIASES[it] }
        } else {
            listOf("term", "meaning_fa", "pos", "ipa", "example_en", "example_fa", "tags")
        }
        val unknown = if (hasHeader) {
            headerCells.filter { CsvCodec.HEADER_ALIASES[it] == null }
        } else {
            emptyList()
        }
        val dataRows = if (hasHeader) parsed.drop(1) else parsed

        var added = 0
        var duplicates = 0
        var missingMeaning = 0
        var missingTerm = 0
        var invalid = 0
        val tagsBefore = db.tagDao().count()
        val rejected = mutableListOf<RejectedRow>()
        val reportedTags = mutableSetOf<String>()

        db.withTransaction {
            dataRows.forEach { (line, cells) ->
                fun cell(name: String): String? {
                    val index = mapping.indexOf(name)
                    if (index < 0) return null
                    return cells.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() }
                }
                val term = cell("term")
                val meaning = cell("meaning_fa")
                when {
                    term.isNullOrBlank() && meaning.isNullOrBlank() -> Unit
                    term.isNullOrBlank() -> {
                        missingTerm++
                        rejected += RejectedRow(line, "", "missing_term")
                    }
                    meaning.isNullOrBlank() -> {
                        missingMeaning++
                        rejected += RejectedRow(line, term, "missing_meaning")
                    }
                    else -> {
                        val norm = TextNormalizer.normalizeKey(term)
                        if (db.wordDao().findByTermNorm(deck.id, norm) != null) {
                            duplicates++
                            rejected += RejectedRow(line, term, "duplicate")
                        } else {
                            val now = clock.now().toEpochMilliseconds()
                            val wordId = db.wordDao().insert(
                                WordEntity(
                                    deckId = deck.id,
                                    term = term,
                                    termNorm = norm,
                                    meaningFa = meaning,
                                    pos = cell("pos"),
                                    ipa = cell("ipa"),
                                    exampleEn = cell("example_en"),
                                    exampleFa = cell("example_fa"),
                                    source = WordSource.CSV.name,
                                    createdAt = now,
                                    updatedAt = now,
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
                            cell("tags")?.split(',', '،')
                                ?.map { it.trim() }
                                ?.filter { it.isNotEmpty() }
                                ?.forEach { tag ->
                                    db.tagDao().insert(ir.wazhebox.data.db.TagEntity(name = tag))
                                    val entity = db.tagDao().byName(tag)
                                    if (entity != null) {
                                        db.tagDao().link(ir.wazhebox.data.db.WordTagEntity(wordId, entity.id))
                                        reportedTags += tag
                                    }
                                }
                            added++
                        }
                    }
                }
            }
        }

        return ImportReport(
            rowsRead = dataRows.size,
            added = added,
            duplicates = duplicates,
            missingMeaning = missingMeaning,
            missingTerm = missingTerm,
            invalid = invalid,
            tagsCreated = (db.tagDao().count() - tagsBefore).coerceAtLeast(reportedTags.size),
            unknownColumns = unknown,
            rejected = rejected,
        )
    }

    private fun parseBackup(text: String): BackupFile? = runCatching {
        json.decodeFromString<BackupFile>(text)
    }.getOrNull()

    override suspend fun previewBackup(text: String): BackupResult {
        val file = parseBackup(text) ?: return BackupResult.Corrupt
        if (file.format != "wazhebox.backup") return BackupResult.Corrupt
        if (file.version > 1) return BackupResult.NewerVersion
        return BackupResult.Success(
            BackupPreview(
                decks = file.decks.size,
                words = file.words.size,
                reviews = file.reviews.size,
            ),
        )
    }

    override suspend fun restoreBackup(text: String, merge: Boolean): BackupResult {
        val file = parseBackup(text) ?: return BackupResult.Corrupt
        if (file.format != "wazhebox.backup") return BackupResult.Corrupt
        if (file.version > 1) return BackupResult.NewerVersion
        if (file.words.any { it.term.isBlank() || it.meaning_fa.isBlank() }) return BackupResult.Corrupt

        val now = clock.now().toEpochMilliseconds()
        val wordIdMap = mutableMapOf<Long, Long>()
        val cardIdMap = mutableMapOf<Long, Long>()

        db.withTransaction {
            if (!merge) {
                db.clearTables()
            }
            val deckMap = mutableMapOf<Long, Long>()
            file.decks.forEach { deck ->
                val existing = db.deckDao().byName(deck.name)
                val id = existing?.id ?: db.deckDao().insert(
                    DeckEntity(
                        name = deck.name,
                        description = deck.description,
                        isDefault = deck.is_default,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                deckMap[deck.id] = id
            }
            val fallbackDeck = deckRepository.ensureDefaultDeck()
            file.words.forEach { word ->
                val deckId = deckMap[word.deck_id] ?: fallbackDeck.id
                val norm = TextNormalizer.normalizeKey(word.term)
                val existing = db.wordDao().findByTermNorm(deckId, norm)
                val wordId = if (existing != null) {
                    if (merge) {
                        db.wordDao().update(
                            existing.copy(
                                term = word.term,
                                meaningFa = word.meaning_fa,
                                pos = word.pos,
                                ipa = word.ipa,
                                exampleEn = word.example_en,
                                exampleFa = word.example_fa,
                                notes = word.notes,
                                updatedAt = now,
                            ),
                        )
                        existing.id
                    } else {
                        existing.id
                    }
                } else {
                    db.wordDao().insert(
                        WordEntity(
                            deckId = deckId,
                            term = word.term,
                            termNorm = norm,
                            meaningFa = word.meaning_fa,
                            pos = word.pos,
                            ipa = word.ipa,
                            exampleEn = word.example_en,
                            exampleFa = word.example_fa,
                            notes = word.notes,
                            source = WordSource.CSV.name,
                            createdAt = word.created_at.takeIf { it > 0 } ?: now,
                            updatedAt = now,
                        ),
                    )
                }
                wordIdMap[word.id] = wordId
                word.tags.forEach { tag ->
                    db.tagDao().insert(ir.wazhebox.data.db.TagEntity(name = tag))
                    db.tagDao().byName(tag)?.let {
                        db.tagDao().link(ir.wazhebox.data.db.WordTagEntity(wordId, it.id))
                    }
                }
            }
            file.cards.forEach { card ->
                val newWordId = wordIdMap[card.word_id] ?: return@forEach
                val existingCard = db.cardDao().forWord(newWordId)
                    .firstOrNull { it.direction == card.direction }
                if (existingCard != null) {
                    // Never discard the learner's progress while merging.
                    cardIdMap[card.id] = existingCard.id
                } else {
                    val id = db.cardDao().insert(
                        CardEntity(
                            wordId = newWordId,
                            direction = card.direction,
                            state = card.state,
                            dueAt = card.due_at,
                            stability = card.stability,
                            difficulty = card.difficulty,
                            stepIndex = card.step_index,
                            reps = card.reps,
                            lapses = card.lapses,
                            lastReviewAt = card.last_review_at,
                            createdAt = now,
                        ),
                    )
                    cardIdMap[card.id] = id
                }
            }
            file.reviews.forEach { review ->
                val cardId = cardIdMap[review.card_id] ?: return@forEach
                db.reviewDao().insert(
                    ReviewEntity(
                        cardId = cardId,
                        reviewedAt = review.reviewed_at,
                        rating = review.rating,
                        verdict = review.verdict,
                        prevState = review.prev_state,
                        nextState = review.next_state,
                        elapsedDays = review.elapsed_days,
                        scheduledDays = review.scheduled_days,
                    ),
                )
            }
        }

        return BackupResult.Success(
            BackupPreview(
                decks = file.decks.size,
                words = file.words.size,
                reviews = file.reviews.size,
            ),
        )
    }
}

/** Deletes every row; used only by an explicit "replace" restore. */
private suspend fun WazheBoxDatabase.clearTables() {
    // Room enables SQLite foreign keys, so deleting words/decks cascades to
    // cards and reviews (PRD §8.2).
    wordDao().deleteAllRows()
    deckDao().deleteAllRows()
}
