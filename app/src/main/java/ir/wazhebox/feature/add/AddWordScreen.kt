package ir.wazhebox.feature.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.wazhebox.R
import ir.wazhebox.core.ui.theme.Spacing
import ir.wazhebox.domain.repo.InvalidReason

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWordScreen(
    onDone: () -> Unit,
    onOpenExisting: (Long) -> Unit,
    viewModel: AddWordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var posExpanded by remember { mutableStateOf(false) }
    var deckExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Edge-to-edge: reserve the IME height so the save button can be
            // scrolled above the keyboard.
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = onDone) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(
                text = stringResource(
                    if (state.wordId > 0) R.string.word_edit_title else R.string.word_add_title,
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        OutlinedTextField(
            value = state.term,
            onValueChange = viewModel::setTerm,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.word_term)) },
            singleLine = true,
            isError = state.error == InvalidReason.EMPTY_TERM,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                autoCorrectEnabled = false,
            ),
        )

        OutlinedTextField(
            value = state.meaningFa,
            onValueChange = viewModel::setMeaning,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.word_meaning)) },
            supportingText = { Text(stringResource(R.string.word_meaning_hint)) },
            isError = state.error == InvalidReason.EMPTY_MEANING,
            minLines = 2,
        )

        // Only worth showing once a second deck exists; with one deck the field
        // would be a choice with a single option.
        if (state.decks.size > 1) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { deckExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val current = state.decks.firstOrNull { it.id == state.deckId }
                    Text(
                        text = stringResource(R.string.word_deck) + ": " +
                            (current?.name ?: "—"),
                    )
                }
                DropdownMenu(
                    expanded = deckExpanded,
                    onDismissRequest = { deckExpanded = false },
                ) {
                    state.decks.forEach { deck ->
                        DropdownMenuItem(
                            text = { Text(deck.name) },
                            onClick = {
                                viewModel.setDeck(deck.id)
                                deckExpanded = false
                            },
                        )
                    }
                }
            }
        }

        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { posExpanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.word_pos) + ": " +
                        posLabel(state.pos).ifEmpty { "—" },
                )
            }
            DropdownMenu(
                expanded = posExpanded,
                onDismissRequest = { posExpanded = false },
            ) {
                posOptionKeys.forEach { (key, labelRes) ->
                    DropdownMenuItem(
                        text = { Text(if (labelRes == 0) "—" else stringResource(labelRes)) },
                        onClick = {
                            viewModel.setPos(key.ifEmpty { null })
                            posExpanded = false
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = state.ipa,
            onValueChange = viewModel::setIpa,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.word_ipa)) },
            singleLine = true,
        )

        OutlinedTextField(
            value = state.exampleEn,
            onValueChange = viewModel::setExampleEn,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.word_example_en)) },
            minLines = 2,
        )

        OutlinedTextField(
            value = state.exampleFa,
            onValueChange = viewModel::setExampleFa,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.word_example_fa)) },
            minLines = 2,
        )

        OutlinedTextField(
            value = state.tags,
            onValueChange = viewModel::setTags,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.word_tags)) },
            supportingText = { Text(stringResource(R.string.word_tags_hint)) },
            singleLine = true,
        )

        OutlinedTextField(
            value = state.notes,
            onValueChange = viewModel::setNotes,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.word_notes)) },
            minLines = 2,
        )

        state.error?.let { error ->
            Text(
                text = when (error) {
                    InvalidReason.EMPTY_TERM -> stringResource(R.string.word_error_term_required)
                    InvalidReason.EMPTY_MEANING -> stringResource(R.string.word_error_meaning_required)
                    InvalidReason.TOO_LONG -> stringResource(R.string.word_error_too_long)
                },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            onClick = { viewModel.save(andNext = false) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.saving,
        ) { Text(stringResource(R.string.action_save)) }

        if (state.wordId == 0L) {
            TextButton(
                onClick = { viewModel.save(andNext = true) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.word_add_and_next)) }
        }

        if (state.savedHint) {
            Text(
                text = stringResource(R.string.word_saved),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (state.duplicateId != null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissDuplicate,
                title = { Text(stringResource(R.string.word_duplicate_warning)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.save(allowDuplicate = true) }) {
                        Text(stringResource(R.string.word_duplicate_continue))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        val id = state.duplicateId
                        viewModel.dismissDuplicate()
                        if (id != null) onOpenExisting(id)
                    }) { Text(stringResource(R.string.word_duplicate_edit_existing)) }
                },
            )
        }
    }
}

/** Labels live in strings.xml so a changed wording only has one source. */
@Composable
private fun posLabel(key: String?): String = when (key) {
    "noun" -> stringResource(R.string.word_pos_noun)
    "verb" -> stringResource(R.string.word_pos_verb)
    "adj" -> stringResource(R.string.word_pos_adj)
    "adv" -> stringResource(R.string.word_pos_adv)
    "phrase" -> stringResource(R.string.word_pos_phrase)
    "prep" -> stringResource(R.string.word_pos_preposition)
    "other" -> stringResource(R.string.word_pos_other)
    else -> ""
}

private val posOptionKeys = listOf(
    "" to 0,
    "noun" to R.string.word_pos_noun,
    "verb" to R.string.word_pos_verb,
    "adj" to R.string.word_pos_adj,
    "adv" to R.string.word_pos_adv,
    "phrase" to R.string.word_pos_phrase,
    "prep" to R.string.word_pos_preposition,
    "other" to R.string.word_pos_other,
)
