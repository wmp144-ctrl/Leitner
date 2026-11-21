package ir.wazhebox.feature.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.wazhebox.R
import ir.wazhebox.core.text.PersianText
import ir.wazhebox.core.ui.components.BoxBadge
import ir.wazhebox.core.ui.components.LoadingBox
import ir.wazhebox.core.ui.components.ProgressRing
import ir.wazhebox.core.ui.components.SectionCard
import ir.wazhebox.core.ui.components.StatCard
import ir.wazhebox.core.ui.theme.Spacing
import ir.wazhebox.core.ui.theme.accents

/**
 * Statistics speak the same language as the home screen: one ring for the
 * headline rate, metric cards for the day, an animated 30-day chart and the
 * same stacked distribution bar for boxes.
 */
@Composable
fun StatsScreen(
    onOpenMenu: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Statistics are read once per subscription; a resume refetches them so a
    // session that just ended is reflected without leaving the screen.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val today = state.today

    if (state.loading) {
        LoadingBox()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Spacing.lg, bottom = Spacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Header(totalWords = state.totalWords, onOpenMenu = onOpenMenu)
        RetentionHero(state)
        TodayCard(state = state, today = today)
        Last30Card(state)
        BoxesCard(state.boxes)
    }
}

@Composable
private fun Header(totalWords: Int, onOpenMenu: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.stats_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.library_count, PersianText.number(totalWords)),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onPrimaryContainer,
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.primaryContainer)
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
        IconButton(onClick = onOpenMenu) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = stringResource(R.string.nav_menu),
            )
        }
    }
}

/**
 * Retention is the number the scheduler is tuned for, so it gets the ring and
 * the gradient: same hero treatment as today's plan on the home screen.
 */
@Composable
private fun RetentionHero(state: StatsUiState) {
    val colors = MaterialTheme.colorScheme
    val rate = state.retention.rate
    val hasData = state.retention.total > 0
    val shape = RoundedCornerShape(30.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.primary.copy(alpha = 0.16f),
                        colors.secondaryContainer.copy(alpha = 0.55f),
                    ),
                ),
            )
            .border(1.dp, colors.primary.copy(alpha = 0.14f), shape)
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            ProgressRing(
                progress = if (hasData) rate.toFloat() else 0f,
                size = 120.dp,
                strokeWidth = 12.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (hasData) PersianText.percent(rate) else "—",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = stringResource(R.string.stats_retention),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = stringResource(R.string.stats_retention),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (hasData) {
                    Text(
                        text = stringResource(
                            R.string.stats_retention_sample,
                            PersianText.number(state.retention.total),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.stats_no_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                if (state.streak > 0) {
                    StreakChip(days = state.streak)
                }
            }
        }

        Text(
            text = stringResource(R.string.stats_retention_hint),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
        if (hasData && rate < 0.80) {
            Text(
                text = stringResource(R.string.stats_suggestion),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.error,
            )
        }
    }
}

@Composable
private fun StreakChip(days: Int) {
    val colors = MaterialTheme.colorScheme
    val accent = MaterialTheme.accents
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(accent.warm.copy(alpha = 0.14f))
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(
            imageVector = Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            tint = accent.warm,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = stringResource(R.string.stats_streak_days, PersianText.number(days)),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurface,
        )
    }
}

@Composable
private fun TodayCard(state: StatsUiState, today: ir.wazhebox.domain.model.DailyStat?) {
    SectionCard {
        Text(
            text = stringResource(R.string.stats_today),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            StatCard(
                value = PersianText.number(today?.reviewsDone ?: 0),
                label = stringResource(R.string.stats_reviews),
                icon = Icons.Filled.Refresh,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = PersianText.number(today?.newIntroduced ?: 0),
                label = stringResource(R.string.stats_new_words),
                icon = Icons.Filled.AddCircle,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            StatCard(
                value = if ((today?.reviewsDone ?: 0) == 0) "—" else PersianText.percent(
                    (today?.correctCount ?: 0).toDouble() / (today?.reviewsDone ?: 1),
                ),
                label = stringResource(R.string.stats_correct_rate),
                icon = Icons.Filled.CheckCircle,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = PersianText.number(state.streak),
                label = stringResource(R.string.stats_streak_label),
                icon = Icons.Filled.LocalFireDepartment,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 30 days as rising bars. The first frame draws them at zero and then each bar
 * grows to its value with a 16ms stagger, so the chart assembles left-to-right
 * instead of appearing as a finished picture (ADR-012: motion that explains).
 */
@Composable
private fun Last30Card(state: StatsUiState) {
    val colors = MaterialTheme.colorScheme
    val max = state.last30.maxOfOrNull { it.reviewsDone }?.coerceAtLeast(1) ?: 1
    var chartReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { chartReady = true }

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.stats_last_30),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.stats_chart_peak, PersianText.number(max)),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            state.last30.forEachIndexed { index, stat ->
                val fraction = stat.reviewsDone.toFloat() / max
                val grown by animateFloatAsState(
                    targetValue = if (chartReady) fraction else 0f,
                    animationSpec = tween(durationMillis = 560, delayMillis = index * 16),
                    label = "bar",
                )
                val heightFraction = if (stat.reviewsDone == 0) {
                    0.03f
                } else {
                    grown.coerceIn(0.06f, 1f)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(heightFraction)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(
                                if (stat.reviewsDone == 0) {
                                    Brush.verticalGradient(
                                        listOf(colors.surfaceVariant, colors.surfaceVariant),
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        listOf(colors.primary, colors.primary.copy(alpha = 0.45f)),
                                    )
                                },
                            ),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.stats_chart_from),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.stats_chart_to),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(
                text = stringResource(
                    R.string.stats_total_reviews,
                    PersianText.number(state.totalReviews),
                ),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = stringResource(
                    R.string.stats_total_words,
                    PersianText.number(state.totalWords),
                ),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun BoxesCard(boxes: Map<Int, Int>) {
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

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.stats_boxes),
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
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = PersianText.number(boxes[box] ?: 0),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    BoxBadge(box = box)
                }
            }
        }
        Spacer(Modifier.width(Spacing.xs))
    }
}
