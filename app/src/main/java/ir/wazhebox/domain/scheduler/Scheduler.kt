package ir.wazhebox.domain.scheduler

import ir.wazhebox.domain.model.BoxMapper
import ir.wazhebox.domain.model.Card
import ir.wazhebox.domain.model.CardState
import ir.wazhebox.domain.model.Rating
import kotlinx.datetime.Instant

/**
 * Scheduler configuration (PRD §9.2). Defaults are the ones the PRD fixes;
 * the user-facing settings only expose desired retention and the interval cap.
 */
data class SchedulerConfig(
    val learningStepsMinutes: List<Int> = listOf(1, 10),
    val relearningStepsMinutes: List<Int> = listOf(10),
    val graduatingIntervalDays: Int = 1,
    val easyIntervalDays: Int = 4,
    val desiredRetention: Double = 0.90,
    val maximumIntervalDays: Int = 365,
    val leechThreshold: Int = 4,
    val weights: DoubleArray = Fsrs6.DEFAULT_WEIGHTS,
) {
    init {
        require(desiredRetention in 0.70..0.99) { "desiredRetention out of range" }
        require(maximumIntervalDays >= 1) { "maximumIntervalDays must be positive" }
    }

    // DoubleArray has identity equality; make the config value-comparable anyway.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SchedulerConfig) return false
        return learningStepsMinutes == other.learningStepsMinutes &&
            relearningStepsMinutes == other.relearningStepsMinutes &&
            graduatingIntervalDays == other.graduatingIntervalDays &&
            easyIntervalDays == other.easyIntervalDays &&
            desiredRetention == other.desiredRetention &&
            maximumIntervalDays == other.maximumIntervalDays &&
            leechThreshold == other.leechThreshold &&
            weights.contentEquals(other.weights)
    }

    override fun hashCode(): Int {
        var result = learningStepsMinutes.hashCode()
        result = 31 * result + relearningStepsMinutes.hashCode()
        result = 31 * result + graduatingIntervalDays
        result = 31 * result + easyIntervalDays
        result = 31 * result + desiredRetention.hashCode()
        result = 31 * result + maximumIntervalDays
        result = 31 * result + leechThreshold
        result = 31 * result + weights.contentHashCode()
        return result
    }
}

/** Result of scheduling one rating; everything the DB needs to persist. */
data class ScheduleResult(
    val state: CardState,
    val dueAt: Instant,
    val stability: Double,
    val difficulty: Double,
    val stepIndex: Int,
    val intervalDays: Double,
    val elapsedDays: Double,
    val clockAnomaly: Boolean = false,
    val nextBox: Int,
)

/**
 * The scheduler turns (card, rating, now) into the next card state (PRD §9.3).
 *
 * Sub-day learning steps live outside FSRS so a new card can be reviewed within
 * the same session; once a card graduates, FSRS-6 owns the interval.
 */
object Scheduler {

    fun schedule(
        card: Card,
        rating: Rating,
        now: Instant,
        config: SchedulerConfig,
    ): ScheduleResult = when (card.state) {
        CardState.NEW -> scheduleNew(card, rating, now, config)
        CardState.LEARNING -> scheduleLearning(card, rating, now, config, relearning = false)
        CardState.RELEARNING -> scheduleLearning(card, rating, now, config, relearning = true)
        CardState.REVIEW -> scheduleReview(card, rating, now, config)
        CardState.SUSPENDED -> suspendedResult(card, now, config)
    }

    private fun scheduleNew(
        card: Card,
        rating: Rating,
        now: Instant,
        config: SchedulerConfig,
    ): ScheduleResult {
        val stability = Fsrs6.initialStability(rating, config.weights)
        val difficulty = Fsrs6.initialDifficulty(rating, config.weights)
        return when (rating) {
            Rating.AGAIN, Rating.HARD -> {
                val minutes = config.learningStepsMinutes.firstOrNull() ?: 1
                steppingResult(
                    card = card,
                    state = CardState.LEARNING,
                    stepIndex = 0,
                    minutes = minutes,
                    now = now,
                    stability = stability,
                    difficulty = difficulty,
                    config = config,
                )
            }
            Rating.GOOD -> {
                if (config.learningStepsMinutes.size > 1) {
                    steppingResult(
                        card = card,
                        state = CardState.LEARNING,
                        stepIndex = 1,
                        minutes = config.learningStepsMinutes[1],
                        now = now,
                        stability = stability,
                        difficulty = difficulty,
                        config = config,
                    )
                } else {
                    graduate(
                        card = card,
                        now = now,
                        stability = stability,
                        difficulty = difficulty,
                        intervalDays = config.graduatingIntervalDays.toDouble(),
                        config = config,
                    )
                }
            }
            Rating.EASY -> graduate(
                card = card,
                now = now,
                stability = stability,
                difficulty = difficulty,
                intervalDays = config.easyIntervalDays.toDouble(),
                config = config,
            )
        }
    }

