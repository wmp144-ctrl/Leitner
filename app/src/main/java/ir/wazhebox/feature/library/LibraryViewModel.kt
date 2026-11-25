package ir.wazhebox.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.wazhebox.domain.grading.TextNormalizer
import ir.wazhebox.domain.model.Card
import ir.wazhebox.domain.model.CardState
import ir.wazhebox.domain.model.Word
import ir.wazhebox.domain.repo.WordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibrarySort(val labelRes: Int) {
    NEWEST(ir.wazhebox.R.string.library_sort_newest),
    DUE(ir.wazhebox.R.string.library_sort_due),
    LAPSES(ir.wazhebox.R.string.library_sort_lapses),
    ALPHABET(ir.wazhebox.R.string.library_sort_alphabet),
}

enum class StateFilter {
    ALL,
    LEARNING,
    REVIEW,
    SUSPENDED,
    HARD,
}

data class LibraryRow(
    val word: Word,
    val card: Card?,
    val box: Int,
    val lapses: Int,
    /** Term + meaning normalized once, so search ignores ي/ی and Arabic digits. */
    val searchKey: String = "",
)

data class LibraryUiState(
    val rows: List<LibraryRow> = emptyList(),
    val decks: List<ir.wazhebox.domain.model.Deck> = emptyList(),
    val deckFilter: Long? = null,
    val query: String = "",
    val sort: LibrarySort = LibrarySort.NEWEST,
    val stateFilter: StateFilter = StateFilter.ALL,
    val boxFilter: Int? = null,
    val selected: Set<Long> = emptySet(),
    val loading: Boolean = true,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val wordRepository: WordRepository,
    private val deckRepository: ir.wazhebox.domain.repo.DeckRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(LibraryUiState())

    val state: StateFlow<LibraryUiState> = combine(
        wordRepository.observeLibrary(),
        deckRepository.observeDecks(),
        filters,
    ) { words, decks, ui ->
        val rows = words.map { entry ->
            val card = entry.enFa ?: entry.cards.minByOrNull { it.dueAt }
            LibraryRow(
                word = entry.word,
                card = card,
                box = card?.box ?: 1,
                lapses = card?.lapses ?: 0,
                searchKey = TextNormalizer.normalize("${entry.word.term} ${entry.word.meaningFa}"),
            )
        }
        // Persian input often arrives with Arabic ي/ك or Arabic-Indic digits, and a
        // plain contains() then found nothing for a word that is clearly there.
        val normalizedQuery = TextNormalizer.normalize(ui.query)
        val filtered = rows
            .asSequence()
            .filter { row -> normalizedQuery.isEmpty() || row.searchKey.contains(normalizedQuery) }
            .filter { row ->
                when (ui.stateFilter) {
                    StateFilter.ALL -> true
                    StateFilter.LEARNING -> row.card?.state == CardState.LEARNING ||
                        row.card?.state == CardState.RELEARNING
                    StateFilter.REVIEW -> row.card?.state == CardState.REVIEW
                    StateFilter.SUSPENDED -> row.card?.state == CardState.SUSPENDED ||
                        row.word.suspended
                    StateFilter.HARD -> row.lapses >= 2
                }
            }
            .filter { row -> ui.boxFilter == null || row.box == ui.boxFilter }
            .filter { row -> ui.deckFilter == null || row.word.deckId == ui.deckFilter }
            .let { sequence ->
                when (ui.sort) {
                    LibrarySort.NEWEST -> sequence.sortedByDescending { it.word.createdAt }
                    LibrarySort.DUE -> sequence.sortedBy { it.card?.dueAt }
                    LibrarySort.LAPSES -> sequence.sortedByDescending { it.lapses }
                    LibrarySort.ALPHABET -> sequence.sortedBy { it.word.termNorm }
                }
            }
            .toList()
        ui.copy(rows = filtered, decks = decks, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setQuery(query: String) {
        filters.value = filters.value.copy(query = query)
    }

    fun setSort(sort: LibrarySort) {
        filters.value = filters.value.copy(sort = sort)
    }

    fun setStateFilter(filter: StateFilter) {
        filters.value = filters.value.copy(stateFilter = filter)
    }

    fun setBoxFilter(box: Int?) {
        filters.value = filters.value.copy(boxFilter = box)
    }

    fun setDeckFilter(deckId: Long?) {
        filters.value = filters.value.copy(deckFilter = deckId)
    }

    fun toggleSelection(wordId: Long) {
        val current = filters.value.selected
        filters.value = filters.value.copy(
            selected = if (wordId in current) current - wordId else current + wordId,
        )
    }

    fun clearSelection() {
        filters.value = filters.value.copy(selected = emptySet())
    }

    fun deleteSelected() {
        val ids = filters.value.selected.toList()
        viewModelScope.launch {
            wordRepository.delete(ids)
            filters.value = filters.value.copy(selected = emptySet())
        }
    }

    fun setSuspended(suspended: Boolean) {
        val ids = filters.value.selected.toList()
        viewModelScope.launch {
            wordRepository.setSuspended(ids, suspended)
            filters.value = filters.value.copy(selected = emptySet())
        }
    }
}
