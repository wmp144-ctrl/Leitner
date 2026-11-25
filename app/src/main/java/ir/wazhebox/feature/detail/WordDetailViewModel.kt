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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WordDetailUiState(
    val word: WordWithCards? = null,
    val tags: List<String> = emptyList(),
    val loading: Boolean = true,
    val deleted: Boolean = false,
    /** A short code for the card-creation result; the screen localizes it. */
    val cardMessage: String? = null,
)

@HiltViewModel
class WordDetailViewModel @Inject constructor(
    private val wordRepository: WordRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val wordId: Long = savedStateHandle.get<Long>("wordId") ?: 0L
    private val tagsState = MutableStateFlow<List<String>>(emptyList())
    private val cardMessage = MutableStateFlow<String?>(null)

    val state: StateFlow<WordDetailUiState> = combine(
        wordRepository.observeWord(wordId),
        tagsState,
        cardMessage,
    ) { word, tags, message ->
        WordDetailUiState(word = word, tags = tags, loading = false, cardMessage = message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WordDetailUiState())

    init {
        // Tags were read once, so editing them and coming back showed the old
        // chips. They are reloaded whenever the word's updatedAt moves.
        viewModelScope.launch {
            var seen: kotlinx.datetime.Instant? = null
            wordRepository.observeWord(wordId).collect { entry ->
                val updated = entry?.word?.updatedAt
                if (updated != seen) {
                    seen = updated
                    tagsState.value = wordRepository.tagsFor(wordId)
                }
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            wordRepository.delete(listOf(wordId))
            onDeleted()
        }
    }

    fun dismissCardMessage() {
        cardMessage.value = null
    }

    /** Adds a CLOZE or LISTEN card to this word. */
    fun createCard(direction: ir.wazhebox.domain.model.Direction) {
        viewModelScope.launch {
            val entry = state.value.word
            val created = wordRepository.createCard(wordId, direction)
            cardMessage.value = when {
                created -> "created"
                direction == ir.wazhebox.domain.model.Direction.CLOZE &&
                    entry?.word?.exampleEn.isNullOrBlank() -> "cloze_needs_example"
                else -> "exists"
            }
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
