package ir.wazhebox.feature.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.wazhebox.R
import ir.wazhebox.core.text.PersianText
import ir.wazhebox.core.ui.components.BoxBadge
import ir.wazhebox.core.ui.components.EmptyState
import ir.wazhebox.core.ui.components.ProgressRing
import ir.wazhebox.core.ui.components.StatCard
import ir.wazhebox.core.ui.dueLabel
import ir.wazhebox.core.ui.jalaliDayLabel
import ir.wazhebox.core.ui.theme.Spacing
import ir.wazhebox.core.ui.theme.accents
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

/**
 * Home is a single scrollable "today" page: header, the progress hero card,
 * three metric cards and the box distribution. There is no add-word button
 * here any more (v2): words come from the file import or the menu.
 */
@Composable
fun HomeScreen(
    onStartReview: () -> Unit,
    onFreePractice: () -> Unit,
    onOpenMenu: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The home numbers are a snapshot of the queue; coming back from a session
    // (or from an import) has to refetch them, otherwise "left today" kept the
    // count it had before the reviews were written.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val importReport by viewModel.importReport.collectAsStateWithLifecycle()
    val importError by viewModel.importError.collectAsStateWithLifecycle()
    val now = Clock.System.now()
    val timeZone = TimeZone.currentSystemDefault()

    val openCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importCsv)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen),
        contentPadding = PaddingValues(top = Spacing.lg, bottom = Spacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item {
            HomeHeader(
                dateLabel = jalaliDayLabel(now, timeZone),
                totalWords = state.totalWords,
                onOpenMenu = onOpenMenu,
            )
        }

        // With an empty deck the hero would claim the day is done, which is
        // wrong: the import card below is the only thing worth showing.
        if (state.totalWords > 0) {
            item {
                TodayHero(
                    state = state,
                    now = now,
                    onStartReview = onStartReview,
                    onFreePractice = onFreePractice,
                )
            }
            item { TodayStats(state) }
            item { BoxDistribution(state.boxes) }
        } else if (!state.loading) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    EmptyState(
                        title = stringResource(R.string.home_empty),
                        message = stringResource(R.string.home_empty_hint),
                        actionLabel = stringResource(R.string.home_import_csv),
                        onAction = {
                            openCsv.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values"))
                        },
                    )
                    if (importing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    importReport?.let { report ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(
                                    R.string.home_import_done,
                                    PersianText.number(report.added),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (report.duplicates > 0) {
                                Text(
                                    text = stringResource(
                                        R.string.home_import_skipped,
                                        PersianText.number(report.duplicates),
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (report.rejected.isNotEmpty()) {
                                Text(
                                    text = stringResource(
                                        R.string.home_import_rejected,
                                        PersianText.number(report.rejected.size),
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    if (importError) {
                        Text(
                            text = stringResource(R.string.home_import_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(dateLabel: String, totalWords: Int, onOpenMenu: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_greeting),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Text(text = dateLabel, style = MaterialTheme.typography.headlineSmall)
        }
        if (totalWords > 0) {
            Text(
                text = stringResource(R.string.library_count, PersianText.number(totalWords)),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onPrimaryContainer,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.primaryContainer)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
            Spacer(Modifier.width(Spacing.xs))
        }
        IconButton(onClick = onOpenMenu) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = stringResource(R.string.nav_menu),
                tint = colors.onSurface,
            )
        }
    }
}

/**
 * The hero: a ring with how much of today is done, the count that is still
 * waiting and the one action that matters. When the day is clear the card
 * turns into a calm "done" state with the next due date and free practice.
 */
@Composable
private fun TodayHero(
    state: HomeUiState,
    now: kotlinx.datetime.Instant,
    onStartReview: () -> Unit,
    onFreePractice: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val done = state.dueCount == 0
    val total = state.todayReviews + state.dueCount
    val progress = if (total == 0) 0f else state.todayReviews.toFloat() / total
    val shape = RoundedCornerShape(30.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.primaryContainer,
                        colors.secondaryContainer.copy(alpha = 0.7f),
                    ),
                ),
            )
            .border(1.dp, colors.primary.copy(alpha = 0.12f), shape)
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            ProgressRing(progress = progress, size = 96.dp, strokeWidth = 10.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (done) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(30.dp),
                        )
                    } else {
                        Text(
                            text = PersianText.number(state.dueCount),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            text = stringResource(R.string.home_hero_left),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = stringResource(R.string.home_hero_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (done) {
                        stringResource(R.string.home_hero_done_note)
                    } else {
                        stringResource(
                            R.string.home_hero_progress,
                            PersianText.number(state.todayReviews),
                            PersianText.number(total),
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        if (!done) {
            Button(
                onClick = onStartReview,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = Spacing.md),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.home_start_session))
            }
        } else {
            OutlinedButton(
                onClick = onFreePractice,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = Spacing.md),
            ) {
                Text(stringResource(R.string.home_free_practice))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (state.nextDueAt != null) {
                    Text(
                        text = stringResource(
                            R.string.home_hero_next,
                            dueLabel(state.nextDueAt, now),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.home_free_practice_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TodayStats(state: HomeUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        StatCard(
            value = PersianText.number(state.totalWords),
            label = stringResource(R.string.home_words_label),
            icon = Icons.AutoMirrored.Filled.MenuBook,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = PersianText.number(state.todayReviews),
            label = stringResource(R.string.stats_reviews),
            icon = Icons.Filled.Refresh,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = if (state.todayReviews == 0) "—" else PersianText.percent(
                state.todayCorrect.toDouble() / state.todayReviews,
            ),
            label = stringResource(R.string.stats_correct_rate),
            icon = Icons.Filled.CheckCircle,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Boxes as one stacked bar plus the numbered badges: the bar shows the shape of
 * the collection at a glance, the badges keep every number readable.
 */
@Composable
private fun BoxDistribution(boxes: Map<Int, Int>) {
    val colors = MaterialTheme.colorScheme
    val accent = MaterialTheme.accents
    val palette = listOf(
        colors.primary,
        colors.primary.copy(alpha = 0.72f),
        colors.secondary,
        colors.secondary.copy(alpha = 0.72f),
        accent.cool,
        accent.warm,
    )
    val total = boxes.values.sum()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerLow)
            .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.large)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_boxes_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.library_count, PersianText.number(total)),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(colors.surfaceVariant),
        ) {
            if (total > 0) {
                (1..6).forEach { box ->
                    val count = boxes[box] ?: 0
                    if (count > 0) {
                        Box(
                            modifier = Modifier
                                .weight(count.toFloat())
                                .fillMaxHeight()
                                .background(palette[box - 1]),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            (1..6).forEach { box ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    BoxBadge(box = box)
                    Text(
                        text = PersianText.number(boxes[box] ?: 0),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

    }
}
