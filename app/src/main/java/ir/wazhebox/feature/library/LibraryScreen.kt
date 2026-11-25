package ir.wazhebox.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.wazhebox.R
import ir.wazhebox.core.text.PersianText
import ir.wazhebox.core.ui.components.BoxBadge
import ir.wazhebox.core.ui.components.EmptyState
import ir.wazhebox.core.ui.dueLabel
import ir.wazhebox.core.ui.theme.Spacing
import kotlinx.datetime.Clock

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onOpenWord: (Long) -> Unit,
    onOpenMenu: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val now = Clock.System.now()
    var sortMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.nav_library),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.library_count, PersianText.number(state.rows.size)),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
            TextButton(onClick = { sortMenu = true }) {
                Text(stringResource(R.string.library_sort))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            IconButton(onClick = onOpenMenu) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = stringResource(R.string.nav_menu),
                )
            }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                LibrarySort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(stringResource(sort.labelRes)) },
                        onClick = {
                            viewModel.setSort(sort)
                            sortMenu = false
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.library_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = CircleShape,
            trailingIcon = {
                if (state.query.isNotBlank()) {
                    IconButton(onClick = { viewModel.setQuery("") }) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = stringResource(R.string.action_cancel),
                        )
                    }
                }
            },
        )

        // Deck scope: one chip per deck, only when the library has more than one.
        if (state.decks.size > 1) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                contentPadding = PaddingValues(end = Spacing.xs),
            ) {
                item {
                    FilterChip(
                        selected = state.deckFilter == null,
                        onClick = { viewModel.setDeckFilter(null) },
                        label = {
                            Text(
                                stringResource(R.string.deck_all),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
                items(state.decks) { deck ->
                    FilterChip(
                        selected = state.deckFilter == deck.id,
                        onClick = { viewModel.setDeckFilter(deck.id) },
                        label = {
                            Text(deck.name, style = MaterialTheme.typography.labelSmall)
                        },
                    )
                }
            }
        }

        // Five chips overflow a phone width, so the row scrolls instead of
        // clipping the last filter (a real bug in the previous layout).
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            contentPadding = PaddingValues(end = Spacing.xs),
        ) {
            items(StateFilter.entries) { filter ->
                val label = when (filter) {
                    StateFilter.ALL -> stringResource(R.string.library_filter_all)
                    StateFilter.LEARNING -> stringResource(R.string.library_filter_state_learning)
                    StateFilter.REVIEW -> stringResource(R.string.library_filter_state_review)
                    StateFilter.SUSPENDED -> stringResource(R.string.library_filter_state_suspended)
                    StateFilter.HARD -> stringResource(R.string.library_filter_hard)
                }
                FilterChip(
                    selected = state.stateFilter == filter,
                    onClick = { viewModel.setStateFilter(filter) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }

        if (state.selected.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(colors.secondaryContainer)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.library_selected,
                        PersianText.number(state.selected.size),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { viewModel.setSuspended(true) }) {
                    Text(stringResource(R.string.library_suspend))
                }
                TextButton(onClick = { confirmDelete = true }) {
                    Text(stringResource(R.string.action_delete), color = colors.error)
                }
                TextButton(onClick = viewModel::clearSelection) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }

        if (state.rows.isEmpty() && !state.loading) {
            EmptyState(
                title = if (state.query.isBlank()) stringResource(R.string.library_empty)
                else stringResource(R.string.library_empty_search),
                message = if (state.query.isBlank()) stringResource(R.string.home_empty_hint)
                else stringResource(R.string.library_empty_search_hint),
            )
        } else {
            LazyColumn(
                // The list is the last child of a Column: weight keeps it inside
                // the remaining height instead of taking the full screen and
                // losing its last rows behind the header.
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.xxxl),
            ) {
                items(state.rows, key = { it.word.id }) { row ->
                    val selected = row.word.id in state.selected
                    val background by animateColorAsState(
                        targetValue = if (selected) colors.secondaryContainer else colors.surfaceContainerLow,
                        label = "library-row",
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .clip(MaterialTheme.shapes.medium)
                            .background(background)
                            .border(
                                width = 1.dp,
                                color = if (selected) colors.secondary.copy(alpha = 0.45f)
                                else colors.outline.copy(alpha = 0.10f),
                                shape = MaterialTheme.shapes.medium,
                            )
                            .combinedClickable(
                                onClick = {
                                    if (state.selected.isEmpty()) onOpenWord(row.word.id)
                                    else viewModel.toggleSelection(row.word.id)
                                },
                                onLongClick = { viewModel.toggleSelection(row.word.id) },
                            )
                            .padding(horizontal = Spacing.md, vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = row.word.term,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = row.word.meaningText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            BoxBadge(box = row.box)
                            Spacer(Modifier.size(2.dp))
                            Text(
                                text = dueLabel(row.card?.dueAt, now),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    // Deleting a batch is irreversible, so it asks first (undo only covers the
    // current review session, not library deletes).
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.library_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteSelected()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
