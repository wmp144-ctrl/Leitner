package ir.wazhebox.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {

    @Query("SELECT * FROM decks WHERE archived = 0 ORDER BY is_default DESC, created_at ASC")
    fun observeActive(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks ORDER BY is_default DESC, created_at ASC")
    fun observeAll(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun byId(id: Long): DeckEntity?

    @Query("SELECT * FROM decks WHERE is_default = 1 LIMIT 1")
    suspend fun defaultDeck(): DeckEntity?

    @Query("SELECT * FROM decks WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): DeckEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(deck: DeckEntity): Long

    @Update
    suspend fun update(deck: DeckEntity)

    @Query("UPDATE decks SET archived = 1, updated_at = :now WHERE id = :id")
    suspend fun archive(id: Long, now: Long)

    @Query("UPDATE decks SET archived = 0, updated_at = :now WHERE id = :id")
    suspend fun unarchive(id: Long, now: Long)

    @Query(
        """
        UPDATE decks SET name = :name, description = :description, updated_at = :now
        WHERE id = :id
        """,
    )
    suspend fun rename(id: Long, name: String, description: String?, now: Long)

    @Query("SELECT deck_id AS deck_id, COUNT(*) AS word_count FROM words GROUP BY deck_id")
    fun observeWordCounts(): Flow<List<DeckWordCount>>

    @Query("DELETE FROM decks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM decks")
    suspend fun deleteAllRows()

    @Query("SELECT COUNT(*) FROM decks")
    suspend fun count(): Int
}

@Dao
interface WordDao {

    @Transaction
    @Query("SELECT * FROM words ORDER BY created_at DESC")
    fun observeAllWithCards(): Flow<List<WordWithCardsRow>>

    @Transaction
    @Query("SELECT * FROM words WHERE id = :id")
    fun observeWithCards(id: Long): Flow<WordWithCardsRow?>

    @Transaction
    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun withCards(id: Long): WordWithCardsRow?

    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun byId(id: Long): WordEntity?

    @Query("SELECT * FROM words WHERE deck_id = :deckId AND term_norm = :termNorm LIMIT 1")
    suspend fun findByTermNorm(deckId: Long, termNorm: String): WordEntity?

    @Query("SELECT COUNT(*) FROM words")
    fun observeWordCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM words WHERE deck_id = :deckId")
    suspend fun countInDeck(deckId: Long): Int

    @Query("SELECT COUNT(*) FROM words")
    suspend fun count(): Int

    @Query("SELECT * FROM words")
    suspend fun all(): List<WordEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(word: WordEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(words: List<WordEntity>): List<Long>

    @Update
    suspend fun update(word: WordEntity)

    @Query("UPDATE words SET suspended = :suspended, updated_at = :now WHERE id = :id")
    suspend fun setSuspended(id: Long, suspended: Boolean, now: Long)

    @Query("DELETE FROM words WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM words WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    @Query("DELETE FROM words")
    suspend fun deleteAllRows()

    @Transaction
    @Query("SELECT * FROM words")
    suspend fun allWithCards(): List<WordWithCardsRow>
}

@Dao
interface CardDao {

    @Query("SELECT * FROM cards")
    fun observeAll(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards")
    suspend fun all(): List<CardEntity>

    @Query("SELECT * FROM cards WHERE word_id = :wordId")
    suspend fun forWord(wordId: Long): List<CardEntity>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun byId(id: Long): CardEntity?

    @Query("SELECT * FROM cards WHERE state = 'NEW' ORDER BY created_at ASC")
    suspend fun newCards(): List<CardEntity>

    @Query("SELECT COUNT(*) FROM cards WHERE state = :state AND due_at <= :now")
    suspend fun countDue(state: String, now: Long): Int

    @Query("SELECT COUNT(*) FROM cards WHERE state = :state")
    suspend fun countInState(state: String): Int

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun count(): Int

    @Query("SELECT MIN(due_at) FROM cards WHERE state NOT IN ('NEW', 'SUSPENDED')")
    suspend fun nextDueAt(): Long?

    @Query(
        """
        SELECT COUNT(*) FROM cards
        WHERE state IN ('LEARNING','RELEARNING','REVIEW') AND due_at <= :now
        """,
    )
    suspend fun countDueNow(now: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(card: CardEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(cards: List<CardEntity>): List<Long>

    @Update
    suspend fun update(card: CardEntity)

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ReviewDao {

    @Insert
    suspend fun insert(review: ReviewEntity): Long

    @Query("SELECT * FROM reviews ORDER BY reviewed_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ReviewEntity>>

    @Query("SELECT * FROM reviews WHERE card_id = :cardId ORDER BY reviewed_at DESC")
    suspend fun forCard(cardId: Long): List<ReviewEntity>

    @Query("SELECT * FROM reviews WHERE card_id = :cardId AND undone = 0 ORDER BY reviewed_at DESC LIMIT 1")
    suspend fun lastForCard(cardId: Long): ReviewEntity?

    @Query("SELECT * FROM reviews ORDER BY reviewed_at DESC LIMIT 1")
    suspend fun last(): ReviewEntity?

    @Query("SELECT * FROM reviews ORDER BY reviewed_at ASC")
    suspend fun all(): List<ReviewEntity>

    @Query("SELECT COUNT(*) FROM reviews WHERE card_id = :cardId AND reviewed_at = :reviewedAt")
    suspend fun countAt(cardId: Long, reviewedAt: Long): Int

    @Query("UPDATE reviews SET undone = 1 WHERE id = :id")
    suspend fun markUndone(id: Long)

    @Query("SELECT COUNT(*) FROM reviews WHERE undone = 0 AND reviewed_at >= :from AND reviewed_at < :to")
    suspend fun countBetween(from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM reviews WHERE undone = 0")
    suspend fun count(): Int

    @Query(
        """
        SELECT COUNT(*) FROM reviews
        WHERE undone = 0 AND rating > 1 AND elapsed_days > 1.0
        """,
    )
    suspend fun countMatureCorrect(): Int

    @Query(
        """
        SELECT COUNT(*) FROM reviews
        WHERE undone = 0 AND elapsed_days > 1.0
        """,
    )
    suspend fun countMature(): Int
}

@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): TagEntity?

    @Query("SELECT * FROM tags ORDER BY name")
    suspend fun all(): List<TagEntity>

    @Query("SELECT t.name FROM tags t INNER JOIN word_tags wt ON wt.tag_id = t.id WHERE wt.word_id = :wordId")
    suspend fun tagsForWord(wordId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(link: WordTagEntity)

    @Query("DELETE FROM word_tags WHERE word_id = :wordId")
    suspend fun clearWordTags(wordId: Long)

    @Query("SELECT COUNT(*) FROM tags")
    suspend fun count(): Int
}

@Dao
interface StatsDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(stat: DailyStatEntity)

    @Query(
        """
        UPDATE daily_stats SET
          reviews_done = reviews_done + :reviews,
          new_introduced = new_introduced + :newWords,
          correct_count = correct_count + :correct,
          total_time_ms = total_time_ms + :totalMs
        WHERE study_day = :day
        """,
    )
    suspend fun increment(day: String, reviews: Int, newWords: Int, correct: Int, totalMs: Long)

    @Query(
        """
        UPDATE daily_stats SET
          reviews_done = MAX(0, reviews_done - :reviews),
          new_introduced = MAX(0, new_introduced - :newWords),
          correct_count = MAX(0, correct_count - :correct),
          total_time_ms = MAX(0, total_time_ms - :totalMs)
        WHERE study_day = :day
        """,
    )
    suspend fun decrement(day: String, reviews: Int, newWords: Int, correct: Int, totalMs: Long)

    @Query("SELECT * FROM daily_stats WHERE study_day = :day")
    suspend fun forDay(day: String): DailyStatEntity?

    @Query("SELECT * FROM daily_stats WHERE study_day >= :from ORDER BY study_day")
    fun observeSince(from: String): Flow<List<DailyStatEntity>>

    @Query("SELECT * FROM daily_stats ORDER BY study_day DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<DailyStatEntity>

    @Query("DELETE FROM daily_stats")
    suspend fun deleteAllRows()
}
