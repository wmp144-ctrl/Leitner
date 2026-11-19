package ir.wazhebox.data.repo

import ir.wazhebox.core.time.StudyClock
import ir.wazhebox.data.db.DeckEntity
import ir.wazhebox.data.db.WazheBoxDatabase
import ir.wazhebox.domain.model.Deck
import ir.wazhebox.domain.model.Level
import ir.wazhebox.domain.model.Word
import ir.wazhebox.domain.model.WordSource
import ir.wazhebox.domain.grading.TextNormalizer
import ir.wazhebox.domain.repo.DeckRepository
import ir.wazhebox.domain.repo.SeedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeckRepositoryImpl @Inject constructor(
    private val db: WazheBoxDatabase,
    private val clock: StudyClock,
) : DeckRepository {

    private val deckDao = db.deckDao()

    override suspend fun ensureDefaultDeck(): Deck {
        deckDao.defaultDeck()?.let { return it.toDomain() }
        deckDao.byName(DEFAULT_DECK_NAME)?.let { existing ->
            deckDao.update(existing.copy(isDefault = true, archived = false))
            return existing.copy(isDefault = true).toDomain()
        }
        val now = clock.now().toEpochMillis()
        val id = deckDao.insert(
            DeckEntity(
                name = DEFAULT_DECK_NAME,
                description = "دک اصلی",
                isDefault = true,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return checkNotNull(deckDao.byId(id)).toDomain()
    }

    override fun observeDecks(): Flow<List<Deck>> =
        deckDao.observeAll().map { decks -> decks.map { it.toDomain() } }

    override suspend fun createDeck(name: String, description: String?): Long {
        val now = clock.now().toEpochMillis()
        return deckDao.insert(
            DeckEntity(
                name = name.trim(),
                description = description?.trim(),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun archiveDeck(id: Long) {
        deckDao.archive(id, clock.now().toEpochMillis())
    }

    override suspend fun deleteDeck(id: Long) {
        deckDao.delete(id)
    }

    companion object {
        const val DEFAULT_DECK_NAME = "دک من"
    }
}

/**
 * Loads the bundled starter deck (assets/starter_deck.csv).
 * The CSV is parsed locally; nothing is downloaded (PRD §13.4).
 */
@Singleton
class SeedRepositoryImpl @Inject constructor(
    private val db: WazheBoxDatabase,
    private val wordRepository: WordRepositoryImpl,
    private val deckRepository: DeckRepository,
    private val clock: StudyClock,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : SeedRepository {

    override suspend fun starterDeckSize(): Int = SeedDeck.parse(readCsv()).size

    override suspend fun importStarterDeck(level: Level, limit: Int): Int {
        val deck = deckRepository.ensureDefaultDeck()
        val now = clock.now()
        val allowedLevels = when (level) {
            Level.BEGINNER -> setOf("A1")
            Level.INTERMEDIATE -> setOf("A1", "A2")
            Level.ADVANCED -> setOf("A1", "A2", "B1", "B2")
        }
        val entries = SeedDeck.parse(readCsv())
            .filter { it.level.uppercase() in allowedLevels }
            .sortedWith(compareBy({ levelRank(it.level) }, { it.rank }, { it.term }))
            .take(limit)

        val words = entries.map { entry ->
            Word(
                deckId = deck.id,
                term = entry.term,
                termNorm = TextNormalizer.normalizeKey(entry.term),
                meaningFa = entry.meaningFa,
                pos = entry.pos,
                ipa = entry.ipa,
                exampleEn = entry.exampleEn,
                exampleFa = entry.exampleFa,
                source = WordSource.SEED,
                createdAt = now,
                updatedAt = now,
            )
        }
        return wordRepository.insertSeedWords(words)
    }

    private fun readCsv(): String = context.assets.open("starter_deck.csv")
        .bufferedReader(Charsets.UTF_8)
        .use { it.readText() }

    private fun levelRank(level: String): Int = when (level.uppercase()) {
        "A1" -> 0
        "A2" -> 1
        "B1" -> 2
        "B2" -> 3
        else -> 4
    }
}

/** One row of the starter deck. */
data class SeedEntry(
    val rank: Int,
    val term: String,
    val meaningFa: String,
    val pos: String?,
    val ipa: String?,
    val exampleEn: String?,
    val exampleFa: String?,
    val level: String,
)

object SeedDeck {

    fun parse(csv: String): List<SeedEntry> {
        val lines = csv.lineSequence()
            .filter { it.isNotBlank() }
            .toList()
        if (lines.isEmpty()) return emptyList()
        val header = splitCsvLine(lines.first()).map { it.trim().lowercase() }
        val index = header.withIndex().associate { (i, name) -> name to i }
        fun column(row: List<String>, name: String): String? =
            index[name]?.let { row.getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() }

        return lines.drop(1).mapNotNull { line ->
            val row = splitCsvLine(line)
            val term = column(row, "term") ?: return@mapNotNull null
            val meaning = column(row, "meaning_fa") ?: return@mapNotNull null
            SeedEntry(
                rank = column(row, "rank")?.toIntOrNull() ?: Int.MAX_VALUE,
                term = term,
                meaningFa = meaning,
                pos = column(row, "pos"),
                ipa = column(row, "ipa"),
                exampleEn = column(row, "example_en"),
                exampleFa = column(row, "example_fa"),
                level = column(row, "level") ?: "A1",
            )
        }
    }

    /** RFC 4180-ish splitter: quoted fields, doubled quotes, commas inside. */
    fun splitCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val char = line[i]
            when {
                inQuotes && char == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    cells += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            i++
        }
        cells += current.toString()
        return cells
    }
}
