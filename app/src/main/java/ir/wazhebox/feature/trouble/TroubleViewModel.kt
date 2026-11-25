package ir.wazhebox.feature.trouble

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.wazhebox.domain.model.WordWithCards
import ir.wazhebox.domain.repo.MistakeWord
import ir.wazhebox.domain.repo.WordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days
import javax.inject.Inject

/** Two lists: cards that keep lapsing, and words missed in the last month. */
data class TroubleUiState(
    val leeches: List<WordWithCards> = emptyList(),
    val mistakes: List<MistakeWord> = emptyList(),
    val tab: Int = 0,
    val loading: Boolean = true,
)

@HiltViewModel
class TroubleViewModel @Inject constructor(
    private val wordRepository: WordRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TroubleUiState())
    val state: StateFlow<TroubleUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    fun selectTab(index: Int) {
        _state.value = _state.value.copy(tab = index)
    }

    fun setSuspended(wordId: Long, suspended: Boolean) {
        viewModelScope.launch {
            wordRepository.setSuspended(listOf(wordId), suspended)
            load()
        }
    }

    private suspend fun load() {
        val leeches = runCatching { wordRepository.troubleWords() }.getOrDefault(emptyList())
        val since = Clock.System.now() - MISTAKE_DAYS.days
        val mistakes = runCatching { wordRepository.mistakeWords(since) }.getOrDefault(emptyList())
        _state.value = _state.value.copy(leeches = leeches, mistakes = mistakes, loading = false)
    }

    private companion object {
        const val MISTAKE_DAYS = 30
    }
}
