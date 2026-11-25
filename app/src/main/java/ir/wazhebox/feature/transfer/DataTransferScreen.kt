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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.wazhebox.R
import ir.wazhebox.core.text.PersianText
import ir.wazhebox.core.ui.rememberPickerLauncher
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
    var confirmReplace by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }
    // Every picker launch goes through this so a device without a document
    // provider shows a message instead of dying with an unhandled exception.
    val picker = rememberPickerLauncher()

    val createCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let(viewModel::exportCsv) }

    val createJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportBackup) }

    val openCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importCsv) }

    val openApkg = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importAnki) }

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
            Text(
                text = stringResource(R.string.io_export_all_decks_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { picker.launch { createCsv.launch("wazhebox-export-$today.csv") } },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            ) { Text(stringResource(R.string.settings_export_csv)) }
            OutlinedButton(
                onClick = { picker.launch { createJson.launch("wazhebox-backup-$today.json") } },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            ) { Text(stringResource(R.string.settings_backup)) }
        }

        SectionCard {
            Text(
                text = stringResource(R.string.io_import_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.io_import_preview_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = {
                    picker.launch {
                        openCsv.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values"))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            ) { Text(stringResource(R.string.settings_import_csv)) }
            OutlinedButton(
                onClick = {
                    picker.launch {
                        openApkg.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            ) { Text(stringResource(R.string.io_import_anki)) }
            Text(
                text = stringResource(R.string.io_import_anki_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // A preview is a promise: nothing has been written until the user taps
        // the confirm button below it.
        if (state.pending != null && state.report != null) {
            val preview = state.report!!
            SectionCard {
                Text(
                    text = stringResource(R.string.io_preview_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                ReportLine(stringResource(R.string.io_rows_read, PersianText.number(preview.rowsRead)))
                ReportLine(stringResource(R.string.io_preview_add, PersianText.number(preview.added)))
                if (state.ankiDecks > 0) {
                    ReportLine(stringResource(R.string.io_preview_decks, PersianText.number(state.ankiDecks)))
                }
                ReportLine(stringResource(R.string.io_rows_duplicate, PersianText.number(preview.duplicates)))
                ReportLine(stringResource(R.string.io_rows_no_meaning, PersianText.number(preview.missingMeaning)))
                ReportLine(stringResource(R.string.io_rows_no_term, PersianText.number(preview.missingTerm)))
                Button(
                    onClick = viewModel::confirmImport,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.busy,
                ) { Text(stringResource(R.string.io_confirm_import)) }
                TextButton(
                    onClick = viewModel::cancelImport,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.busy,
                ) { Text(stringResource(R.string.io_discard_import)) }
            }
        }

        if (state.pending == null && state.report != null) {
            val report = state.report!!
            SectionCard {
                Text(
                    text = stringResource(R.string.io_import_report),
                    style = MaterialTheme.typography.titleMedium,
                )
                state.importedWords?.let { added ->
                    Text(
                        text = stringResource(R.string.io_imported_now, PersianText.number(added)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                ReportLine(stringResource(R.string.io_rows_read, PersianText.number(report.rowsRead)))
                ReportLine(stringResource(R.string.io_words_added, PersianText.number(report.added)))
                ReportLine(stringResource(R.string.io_rows_duplicate, PersianText.number(report.duplicates)))
                ReportLine(stringResource(R.string.io_rows_no_meaning, PersianText.number(report.missingMeaning)))
                ReportLine(stringResource(R.string.io_rows_no_term, PersianText.number(report.missingTerm)))
                ReportLine(stringResource(R.string.io_tags_created, PersianText.number(report.tagsCreated)))
                if (report.batchId != null && state.importedWords != null) {
                    OutlinedButton(
                        onClick = { confirmUndo = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.busy,
                    ) {
                        Text(
                            text = stringResource(R.string.io_undo_import),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (report.rejected.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            picker.launch {
                                createReport.launch("wazhebox-import-report-$today.csv")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.io_download_report)) }
                }
            }
        }

        SectionCard {
            Text(
                text = stringResource(R.string.backup_auto_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.backup_auto_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = viewModel::backupNow,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            ) { Text(stringResource(R.string.settings_backup)) }
            if (state.autoBackups.isEmpty()) {
                Text(
                    text = stringResource(R.string.backup_auto_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.autoBackups.forEach { backup ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(
                                    R.string.backup_auto_item,
                                    PersianText.jalaliShort(
                                        kotlinx.datetime.Instant.fromEpochMilliseconds(backup.createdAt)
                                            .toLocalDateTime(TimeZone.currentSystemDefault()).date,
                                    ),
                                    PersianText.number((backup.sizeBytes / 1024).coerceAtLeast(1)),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        TextButton(
                            onClick = { viewModel.restoreAutoBackup(backup) },
                            enabled = !state.busy,
                        ) { Text(stringResource(R.string.backup_auto_restore)) }
                        TextButton(
                            onClick = { viewModel.deleteAutoBackup(backup) },
                            enabled = !state.busy,
                        ) {
                            Text(
                                text = stringResource(R.string.action_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
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
                onClick = {
                    picker.launch { openJsonPreview.launch(arrayOf("application/json", "text/plain", "*/*")) }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            ) { Text(stringResource(R.string.io_preview_backup)) }
            Button(
                onClick = {
                    // Replace wipes the database, so it asks first; merge is additive
                    // and only overwrites the fields the backup carries.
                    if (state.restoreMode == RestoreMode.REPLACE) {
                        confirmReplace = true
                    } else {
                        picker.launch {
                            openJsonRestore.launch(arrayOf("application/json", "text/plain", "*/*"))
                        }
                    }
                },
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

        if (picker.unavailable) {
            Text(
                text = stringResource(R.string.picker_missing),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        state.error?.let { error ->
            Text(
                text = when (error) {
                    "corrupt" -> stringResource(R.string.io_error_corrupt)
                    "version" -> stringResource(R.string.io_error_version)
                    "anki_unsupported" -> stringResource(R.string.io_anki_unsupported)
                    "anki_corrupt" -> stringResource(R.string.io_anki_corrupt)
                    "import_failed" -> stringResource(R.string.io_import_failed)
                    else -> stringResource(R.string.common_error)
                },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        state.message?.let { message ->
            Text(
                // Every write path reports its own sentence: a restore that
                // reused the export message looked like a broken export.
                text = when {
                    message.startsWith("import_undone:") -> stringResource(
                        R.string.io_undo_done,
                        PersianText.number(message.substringAfter(':').toIntOrNull() ?: 0),
                    )
                    message == "imported" -> stringResource(R.string.io_imported_done)
                    message == "restored" -> stringResource(R.string.io_restored)
                    message == "auto_restored" -> stringResource(R.string.backup_auto_restored)
                    message == "auto_saved" -> stringResource(R.string.io_saved)
                    else -> stringResource(R.string.io_saved)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            TextButton(onClick = viewModel::dismissMessage) {
                Text(stringResource(R.string.action_close))
            }
        }

        androidx.compose.foundation.layout.Spacer(Modifier.padding(Spacing.xl))
    }

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text(stringResource(R.string.io_restore_replace)) },
            text = { Text(stringResource(R.string.io_restore_replace_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReplace = false
                    picker.launch {
                        openJsonRestore.launch(arrayOf("application/json", "text/plain", "*/*"))
                    }
                }) { Text(stringResource(R.string.io_restore_replace), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReplace = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (confirmUndo) {
        AlertDialog(
            onDismissRequest = { confirmUndo = false },
            title = { Text(stringResource(R.string.io_undo_import)) },
            text = { Text(stringResource(R.string.io_undo_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUndo = false
                    viewModel.undoLastImport()
                }) { Text(stringResource(R.string.io_undo_import), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUndo = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ReportLine(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium)
}
