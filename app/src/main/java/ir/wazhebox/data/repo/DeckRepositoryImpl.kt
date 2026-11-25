package ir.wazhebox.data.repo

import ir.wazhebox.core.time.StudyClock
import ir.wazhebox.data.db.DeckEntity
import ir.wazhebox.data.db.WazheBoxDatabase
import ir.wazhebox.domain.model.Deck
import ir.wazhebox.domain.repo.DeckRepository
import ir.wazhebox.domain.repo.DeckSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

    override fun observeDecksWithCounts(): Flow<List<DeckSummary>> =
        combine(deckDao.observeAll(), deckDao.observeWordCounts()) { decks, counts ->
            val byDeck = counts.associate { it.deckId to it.wordCount }
            decks.map { DeckSummary(deck = it.toDomain(), wordCount = byDeck[it.id] ?: 0) }
        }

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

    override suspend fun renameDeck(id: Long, name: String, description: String?) {
        deckDao.rename(
            id = id,
            name = name.trim(),
            description = description?.trim(),
            now = clock.now().toEpochMillis(),
        )
    }

    override suspend fun archiveDeck(id: Long) {
        deckDao.archive(id, clock.now().toEpochMillis())
    }

    override suspend fun unarchiveDeck(id: Long) {
        deckDao.unarchive(id, clock.now().toEpochMillis())
    }

    override suspend fun deleteDeck(id: Long) {
        // The default deck is where imported words land, so it must survive.
        if (deckDao.byId(id)?.isDefault == true) return
        deckDao.delete(id)
    }

    companion object {
        const val DEFAULT_DECK_NAME = "دک من"
    }
}

