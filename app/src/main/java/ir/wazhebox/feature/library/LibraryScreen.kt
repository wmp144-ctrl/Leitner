package ir.wazhebox.feature.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now = Clock.System.now()
    var sortMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { sortMenu = true }) {
                Text(stringResource(R.string.library_sort))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
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
            label = { Text(stringResource(R.string.library_search_hint)) },
            singleLine = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            StateFilter.entries.forEach { filter ->
                val label = when (filter) {
                    StateFilter.ALL -> stringResource(R.string.common_yes)
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
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(Spacing.sm),
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
                TextButton(onClick = viewModel::deleteSelected) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
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
                message = stringResource(R.string.home_empty_hint),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Spacing.xxxl),
            ) {
                items(state.rows, key = { it.word.id }) { row ->
                    val selected = row.word.id in state.selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (selected) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surface,
                            )
                            .combinedClickable(
                                onClick = {
                                    if (state.selected.isEmpty()) onOpenWord(row.word.id)
                                    else viewModel.toggleSelection(row.word.id)
                                },
                                onLongClick = { viewModel.toggleSelection(row.word.id) },
                            )
                            .padding(vertical = Spacing.md, horizontal = Spacing.sm),
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
                                text = row.word.meaningFa,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            BoxBadge(box = row.box)
                            androidx.compose.foundation.layout.Spacer(Modifier.padding(2.dp))
                            Text(
                                text = dueLabel(row.card?.dueAt, now),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
