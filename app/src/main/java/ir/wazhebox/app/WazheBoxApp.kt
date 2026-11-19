package ir.wazhebox.app

import android.app.Application
import android.content.res.Configuration
import dagger.hilt.android.HiltAndroidApp
import ir.wazhebox.core.notifications.NotificationChannels
import ir.wazhebox.data.reminder.ReminderScheduler
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
            runCatching { reminderScheduler.schedule() }
            runCatching { widgetUpdater.refresh() }
        }
    }
}
