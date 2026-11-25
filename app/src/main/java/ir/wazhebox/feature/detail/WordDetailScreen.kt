@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ir.wazhebox.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.wazhebox.R
import ir.wazhebox.core.text.BidiText
import ir.wazhebox.core.text.PersianText
import ir.wazhebox.core.ui.components.BoxBadge
import ir.wazhebox.core.ui.components.KeyValueRow
import ir.wazhebox.core.ui.components.LoadingBox
import ir.wazhebox.core.ui.components.SectionCard
import ir.wazhebox.core.ui.dueLabel
import ir.wazhebox.core.ui.theme.EnglishWordStyle
import ir.wazhebox.core.ui.theme.Spacing
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun WordDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: WordDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val now = Clock.System.now()

    if (state.loading) {
        LoadingBox()
        return
    }
    val entry = state.word ?: run {
        Text(
            text = stringResource(R.string.library_empty),
            modifier = Modifier.padding(Spacing.screen),
        )
        return
    }
    val word = entry.word

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(
                text = stringResource(R.string.detail_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        SectionCard {
            Text(text = BidiText.isolateLtr(word.term), style = EnglishWordStyle)
            if (!word.ipa.isNullOrBlank()) {
                Text(
                    text = BidiText.isolateLtr(word.ipa),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(text = word.meaningText, style = MaterialTheme.typography.titleLarge)
            if (!word.exampleEn.isNullOrBlank()) {
                Text(text = BidiText.isolateLtr(word.exampleEn), style = MaterialTheme.typography.bodyLarge)
                if (!word.exampleFa.isNullOrBlank()) {
                    Text(
                        text = word.exampleFa,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.tags.isNotEmpty()) {
                // A word can carry several tags; a Row would push the extra
                // chips off the card, so they wrap instead.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    state.tags.forEach { tag -> AssistChip(onClick = {}, label = { Text(tag) }) }
                }
            }
        }

        SectionCard {
            Text(text = stringResource(R.string.detail_cards), style = MaterialTheme.typography.titleMedium)
            entry.cards.forEach { card ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(
                        text = when (card.direction.name) {
                            "FA_EN" -> stringResource(R.string.review_direction_fa_en)
                            "CLOZE" -> stringResource(R.string.review_direction_cloze)
                            "LISTEN" -> stringResource(R.string.review_direction_listen)
                            else -> stringResource(R.string.review_direction_en_fa)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        BoxBadge(box = card.box)
                        Text(
                            text = dueLabel(card.dueAt, now),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // Extra card shapes for the same word: a gap in its own example
            // sentence, and a card that is only the spoken word.
            Text(
                text = stringResource(R.string.detail_new_card),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedButton(
                    onClick = { viewModel.createCard(ir.wazhebox.domain.model.Direction.CLOZE) },
                    modifier = Modifier.weight(1f),
                    enabled = entry.cloze == null,
                ) { Text(stringResource(R.string.detail_add_cloze)) }
                OutlinedButton(
                    onClick = { viewModel.createCard(ir.wazhebox.domain.model.Direction.LISTEN) },
                    modifier = Modifier.weight(1f),
                    enabled = entry.listen == null,
                ) { Text(stringResource(R.string.detail_add_listen)) }
            }
            state.cardMessage?.let { code ->
                Text(
                    text = stringResource(
                        when (code) {
                            "created" -> R.string.detail_card_created
                            "cloze_needs_example" -> R.string.detail_cloze_needs_example
                            else -> R.string.detail_card_exists
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (code == "created") {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                Column {
                    Text(
                        text = PersianText.number(entry.enFa?.reps ?: 0),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(R.string.detail_reviews),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column {
                    Text(
                        text = PersianText.number(entry.enFa?.lapses ?: 0),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(R.string.detail_lapses),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SectionCard {
            KeyValueRow(
                label = stringResource(R.string.detail_created),
                value = PersianText.jalaliDate(
                    word.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date,
                ),
            )
            word.pos?.let { pos ->
                KeyValueRow(label = stringResource(R.string.word_pos), value = pos)
            }
        }

        Button(onClick = { onEdit(word.id) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_edit))
        }
        OutlinedButton(
            onClick = viewModel::toggleSuspended,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (word.suspended) R.string.detail_unsuspend else R.string.detail_suspend,
                ),
            )
        }
        TextButton(
            onClick = { confirmDelete = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
        }

        androidx.compose.foundation.layout.Spacer(Modifier.padding(Spacing.xl))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.word_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onBack)
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
