package ir.wazhebox.domain.scheduler

import com.google.common.truth.Truth.assertThat
import ir.wazhebox.domain.model.Card
import ir.wazhebox.domain.model.CardState
import ir.wazhebox.domain.model.Direction
import ir.wazhebox.domain.model.Rating
import kotlinx.datetime.Instant
import org.junit.Test

/** Acceptance tests for scheduling (PRD §9, Appendix B). */
class SchedulerTest {

    private val now = Instant.parse("2026-11-19T10:00:00Z")
    private val day = 86_400_000L

    private fun card(
        state: CardState = CardState.NEW,
        stability: Double = 0.0,
        difficulty: Double = 0.0,
        lastReviewAt: Instant? = null,
        stepIndex: Int = 0,
        reps: Int = 0,
        lapses: Int = 0,
    ) = Card(
        id = 1,
        wordId = 1,
        direction = Direction.EN_FA,
        state = state,
        dueAt = now,
        stability = stability,
        difficulty = difficulty,
        stepIndex = stepIndex,
        reps = reps,
        lapses = lapses,
        lastReviewAt = lastReviewAt,
        createdAt = now,
    )

    @Test
    fun `new card graded good with learning steps moves to the second step`() {
        val result = Scheduler.schedule(card(), Rating.GOOD, now, SchedulerConfig())
        assertThat(result.state).isEqualTo(CardState.LEARNING)
        assertThat(result.stepIndex).isEqualTo(1)
        assertThat(result.dueAt.toEpochMilliseconds() - now.toEpochMilliseconds())
            .isEqualTo(10 * 60_000L)
    }

    @Test
    fun `new card graded easy graduates to four days`() {
        val result = Scheduler.schedule(card(), Rating.EASY, now, SchedulerConfig())
        assertThat(result.state).isEqualTo(CardState.REVIEW)
        assertThat(result.intervalDays).isEqualTo(4.0)
        assertThat(result.dueAt.toEpochMilliseconds()).isEqualTo(now.toEpochMilliseconds() + 4 * day)
    }

    @Test
    fun `new card graded again returns to the first learning step`() {
        val result = Scheduler.schedule(card(), Rating.AGAIN, now, SchedulerConfig())
        assertThat(result.state).isEqualTo(CardState.LEARNING)
        assertThat(result.stepIndex).isEqualTo(0)
        assertThat(result.dueAt.toEpochMilliseconds()).isEqualTo(now.toEpochMilliseconds() + 60_000L)
    }

    @Test
    fun `good on the last learning step graduates to one day`() {
        val learning = card(state = CardState.LEARNING, stepIndex = 1, stability = 1.29, difficulty = 6.4)
        val result = Scheduler.schedule(learning, Rating.GOOD, now, SchedulerConfig())
        assertThat(result.state).isEqualTo(CardState.REVIEW)
        assertThat(result.intervalDays).isAtMost(1.0)
        assertThat(result.dueAt.toEpochMilliseconds()).isAtLeast(now.toEpochMilliseconds() + day - 1000)
    }

    @Test
    fun `without learning steps a new card graded good is due tomorrow`() {
        val config = SchedulerConfig(learningStepsMinutes = emptyList())
        val result = Scheduler.schedule(card(), Rating.GOOD, now, config)
        assertThat(result.state).isEqualTo(CardState.REVIEW)
        assertThat(result.intervalDays).isEqualTo(1.0)
    }

