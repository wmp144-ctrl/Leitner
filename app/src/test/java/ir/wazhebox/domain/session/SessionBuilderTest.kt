package ir.wazhebox.domain.session

import com.google.common.truth.Truth.assertThat
import ir.wazhebox.domain.model.Card
import ir.wazhebox.domain.model.CardState
import ir.wazhebox.domain.model.Direction
import ir.wazhebox.domain.model.StudyItem
import ir.wazhebox.domain.model.Word
import kotlinx.datetime.Instant
import org.junit.Test

class SessionBuilderTest {

    private val now = Instant.parse("2026-11-19T10:00:00Z")
    private val day = 86_400_000L

    private fun item(
        id: Long,
        state: CardState,
        dueAt: Instant,
        createdAt: Instant = now,
        reps: Int = 0,
    ): StudyItem {
        val word = Word(
            id = id,
            deckId = 1,
            term = "word$id",
            termNorm = "word$id",
            meaningFa = "لغت$id",
            createdAt = createdAt,
            updatedAt = createdAt,
        )
        val card = Card(
            id = id,
            wordId = id,
            direction = Direction.EN_FA,
            state = state,
            dueAt = dueAt,
            stability = if (state == CardState.REVIEW) 10.0 else 0.0,
            difficulty = if (state == CardState.REVIEW) 5.0 else 0.0,
            reps = reps,
            createdAt = createdAt,
        )
        return StudyItem(word, card)
    }

    @Test
    fun `learning cards that are due come first and are uncapped`() {
        val items = listOf(
            item(1, CardState.LEARNING, now, reps = 1),
            item(2, CardState.LEARNING, now, reps = 1),
        )
        val queue = SessionBuilder.buildQueue(items, now, SessionConfig(dailyReviewLimit = 1))
        assertThat(queue).hasSize(2)
    }

    @Test
    fun `future learning cards are excluded`() {
        val items = listOf(item(1, CardState.LEARNING, Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + 600_000)))
        assertThat(SessionBuilder.buildQueue(items, now, SessionConfig())).isEmpty()
    }

    @Test
    fun `review cards respect the daily review limit`() {
        val items = (1L..5L).map {
            item(it, CardState.REVIEW, Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - it * day))
        }
        val queue = SessionBuilder.buildQueue(items, now, SessionConfig(dailyReviewLimit = 3))
        assertThat(queue).hasSize(3)
        assertThat(queue.first().card.id).isEqualTo(5)
    }

    @Test
    fun `new cards respect the daily new limit minus what was already introduced`() {
        val items = (1L..10L).map { item(it, CardState.NEW, now) }
        val queue = SessionBuilder.buildQueue(
            items,
            now,
            SessionConfig(dailyNewLimit = 5, newIntroducedToday = 3, dailyReviewLimit = 0),
        )
        assertThat(queue).hasSize(2)
    }

    @Test
    fun `new cards are spread through reviews instead of front loaded`() {
        val reviews = (1L..8L).map {
            item(it, CardState.REVIEW, Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - day))
        }
        val newCards = (100L..101L).map { item(it, CardState.NEW, now) }
        val queue = SessionBuilder.interleave(reviews, newCards)
        assertThat(queue).hasSize(10)
        assertThat(queue.first().card.state).isNotEqualTo(CardState.NEW)
        assertThat(queue.last().card.state).isNotEqualTo(CardState.NEW)
    }

    @Test
    fun `interleave with only new cards keeps them all`() {
        val newCards = (1L..3L).map { item(it, CardState.NEW, now) }
        assertThat(SessionBuilder.interleave(emptyList(), newCards)).hasSize(3)
    }

    @Test
    fun `suspended cards never enter the queue`() {
        val items = listOf(item(1, CardState.SUSPENDED, now))
        assertThat(SessionBuilder.buildQueue(items, now, SessionConfig())).isEmpty()
    }

    @Test
    fun `free practice ignores due dates but never affects scheduling`() {
        val items = listOf(item(1, CardState.REVIEW, Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + 10 * day)))
        val queue = SessionBuilder.buildQueue(items, now, SessionConfig(freePractice = true))
        assertThat(queue).hasSize(1)
    }

    @Test
    fun `due count matches the built queue`() {
        val items = listOf(
            item(1, CardState.LEARNING, now, reps = 1),
            item(2, CardState.REVIEW, now),
            item(3, CardState.NEW, now),
            item(4, CardState.REVIEW, Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + 5 * day)),
        )
        val config = SessionConfig(dailyReviewLimit = 10, dailyNewLimit = 10)
        assertThat(SessionBuilder.dueCount(items, now, config))
            .isEqualTo(SessionBuilder.buildQueue(items, now, config).size)
    }

    @Test
    fun `next due ignores new and suspended cards`() {
        val items = listOf(
            item(1, CardState.NEW, now),
            item(2, CardState.SUSPENDED, now),
            item(3, CardState.REVIEW, Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + day)),
        )
        assertThat(SessionBuilder.nextDue(items)?.toEpochMilliseconds())
            .isEqualTo(now.toEpochMilliseconds() + day)
    }
}
