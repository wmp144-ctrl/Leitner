package ir.wazhebox.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.wazhebox.domain.model.DailyStat
import ir.wazhebox.domain.repo.RetentionStat
import ir.wazhebox.domain.repo.StatsRepository
import ir.wazhebox.domain.repo.WordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.minus
import javax.inject.Inject

data class StatsUiState(
    val today: DailyStat? = null,
    val last30: List<DailyStat> = emptyList(),
    val retention: RetentionStat = RetentionStat(0, 0),
    val boxes: Map<Int, Int> = emptyMap(),
    val totalWords: Int = 0,
    val totalReviews: Int = 0,
    val streak: Int = 0,
    val loading: Boolean = true,
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsRepository: StatsRepository,
    wordRepository: WordRepository,
) : ViewModel() {

    private val refresh = MutableStateFlow(0)

    val state: StateFlow<StatsUiState> = combine(
        statsRepository.observeDailyStats(days = 30),
        wordRepository.observeWordCount(),
        refresh,
    ) { daily, words, _ ->
        val today = statsRepository.today()
        val retention = statsRepository.retention()
        val series = padToDays(daily, today.studyDay)
        StatsUiState(
            today = today,
            last30 = series,
            retention = retention,
            boxes = statsRepository.boxDistribution(),
            totalWords = words,
            totalReviews = statsRepository.totalReviews(),
            streak = streakOf(series, today.studyDay),
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun refresh() {
        refresh.value = refresh.value + 1
    }

    /**
     * The DAO only returns days that have a row, which made the "last 30 days"
     * chart collapse to the days that were actually studied and hid every quiet
     * day in between. The chart needs the full window, zeros included.
     */
    private fun padToDays(daily: List<DailyStat>, end: kotlinx.datetime.LocalDate): List<DailyStat> {
        val byDay = daily.associateBy { it.studyDay }
        return (DAYS - 1 downTo 0).map { offset ->
            val day = end.minus(offset, kotlinx.datetime.DateTimeUnit.DAY)
            byDay[day] ?: DailyStat(studyDay = day)
        }
    }

    private fun streakOf(daily: List<DailyStat>, today: kotlinx.datetime.LocalDate): Int {
        val activeDays = daily.filter { it.reviewsDone > 0 }.map { it.studyDay }.toSet()
        if (activeDays.isEmpty()) return 0
        // Counting from "the newest row" showed a zero streak every morning, before
        // the first review of the day. Today not being studied yet is a grace day.
        val yesterday = today.minus(1, kotlinx.datetime.DateTimeUnit.DAY)
        var day = when {
            today in activeDays -> today
            yesterday in activeDays -> yesterday
            else -> return 0
        }
        var streak = 0
        while (day in activeDays) {
            streak++
            day = day.minus(1, kotlinx.datetime.DateTimeUnit.DAY)
        }
        return streak
    }

    private companion object {
        const val DAYS = 30
    }
}