    private fun scheduleLearning(
        card: Card,
        rating: Rating,
        now: Instant,
        config: SchedulerConfig,
        relearning: Boolean,
    ): ScheduleResult {
        val steps = if (relearning) config.relearningStepsMinutes else config.learningStepsMinutes
        val difficulty = if (card.difficulty > 0.0) {
            Fsrs6.nextDifficulty(card.difficulty, rating, config.weights)
        } else {
            Fsrs6.initialDifficulty(rating, config.weights)
        }
        val stability = Fsrs6.clampStability(
            if (card.stability > 0.0) card.stability else Fsrs6.initialStability(rating, config.weights),
        )

        return when (rating) {
            Rating.AGAIN -> steppingResult(
                card = card,
                state = if (relearning) CardState.RELEARNING else CardState.LEARNING,
                stepIndex = 0,
                minutes = steps.firstOrNull() ?: 1,
                now = now,
                stability = stability,
                difficulty = difficulty,
                config = config,
            )
            Rating.HARD -> {
                val index = card.stepIndex.coerceIn(0, (steps.size - 1).coerceAtLeast(0))
                steppingResult(
                    card = card,
                    state = if (relearning) CardState.RELEARNING else CardState.LEARNING,
                    stepIndex = index,
                    minutes = steps.getOrElse(index) { steps.lastOrNull() ?: 1 },
                    now = now,
                    stability = stability,
                    difficulty = difficulty,
                    config = config,
                )
            }
            Rating.GOOD, Rating.EASY -> {
                val nextIndex = card.stepIndex + 1
                val finished = rating == Rating.EASY || nextIndex >= steps.size
                if (!finished) {
                    steppingResult(
                        card = card,
                        state = if (relearning) CardState.RELEARNING else CardState.LEARNING,
                        stepIndex = nextIndex,
                        minutes = steps[nextIndex],
                        now = now,
                        stability = stability,
                        difficulty = difficulty,
                        config = config,
                    )
                } else {
                    val interval = if (relearning) {
                        Fsrs6.intervalDays(
                            stability = stability,
                            desiredRetention = config.desiredRetention,
                            maximumIntervalDays = config.maximumIntervalDays.toDouble(),
                            weights = config.weights,
                        )
                    } else {
                        config.graduatingIntervalDays.toDouble()
                    }
                    graduate(
                        card = card,
                        now = now,
                        stability = stability,
                        difficulty = difficulty,
                        intervalDays = interval,
                        config = config,
                    )
                }
            }
        }
    }

