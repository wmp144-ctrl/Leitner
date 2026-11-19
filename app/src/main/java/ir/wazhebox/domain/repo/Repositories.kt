package ir.wazhebox.domain.repo

import ir.wazhebox.domain.model.Card
import ir.wazhebox.domain.model.DailyStat
import ir.wazhebox.domain.model.Deck
import ir.wazhebox.domain.model.Level
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
}

interface DeckRepository {
    suspend fun ensureDefaultDeck(): Deck
    fun observeDecks(): Flow<List<Deck>>
    suspend fun createDeck(name: String, description: String? = null): Long
    suspend fun archiveDeck(id: Long)
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
)

interface StudyRepository {
    suspend fun buildSession(freePractice: Boolean = false): List<StudyItem>
    suspend fun workload(freePractice: Boolean = false): Workload
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

interface SeedRepository {
    suspend fun importStarterDeck(level: Level, limit: Int = 100): Int
    suspend fun starterDeckSize(): Int
}