    @Test
    fun `review card graded again goes to relearning ten minutes later`() {
        val review = card(
            state = CardState.REVIEW,
            stability = 12.0,
            difficulty = 5.0,
            lastReviewAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 12 * day),
            reps = 3,
        )
        val result = Scheduler.schedule(review, Rating.AGAIN, now, SchedulerConfig())
        assertThat(result.state).isEqualTo(CardState.RELEARNING)
        assertThat(result.dueAt.toEpochMilliseconds()).isEqualTo(now.toEpochMilliseconds() + 10 * 60_000L)
        assertThat(result.stability).isLessThan(12.0)
    }

    @Test
    fun `review card graded good grows stability`() {
        val review = card(
            state = CardState.REVIEW,
            stability = 12.0,
            difficulty = 5.0,
            lastReviewAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 12 * day),
        )
        val result = Scheduler.schedule(review, Rating.GOOD, now, SchedulerConfig())
        assertThat(result.state).isEqualTo(CardState.REVIEW)
        assertThat(result.stability).isGreaterThan(12.0)
        assertThat(result.difficulty).isWithin(0.01).of(5.0)
    }

    @Test
    fun `hard review grows stability less than easy`() {
        val review = card(
            state = CardState.REVIEW,
            stability = 20.0,
            difficulty = 5.0,
            lastReviewAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 20 * day),
        )
        val hard = Scheduler.schedule(review, Rating.HARD, now, SchedulerConfig())
        val easy = Scheduler.schedule(review, Rating.EASY, now, SchedulerConfig())
        assertThat(easy.stability).isGreaterThan(hard.stability)
    }

    @Test
    fun `interval never exceeds the configured maximum`() {
        var current = card(
            state = CardState.REVIEW,
            stability = 200.0,
            difficulty = 3.0,
            lastReviewAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 200 * day),
        )
        var moment = now
        repeat(10) {
            val result = Scheduler.schedule(current, Rating.EASY, moment, SchedulerConfig())
            assertThat(result.intervalDays).isAtMost(365.0)
            moment = result.dueAt
            current = current.copy(
                state = result.state,
                stability = result.stability,
                difficulty = result.difficulty,
                lastReviewAt = moment,
                reps = current.reps + 1,
            ).copy(lastReviewAt = Instant.fromEpochMilliseconds(moment.toEpochMilliseconds() - day))
        }
    }

    @Test
    fun `clock moved backwards yields zero elapsed days and a flag`() {
        val future = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + 30 * day)
        val review = card(
            state = CardState.REVIEW,
            stability = 10.0,
            difficulty = 5.0,
            lastReviewAt = future,
        )
        val result = Scheduler.schedule(review, Rating.GOOD, now, SchedulerConfig())
        assertThat(result.clockAnomaly).isTrue()
        assertThat(result.elapsedDays).isEqualTo(0.0)
        assertThat(result.dueAt.toEpochMilliseconds()).isGreaterThan(now.toEpochMilliseconds())
    }

    @Test
    fun `suspended card keeps its due date`() {
        val suspended = card(state = CardState.SUSPENDED, stability = 9.0, difficulty = 5.0)
        val result = Scheduler.schedule(suspended, Rating.GOOD, now, SchedulerConfig())
        assertThat(result.state).isEqualTo(CardState.SUSPENDED)
        assertThat(result.dueAt).isEqualTo(suspended.dueAt)
    }

    @Test
    fun `retention target changes the interval`() {
        val review = card(
            state = CardState.REVIEW,
            stability = 30.0,
            difficulty = 5.0,
            lastReviewAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 30 * day),
        )
        val relaxed = Scheduler.schedule(review, Rating.GOOD, now, SchedulerConfig(desiredRetention = 0.80))
        val strict = Scheduler.schedule(review, Rating.GOOD, now, SchedulerConfig(desiredRetention = 0.95))
        assertThat(relaxed.intervalDays).isGreaterThan(strict.intervalDays)
    }

    @Test
    fun `sub-day review of a review card is scheduled for tomorrow`() {
        val review = card(
            state = CardState.REVIEW,
            stability = 3.0,
            difficulty = 5.0,
            lastReviewAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 60_000L),
        )
        val result = Scheduler.schedule(review, Rating.GOOD, now, SchedulerConfig())
        assertThat(result.state).isEqualTo(CardState.REVIEW)
        assertThat(result.dueAt.toEpochMilliseconds()).isAtLeast(now.toEpochMilliseconds() + day - 1000)
    }

    @Test
    fun `relearning good returns to review with a positive interval`() {
        val relearning = card(
            state = CardState.RELEARNING,
            stability = 2.0,
            difficulty = 6.0,
            lastReviewAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 2 * day),
        )
        val result = Scheduler.schedule(relearning, Rating.GOOD, now, SchedulerConfig())
        assertThat(result.state).isEqualTo(CardState.REVIEW)
        assertThat(result.intervalDays).isAtLeast(1.0)
    }

    @Test
    fun `box mapping follows stability`() {
        val config = SchedulerConfig()
        val fresh = Scheduler.schedule(card(), Rating.AGAIN, now, config)
        assertThat(fresh.nextBox).isEqualTo(1)

        val mature = Scheduler.schedule(
            card(
                state = CardState.REVIEW,
                stability = 90.0,
                difficulty = 4.0,
                lastReviewAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 90 * day),
            ),
            Rating.EASY,
            now,
            config,
        )
        assertThat(mature.nextBox).isEqualTo(6)
    }
}
