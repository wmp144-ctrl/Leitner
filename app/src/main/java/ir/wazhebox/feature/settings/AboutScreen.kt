package ir.wazhebox.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ir.wazhebox.BuildConfig
import ir.wazhebox.R
import ir.wazhebox.core.ui.components.SectionCard
import ir.wazhebox.core.ui.theme.Spacing

@Composable
fun AboutScreen(onBack: () -> Unit) {
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
                text = stringResource(R.string.settings_about),
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        SectionCard {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.settings_privacy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard {
            Text(
                text = stringResource(R.string.settings_licenses),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Vazirmatn — مجوز SIL Open Font License 1.1\n" +
                    "Inter — مجوز SIL Open Font License 1.1\n" +
                    "FSRS-6 — الگوریتم تکرار فاصله‌دار (open-spaced-repetition)\n" +
                    "Jetpack Compose، Room، Hilt، Glance، WorkManager — Apache 2.0",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SectionCard {
            Text(
                text = "این اپ در نسخهٔ ۱٫۰ هیچ داده‌ای جمع‌آوری، ارسال یا تحلیل نمی‌کند. " +
                    "همهٔ لغات، مرورها و تنظیمات فقط روی همین گوشی ذخیره می‌شوند.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
