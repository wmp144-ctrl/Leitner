package ir.wazhebox.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    @ColumnInfo(name = "is_default") val isDefault: Boolean = false,
    val archived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "words",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deck_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["deck_id", "term_norm"], unique = true),
        Index(value = ["term_norm"]),
    ],
)
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "deck_id") val deckId: Long,
    val term: String,
    @ColumnInfo(name = "term_norm") val termNorm: String,
    @ColumnInfo(name = "meaning_fa") val meaningFa: String,
    val pos: String? = null,
    val ipa: String? = null,
    @ColumnInfo(name = "example_en") val exampleEn: String? = null,
    @ColumnInfo(name = "example_fa") val exampleFa: String? = null,
    val notes: String? = null,
    val source: String = "MANUAL",
    val suspended: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** Set by a CSV/Anki import run, so that run can be taken back in one step. */
    @ColumnInfo(name = "import_batch") val importBatch: Long? = null,
)

@Entity(
    tableName = "cards",
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["state", "due_at"]),
        Index(value = ["word_id", "direction"], unique = true),
    ],
)
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "word_id") val wordId: Long,
    val direction: String,
    val state: String,
    @ColumnInfo(name = "due_at") val dueAt: Long,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    @ColumnInfo(name = "step_index") val stepIndex: Int = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
    @ColumnInfo(name = "last_review_at") val lastReviewAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "reviews",
    foreignKeys = [
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["id"],
            childColumns = ["card_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["card_id", "reviewed_at"]),
        Index(value = ["reviewed_at"]),
    ],
)
data class ReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "card_id") val cardId: Long,
    @ColumnInfo(name = "reviewed_at") val reviewedAt: Long,
    val rating: Int,
    @ColumnInfo(name = "answer_text") val answerText: String? = null,
    val verdict: String? = null,
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0,
    @ColumnInfo(name = "prev_state") val prevState: String,
    @ColumnInfo(name = "prev_stability") val prevStability: Double? = null,
    @ColumnInfo(name = "prev_difficulty") val prevDifficulty: Double? = null,
    @ColumnInfo(name = "next_state") val nextState: String,
    @ColumnInfo(name = "next_stability") val nextStability: Double? = null,
    @ColumnInfo(name = "next_difficulty") val nextDifficulty: Double? = null,
    @ColumnInfo(name = "elapsed_days") val elapsedDays: Double = 0.0,
    @ColumnInfo(name = "scheduled_days") val scheduledDays: Double = 0.0,
    val undone: Boolean = false,
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "word_tags",
    primaryKeys = ["word_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tag_id"])],
)
data class WordTagEntity(
    @ColumnInfo(name = "word_id") val wordId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)

@Entity(tableName = "daily_stats")
data class DailyStatEntity(
    @PrimaryKey @ColumnInfo(name = "study_day") val studyDay: String,
    @ColumnInfo(name = "reviews_done") val reviewsDone: Int = 0,
    @ColumnInfo(name = "new_introduced") val newIntroduced: Int = 0,
    @ColumnInfo(name = "correct_count") val correctCount: Int = 0,
    @ColumnInfo(name = "total_time_ms") val totalTimeMs: Long = 0,
)

/** Word with all of its cards, for library rows and review hydration. */
data class WordWithCardsRow(
    @Embedded val word: WordEntity,
    @Relation(parentColumn = "id", entityColumn = "word_id")
    val cards: List<CardEntity>,
) {
    val enFa: CardEntity? get() = cards.firstOrNull { it.direction == "EN_FA" }
    val faEn: CardEntity? get() = cards.firstOrNull { it.direction == "FA_EN" }
}

/** One word's tag, flattened for the library's tag filter. */
data class WordTagRow(
    @ColumnInfo(name = "word_id") val wordId: Long,
    val name: String,
)

/** A word and how often it was missed, for the trouble-words screen. */
data class MistakeRow(
    @Embedded val word: WordEntity,
    val mistakes: Int,
    @ColumnInfo(name = "last_at") val lastAt: Long,
)

/** Words per deck, for the deck list. */
data class DeckWordCount(
    @ColumnInfo(name = "deck_id") val deckId: Long,
    @ColumnInfo(name = "word_count") val wordCount: Int,
)

/** Counts for the home screen and the widget. */
data class DueCounts(
    @ColumnInfo(name = "new_count") val newCount: Int,
    @ColumnInfo(name = "learning_count") val learningCount: Int,
    @ColumnInfo(name = "review_count") val reviewCount: Int,
)
