package ir.wazhebox.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.wazhebox.R
import ir.wazhebox.core.text.PersianText
import ir.wazhebox.core.ui.theme.Spacing
import ir.wazhebox.domain.model.Level

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxl),
    ) {
        Spacer(Modifier.height(Spacing.xxxl))
        LinearProgressIndicator(
            progress = { (state.step + 1) / 3f },
            modifier = Modifier.fillMaxWidth(),
        )

        when (state.step) {
            0 -> ChoiceStep(
                title = stringResource(R.string.onboarding_minutes_title),
                subtitle = stringResource(R.string.onboarding_minutes_subtitle),
                options = listOf(
                    5 to stringResource(R.string.onboarding_minutes_5),
                    10 to stringResource(R.string.onboarding_minutes_10),
                    20 to stringResource(R.string.onboarding_minutes_20),
                ),
                selected = state.minutes,
                onSelect = viewModel::selectMinutes,
            )
            1 -> ChoiceStep(
                title = stringResource(R.string.onboarding_level_title),
                subtitle = stringResource(R.string.onboarding_level_subtitle),
                options = listOf(
                    Level.BEGINNER to stringResource(R.string.onboarding_level_beginner),
                    Level.INTERMEDIATE to stringResource(R.string.onboarding_level_intermediate),
                    Level.ADVANCED to stringResource(R.string.onboarding_level_advanced),
                ),
                selected = state.level,
                onSelect = viewModel::selectLevel,
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = stringResource(R.string.onboarding_ready_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.onboarding_ready_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.onboarding_sound_later),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.step > 0) {
                TextButton(onClick = viewModel::back) {
                    Text(stringResource(R.string.action_back))
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    if (state.step < 2) viewModel.next() else viewModel.finish()
                },
                enabled = !state.saving,
            ) {
                Text(
                    if (state.step < 2) stringResource(R.string.action_continue)
                    else stringResource(R.string.onboarding_start),
                )
            }
        }
    }
}

@Composable
private fun <T> ChoiceStep(
    title: String,
    subtitle: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        options.forEach { (value, label) ->
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = value == selected, onClick = { onSelect(value) }),
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    RadioButton(selected = value == selected, onClick = { onSelect(value) })
                    Text(text = label, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    if (value == selected) {
                        Text(
                            text = PersianText.number(1) + "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
