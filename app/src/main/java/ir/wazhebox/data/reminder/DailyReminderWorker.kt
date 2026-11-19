package ir.wazhebox.data.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.wazhebox.R
import ir.wazhebox.app.MainActivity
import ir.wazhebox.core.notifications.NotificationChannels
import ir.wazhebox.core.time.StudyClock
import ir.wazhebox.core.time.StudyDays
import ir.wazhebox.data.db.WazheBoxDatabase
import ir.wazhebox.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * One daily reminder (PRD §17): skipped when today's queue is empty, silent
 * between 23:00 and 07:00, no streaks and no guilt.
 */
class DailyReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var database: WazheBoxDatabase

    @Inject
    lateinit var clock: StudyClock

    override suspend fun doWork(): Result {
        val entryPoint = dagger.hilt.android.EntryPointAccessors
            .fromApplication(applicationContext, ReminderEntryPoint::class.java)
        settingsRepository = entryPoint.settingsRepository()
        database = entryPoint.database()
        clock = entryPoint.clock()

        val settings = settingsRepository.settings.first()
        if (!settings.reminderEnabled) return Result.success()

        val now = clock.now()
        val zone = ZoneId.of(clock.timeZone().id)
        val local = LocalDateTime.now(zone)
        val dayOfWeek = local.dayOfWeek.value
        if (dayOfWeek !in settings.reminderDays) return Result.success()
        if (local.hour < 7 || local.hour >= 23) return Result.success()

        val due = database.cardDao().countDueNow(now.toEpochMilliseconds())
        val today = StudyDays.studyDayOf(now, clock.timeZone(), settings.studyDayStartHour)
        val stat = database.statsDao().forDay(today.toString())
        if (due <= 0) return Result.success()
        if (stat != null && stat.reviewsDone > 0 && due == 0) return Result.success()

        showNotification(due)
        return Result.success()
    }

    private fun showNotification(due: Int) {
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return
        NotificationChannels.ensureCreated(applicationContext)

        val intent = Intent(android.content.Intent.ACTION_VIEW, Uri.parse("wazhebox://session")).apply {
            setClass(applicationContext, MainActivity::class.java)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0,
        )

        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.DAILY_REVIEW_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(
                applicationContext.getString(R.string.notification_title, due.toString()),
            )
            .setContentText(applicationContext.getString(R.string.notification_body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        const val NOTIFICATION_ID = 101
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface ReminderEntryPoint {
    fun settingsRepository(): SettingsRepository
    fun database(): WazheBoxDatabase
    fun clock(): StudyClock
}

/** Schedules the single daily worker at the user's chosen hour (§16.3, §17). */
@javax.inject.Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun schedule() {
        val settings = settingsRepository.settings.first()
        val work = if (!settings.reminderEnabled) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
            return
        } else {
            val delayMinutes = initialDelayMinutes(settings.reminderHour, settings.reminderMinute)
            PeriodicWorkRequestBuilder<DailyReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .build()
        }
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, work)
    }

    private fun initialDelayMinutes(hour: Int, minute: Int): Long {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        var target = now.toLocalDate().atTime(hour, minute).atZone(zone)
        if (target.isBefore(now)) target = target.plusDays(1)
        return Duration.between(now, target).toMinutes().coerceAtLeast(1)
    }

    private companion object {
        const val UNIQUE_NAME = "daily_review_reminder"
    }
}
