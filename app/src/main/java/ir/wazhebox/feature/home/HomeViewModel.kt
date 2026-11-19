package ir.wazhebox.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.wazhebox.data.settings.SettingsRepository
import ir.wazhebox.data.settings.UserSettings
import ir.wazhebox.domain.model.Level
import ir.wazhebox.domain.repo.SeedRepository
import ir.wazhebox.domain.repo.StatsRepository
import ir.wazhebox.domain.repo.StudyRepository
import ir.wazhebox.domain.repo.WordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val dueCount: Int = 0,
    val totalWords: Int = 0,
    val learningCount: Int = 0,
    val reviewCount: Int = 0,
    val newCount: Int = 0,
    val todayReviews: Int = 0,
    val todayCorrect: Int = 0,
    val nextDueAt: Instant? = null,
    val boxes: Map<Int, Int> = emptyMap(),
    val settings: UserSettings = UserSettings(),
    val importing: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val studyRepository: StudyRepository,
    private val statsRepository: StatsRepository,
    private val settingsRepository: SettingsRepository,
    private val wordRepository: WordRepository,
    private val seedRepository: SeedRepository,
    private val widgetUpdater: ir.wazhebox.widget.WidgetUpdater,
) : ViewModel() {

    private val refresh = MutableStateFlow(0)

    val state: StateFlow<HomeUiState> = combine(
        wordRepository.observeWordCount(),
        settingsRepository.settings,
        refresh,
    ) { count, settings, _ -> count to settings }
        .mapLatest { (_, settings) ->
            val workload = studyRepository.workload()
            val today = statsRepository.today()
            runCatching { widgetUpdater.refresh() }
            HomeUiState(
                loading = false,
                dueCount = workload.dueCount,
                totalWords = workload.totalWords,
                learningCount = workload.learningCount,
                reviewCount = workload.reviewCount,
                newCount = workload.newCount,
                todayReviews = today.reviewsDone,
                todayCorrect = today.correctCount,
                nextDueAt = workload.nextDueAt,
                boxes = statsRepository.boxDistribution(),
                settings = settings,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing

    fun refresh() {
        refresh.value = refresh.value + 1
    }

    fun importStarterDeck() {
        if (_importing.value) return
        _importing.value = true
        viewModelScope.launch {
            val settings = state.value.settings
            seedRepository.importStarterDeck(settings.level, limit = 100)
            _importing.value = false
            refresh()
        }
    }

    fun setLevel(level: Level) {
        viewModelScope.launch { settingsRepository.completeOnboarding(state.value.settings.dailyMinutes, level) }
    }
}
