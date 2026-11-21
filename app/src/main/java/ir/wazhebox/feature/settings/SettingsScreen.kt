package ir.wazhebox.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import ir.wazhebox.core.ui.components.KeyValueRow
import ir.wazhebox.core.ui.components.SectionCard
import ir.wazhebox.core.ui.theme.Spacing
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onOpenMenu: () -> Unit,
    onOpenDataTransfer: () -> Unit,
    onOpenTts: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenMenu) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = stringResource(R.string.nav_menu),
                )
            }
        }

        SectionCard {
            Text(
                text = stringResource(R.string.settings_daily),
                style = MaterialTheme.typography.titleMedium,
            )
            KeyValueRow(
                label = stringResource(R.string.settings_new_limit),
                value = PersianText.number(settings.dailyNewLimit),
            )
            Slider(
                value = settings.dailyNewLimit.toFloat(),
                onValueChange = { viewModel.setNewLimit(it.roundToInt()) },
                valueRange = 0f..50f,
                steps = 49,
            )
            KeyValueRow(
                label = stringResource(R.string.settings_review_limit),
                value = PersianText.number(settings.dailyReviewLimit),
            )
            Slider(
                value = settings.dailyReviewLimit.toFloat(),
                onValueChange = { viewModel.setReviewLimit(it.roundToInt()) },
                valueRange = 0f..500f,
                steps = 9,
            )
        }

        SectionCard {
            Text(
                text = stringResource(R.string.stats_retention),
                style = MaterialTheme.typography.titleMedium,
            )
            KeyValueRow(
                label = stringResource(R.string.settings_retention),
                value = stringResource(
                    R.string.settings_retention_value,
                    PersianText.number((settings.desiredRetention * 100).roundToInt()),
                ),
            )
            Slider(
                value = settings.desiredRetention.toFloat(),
                onValueChange = { viewModel.setRetention(((it * 100).roundToInt() / 100.0)) },
                valueRange = 0.80f..0.95f,
                steps = 14,
            )
            KeyValueRow(
                label = stringResource(R.string.settings_max_interval),
                value = PersianText.number(settings.maximumIntervalDays),
            )
            Slider(
                value = settings.maximumIntervalDays.toFloat(),
                onValueChange = { viewModel.setMaxIntervalDays(it.roundToInt()) },
                valueRange = 30f..730f,
                steps = 6,
            )
            KeyValueRow(
                label = stringResource(R.string.settings_start_hour),
                value = PersianText.number(settings.studyDayStartHour),
            )
            Slider(
                value = settings.studyDayStartHour.toFloat(),
                onValueChange = { viewModel.setStudyDayStartHour(it.roundToInt()) },
                valueRange = 0f..12f,
                steps = 11,
            )
        }

        SectionCard {
            Text(
                text = stringResource(R.string.settings_reminder),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.settings_reminder_enable))
                Switch(
                    checked = settings.reminderEnabled,
                    onCheckedChange = viewModel::setReminderEnabled,
                )
            }
            KeyValueRow(
                label = stringResource(R.string.settings_reminder_time),
                value = PersianText.toPersianDigits(
                    "%02d:%02d".format(settings.reminderHour, settings.reminderMinute),
                ),
            )
            Slider(
                value = settings.reminderHour.toFloat(),
                onValueChange = { viewModel.setReminderTime(it.roundToInt(), settings.reminderMinute) },
                valueRange = 0f..23f,
                steps = 22,
            )
            Text(
                text = stringResource(R.string.settings_reminder_quiet),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                (1..7).forEach { day ->
                    val labels = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
                    TextButton(
                        // The last active day cannot be switched off (it would silence
                        // the reminder for good), so the button is disabled instead of
                        // silently doing nothing.
                        enabled = !(day in settings.reminderDays && settings.reminderDays.size == 1),
                        onClick = { viewModel.setReminderDay(day, day !in settings.reminderDays) },
                    ) {
                        Text(
                            text = labels[day - 1],
                            color = if (day in settings.reminderDays) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }

        SectionCard {
            Text(
                text = stringResource(R.string.settings_sound),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.settings_haptics))
                Switch(
                    checked = settings.hapticsEnabled,
                    onCheckedChange = viewModel::setHaptics,
                )
            }
            KeyValueRow(
                label = stringResource(R.string.settings_speech_rate),
                value = PersianText.number((settings.speechRate * 100).roundToInt()) + "٪",
            )
            Slider(
                value = settings.speechRate,
                onValueChange = viewModel::setSpeechRate,
                valueRange = 0.5f..1.25f,
                steps = 14,
            )
            TextButton(onClick = viewModel::testSpeech) {
                Text(stringResource(R.string.settings_test_sound))
            }
            TextButton(onClick = onOpenTts) {
                Text(stringResource(R.string.review_install_tts))
            }
        }

        SectionCard {
            Text(
                text = stringResource(R.string.settings_data),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = onOpenDataTransfer, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_import_csv) + " / " + stringResource(R.string.settings_export_csv))
            }
            KeyValueRow(
                label = stringResource(R.string.settings_backup),
                value = settings.lastBackupAt?.let {
                    PersianText.jalaliShort(
                        kotlinx.datetime.Instant.fromEpochMilliseconds(it)
                            .toLocalDateTime(TimeZone.currentSystemDefault()).date,
                    )
                } ?: stringResource(R.string.settings_no_backup),
            )
            HorizontalDivider()
            Text(
                text = stringResource(R.string.settings_privacy),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TextButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_about))
        }
        Spacer(Modifier.padding(Spacing.xl))
    }
}
