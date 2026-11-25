package ir.wazhebox.feature.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.wazhebox.domain.repo.DeckRepository
import ir.wazhebox.domain.repo.DeckSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeckListUiState(
    val decks: List<DeckSummary> = emptyList(),
    val loading: Boolean = true,
    /** A string code the screen turns into a localized message. */
    val error: String? = null,
)

@HiltViewModel
class DeckListViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
) : ViewModel() {

    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<DeckListUiState> = combine(
        deckRepository.observeDecksWithCounts(),
        error,
    ) { decks, errorCode ->
        DeckListUiState(decks = decks, loading = false, error = errorCode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeckListUiState())

    fun clearError() {
        error.value = null
    }

    fun create(name: String, description: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            error.value = "name_required"
            return
        }
        if (nameTaken(trimmed, except = null)) {
            error.value = "duplicate"
            return
        }
        error.value = null
        viewModelScope.launch {
            deckRepository.createDeck(trimmed, description.trim().ifEmpty { null })
        }
    }

    fun rename(id: Long, name: String, description: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            error.value = "name_required"
            return
        }
        if (nameTaken(trimmed, except = id)) {
            error.value = "duplicate"
            return
        }
        error.value = null
        viewModelScope.launch {
            deckRepository.renameDeck(id, trimmed, description.trim().ifEmpty { null })
        }
    }

    fun setArchived(id: Long, archived: Boolean) {
        viewModelScope.launch {
            if (archived) deckRepository.archiveDeck(id) else deckRepository.unarchiveDeck(id)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { deckRepository.deleteDeck(id) }
    }

    private fun nameTaken(name: String, except: Long?): Boolean =
        state.value.decks.any { it.deck.id != except && it.deck.name.equals(name, ignoreCase = true) }
}
