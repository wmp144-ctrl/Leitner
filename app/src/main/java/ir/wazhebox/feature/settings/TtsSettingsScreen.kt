package ir.wazhebox.feature.settings

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.wazhebox.R
import ir.wazhebox.core.text.PersianText
import ir.wazhebox.core.ui.components.SectionCard
import ir.wazhebox.core.ui.theme.Spacing
import ir.wazhebox.data.tts.TtsStatus
import kotlin.math.roundToInt

@Composable
fun TtsSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val status by viewModel.ttsStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(
                text = stringResource(R.string.settings_sound),
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        SectionCard {
            Text(
                text = when (status) {
                    TtsStatus.READY -> "صدای انگلیسی آماده است."
                    TtsStatus.MISSING_DATA -> stringResource(R.string.review_tts_missing)
                    TtsStatus.NOT_SUPPORTED -> stringResource(R.string.review_tts_missing)
                    TtsStatus.ERROR -> stringResource(R.string.review_tts_missing)
                    TtsStatus.UNKNOWN -> stringResource(R.string.common_loading)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.settings_speech_rate) + ": " +
                    PersianText.number((settings.speechRate * 100).roundToInt()) + "٪",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = settings.speechRate,
                onValueChange = viewModel::setSpeechRate,
                valueRange = 0.5f..1.25f,
                steps = 14,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(onClick = viewModel::testSpeech) {
                    Text(stringResource(R.string.settings_test_sound))
                }
                Button(onClick = {
                    val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                    runCatching { context.startActivity(intent) }
                        .onFailure {
                            runCatching {
                                context.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                            }
                        }
                }) {
                    Text(stringResource(R.string.review_install_tts))
                }
            }
        }

        SectionCard {
            Text(
                text = "برای نصب صدای انگلیسی: تنظیمات گوشی ← زبان و ورودی ← خروجی تبدیل متن به گفتار ← موتور Google ← نصب داده‌های صوتی.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
