package ir.wazhebox.data.repo

import androidx.room.withTransaction
import ir.wazhebox.core.time.StudyClock
import ir.wazhebox.core.time.StudyDays
import ir.wazhebox.data.db.CardEntity
import ir.wazhebox.data.db.DailyStatEntity
import ir.wazhebox.data.db.ReviewEntity
import ir.wazhebox.data.db.WazheBoxDatabase
import ir.wazhebox.data.settings.SettingsRepository
import ir.wazhebox.domain.model.Card
import ir.wazhebox.domain.model.CardState
import ir.wazhebox.domain.model.Direction
import ir.wazhebox.domain.model.Rating
import ir.wazhebox.domain.model.StudyItem
import ir.wazhebox.domain.model.Verdict
import ir.wazhebox.domain.repo.GradeOutcome
import ir.wazhebox.domain.repo.StudyRepository
import ir.wazhebox.domain.repo.Workload
import ir.wazhebox.domain.scheduler.ScheduleResult
import ir.wazhebox.domain.scheduler.Scheduler
import ir.wazhebox.domain.scheduler.SchedulerConfig
import ir.wazhebox.domain.session.SessionBuilder
import ir.wazhebox.domain.session.SessionConfig
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Review persistence: one transaction writes the review log, the card and the
 * daily counters, then decides whether the reverse (FA→EN) card has unlocked.
 */
