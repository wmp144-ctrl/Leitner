package ir.wazhebox.feature.transfer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import ir.wazhebox.core.ui.components.SectionCard
import ir.wazhebox.core.ui.theme.Spacing
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun DataTransferScreen(
    onBack: () -> Unit,
    viewModel: DataTransferViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val createCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let(viewModel::exportCsv) }

    val createJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportBackup) }

    val openCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importCsv) }

    val openJsonPreview = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::previewBackup) }

    val openJsonRestore = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::restoreBackup) }

    val createReport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        uri?.let { target ->
            state.report?.let { report -> viewModel.exportReport(target, report) }
        }
    }

    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

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
                text = stringResource(R.string.settings_data),
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        SectionCard {
            Text(
                text = stringResource(R.string.io_export_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Button(
                onClick = { createCsv.launch("wazhebox-export-$today.csv") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            ) { Text(stringResource(R.string.settings_export_csv)) }
            OutlinedButton(
                onClick = { createJson.launch("wazhebox-backup-$today.json") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            ) { Text(stringResource(R.string.settings_backup)) }
        }

        SectionCard {
            Text(
                text = stringResource(R.string.io_import_title),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedButton(
                onClick = { openCsv.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            ) { Text(stringResource(R.string.settings_import_csv)) }
        }

        SectionCard {
            Text(
                text = stringResource(R.string.settings_restore),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.io_restore_mode),
                style = MaterialTheme.typography.labelLarge,
            )
            RestoreMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = state.restoreMode == mode,
                            onClick = { viewModel.setRestoreMode(mode) },
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    RadioButton(
                        selected = state.restoreMode == mode,
                        onClick = { viewModel.setRestoreMode(mode) },
                    )
                    Text(
                        stringResource(
                            if (mode == RestoreMode.MERGE) R.string.io_restore_merge
                            else R.string.io_restore_replace,
                        ),
                    )
                }
            }
            OutlinedButton(
                onClick = { openJsonPreview.launch(arrayOf("application/json", "text/plain", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            ) { Text(stringResource(R.string.settings_restore) + " (پیش‌نمایش)") }
            Button(
                onClick = { openJsonRestore.launch(arrayOf("application/json", "text/plain", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            ) { Text(stringResource(R.string.settings_restore)) }
        }

        state.previewText?.let { preview ->
            val parts = preview.split(',')
            SectionCard {
                Text(
                    text = stringResource(
                        R.string.io_restore_preview,
                        PersianText.number(parts.getOrNull(0)?.toIntOrNull() ?: 0),
                        PersianText.number(parts.getOrNull(1)?.toIntOrNull() ?: 0),
                        PersianText.number(parts.getOrNull(2)?.toIntOrNull() ?: 0),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        state.report?.let { report ->
            SectionCard {
                Text(
                    text = stringResource(R.string.io_import_report),
                    style = MaterialTheme.typography.titleMedium,
                )
                ReportLine(stringResource(R.string.io_rows_read, PersianText.number(report.rowsRead)))
                ReportLine(stringResource(R.string.io_words_added, PersianText.number(report.added)))
                ReportLine(stringResource(R.string.io_rows_duplicate, PersianText.number(report.duplicates)))
                ReportLine(stringResource(R.string.io_rows_no_meaning, PersianText.number(report.missingMeaning)))
                ReportLine(stringResource(R.string.io_rows_no_term, PersianText.number(report.missingTerm)))
                ReportLine(stringResource(R.string.io_tags_created, PersianText.number(report.tagsCreated)))
                if (report.rejected.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { createReport.launch("wazhebox-import-report-$today.csv") },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.io_download_report)) }
                }
            }
        }

        state.error?.let { error ->
            Text(
                text = when (error) {
                    "corrupt" -> stringResource(R.string.io_error_corrupt)
                    "version" -> stringResource(R.string.io_error_version)
                    else -> stringResource(R.string.common_error)
                },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        state.message?.let {
            Text(
                text = stringResource(R.string.io_export_done, ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            TextButton(onClick = viewModel::dismissMessage) {
                Text(stringResource(R.string.action_close))
            }
        }

        androidx.compose.foundation.layout.Spacer(Modifier.padding(Spacing.xl))
    }
}

@Composable
private fun ReportLine(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium)
}
