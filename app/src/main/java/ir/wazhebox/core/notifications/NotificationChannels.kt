package ir.wazhebox.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * One channel only (PRD §17): no growth nudges, no streak shaming.
 */
object NotificationChannels {

    const val DAILY_REVIEW_ID = "daily_review"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            DAILY_REVIEW_ID,
            context.getString(ir.wazhebox.R.string.notification_channel_review),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(ir.wazhebox.R.string.notification_body)
            setShowBadge(true)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }
}
