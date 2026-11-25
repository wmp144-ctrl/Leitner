package ir.wazhebox.feature.decks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.wazhebox.R
import ir.wazhebox.core.text.PersianText
import ir.wazhebox.core.ui.components.EmptyState
import ir.wazhebox.core.ui.theme.Spacing
import ir.wazhebox.domain.repo.DeckSummary

/**
 * The deck manager: create, rename, archive and delete decks, and start a
 * session that only contains one deck's cards.
 */
@Composable
fun DeckListScreen(
    onBack: () -> Unit,
    onReviewDeck: (Long) -> Unit,
    viewModel: DeckListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<DeckSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<DeckSummary?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen),
        contentPadding = PaddingValues(top = Spacing.lg, bottom = Spacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.deck_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = stringResource(R.string.deck_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Button(
                onClick = { showCreate = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.deck_new),
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
        }

        state.error?.let { code ->
            item {
                Text(
                    text = stringResource(
                        when (code) {
                            "duplicate" -> R.string.deck_duplicate_name
                            else -> R.string.deck_name_required
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (!state.loading && state.decks.size <= 1) {
            item {
                EmptyState(
                    title = stringResource(R.string.deck_title),
                    message = stringResource(R.string.deck_empty),
                )
            }
        }

        items(state.decks, key = { it.deck.id }) { summary ->
            DeckCard(
                summary = summary,
                onReview = { onReviewDeck(summary.deck.id) },
                onRename = { renameTarget = summary },
                onArchive = { viewModel.setArchived(summary.deck.id, !summary.deck.archived) },
                onDelete = { deleteTarget = summary },
            )
        }
    }

    if (showCreate) {
        DeckDialog(
            title = stringResource(R.string.deck_new),
            initialName = "",
            initialDescription = "",
            confirmLabel = stringResource(R.string.deck_create),
            onDismiss = { showCreate = false },
            onConfirm = { name, description ->
                viewModel.create(name, description)
                showCreate = false
            },
        )
    }

    renameTarget?.let { target ->
        DeckDialog(
            title = stringResource(R.string.deck_rename),
            initialName = target.deck.name,
            initialDescription = target.deck.description.orEmpty(),
            confirmLabel = stringResource(R.string.action_save),
            onDismiss = { renameTarget = null },
            onConfirm = { name, description ->
                viewModel.rename(target.deck.id, name, description)
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.deck_delete)) },
            text = { Text(stringResource(R.string.deck_delete_confirm, target.deck.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target.deck.id)
                    deleteTarget = null
                }) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun DeckCard(
    summary: DeckSummary,
    onReview: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val deck = summary.deck
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = deck.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Spacing.sm),
                )
                Text(
                    text = stringResource(
                        R.string.deck_word_count,
                        PersianText.number(summary.wordCount),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            deck.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (deck.isDefault) {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.deck_default_badge)) },
                    )
                }
                if (deck.archived) {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.deck_archived_badge)) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                OutlinedButton(
                    onClick = onReview,
                    modifier = Modifier.weight(1f),
                    enabled = summary.wordCount > 0 && !deck.archived,
                ) { Text(stringResource(R.string.deck_review)) }
                TextButton(onClick = onRename) { Text(stringResource(R.string.deck_rename)) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                TextButton(onClick = onArchive) {
                    Text(
                        stringResource(
                            if (deck.archived) R.string.deck_unarchive else R.string.deck_archive,
                        ),
                    )
                }
                if (!deck.isDefault) {
                    TextButton(onClick = onDelete) {
                        Text(
                            text = stringResource(R.string.deck_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckDialog(
    title: String,
    initialName: String,
    initialDescription: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.deck_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.deck_description)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, description) },
                enabled = name.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