@Singleton
class StudyRepositoryImpl @Inject constructor(
    private val db: WazheBoxDatabase,
    private val settingsRepository: SettingsRepository,
    private val clock: StudyClock,
) : StudyRepository {

    private val wordDao = db.wordDao()
    private val cardDao = db.cardDao()
    private val reviewDao = db.reviewDao()
    private val statsDao = db.statsDao()

    private suspend fun sessionConfig(freePractice: Boolean): Pair<SessionConfig, SchedulerConfig> {
        val settings = settingsRepository.settings.first()
        val now = clock.now()
        val day = StudyDays.studyDayOf(now, clock.timeZone(), settings.studyDayStartHour).toString()
        val stat = statsDao.forDay(day)
        return SessionConfig(
            dailyReviewLimit = settings.dailyReviewLimit,
            dailyNewLimit = settings.dailyNewLimit,
            newIntroducedToday = stat?.newIntroduced ?: 0,
            freePractice = freePractice,
        ) to SchedulerConfig(
            desiredRetention = settings.desiredRetention,
            maximumIntervalDays = settings.maximumIntervalDays,
        )
    }

    /**
     * Every card of every live word, one item per card.
     *
     * An earlier version kept only the recognition (EN_FA) card, which meant the
     * reverse card that §10.1 unlocks never entered a real session at all: the
     * word was already represented by its EN_FA card. One item per card keeps
     * both directions reviewable, the way a deck of cards actually works.
     */
    private suspend fun liveItems(deckId: Long? = null): List<StudyItem> =
        wordDao.allWithCards().flatMap { row ->
            val word = row.word.toDomain()
            if (word.suspended) return@flatMap emptyList()
            if (deckId != null && word.deckId != deckId) return@flatMap emptyList()
            row.cards
                .filter { it.state != CardState.SUSPENDED.name }
                .map { StudyItem(word = word, card = it.toDomain()) }
        }

    override suspend fun buildSession(freePractice: Boolean, deckId: Long?): List<StudyItem> {
        val (sessionConfig, _) = sessionConfig(freePractice)
        return SessionBuilder.buildQueue(liveItems(deckId), clock.now(), sessionConfig)
    }

    override suspend fun workload(freePractice: Boolean, deckId: Long?): Workload {
        val (sessionConfig, _) = sessionConfig(freePractice)
        val items = liveItems(deckId)
        val queue = SessionBuilder.buildQueue(items, clock.now(), sessionConfig)
        return Workload(
            dueCount = queue.size,
            nextDueAt = SessionBuilder.nextDue(items),
            totalWords = wordDao.count(),
            newCount = items.count { it.card.state == CardState.NEW },
            learningCount = items.count {
                it.card.state == CardState.LEARNING || it.card.state == CardState.RELEARNING
            },
            reviewCount = items.count { it.card.state == CardState.REVIEW },
        )
    }

    override suspend fun grade(
        cardId: Long,
        rating: Rating,
        verdict: Verdict?,
        answerText: String?,
        durationMs: Long,
    ): GradeOutcome? = db.withTransaction {
        val entity = cardDao.byId(cardId) ?: return@withTransaction null
        val before = entity.toDomain()
        val (_, schedulerConfig) = sessionConfig(freePractice = false)
        val settings = settingsRepository.settings.first()
        val now = clock.now()
        val result = Scheduler.schedule(before, rating, now, schedulerConfig)

        val nextReps = before.reps + 1
        val lapses = before.lapses +
            if (rating == Rating.AGAIN && before.state == CardState.REVIEW) 1 else 0

        val reviewId = reviewDao.insert(
            ReviewEntity(
                cardId = cardId,
                reviewedAt = now.toEpochMillis(),
                rating = rating.grade,
                answerText = answerText,
                verdict = verdict?.name,
                durationMs = durationMs,
                prevState = before.state.name,
                prevStability = before.stability,
                prevDifficulty = before.difficulty,
                nextState = result.state.name,
                nextStability = result.stability,
                nextDifficulty = result.difficulty,
                elapsedDays = result.elapsedDays,
                scheduledDays = result.intervalDays,
            ),
        )

        val updated = entity.copy(
            state = result.state.name,
            dueAt = result.dueAt.toEpochMillis(),
            stability = result.stability,
            difficulty = result.difficulty,
            stepIndex = result.stepIndex,
            reps = nextReps,
            lapses = lapses,
            lastReviewAt = now.toEpochMillis(),
        )
        cardDao.update(updated)

        recordDailyStats(
            now = now,
            reviews = 1,
            newWords = if (before.state == CardState.NEW) 1 else 0,
            correct = if (rating != Rating.AGAIN) 1 else 0,
            totalMs = durationMs,
            startHour = settings.studyDayStartHour,
        )

        val faEnCreated = maybeCreateReverseCard(before, result, nextReps, now)

        GradeOutcome(
            card = updated.toDomain(),
            reviewId = reviewId,
            cardBefore = before,
            rating = rating,
            wasNew = before.state == CardState.NEW,
            leechSuggestion = lapses >= (if (schedulerConfig.leechThreshold > 0) schedulerConfig.leechThreshold else 4),
            faEnCardCreated = faEnCreated,
            clockAnomaly = result.clockAnomaly,
            durationMs = durationMs,
        )
    }

    /** PRD §10.1: reverse card unlocks at REVIEW with at least two reps. */
    private suspend fun maybeCreateReverseCard(
        before: Card,
        result: ScheduleResult,
        reps: Int,
        now: Instant,
    ): Boolean {
        if (before.direction != Direction.EN_FA) return false
        if (result.state != CardState.REVIEW || reps < 2) return false
        val existing = cardDao.forWord(before.wordId)
        if (existing.any { it.direction == Direction.FA_EN.name }) return false
        cardDao.insertIgnore(
            listOf(
                CardEntity(
                    wordId = before.wordId,
                    direction = Direction.FA_EN.name,
                    state = CardState.NEW.name,
                    dueAt = now.toEpochMillis(),
                    createdAt = now.toEpochMillis(),
                ),
            ),
        )
        return true
    }

    override suspend fun undo(outcome: GradeOutcome) {
        db.withTransaction {
            reviewDao.markUndone(outcome.reviewId)
            cardDao.byId(outcome.card.id)?.let { entity ->
                cardDao.update(
                    entity.copy(
                        state = outcome.cardBefore.state.name,
                        dueAt = outcome.cardBefore.dueAt.toEpochMillis(),
                        stability = outcome.cardBefore.stability,
                        difficulty = outcome.cardBefore.difficulty,
                        stepIndex = outcome.cardBefore.stepIndex,
                        reps = outcome.cardBefore.reps,
                        lapses = outcome.cardBefore.lapses,
                        lastReviewAt = outcome.cardBefore.lastReviewAt?.toEpochMillis(),
                    ),
                )
            }
            if (outcome.faEnCardCreated) {
                cardDao.forWord(outcome.cardBefore.wordId)
                    .firstOrNull {
                        it.direction == Direction.FA_EN.name && it.state == CardState.NEW.name
                    }
                    ?.let { cardDao.delete(it.id) }
            }
            val settings = settingsRepository.settings.first()
            val day = StudyDays.studyDayOf(
                clock.now(),
                clock.timeZone(),
                settings.studyDayStartHour,
            ).toString()
            statsDao.decrement(
                day = day,
                reviews = 1,
                newWords = if (outcome.wasNew) 1 else 0,
                correct = if (outcome.rating != Rating.AGAIN) 1 else 0,
                totalMs = outcome.durationMs,
            )
        }
    }

    override suspend fun suspendCard(cardId: Long, suspended: Boolean) {
        val entity = cardDao.byId(cardId) ?: return
        val now = clock.now().toEpochMillis()
        // A resumed card goes back to the state its history implies: a card that
        // was never answered is still new, anything that has a stability is a
        // review card. The old version made every resumed card a review card,
        // which pushed untouched words straight into the review queue.
        val resumedState = if (entity.stability > 0.0 || entity.reps > 0) {
            CardState.REVIEW
        } else {
            CardState.NEW
        }
        cardDao.update(
            entity.copy(
                state = if (suspended) CardState.SUSPENDED.name else resumedState.name,
                dueAt = if (suspended) entity.dueAt else now,
            ),
        )
    }

    override suspend fun stateOfCard(cardId: Long): Card? = cardDao.byId(cardId)?.toDomain()

    override suspend fun nextFreePracticeCard(excludeIds: Set<Long>): StudyItem? =
        liveItems()
            .filter { it.card.id !in excludeIds }
            .minByOrNull { it.card.dueAt }

    private suspend fun recordDailyStats(
        now: Instant,
        reviews: Int,
        newWords: Int,
        correct: Int,
        totalMs: Long,
        startHour: Int,
    ) {
        val day = StudyDays.studyDayOf(now, clock.timeZone(), startHour).toString()
        statsDao.insertIfAbsent(DailyStatEntity(studyDay = day))
        statsDao.increment(day, reviews, newWords, correct, totalMs)
    }
}
