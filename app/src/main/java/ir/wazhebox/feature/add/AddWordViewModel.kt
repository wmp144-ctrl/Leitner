package ir.wazhebox.feature.add

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.wazhebox.domain.repo.AddWordResult
import ir.wazhebox.domain.repo.DeckRepository
import ir.wazhebox.domain.repo.InvalidReason
import ir.wazhebox.domain.repo.WordDraft
import ir.wazhebox.domain.repo.WordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddWordUiState(
    val wordId: Long = 0,
    val deckId: Long = 0,
    val term: String = "",
    val meaningFa: String = "",
    val pos: String? = null,
    val ipa: String = "",
    val exampleEn: String = "",
    val exampleFa: String = "",
    val notes: String = "",
    val tags: String = "",
    val error: InvalidReason? = null,
    val duplicateId: Long? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val loading: Boolean = true,
)

@HiltViewModel
class AddWordViewModel @Inject constructor(
    private val wordRepository: WordRepository,
    private val deckRepository: DeckRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val initialWordId: Long = savedStateHandle.get<Long>("wordId") ?: 0L

    private val _state = MutableStateFlow(AddWordUiState(wordId = initialWordId))
    val state: StateFlow<AddWordUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val deck = deckRepository.ensureDefaultDeck()
            val existing = if (initialWordId > 0) wordRepository.word(initialWordId) else null
            val tags = if (existing != null) wordRepository.tagsFor(existing.word.id) else emptyList()
            _state.value = _state.value.copy(
                deckId = existing?.word?.deckId ?: deck.id,
                term = existing?.word?.term ?: "",
                meaningFa = existing?.word?.meaningFa ?: "",
                pos = existing?.word?.pos,
                ipa = existing?.word?.ipa ?: "",
                exampleEn = existing?.word?.exampleEn ?: "",
                exampleFa = existing?.word?.exampleFa ?: "",
                notes = existing?.word?.notes ?: "",
                tags = tags.joinToString(", "),
                loading = false,
            )
        }
    }

    fun setTerm(value: String) = update { it.copy(term = value, error = null) }
    fun setMeaning(value: String) = update { it.copy(meaningFa = value, error = null) }
    fun setPos(value: String?) = update { it.copy(pos = value) }
    fun setIpa(value: String) = update { it.copy(ipa = value) }
    fun setExampleEn(value: String) = update { it.copy(exampleEn = value) }
    fun setExampleFa(value: String) = update { it.copy(exampleFa = value) }
    fun setNotes(value: String) = update { it.copy(notes = value) }
    fun setTags(value: String) = update { it.copy(tags = value) }
    fun dismissDuplicate() = update { it.copy(duplicateId = null) }

    fun save(andNext: Boolean = false, allowDuplicate: Boolean = false) {
        val current = _state.value
        if (current.saving) return
        _state.value = current.copy(saving = true, duplicateId = null)
        viewModelScope.launch {
            val draft = WordDraft(
                id = current.wordId,
                deckId = current.deckId,
                term = current.term,
                meaningFa = current.meaningFa,
                pos = current.pos,
                ipa = current.ipa,
                exampleEn = current.exampleEn,
                exampleFa = current.exampleFa,
                notes = current.notes,
                tags = current.tags.split(',', '،').map { it.trim() }.filter { it.isNotEmpty() },
            )
            when (val result = wordRepository.addOrUpdate(draft, allowDuplicate)) {
                is AddWordResult.Added, is AddWordResult.Updated -> {
                    _state.value = if (andNext && current.wordId == 0L) {
                        AddWordUiState(
                            deckId = current.deckId,
                            loading = false,
                            saved = true,
                        )
                    } else {
                        _state.value.copy(saving = false, saved = true)
                    }
                }
                is AddWordResult.Duplicate -> _state.value = _state.value.copy(
                    saving = false,
                    duplicateId = result.existingWordId,
                )
                is AddWordResult.Invalid -> _state.value = _state.value.copy(
                    saving = false,
                    error = result.reason,
                )
            }
        }
    }

    fun delete() {
        val id = _state.value.wordId
        if (id == 0L) return
        viewModelScope.launch {
            wordRepository.delete(listOf(id))
            _state.value = _state.value.copy(deleted = true)
        }
    }

    fun consumeSaved() = update { it.copy(saved = false) }

    private fun update(block: (AddWordUiState) -> AddWordUiState) {
        _state.value = block(_state.value)
    }
}
