package ir.wazhebox.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/** The four learning directions of a card (PRD §6). */
enum class Direction {
    EN_FA,
    FA_EN,
    CLOZE,
    LISTEN,
}

/** Card lifecycle (PRD §6). */
enum class CardState {
    NEW,
    LEARNING,
    REVIEW,
    RELEARNING,
    SUSPENDED,
}

/** FSRS grades 1..4. */
enum class Rating(val grade: Int) {
    AGAIN(1),
    HARD(2),
    GOOD(3),
    EASY(4),
    ;

    companion object {
        fun fromGrade(grade: Int): Rating = entries.first { it.grade == grade }
    }
}

/** Outcome of judging a typed answer (PRD §11). */
enum class Verdict {
    CORRECT,
    TYPO,
    WRONG,
    WRONG_KEYBOARD,
    REVEALED,
    SKIPPED,
}

enum class WordSource {
    MANUAL,
    SEED,
    CSV,
    ANKI,
}

enum class PartOfSpeech(val key: String) {
    NOUN("noun"),
    VERB("verb"),
    ADJECTIVE("adj"),
    ADVERB("adv"),
    PHRASE("phrase"),
    PREPOSITION("prep"),
    OTHER("other"),
    ;

    companion object {
        fun fromKey(key: String?): PartOfSpeech? =
            entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) }
    }
}

data class Deck(
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val isDefault: Boolean = false,
    val archived: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class Word(
    val id: Long = 0,
    val deckId: Long,
    val term: String,
    val termNorm: String,
    val meaningFa: String,
    val pos: String? = null,
    val ipa: String? = null,
    val exampleEn: String? = null,
    val exampleFa: String? = null,
    val notes: String? = null,
    val source: WordSource = WordSource.MANUAL,
    val suspended: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** Multiple meanings are stored pipe-separated: `خانه|منزل`. */
    val meanings: List<String>
        get() = meaningFa.split('|').map { it.trim() }.filter { it.isNotEmpty() }

    val primaryMeaning: String
        get() = meanings.firstOrNull() ?: meaningFa

    val hasExample: Boolean
        get() = !exampleEn.isNullOrBlank()
}

data class Card(
    val id: Long = 0,
    val wordId: Long,
    val direction: Direction,
    val state: CardState,
    val dueAt: Instant,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val stepIndex: Int = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val lastReviewAt: Instant? = null,
    val createdAt: Instant,
) {
    /** Box shown to the user, derived from stability (PRD §5.1, §9). */
    val box: Int
        get() = BoxMapper.boxOf(state, stability)
}

data class ReviewLog(
    val id: Long = 0,
    val cardId: Long,
    val reviewedAt: Instant,
    val rating: Rating,
    val answerText: String? = null,
    val verdict: Verdict? = null,
    val durationMs: Long = 0,
    val prevState: CardState,
    val prevStability: Double? = null,
    val prevDifficulty: Double? = null,
    val nextState: CardState,
    val nextStability: Double? = null,
    val nextDifficulty: Double? = null,
    val elapsedDays: Double = 0.0,
    val scheduledDays: Double = 0.0,
    val undone: Boolean = false,
)

data class DailyStat(
    val studyDay: LocalDate,
    val reviewsDone: Int = 0,
    val newIntroduced: Int = 0,
    val correctCount: Int = 0,
    val totalTimeMs: Long = 0,
)

data class Tag(val id: Long = 0, val name: String)

data class WordWithCards(
    val word: Word,
    val cards: List<Card>,
) {
    val enFa: Card? get() = cards.firstOrNull { it.direction == Direction.EN_FA }
    val faEn: Card? get() = cards.firstOrNull { it.direction == Direction.FA_EN }
    val cloze: Card? get() = cards.firstOrNull { it.direction == Direction.CLOZE }
}

/** A card paired with its word, as used by the review queue. */
data class StudyItem(
    val word: Word,
    val card: Card,
)
