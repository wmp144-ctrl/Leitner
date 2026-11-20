package ir.wazhebox.feature.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.wazhebox.data.io.DataTransferRepository
import ir.wazhebox.data.io.ImportReport
import ir.wazhebox.data.settings.SettingsRepository
import ir.wazhebox.data.settings.UserSettings
import ir.wazhebox.domain.repo.StatsRepository
import ir.wazhebox.domain.repo.StudyRepository
import ir.wazhebox.domain.repo.WordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val studyRepository: StudyRepository,
    private val statsRepository: StatsRepository,
    private val settingsRepository: SettingsRepository,
    private val wordRepository: WordRepository,
    private val dataTransfer: DataTransferRepository,
    @ApplicationContext private val context: Context,
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
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _importReport = MutableStateFlow<ImportReport?>(null)
    val importReport: StateFlow<ImportReport?> = _importReport.asStateFlow()

    private val _importError = MutableStateFlow(false)
    val importError: StateFlow<Boolean> = _importError.asStateFlow()

    fun refresh() {
        refresh.value = refresh.value + 1
    }

    /**
     * Imports a CSV of words straight from the file picker: the app starts with
     * an empty library, so this is the normal way a deck gets in.
     */
    fun importCsv(uri: Uri) {
        if (_importing.value) return
        _importing.value = true
        _importError.value = false
        _importReport.value = null
        viewModelScope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() } ?: error("open_failed")
                }
                dataTransfer.importCsv(text)
            }.onSuccess { report ->
                _importReport.value = report
                refresh()
            }.onFailure {
                _importError.value = true
            }
            _importing.value = false
        }
    }
}
