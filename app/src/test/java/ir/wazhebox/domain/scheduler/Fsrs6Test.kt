package ir.wazhebox.domain.scheduler

import com.google.common.truth.Truth.assertThat
import ir.wazhebox.domain.model.Rating
import org.junit.Test

class Fsrs6Test {

    @Test
    fun `retrievability at stability equals ninety percent`() {
        assertThat(Fsrs6.retrievability(30.0, 30.0)).isWithin(0.0001).of(0.9)
        assertThat(Fsrs6.retrievability(1.0, 1.0)).isWithin(0.0001).of(0.9)
        assertThat(Fsrs6.retrievability(365.0, 365.0)).isWithin(0.0001).of(0.9)
    }

    @Test
    fun `retrievability decreases with time`() {
        val day1 = Fsrs6.retrievability(1.0, 10.0)
        val day30 = Fsrs6.retrievability(30.0, 10.0)
        assertThat(day30).isLessThan(day1)
    }

    @Test
    fun `interval at ninety percent returns stability`() {
        val interval = Fsrs6.intervalDays(30.0, 0.9, 365.0)
        assertThat(interval).isWithin(0.001).of(30.0)
    }

    @Test
    fun `lower desired retention gives a longer interval`() {
        val low = Fsrs6.intervalDays(30.0, 0.8, 365.0)
        val high = Fsrs6.intervalDays(30.0, 0.95, 365.0)
        assertThat(low).isGreaterThan(high)
    }

    @Test
    fun `interval is clamped to the maximum`() {
        assertThat(Fsrs6.intervalDays(10_000.0, 0.9, 365.0)).isAtMost(365.0)
    }

    @Test
    fun `initial stability follows the grade`() {
        val again = Fsrs6.initialStability(Rating.AGAIN)
        val good = Fsrs6.initialStability(Rating.GOOD)
        val easy = Fsrs6.initialStability(Rating.EASY)
        assertThat(again).isLessThan(good)
        assertThat(good).isLessThan(easy)
    }

    @Test
    fun `difficulty stays inside one and ten`() {
        var difficulty = 5.0
        repeat(50) {
            difficulty = Fsrs6.nextDifficulty(difficulty, Rating.AGAIN)
            assertThat(difficulty).isAtLeast(1.0)
            assertThat(difficulty).isAtMost(10.0)
        }
        repeat(50) {
            difficulty = Fsrs6.nextDifficulty(difficulty, Rating.EASY)
            assertThat(difficulty).isAtLeast(1.0)
            assertThat(difficulty).isAtMost(10.0)
        }
    }

    @Test
    fun `forgetting reduces stability but never below the floor`() {
        val next = Fsrs6.stabilityAfterForget(
            difficulty = 5.0,
            stability = 20.0,
            retrievability = 0.85,
        )
        assertThat(next).isLessThan(20.0)
        assertThat(next).isAtLeast(Fsrs6.MIN_STABILITY)
    }

    @Test
    fun `successful recall never lowers stability`() {
        val next = Fsrs6.stabilityAfterRecall(
            difficulty = 9.5,
            stability = 1.0,
            retrievability = 0.99,
            rating = Rating.HARD,
        )
        assertThat(next).isAtLeast(1.0)
    }

    @Test
    fun `same day stability does not decrease`() {
        val next = Fsrs6.stabilitySameDay(5.0, Rating.GOOD)
        assertThat(next).isAtLeast(5.0)
    }

    @Test
    fun `default weight set is fsrs-6 sized`() {
        assertThat(Fsrs6.DEFAULT_WEIGHTS).hasLength(21)
        assertThat(Fsrs6.DEFAULT_WEIGHTS[20]).isWithin(0.0001).of(0.1542)
    }

    @Test
    fun `elapsed days is fractional and exact`() {
        val start = kotlinx.datetime.Instant.parse("2026-11-01T10:00:00Z")
        val end = kotlinx.datetime.Instant.parse("2026-11-02T22:00:00Z")
        assertThat(Scheduler.elapsedDays(start, end)).isWithin(0.0001).of(1.5)
    }
}
