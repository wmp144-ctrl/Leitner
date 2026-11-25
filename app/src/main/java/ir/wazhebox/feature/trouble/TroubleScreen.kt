package ir.wazhebox.feature.trouble

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.wazhebox.R
import ir.wazhebox.core.text.PersianText
import ir.wazhebox.core.ui.components.EmptyState
import ir.wazhebox.core.ui.components.LoadingBox
import ir.wazhebox.core.ui.components.SectionCard
import ir.wazhebox.core.ui.theme.Spacing
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The words that eat the most time: cards that keep lapsing, and words answered
 * wrong in the last month. Both lists can start a drill that ignores due dates.
 */
@Composable
fun TroubleScreen(
    onBack: () -> Unit,
    onPractice: (Int) -> Unit,
    onOpenWord: (Long) -> Unit,
    viewModel: TroubleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showingLeeches = state.tab == 0
    val currentCount = if (showingLeeches) state.leeches.size else state.mistakes.size

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
                        text = stringResource(R.string.trouble_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = stringResource(R.string.trouble_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                item {
                    FilterChip(
                        selected = showingLeeches,
                        onClick = { viewModel.selectTab(0) },
                        label = { Text(stringResource(R.string.trouble_tab_leech)) },
                    )
                }
                item {
                    FilterChip(
                        selected = !showingLeeches,
                        onClick = { viewModel.selectTab(1) },
                        label = { Text(stringResource(R.string.trouble_tab_mistakes)) },
                    )
                }
            }
        }

        item {
            Button(
                onClick = { onPractice(if (showingLeeches) 1 else 2) },
                modifier = Modifier.fillMaxWidth(),
                enabled = currentCount > 0,
            ) { Text(stringResource(R.string.trouble_practice)) }
        }

        if (state.loading) {
            item { LoadingBox() }
        } else if (currentCount == 0) {
            item {
                EmptyState(
                    title = stringResource(R.string.trouble_empty),
                    message = stringResource(
                        if (showingLeeches) R.string.trouble_empty_leech
                        else R.string.trouble_empty_mistakes,
                    ),
                )
            }
        } else if (showingLeeches) {
            items(state.leeches, key = { it.word.id }) { entry ->
                val card = entry.enFa
                TroubleRow(
                    term = entry.word.term,
                    meaning = entry.word.meaningText,
                    metric = stringResource(R.string.trouble_lapses, PersianText.number(card?.lapses ?: 0)),
                    detail = card?.let {
                        stringResource(R.string.review_box, PersianText.number(it.box))
                    },
                    suspended = entry.word.suspended,
                    onClick = { onOpenWord(entry.word.id) },
                    onToggleSuspend = {
                        viewModel.setSuspended(entry.word.id, !entry.word.suspended)
                    },
                )
            }
        } else {
            items(state.mistakes, key = { it.word.word.id }) { entry ->
                TroubleRow(
                    term = entry.word.word.term,
                    meaning = entry.word.word.meaningText,
                    metric = stringResource(R.string.trouble_mistakes, PersianText.number(entry.mistakes)),
                    detail = stringResource(
                        R.string.trouble_last_missed,
                        PersianText.jalaliShort(
                            Instant.fromEpochMilliseconds(entry.lastAt.toEpochMilliseconds())
                                .toLocalDateTime(TimeZone.currentSystemDefault()).date,
                        ),
                    ),
                    suspended = entry.word.word.suspended,
                    onClick = { onOpenWord(entry.word.word.id) },
                    onToggleSuspend = {
                        viewModel.setSuspended(entry.word.word.id, !entry.word.word.suspended)
                    },
                )
            }
        }
    }
}

@Composable
private fun TroubleRow(
    term: String,
    meaning: String,
    metric: String,
    detail: String?,
    suspended: Boolean,
    onClick: () -> Unit,
    onToggleSuspend: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = term,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meaning,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = metric,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                detail?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            TextButton(onClick = onClick) { Text(stringResource(R.string.trouble_open)) }
            TextButton(onClick = onToggleSuspend) {
                Text(
                    text = stringResource(
                        if (suspended) R.string.library_unsuspend else R.string.library_suspend,
                    ),
                )
            }
            if (suspended) {
                Text(
                    text = stringResource(R.string.trouble_suspended),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
    }
}
