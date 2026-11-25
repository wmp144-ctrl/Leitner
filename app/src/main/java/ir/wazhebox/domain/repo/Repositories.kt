package ir.wazhebox.domain.repo

import ir.wazhebox.domain.model.Card
import ir.wazhebox.domain.model.DailyStat
import ir.wazhebox.domain.model.Deck
import ir.wazhebox.domain.model.Rating
import ir.wazhebox.domain.model.StudyItem
import ir.wazhebox.domain.model.Verdict
import ir.wazhebox.domain.model.Word
import ir.wazhebox.domain.model.WordWithCards
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** Draft used by the add/edit screen. */
data class WordDraft(
    val id: Long = 0,
    val deckId: Long,
    val term: String,
    val meaningFa: String,
    val pos: String? = null,
    val ipa: String? = null,
    val exampleEn: String? = null,
    val exampleFa: String? = null,
    val notes: String? = null,
    val tags: List<String> = emptyList(),
)

sealed interface AddWordResult {
    data class Added(val wordId: Long) : AddWordResult
    data class Updated(val wordId: Long) : AddWordResult
    data class Duplicate(val existingWordId: Long) : AddWordResult
    data class Invalid(val reason: InvalidReason) : AddWordResult
}

enum class InvalidReason {
    EMPTY_TERM,
    EMPTY_MEANING,
    TOO_LONG,
}

interface WordRepository {
    fun observeLibrary(): Flow<List<WordWithCards>>
    fun observeWord(id: Long): Flow<WordWithCards?>
    fun observeWordCount(): Flow<Int>
    suspend fun all(): List<WordWithCards>
    suspend fun word(id: Long): WordWithCards?
    suspend fun findDuplicate(deckId: Long, term: String): WordWithCards?
    suspend fun addOrUpdate(draft: WordDraft, allowDuplicate: Boolean = false): AddWordResult
    suspend fun delete(words: List<Long>)
    suspend fun setSuspended(words: List<Long>, suspended: Boolean)
    suspend fun setTags(wordId: Long, tags: List<String>)
    suspend fun tagsFor(wordId: Long): List<String>
    /** Adds a CLOZE or LISTEN card to an existing word. False when it already exists. */
    suspend fun createCard(wordId: Long, direction: ir.wazhebox.domain.model.Direction): Boolean

    /** Every tag in the library, alphabetically. */
    fun observeTags(): Flow<List<String>>

    /** Word id to its tags, so library rows can be filtered by tag. */
    fun observeWordTags(): Flow<Map<Long, List<String>>>

    /** Words that keep coming back (lapses on their EN→FA card). */
    suspend fun troubleWords(minLapses: Int = 4, limit: Int = 100): List<WordWithCards>

    /** Words answered wrong since [since], most-missed first. */
    suspend fun mistakeWords(since: kotlinx.datetime.Instant, limit: Int = 100): List<MistakeWord>
}

/** A word plus how often it was missed, for the trouble-words screen. */
data class MistakeWord(
    val word: WordWithCards,
    val mistakes: Int,
    val lastAt: kotlinx.datetime.Instant,
)

/** Where the review queue comes from. */
enum class SessionSource {
    /** Cards that are due now, the normal SRS session. */
    DUE,
    /** Leech words, regardless of their due date. */
    TROUBLE,
    /** Words missed recently, regardless of their due date. */
    MISTAKES,
}

/** A deck with its word count, for the deck list. */
data class DeckSummary(
    val deck: Deck,
    val wordCount: Int,
)

interface DeckRepository {
    suspend fun ensureDefaultDeck(): Deck
    fun observeDecks(): Flow<List<Deck>>
    fun observeDecksWithCounts(): Flow<List<DeckSummary>>
    suspend fun createDeck(name: String, description: String? = null): Long
    suspend fun renameDeck(id: Long, name: String, description: String? = null)
    suspend fun archiveDeck(id: Long)
    suspend fun unarchiveDeck(id: Long)
    suspend fun deleteDeck(id: Long)
}

/** What the home/review screens need to know about today's workload. */
data class Workload(
    val dueCount: Int,
    val nextDueAt: kotlinx.datetime.Instant?,
    val totalWords: Int,
    val newCount: Int,
    val learningCount: Int,
    val reviewCount: Int,
)

/** One graded review plus everything needed to undo it. */
data class GradeOutcome(
    val card: Card,
    val reviewId: Long,
    val cardBefore: Card,
    val rating: Rating,
    val wasNew: Boolean,
    val leechSuggestion: Boolean,
    val faEnCardCreated: Boolean,
    val clockAnomaly: Boolean,
    /** Answer time, so undo can also take it back out of the daily total. */
    val durationMs: Long = 0,
)

interface StudyRepository {
    /**
     * [deckId] limits the session to one deck; null means every deck.
     * [source] picks a targeted drill instead of the normal due queue.
     */
    suspend fun buildSession(
        freePractice: Boolean = false,
        deckId: Long? = null,
        source: SessionSource = SessionSource.DUE,
    ): List<StudyItem>
    suspend fun workload(freePractice: Boolean = false, deckId: Long? = null): Workload
    suspend fun grade(
        cardId: Long,
        rating: Rating,
        verdict: Verdict? = null,
        answerText: String? = null,
        durationMs: Long = 0,
    ): GradeOutcome?
    suspend fun undo(outcome: GradeOutcome)
    suspend fun suspendCard(cardId: Long, suspended: Boolean)
    suspend fun stateOfCard(cardId: Long): Card?
    /** Free practice never writes to the database (PRD §9.4 rule 6). */
    suspend fun nextFreePracticeCard(excludeIds: Set<Long>): StudyItem?
}

data class RetentionStat(val correct: Int, val total: Int) {
    val rate: Double get() = if (total == 0) 0.0 else correct.toDouble() / total
}

interface StatsRepository {
    fun observeDailyStats(days: Int = 30): Flow<List<DailyStat>>
    suspend fun today(): DailyStat
    suspend fun retention(): RetentionStat
    suspend fun boxDistribution(): Map<Int, Int>
    suspend fun totalReviews(): Int
}

