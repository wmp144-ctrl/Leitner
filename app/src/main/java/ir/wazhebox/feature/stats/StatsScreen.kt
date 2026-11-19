package ir.wazhebox.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.wazhebox.R
import ir.wazhebox.core.text.PersianText
import ir.wazhebox.core.ui.components.BoxBadge
import ir.wazhebox.core.ui.components.SectionCard
import ir.wazhebox.core.ui.components.StatTile
import ir.wazhebox.core.ui.theme.Spacing

@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today = state.today

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = stringResource(R.string.stats_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        SectionCard {
            Text(
                text = stringResource(R.string.stats_today),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatTile(
                    label = stringResource(R.string.stats_reviews),
                    value = PersianText.number(today?.reviewsDone ?: 0),
                )
                StatTile(
                    label = stringResource(R.string.stats_new_words),
                    value = PersianText.number(today?.newIntroduced ?: 0),
                )
                StatTile(
                    label = stringResource(R.string.stats_correct_rate),
                    value = if ((today?.reviewsDone ?: 0) == 0) "—" else PersianText.percent(
                        (today?.correctCount ?: 0).toDouble() / (today?.reviewsDone ?: 1),
                    ),
                )
                StatTile(
                    label = stringResource(R.string.stats_streak_days, ""),
                    value = PersianText.number(state.streak),
                )
            }
        }

        SectionCard {
            Text(
                text = stringResource(R.string.stats_retention),
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.retention.total == 0) {
                Text(
                    text = stringResource(R.string.stats_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = PersianText.percent(state.retention.rate),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.stats_retention_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.retention.rate < 0.80) {
                    Text(
                        text = stringResource(R.string.stats_suggestion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        SectionCard {
            Text(
                text = stringResource(R.string.stats_last_30),
                style = MaterialTheme.typography.titleMedium,
            )
            val max = state.last30.maxOfOrNull { it.reviewsDone }?.coerceAtLeast(1) ?: 1
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                state.last30.forEach { stat ->
                    val fraction = stat.reviewsDone.toFloat() / max
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height((110 * fraction).coerceAtLeast(2f).dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (stat.reviewsDone == 0) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.primary,
                            ),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = PersianText.number(state.totalReviews) + " " + stringResource(R.string.stats_reviews),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = stringResource(R.string.stats_total_words, PersianText.number(state.totalWords)),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        SectionCard {
            Text(
                text = stringResource(R.string.stats_boxes),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                (1..6).forEach { box ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = PersianText.number(state.boxes[box] ?: 0),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        BoxBadge(box = box)
                    }
                }
            }
        }

        androidx.compose.foundation.layout.Spacer(Modifier.padding(Spacing.xl))
    }
}
