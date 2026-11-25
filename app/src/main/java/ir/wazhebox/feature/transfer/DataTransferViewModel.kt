package ir.wazhebox.feature.transfer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.wazhebox.data.backup.AutoBackupFile
import ir.wazhebox.data.backup.AutoBackupManager
import ir.wazhebox.data.io.AnkiImportResult
import ir.wazhebox.data.io.BackupResult
import ir.wazhebox.data.io.DataTransferRepository
import ir.wazhebox.data.io.ImportReport
import ir.wazhebox.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.io.File
import javax.inject.Inject

enum class RestoreMode { MERGE, REPLACE }

/** What a preview is waiting for: the picked file is kept until confirmed. */
enum class PendingImport { CSV, ANKI }

data class TransferUiState(
    val busy: Boolean = false,
    /** A dry run while [pending] is set; the applied report afterwards. */
    val report: ImportReport? = null,
    val restoreMode: RestoreMode = RestoreMode.MERGE,
    val message: String? = null,
    val error: String? = null,
    val previewText: String? = null,
    val autoBackups: List<AutoBackupFile> = emptyList(),
    val ankiDecks: Int = 0,
    val pending: PendingImport? = null,
    /** How many words the just-finished import added (null when none). */
    val importedWords: Int? = null,
)

@HiltViewModel
class DataTransferViewModel @Inject constructor(
    private val dataTransfer: DataTransferRepository,
    private val settingsRepository: SettingsRepository,
    private val autoBackupManager: AutoBackupManager,
    private val widgetUpdater: ir.wazhebox.widget.WidgetUpdater,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(TransferUiState())
    val state: StateFlow<TransferUiState> = _state.asStateFlow()

    // A preview has to be able to run twice: once to count, once to write.
    private var pendingCsvText: String? = null
    private var pendingAnkiFile: File? = null

    init {
        refreshAutoBackups()
    }

    fun setRestoreMode(mode: RestoreMode) {
        _state.value = _state.value.copy(restoreMode = mode)
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null, error = null)
    }

    fun exportCsv(uri: Uri) = write(uri) { dataTransfer.exportCsv() }

    fun exportBackup(uri: Uri) = write(uri) {
        dataTransfer.exportBackupJson().also {
            settingsRepository.setLastBackupAt(Clock.System.now().toEpochMilliseconds())
        }
    }

    fun exportReport(uri: Uri, report: ImportReport) = write(uri) { report.rejectedCsv() }

    /** Step one: read the file, count what it holds and show the numbers. */
    fun importCsv(uri: Uri) {
        _state.value = _state.value.copy(
            busy = true,
            error = null,
            message = null,
            report = null,
            importedWords = null,
        )
        viewModelScope.launch {
            runCatching {
                val text = readUri(uri)
                pendingCsvText = text
                dataTransfer.importCsv(text, apply = false)
            }.onSuccess { preview ->
                _state.value = _state.value.copy(
                    busy = false,
                    report = preview,
                    pending = PendingImport.CSV,
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(busy = false, error = error.message)
            }
        }
    }

    /** Step one for .apkg: the zip is copied once, then read twice. */
    fun importAnki(uri: Uri) {
        _state.value = _state.value.copy(
            busy = true,
            error = null,
            message = null,
            report = null,
            importedWords = null,
            ankiDecks = 0,
        )
        viewModelScope.launch {
            val result = runCatching {
                val file = cacheAnki(uri)
                pendingAnkiFile = file
                dataTransfer.previewAnki(file)
            }.getOrNull()
            _state.value = when (result) {
                is AnkiImportResult.Imported -> _state.value.copy(
                    busy = false,
                    report = result.report,
                    ankiDecks = result.decks,
                    pending = PendingImport.ANKI,
                )
                is AnkiImportResult.Unsupported -> _state.value.copy(busy = false, error = "anki_unsupported")
                is AnkiImportResult.Corrupt, null -> _state.value.copy(busy = false, error = "anki_corrupt")
            }
        }
    }

    /** Step two: the user saw the counts and agreed, so write them. */
    fun confirmImport() {
        val kind = _state.value.pending ?: return
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            val result = runCatching {
                when (kind) {
                    PendingImport.CSV -> {
                        val text = pendingCsvText ?: error("read_failed")
                        AppliedImport(dataTransfer.importCsv(text, apply = true), 0)
                    }
                    PendingImport.ANKI -> {
                        val file = pendingAnkiFile ?: error("read_failed")
                        when (val imported = dataTransfer.importAnki(file)) {
                            is AnkiImportResult.Imported ->
                                AppliedImport(imported.report, imported.decks)
                            else -> error("anki_corrupt")
                        }
                    }
                }
            }.getOrNull()
            if (result == null) {
                _state.value = _state.value.copy(busy = false, error = "import_failed")
                return@launch
            }
            runCatching { widgetUpdater.refresh() }
            clearPending()
            _state.value = _state.value.copy(
                busy = false,
                report = result.report,
                pending = null,
                importedWords = result.report.added,
                ankiDecks = result.decks,
                message = "imported",
                error = null,
            )
            refreshAutoBackups()
        }
    }

    fun cancelImport() {
        clearPending()
        _state.value = _state.value.copy(pending = null, report = null)
    }

    fun undoLastImport() {
        val batch = _state.value.report?.batchId ?: return
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            val removed = runCatching { dataTransfer.undoImport(batch) }.getOrDefault(0)
            runCatching { widgetUpdater.refresh() }
            _state.value = _state.value.copy(
                busy = false,
                report = null,
                importedWords = null,
                message = "import_undone:$removed",
            )
        }
    }

    private data class AppliedImport(val report: ImportReport, val decks: Int)

    private fun clearPending() {
        pendingCsvText = null
        pendingAnkiFile?.delete()
        pendingAnkiFile = null
    }

    private fun cacheAnki(uri: Uri): File = run {
        val file = File(context.cacheDir, "anki-pending.apkg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: error("open_failed")
        file
    }

    fun previewBackup(uri: Uri) {
        _state.value = _state.value.copy(busy = true, error = null, message = null, previewText = null)
        viewModelScope.launch {
            when (val result = runCatching { dataTransfer.previewBackup(readUri(uri)) }.getOrNull()) {
                null -> _state.value = _state.value.copy(busy = false, error = "read_failed")
                is BackupResult.Corrupt -> _state.value = _state.value.copy(busy = false, error = "corrupt")
                is BackupResult.NewerVersion -> _state.value = _state.value.copy(busy = false, error = "version")
                is BackupResult.Success -> _state.value = _state.value.copy(
                    busy = false,
                    previewText = "${result.preview.decks},${result.preview.words},${result.preview.reviews}",
                )
            }
        }
    }

    fun restoreBackup(uri: Uri) {
        _state.value = _state.value.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            val result = runCatching {
                dataTransfer.restoreBackup(readUri(uri), merge = _state.value.restoreMode == RestoreMode.MERGE)
            }.getOrNull()
            if (result is BackupResult.Success) runCatching { widgetUpdater.refresh() }
            _state.value = when (result) {
                is BackupResult.Success -> _state.value.copy(busy = false, message = "restored")
                is BackupResult.Corrupt, null -> _state.value.copy(busy = false, error = "corrupt")
                is BackupResult.NewerVersion -> _state.value.copy(busy = false, error = "version")
            }
        }
    }

    /** Restores one of the backups the app wrote on its own. */
    fun restoreAutoBackup(backup: AutoBackupFile) {
        _state.value = _state.value.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            val result = autoBackupManager.restore(backup.file, merge = _state.value.restoreMode == RestoreMode.MERGE)
            if (result is BackupResult.Success) runCatching { widgetUpdater.refresh() }
            _state.value = when (result) {
                is BackupResult.Success -> _state.value.copy(busy = false, message = "auto_restored")
                is BackupResult.Corrupt -> _state.value.copy(busy = false, error = "corrupt")
                is BackupResult.NewerVersion -> _state.value.copy(busy = false, error = "version")
            }
            refreshAutoBackups()
        }
    }

    fun deleteAutoBackup(backup: AutoBackupFile) {
        autoBackupManager.delete(backup.file)
        refreshAutoBackups()
    }

    /** Writes a fresh automatic backup without waiting for the daily check. */
    fun backupNow() {
        _state.value = _state.value.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            runCatching { autoBackupManager.backupNow() }
                .onSuccess { _state.value = _state.value.copy(busy = false, message = "auto_saved") }
                .onFailure { _state.value = _state.value.copy(busy = false, error = it.message) }
            refreshAutoBackups()
        }
    }

    private fun refreshAutoBackups() {
        _state.value = _state.value.copy(autoBackups = autoBackupManager.list())
    }

    private fun write(uri: Uri, producer: suspend () -> String) {
        _state.value = _state.value.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            runCatching {
                val content = producer()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(content.toByteArray(Charsets.UTF_8))
                    } ?: error("open_failed")
                }
            }.onSuccess {
                _state.value = _state.value.copy(busy = false, message = "exported")
            }.onFailure {
                _state.value = _state.value.copy(busy = false, error = it.message)
            }
            refreshAutoBackups()
        }
    }

    private suspend fun readUri(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("open_failed")
    }

    override fun onCleared() {
        clearPending()
        super.onCleared()
    }
}
