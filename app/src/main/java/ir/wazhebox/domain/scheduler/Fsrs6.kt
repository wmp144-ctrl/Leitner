package ir.wazhebox.domain.scheduler

import ir.wazhebox.domain.model.Rating
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * FSRS-6 memory model (PRD §9).
 *
 * Pure functions only: no Android, no clock, no database. The equations and the
 * default 21 weights follow the published FSRS-6 algorithm
 * (open-spaced-repetition, "The Algorithm"), so the scheduler can be unit-tested
 * and later upgraded to per-user optimisation without touching storage.
 *
 * w[0..3]  initial stability per grade      w[4..5]  initial difficulty
 * w[6]     difficulty delta                 w[7]     mean reversion
 * w[8..10] stability growth on recall       w[11..14] stability after lapse
 * w[15..16] hard penalty / easy bonus       w[17..19] same-day stability
 * w[20]    forgetting-curve decay
 */
object Fsrs6 {

    val DEFAULT_WEIGHTS: DoubleArray = doubleArrayOf(
        0.212, 1.2931, 2.3065, 8.2956,
        6.4133, 0.8334, 3.0194, 0.001,
        1.8722, 0.1666, 0.796, 1.4835,
        0.0614, 0.2629, 1.6483, 0.6014,
        1.8729, 0.5425, 0.0912, 0.0658,
        0.1542,
    )

    const val MIN_DIFFICULTY = 1.0
    const val MAX_DIFFICULTY = 10.0
    const val MIN_STABILITY = 0.01

    private fun w(weights: DoubleArray, index: Int): Double = weights[index]

    /** DECAY = -w20 (negative), so the forgetting curve falls with time. */
    fun decay(weights: DoubleArray = DEFAULT_WEIGHTS): Double = -w(weights, 20)

    /** FACTOR keeps R(t = S) = 0.9 for any decay. */
    fun factor(weights: DoubleArray = DEFAULT_WEIGHTS): Double {
        val d = decay(weights)
        return 0.9.pow(1.0 / d) - 1.0
    }

    fun initialStability(rating: Rating, weights: DoubleArray = DEFAULT_WEIGHTS): Double =
        max(MIN_STABILITY, w(weights, rating.grade - 1))

    fun initialDifficulty(rating: Rating, weights: DoubleArray = DEFAULT_WEIGHTS): Double =
        clampDifficulty(w(weights, 4) - exp(w(weights, 5) * (rating.grade - 1)) + 1.0)

    /** Difficulty after a review, with mean reversion toward D0(Easy). */
    fun nextDifficulty(
        difficulty: Double,
        rating: Rating,
        weights: DoubleArray = DEFAULT_WEIGHTS,
    ): Double {
        val delta = -w(weights, 6) * (rating.grade - 3)
        val damped = difficulty + delta * (MAX_DIFFICULTY - difficulty) / 9.0
        val reverted = w(weights, 7) * initialDifficulty(Rating.EASY, weights) +
            (1 - w(weights, 7)) * damped
        return clampDifficulty(reverted)
    }

    /** Probability of recall after [elapsedDays] for a card of [stability]. */
    fun retrievability(
        elapsedDays: Double,
        stability: Double,
        weights: DoubleArray = DEFAULT_WEIGHTS,
    ): Double {
        val s = max(MIN_STABILITY, stability)
        val f = factor(weights)
        val d = decay(weights)
        val t = max(0.0, elapsedDays)
        return (1.0 + f * t / s).pow(d).coerceIn(0.0, 1.0)
    }

    /** Stability growth after a successful recall (grade >= Hard). */
    fun stabilityAfterRecall(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
        rating: Rating,
        weights: DoubleArray = DEFAULT_WEIGHTS,
    ): Double {
        val s = max(MIN_STABILITY, stability)
        val d = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)
        val hardPenalty = if (rating == Rating.HARD) w(weights, 15) else 1.0
        val easyBonus = if (rating == Rating.EASY) w(weights, 16) else 1.0
        val growth = 1.0 + exp(w(weights, 8)) *
            (11.0 - d) *
            s.pow(-w(weights, 9)) *
            (exp(w(weights, 10) * (1.0 - retrievability)) - 1.0) *
            hardPenalty *
            easyBonus
        return max(MIN_STABILITY, s * max(1.0, growth))
    }

    /** Stability after a lapse (grade = Again); capped at the previous stability. */
    fun stabilityAfterForget(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
        weights: DoubleArray = DEFAULT_WEIGHTS,
    ): Double {
        val s = max(MIN_STABILITY, stability)
        val d = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)
        val next = w(weights, 11) *
            d.pow(-w(weights, 12)) *
            ((s + 1.0).pow(w(weights, 13)) - 1.0) *
            exp(w(weights, 14) * (1.0 - retrievability))
        return max(MIN_STABILITY, min(next, s))
    }

    /** Same-day review stability (PRD §9.4 rule 4: no hand-written formula). */
    fun stabilitySameDay(
        stability: Double,
        rating: Rating,
        weights: DoubleArray = DEFAULT_WEIGHTS,
    ): Double {
        val s = max(MIN_STABILITY, stability)
        val growth = exp(w(weights, 17) * (rating.grade - 3 + w(weights, 18))) *
            s.pow(-w(weights, 19))
        return max(MIN_STABILITY, s * max(1.0, growth))
    }

    /**
     * Interval in days that reaches [desiredRetention] given [stability].
     * Inverted forgetting curve: t = S / FACTOR * (r^(1/DECAY) - 1).
     */
    fun intervalDays(
        stability: Double,
        desiredRetention: Double,
        maximumIntervalDays: Double,
        weights: DoubleArray = DEFAULT_WEIGHTS,
    ): Double {
        val s = max(MIN_STABILITY, stability)
        val r = desiredRetention.coerceIn(0.70, 0.97)
        val f = factor(weights)
        val d = decay(weights)
        val raw = s / f * (r.pow(1.0 / d) - 1.0)
        return raw.coerceIn(1.0, maximumIntervalDays)
    }

    /** Rounded, at-least-one-day interval used for due dates. */
    fun intervalDaysRounded(
        stability: Double,
        desiredRetention: Double,
        maximumIntervalDays: Double,
        weights: DoubleArray = DEFAULT_WEIGHTS,
    ): Long = intervalDays(stability, desiredRetention, maximumIntervalDays, weights)
        .roundToLong()
        .coerceAtLeast(1L)

    fun clampDifficulty(value: Double): Double = value.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    fun clampStability(value: Double): Double = max(MIN_STABILITY, value)
}
