package ir.wazhebox.feature.transfer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import javax.inject.Inject

enum class RestoreMode { MERGE, REPLACE }

data class TransferUiState(
    val busy: Boolean = false,
    val report: ImportReport? = null,
    val restoreMode: RestoreMode = RestoreMode.MERGE,
    val message: String? = null,
    val error: String? = null,
    val previewText: String? = null,
)

@HiltViewModel
class DataTransferViewModel @Inject constructor(
    private val dataTransfer: DataTransferRepository,
    private val settingsRepository: SettingsRepository,
    private val widgetUpdater: ir.wazhebox.widget.WidgetUpdater,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(TransferUiState())
    val state: StateFlow<TransferUiState> = _state.asStateFlow()

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

    fun importCsv(uri: Uri) {
        _state.value = _state.value.copy(busy = true, error = null, message = null, report = null)
        viewModelScope.launch {
            runCatching {
                val text = readUri(uri)
                dataTransfer.importCsv(text)
            }.onSuccess { report ->
                _state.value = _state.value.copy(busy = false, report = report)
                // The widget reads the database itself, so it needs a nudge after
                // words change here as well as after a session.
                runCatching { widgetUpdater.refresh() }
            }.onFailure { error ->
                _state.value = _state.value.copy(busy = false, error = error.message)
            }
        }
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
        }
    }

    private suspend fun readUri(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("open_failed")
    }
}