    private fun scheduleReview(
        card: Card,
        rating: Rating,
        now: Instant,
        config: SchedulerConfig,
    ): ScheduleResult {
        val lastReview = card.lastReviewAt
        val rawElapsed = if (lastReview == null) 0.0 else elapsedDays(lastReview, now)
        val clockAnomaly = rawElapsed < 0.0
        val elapsed = rawElapsed.coerceAtLeast(0.0)

        val difficulty = Fsrs6.nextDifficulty(card.difficulty, rating, config.weights)
        val retrievability = Fsrs6.retrievability(elapsed, card.stability, config.weights)

        if (rating == Rating.AGAIN) {
            val stability = Fsrs6.stabilityAfterForget(
                difficulty = card.difficulty,
                stability = card.stability,
                retrievability = retrievability,
                weights = config.weights,
            )
            val minutes = config.relearningStepsMinutes.firstOrNull() ?: 10
            val dueAt = now.plusMinutes(minutes)
            return ScheduleResult(
                state = CardState.RELEARNING,
                dueAt = dueAt,
                stability = stability,
                difficulty = difficulty,
                stepIndex = 0,
                intervalDays = minutes / MINUTES_PER_DAY,
                elapsedDays = elapsed,
                clockAnomaly = clockAnomaly,
                nextBox = BoxMapper.boxOf(CardState.RELEARNING, stability),
            )
        }

        // Sub-day reviews use the same-day branch of FSRS-6.
        val stability = if (elapsed < 1.0) {
            Fsrs6.stabilitySameDay(card.stability, rating, config.weights)
        } else {
            Fsrs6.stabilityAfterRecall(
                difficulty = card.difficulty,
                stability = card.stability,
                retrievability = retrievability,
                rating = rating,
                weights = config.weights,
            )
        }

        val interval = Fsrs6.intervalDays(
            stability = stability,
            desiredRetention = config.desiredRetention,
            maximumIntervalDays = config.maximumIntervalDays.toDouble(),
            weights = config.weights,
        )
        val dueAt = if (elapsed < 1.0) {
            // Same-day: come back tomorrow rather than never leaving the queue.
            now.plusDays(1)
        } else {
            now.plusDays(interval)
        }

        return ScheduleResult(
            state = CardState.REVIEW,
            dueAt = dueAt,
            stability = stability,
            difficulty = difficulty,
            stepIndex = 0,
            intervalDays = if (elapsed < 1.0) 1.0 else interval,
            elapsedDays = elapsed,
            clockAnomaly = clockAnomaly,
            nextBox = BoxMapper.boxOf(CardState.REVIEW, stability),
        )
    }

    private fun suspendedResult(
        card: Card,
        now: Instant,
        config: SchedulerConfig,
    ): ScheduleResult = ScheduleResult(
        state = CardState.SUSPENDED,
        dueAt = card.dueAt,
        stability = card.stability,
        difficulty = card.difficulty,
        stepIndex = card.stepIndex,
        intervalDays = 0.0,
        elapsedDays = 0.0,
        nextBox = card.box,
    )

    private fun steppingResult(
        card: Card,
        state: CardState,
        stepIndex: Int,
        minutes: Int,
        now: Instant,
        stability: Double,
        difficulty: Double,
        config: SchedulerConfig,
    ): ScheduleResult = ScheduleResult(
        state = state,
        dueAt = now.plusMinutes(minutes),
        stability = stability,
        difficulty = difficulty,
        stepIndex = stepIndex,
        intervalDays = minutes / MINUTES_PER_DAY,
        elapsedDays = card.lastReviewAt?.let { elapsedDays(it, now).coerceAtLeast(0.0) } ?: 0.0,
        nextBox = BoxMapper.boxOf(state, stability),
    )

    private fun graduate(
        card: Card,
        now: Instant,
        stability: Double,
        difficulty: Double,
        intervalDays: Double,
        config: SchedulerConfig,
    ): ScheduleResult {
        val clamped = intervalDays.coerceIn(1.0, config.maximumIntervalDays.toDouble())
        return ScheduleResult(
            state = CardState.REVIEW,
            dueAt = now.plusDays(clamped),
            stability = Fsrs6.clampStability(stability),
            difficulty = Fsrs6.clampDifficulty(difficulty),
            stepIndex = 0,
            intervalDays = clamped,
            elapsedDays = card.lastReviewAt?.let { elapsedDays(it, now).coerceAtLeast(0.0) } ?: 0.0,
            nextBox = BoxMapper.boxOf(CardState.REVIEW, stability),
        )
    }

    /** Days between two instants; callers decide how to treat negatives. */
    fun elapsedDays(from: Instant, to: Instant): Double {
        val millis = to.toEpochMilliseconds() - from.toEpochMilliseconds()
        return millis.toDouble() / MILLIS_PER_DAY
    }

    private const val MINUTES_PER_DAY = 1440.0
    private const val MILLIS_PER_DAY = 86_400_000.0
}

/** Due date arithmetic without pulling Android into the domain. */
fun Instant.plusMinutes(minutes: Int): Instant =
    Instant.fromEpochMilliseconds(toEpochMilliseconds() + minutes * 60_000L)

fun Instant.plusDays(days: Double): Instant =
    Instant.fromEpochMilliseconds(toEpochMilliseconds() + (days * 86_400_000.0).toLong())

fun Instant.plusDays(days: Int): Instant =
    Instant.fromEpochMilliseconds(toEpochMilliseconds() + days * 86_400_000L)
