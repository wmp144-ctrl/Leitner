package ir.wazhebox.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.wazhebox.R
import ir.wazhebox.core.text.PersianText
import ir.wazhebox.core.ui.dueLabel
import ir.wazhebox.core.ui.jalaliDayLabel
import ir.wazhebox.core.ui.components.BoxBadge
import ir.wazhebox.core.ui.components.EmptyState
import ir.wazhebox.core.ui.components.SectionCard
import ir.wazhebox.core.ui.components.StatTile
import ir.wazhebox.core.ui.theme.Spacing
import kotlinx.datetime.TimeZone

@Composable
fun HomeScreen(
    onStartReview: () -> Unit,
    onFreePractice: () -> Unit,
    onAddWord: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now = kotlinx.datetime.Clock.System.now()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item {
            Column {
                Text(
                    text = stringResource(R.string.home_greeting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = jalaliDayLabel(now, TimeZone.currentSystemDefault()),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }

        item {
            SectionCard {
                if (state.dueCount > 0) {
                    Text(
                        text = stringResource(
                            R.string.home_today_remaining,
                            PersianText.number(state.dueCount),
                        ),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Button(
                        onClick = onStartReview,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.size(4.dp))
                        Text(stringResource(R.string.home_start_session))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.home_today_done),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    if (state.nextDueAt != null) {
                        Text(
                            text = stringResource(
                                R.string.home_next_due,
                                dueLabel(state.nextDueAt, now),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = onFreePractice,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.home_free_practice))
                    }
                    Text(
                        text = stringResource(R.string.home_free_practice_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.totalWords == 0 && !state.loading) {
            item {
                EmptyState(
                    title = stringResource(R.string.home_empty),
                    message = stringResource(R.string.home_empty_hint),
                    actionLabel = stringResource(R.string.home_add_starter),
                    onAction = viewModel::importStarterDeck,
                )
                TextButton(
                    onClick = onAddWord,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.home_quick_add))
                }
            }
        } else {
            item {
                SectionCard {
                    Text(
                        text = stringResource(R.string.stats_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        StatTile(
                            label = stringResource(R.string.home_words_label),
                            value = PersianText.number(state.totalWords),
                        )
                        StatTile(
                            label = stringResource(R.string.stats_reviews),
                            value = PersianText.number(state.todayReviews),
                        )
                        StatTile(
                            label = stringResource(R.string.stats_correct_rate),
                            value = if (state.todayReviews == 0) "—" else PersianText.percent(
                                state.todayCorrect.toDouble() / state.todayReviews,
                            ),
                        )
                    }
                }
            }

            item {
                SectionCard {
                    Text(
                        text = stringResource(R.string.home_box_summary),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        (1..6).forEach { box ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                BoxBadge(box = box)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = PersianText.number(state.boxes[box] ?: 0),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    StatTile(
                        label = stringResource(R.string.library_filter_state_learning),
                        value = PersianText.number(state.learningCount),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = stringResource(R.string.library_filter_state_review),
                        value = PersianText.number(state.reviewCount),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = stringResource(R.string.stats_new_words),
                        value = PersianText.number(state.newCount),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = onAddWord,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_add_word))
                }
            }
        }

        item { Box(Modifier.height(Spacing.xxxl)) }
    }
}
