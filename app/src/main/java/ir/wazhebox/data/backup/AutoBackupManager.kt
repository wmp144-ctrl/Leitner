package ir.wazhebox.data.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.wazhebox.core.time.StudyClock
import ir.wazhebox.data.io.BackupResult
import ir.wazhebox.data.io.DataTransferRepository
import ir.wazhebox.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** One automatic backup sitting in the app's own storage. */
data class AutoBackupFile(
    val file: File,
    val createdAt: Long,
    val sizeBytes: Long,
)

/**
 * Keeps a rolling set of backups inside the app's private files, so a phone that
 * never visits the export dialog still has a recovery point. No storage
 * permission and no network are involved: this is a file in `filesDir`.
 */
@Singleton
class AutoBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataTransfer: DataTransferRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: StudyClock,
) {

    private val directory: File
        get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    /**
     * Writes a backup when the last one is older than [minAgeHours]. Returns the
     * file it wrote, or null when the existing backup is still fresh (or the
     * write failed and will be retried on the next launch).
     */
    suspend fun runIfDue(minAgeHours: Long = 20): File? {
        val settings = settingsRepository.settings.first()
        val now = clock.now().toEpochMilliseconds()
        val last = settings.lastBackupAt
        if (last != null && now - last < minAgeHours * 60 * 60 * 1000L) return null
        return runCatching { writeBackup(now) }.getOrNull()
    }

    /** Exports right now, ignoring the age check (used by the settings action). */
    suspend fun backupNow(): File = writeBackup(clock.now().toEpochMilliseconds())

    private suspend fun writeBackup(now: Long): File {
        val json = dataTransfer.exportBackupJson()
        val day = Instant.fromEpochMilliseconds(now)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toString()
        val target = File(directory, "$PREFIX$day.json")
        target.writeText(json, Charsets.UTF_8)
        settingsRepository.setLastBackupAt(now)
        prune()
        return target
    }

    fun list(): List<AutoBackupFile> = directory
        .listFiles { file -> file.isFile && file.name.endsWith(".json") }
        .orEmpty()
        .sortedByDescending { it.lastModified() }
        .map { AutoBackupFile(file = it, createdAt = it.lastModified(), sizeBytes = it.length()) }

    suspend fun restore(file: File, merge: Boolean = true): BackupResult =
        runCatching { dataTransfer.restoreBackup(file.readText(Charsets.UTF_8), merge) }
            .getOrDefault(BackupResult.Corrupt)

    fun delete(file: File): Boolean = runCatching { file.delete() }.getOrDefault(false)

    /** Only the newest [keep] files stay; the rest are deleted oldest-first. */
    private fun prune(keep: Int = 3) {
        list().drop(keep).forEach { runCatching { it.file.delete() } }
    }

    private companion object {
        const val DIRECTORY = "backups"
        const val PREFIX = "wazhebox-auto-"
    }
}
