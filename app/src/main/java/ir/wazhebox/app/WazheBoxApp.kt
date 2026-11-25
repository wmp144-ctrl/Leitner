package ir.wazhebox.app

import android.app.Application
import android.content.res.Configuration
import dagger.hilt.android.HiltAndroidApp
import ir.wazhebox.core.notifications.NotificationChannels
import ir.wazhebox.data.backup.AutoBackupManager
import ir.wazhebox.data.reminder.ReminderScheduler
import ir.wazhebox.data.settings.SettingsRepository
import ir.wazhebox.widget.WidgetRotationScheduler
import ir.wazhebox.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class WazheBoxApp : Application() {

    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var widgetUpdater: WidgetUpdater
    @Inject lateinit var widgetRotationScheduler: WidgetRotationScheduler
    @Inject lateinit var autoBackupManager: AutoBackupManager
    @Inject lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // The 1.0 UI is Persian-only (PRD §23); pin the locale so numbers and
        // formatting never depend on the device language.
        val persian = Locale("fa", "IR")
        Locale.setDefault(persian)
        val config = Configuration(resources.configuration)
        config.setLocale(persian)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)

        NotificationChannels.ensureCreated(this)
        scope.launch {
            runCatching { settingsRepository.incrementAppOpened() }
            // One automatic backup a day, kept in the app's private files.
            runCatching { autoBackupManager.runIfDue() }
            runCatching { reminderScheduler.schedule() }
            runCatching { widgetRotationScheduler.schedule() }
            runCatching { widgetUpdater.refresh() }
        }
    }
}
