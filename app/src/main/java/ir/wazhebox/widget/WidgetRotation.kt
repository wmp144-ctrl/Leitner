package ir.wazhebox.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Redraws the widget shortly after midnight so the new day's words appear even
 * when the launcher never asks for a new frame.
 *
 * The page itself is derived from the date in [WidgetUpdater.page], so this job
 * never has to keep a cursor: it is a nudge, and losing a run only delays the
 * new words until the next redraw.
 */
class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        runCatching {
            EntryPointAccessors
                .fromApplication(applicationContext, WidgetEntryPoint::class.java)
                .widgetUpdater()
                .refresh()
        }
        return Result.success()
    }
}

/** Registers the single daily redraw job. */
@Singleton
class WidgetRotationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun schedule() {
        val now = LocalDateTime.now()
        val nextMidnight = LocalDate.now().plusDays(1).atStartOfDay().plusMinutes(5)
        val initialDelay = Duration.between(now, nextMidnight).toMinutes().coerceAtLeast(0)
        val work = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
            ROTATION_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setInitialDelay(initialDelay, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, work)
    }

    private companion object {
        const val UNIQUE_NAME = "widget_word_rotation"
        const val ROTATION_INTERVAL_HOURS = 24L
    }
}
