package ir.wazhebox.data.repo

import ir.wazhebox.data.db.WazheBoxDatabase
import ir.wazhebox.data.settings.SettingsRepository
import ir.wazhebox.core.time.StudyClock
import ir.wazhebox.core.time.StudyDays
import ir.wazhebox.domain.model.BoxMapper
import ir.wazhebox.domain.model.DailyStat
import ir.wazhebox.domain.repo.RetentionStat
import ir.wazhebox.domain.repo.StatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepositoryImpl @Inject constructor(
    private val db: WazheBoxDatabase,
    private val clock: StudyClock,
    private val settingsRepository: SettingsRepository,
) : StatsRepository {

    private val statsDao = db.statsDao()
    private val reviewDao = db.reviewDao()
    private val cardDao = db.cardDao()

    override fun observeDailyStats(days: Int): Flow<List<DailyStat>> = flow {
        val settings = settingsRepository.settings.first()
        val from = StudyDays.studyDayOf(clock.now(), clock.timeZone(), settings.studyDayStartHour)
            .minus(days, DateTimeUnit.DAY)
            .toString()
        emitAll(
            statsDao.observeSince(from).map { stats ->
                stats.map {
                    DailyStat(
                        studyDay = LocalDate.parse(it.studyDay),
                        reviewsDone = it.reviewsDone,
                        newIntroduced = it.newIntroduced,
                        correctCount = it.correctCount,
                        totalTimeMs = it.totalTimeMs,
                    )
                }
            },
        )
    }

    override suspend fun today(): DailyStat {
        val settings = settingsRepository.settings.first()
        val day = StudyDays.studyDayOf(clock.now(), clock.timeZone(), settings.studyDayStartHour)
        val stat = statsDao.forDay(day.toString())
        return DailyStat(
            studyDay = day,
            reviewsDone = stat?.reviewsDone ?: 0,
            newIntroduced = stat?.newIntroduced ?: 0,
            correctCount = stat?.correctCount ?: 0,
            totalTimeMs = stat?.totalTimeMs ?: 0,
        )
    }

    override suspend fun retention(): RetentionStat = RetentionStat(
        correct = reviewDao.countMatureCorrect(),
        total = reviewDao.countMature(),
    )

    override suspend fun boxDistribution(): Map<Int, Int> {
        val distribution = mutableMapOf<Int, Int>()
        cardDao.all().forEach { card ->
            val state = runCatching { ir.wazhebox.domain.model.CardState.valueOf(card.state) }
                .getOrDefault(ir.wazhebox.domain.model.CardState.NEW)
            val box = BoxMapper.boxOf(state, card.stability)
            distribution[box] = (distribution[box] ?: 0) + 1
        }
        return distribution
    }

    override suspend fun totalReviews(): Int = reviewDao.count()
}
