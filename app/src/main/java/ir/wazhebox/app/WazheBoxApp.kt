package ir.wazhebox.app

import android.app.Application
import android.content.res.Configuration
import dagger.hilt.android.HiltAndroidApp
import ir.wazhebox.core.notifications.NotificationChannels
import java.util.Locale

@HiltAndroidApp
class WazheBoxApp : Application() {

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
    }
}
