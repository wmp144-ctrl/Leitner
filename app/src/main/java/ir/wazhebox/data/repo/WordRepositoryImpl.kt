package ir.wazhebox.data.repo

import androidx.room.withTransaction
import ir.wazhebox.core.time.StudyClock
import ir.wazhebox.data.db.CardEntity
import ir.wazhebox.data.db.TagEntity
import ir.wazhebox.data.db.WordEntity
import ir.wazhebox.data.db.WordTagEntity
import ir.wazhebox.data.db.WazheBoxDatabase
import ir.wazhebox.domain.grading.TextNormalizer
import ir.wazhebox.domain.model.CardState
import ir.wazhebox.domain.model.Direction
import ir.wazhebox.domain.model.Word
import ir.wazhebox.domain.model.WordSource
import ir.wazhebox.domain.model.WordWithCards
import ir.wazhebox.domain.repo.AddWordResult
import ir.wazhebox.domain.repo.InvalidReason
import ir.wazhebox.domain.repo.WordDraft
import ir.wazhebox.domain.repo.WordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WordRepositoryImpl @Inject constructor(
    private val db: WazheBoxDatabase,
    private val clock: StudyClock,
) : WordRepository {

    private val wordDao = db.wordDao()
    private val cardDao = db.cardDao()
    private val tagDao = db.tagDao()

    override fun observeLibrary(): Flow<List<WordWithCards>> =
        wordDao.observeAllWithCards().map { rows -> rows.map { it.toDomain() } }

    override fun observeWord(id: Long): Flow<WordWithCards?> =
        wordDao.observeWithCards(id).map { it?.toDomain() }

    override fun observeWordCount(): Flow<Int> = wordDao.observeWordCount()

    override suspend fun all(): List<WordWithCards> =
        wordDao.allWithCards().map { it.toDomain() }

    override suspend fun word(id: Long): WordWithCards? = wordDao.withCards(id)?.toDomain()

    override suspend fun findDuplicate(deckId: Long, term: String): WordWithCards? {
        val norm = TextNormalizer.normalizeKey(term)
        val existing = wordDao.findByTermNorm(deckId, norm) ?: return null
        return wordDao.withCards(existing.id)?.toDomain()
    }

    override suspend fun addOrUpdate(draft: WordDraft, allowDuplicate: Boolean): AddWordResult {
        val term = draft.term.trim()
        val meaning = draft.meaningFa.trim()
        if (term.isEmpty()) return AddWordResult.Invalid(InvalidReason.EMPTY_TERM)
        if (meaning.isEmpty()) return AddWordResult.Invalid(InvalidReason.EMPTY_MEANING)
        if (term.length > 120 || meaning.length > 240) {
            return AddWordResult.Invalid(InvalidReason.TOO_LONG)
        }

        val now = clock.now().toEpochMillis()
        val norm = TextNormalizer.normalizeKey(term)

        return db.withTransaction {
            if (draft.id == 0L) {
                val duplicate = wordDao.findByTermNorm(draft.deckId, norm)
                if (duplicate != null && !allowDuplicate) {
                    return@withTransaction AddWordResult.Duplicate(duplicate.id)
                }
                val wordId = wordDao.insert(
                    WordEntity(
                        deckId = draft.deckId,
                        term = term,
                        termNorm = norm,
                        meaningFa = meaning,
                        pos = draft.pos,
                        ipa = draft.ipa?.trim()?.takeIf { it.isNotEmpty() },
                        exampleEn = draft.exampleEn?.trim()?.takeIf { it.isNotEmpty() },
                        exampleFa = draft.exampleFa?.trim()?.takeIf { it.isNotEmpty() },
                        notes = draft.notes?.trim()?.takeIf { it.isNotEmpty() },
                        source = WordSource.MANUAL.name,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                cardDao.insert(
                    CardEntity(
                        wordId = wordId,
                        direction = Direction.EN_FA.name,
                        state = CardState.NEW.name,
                        dueAt = now,
                        createdAt = now,
                    ),
                )
                applyTags(wordId, draft.tags)
                AddWordResult.Added(wordId)
            } else {
                val existing = wordDao.byId(draft.id)
                    ?: return@withTransaction AddWordResult.Invalid(InvalidReason.EMPTY_TERM)
                // Renaming a word to one that already exists in the deck would hit
                // the unique (deck_id, term_norm) index and crash the screen, so the
                // edit path asks the same duplicate question the insert path does.
                val duplicate = wordDao.findByTermNorm(draft.deckId, norm)
                if (duplicate != null && duplicate.id != existing.id && !allowDuplicate) {
                    return@withTransaction AddWordResult.Duplicate(duplicate.id)
                }
                wordDao.update(
                    existing.copy(
                        term = term,
                        termNorm = norm,
                        meaningFa = meaning,
                        pos = draft.pos,
                        ipa = draft.ipa?.trim()?.takeIf { it.isNotEmpty() },
                        exampleEn = draft.exampleEn?.trim()?.takeIf { it.isNotEmpty() },
                        exampleFa = draft.exampleFa?.trim()?.takeIf { it.isNotEmpty() },
                        notes = draft.notes?.trim()?.takeIf { it.isNotEmpty() },
                        updatedAt = now,
                    ),
                )
                applyTags(existing.id, draft.tags)
                AddWordResult.Updated(existing.id)
            }
        }
    }

    override suspend fun delete(words: List<Long>) {
        if (words.isEmpty()) return
        db.withTransaction { wordDao.deleteAll(words) }
    }

    override suspend fun setSuspended(words: List<Long>, suspended: Boolean) {
        val now = clock.now().toEpochMillis()
        db.withTransaction {
            words.forEach { wordDao.setSuspended(it, suspended, now) }
        }
    }

    override suspend fun setTags(wordId: Long, tags: List<String>) {
        db.withTransaction { applyTags(wordId, tags) }
    }

    override suspend fun tagsFor(wordId: Long): List<String> = tagDao.tagsForWord(wordId)

    override suspend fun createCard(wordId: Long, direction: Direction): Boolean =
        db.withTransaction {
            val word = wordDao.byId(wordId) ?: return@withTransaction false
            // A cloze card is only meaningful with a sentence to blank out.
            if (direction == Direction.CLOZE && word.exampleEn.isNullOrBlank()) {
                return@withTransaction false
            }
            if (cardDao.forWord(wordId).any { it.direction == direction.name }) {
                return@withTransaction false
            }
            val now = clock.now().toEpochMillis()
            cardDao.insert(
                CardEntity(
                    wordId = wordId,
                    direction = direction.name,
                    state = CardState.NEW.name,
                    dueAt = now,
                    createdAt = now,
                ),
            )
            true
        }

    private suspend fun applyTags(wordId: Long, tags: List<String>) {
        tagDao.clearWordTags(wordId)
        tags.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .forEach { name ->
                tagDao.insert(TagEntity(name = name))
                val tag = tagDao.byName(name) ?: return@forEach
                tagDao.link(WordTagEntity(wordId = wordId, tagId = tag.id))
            }
    }

    /** Escape hatch for the seed loader: insert a batch in one transaction. */
    suspend fun insertSeedWords(words: List<Word>, tags: Map<String, List<String>> = emptyMap()): Int {
        if (words.isEmpty()) return 0
        val now = clock.now().toEpochMillis()
        var inserted = 0
        db.withTransaction {
            words.forEach { word ->
                val existing = wordDao.findByTermNorm(word.deckId, word.termNorm)
                if (existing != null) return@forEach
                val wordId = wordDao.insert(
                    WordEntity(
                        deckId = word.deckId,
                        term = word.term,
                        termNorm = word.termNorm,
                        meaningFa = word.meaningFa,
                        pos = word.pos,
                        ipa = word.ipa,
                        exampleEn = word.exampleEn,
                        exampleFa = word.exampleFa,
                        source = WordSource.SEED.name,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                cardDao.insert(
                    CardEntity(
                        wordId = wordId,
                        direction = Direction.EN_FA.name,
                        state = CardState.NEW.name,
                        dueAt = now,
                        createdAt = now,
                    ),
                )
                applyTags(wordId, tags[word.termNorm].orEmpty())
                inserted++
            }
        }
        return inserted
    }
}
