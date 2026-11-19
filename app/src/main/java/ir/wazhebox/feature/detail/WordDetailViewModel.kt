package ir.wazhebox.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.wazhebox.domain.model.WordWithCards
import ir.wazhebox.domain.repo.WordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WordDetailUiState(
    val word: WordWithCards? = null,
    val tags: List<String> = emptyList(),
    val loading: Boolean = true,
    val deleted: Boolean = false,
)

@HiltViewModel
class WordDetailViewModel @Inject constructor(
    private val wordRepository: WordRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val wordId: Long = savedStateHandle.get<Long>("wordId") ?: 0L
    private val tagsState = MutableStateFlow<List<String>>(emptyList())

    val state: StateFlow<WordDetailUiState> = combine(
        wordRepository.observeWord(wordId),
        tagsState,
    ) { word, tags ->
        WordDetailUiState(word = word, tags = tags, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WordDetailUiState())

    init {
        viewModelScope.launch { tagsState.value = wordRepository.tagsFor(wordId) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            wordRepository.delete(listOf(wordId))
            onDeleted()
        }
    }

    fun toggleSuspended() {
        val current = state.value.word ?: return
        val suspended = current.word.suspended
        viewModelScope.launch {
            wordRepository.setSuspended(listOf(wordId), !suspended)
        }
    }
}
