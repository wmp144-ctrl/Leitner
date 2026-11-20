package ir.wazhebox.data.repo

import ir.wazhebox.core.time.StudyClock
import ir.wazhebox.data.db.DeckEntity
import ir.wazhebox.data.db.WazheBoxDatabase
import ir.wazhebox.domain.model.Deck
import ir.wazhebox.domain.repo.DeckRepository
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

