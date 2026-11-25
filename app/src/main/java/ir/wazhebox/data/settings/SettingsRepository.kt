package ir.wazhebox.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.wazhebox.domain.model.Level
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** DataStore-backed settings; every write is non-blocking (PRD §7.2). */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val ONBOARDING = booleanPreferencesKey("onboarding_completed")
        val DAILY_MINUTES = intPreferencesKey("daily_minutes")
        val LEVEL = stringPreferencesKey("level")
        val NEW_LIMIT = intPreferencesKey("daily_new_limit")
        val REVIEW_LIMIT = intPreferencesKey("daily_review_limit")
        val RETENTION = floatPreferencesKey("desired_retention")
        val MAX_INTERVAL = intPreferencesKey("maximum_interval_days")
        val START_HOUR = intPreferencesKey("study_day_start_hour")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val SWIPE = booleanPreferencesKey("swipe_enabled")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val REMINDER_DAYS = stringPreferencesKey("reminder_days")
        val LAST_BACKUP = longPreferencesKey("last_backup_at")
        val LAST_BACKUP_REMINDER = longPreferencesKey("last_backup_reminder_at")
        val TTS_CHECKED = booleanPreferencesKey("tts_checked")
        val APP_OPENED = intPreferencesKey("app_opened_count")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AMOLED = booleanPreferencesKey("amoled_dark")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val defaults = UserSettings()
        UserSettings(
            onboardingCompleted = prefs[Keys.ONBOARDING] ?: defaults.onboardingCompleted,
            dailyMinutes = prefs[Keys.DAILY_MINUTES] ?: defaults.dailyMinutes,
            level = Level.fromName(prefs[Keys.LEVEL]),
            dailyNewLimit = prefs[Keys.NEW_LIMIT] ?: defaults.dailyNewLimit,
            dailyReviewLimit = prefs[Keys.REVIEW_LIMIT] ?: defaults.dailyReviewLimit,
            desiredRetention = (prefs[Keys.RETENTION] ?: defaults.desiredRetention.toFloat()).toDouble(),
            maximumIntervalDays = prefs[Keys.MAX_INTERVAL] ?: defaults.maximumIntervalDays,
            studyDayStartHour = prefs[Keys.START_HOUR] ?: defaults.studyDayStartHour,
            speechRate = prefs[Keys.SPEECH_RATE] ?: defaults.speechRate,
            hapticsEnabled = prefs[Keys.HAPTICS] ?: defaults.hapticsEnabled,
            swipeEnabled = prefs[Keys.SWIPE] ?: defaults.swipeEnabled,
            reminderEnabled = prefs[Keys.REMINDER_ENABLED] ?: defaults.reminderEnabled,
            reminderHour = prefs[Keys.REMINDER_HOUR] ?: defaults.reminderHour,
            reminderMinute = prefs[Keys.REMINDER_MINUTE] ?: defaults.reminderMinute,
            reminderDays = prefs[Keys.REMINDER_DAYS]
                ?.split(',')
                ?.mapNotNull { it.toIntOrNull() }
                ?.toSet()
                ?: defaults.reminderDays,
            lastBackupAt = prefs[Keys.LAST_BACKUP],
            lastBackupReminderAt = prefs[Keys.LAST_BACKUP_REMINDER],
            ttsChecked = prefs[Keys.TTS_CHECKED] ?: defaults.ttsChecked,
            appOpenedCount = prefs[Keys.APP_OPENED] ?: defaults.appOpenedCount,
            themeMode = ThemeMode.fromName(prefs[Keys.THEME_MODE]),
            amoled = prefs[Keys.AMOLED] ?: defaults.amoled,
        )
    }

    suspend fun completeOnboarding(minutes: Int, level: Level) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING] = true
            prefs[Keys.DAILY_MINUTES] = minutes
            prefs[Keys.LEVEL] = level.name
            prefs[Keys.NEW_LIMIT] = when {
                minutes <= 5 -> 5
                minutes <= 10 -> 10
                else -> 20
            }
        }
    }

    suspend fun setDailyNewLimit(value: Int) = edit { it[Keys.NEW_LIMIT] = value.coerceIn(0, 200) }

    suspend fun setDailyReviewLimit(value: Int) = edit { it[Keys.REVIEW_LIMIT] = value.coerceIn(0, 1000) }

    suspend fun setDesiredRetention(value: Double) =
        edit { it[Keys.RETENTION] = value.coerceIn(0.80, 0.95).toFloat() }

    suspend fun setMaximumIntervalDays(value: Int) =
        edit { it[Keys.MAX_INTERVAL] = value.coerceIn(30, 3650) }

    suspend fun setStudyDayStartHour(value: Int) = edit { it[Keys.START_HOUR] = value.coerceIn(0, 12) }

    suspend fun setSpeechRate(value: Float) = edit { it[Keys.SPEECH_RATE] = value.coerceIn(0.5f, 1.25f) }

    suspend fun setHapticsEnabled(value: Boolean) = edit { it[Keys.HAPTICS] = value }

    suspend fun setSwipeEnabled(value: Boolean) = edit { it[Keys.SWIPE] = value }

    suspend fun setReminderEnabled(value: Boolean) = edit { it[Keys.REMINDER_ENABLED] = value }

    suspend fun setReminderTime(hour: Int, minute: Int) = edit {
        it[Keys.REMINDER_HOUR] = hour.coerceIn(0, 23)
        it[Keys.REMINDER_MINUTE] = minute.coerceIn(0, 59)
    }

    suspend fun setReminderDays(days: Set<Int>) = edit {
        it[Keys.REMINDER_DAYS] = days.filter { day -> day in 1..7 }.sorted().joinToString(",")
    }

    suspend fun setLastBackupAt(epochMillis: Long) = edit { it[Keys.LAST_BACKUP] = epochMillis }

    suspend fun setLastBackupReminderAt(epochMillis: Long) =
        edit { it[Keys.LAST_BACKUP_REMINDER] = epochMillis }

    suspend fun setTtsChecked(value: Boolean) = edit { it[Keys.TTS_CHECKED] = value }

    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.THEME_MODE] = value.name }

    suspend fun setAmoled(value: Boolean) = edit { it[Keys.AMOLED] = value }

    suspend fun incrementAppOpened() = edit { prefs ->
        prefs[Keys.APP_OPENED] = (prefs[Keys.APP_OPENED] ?: 0) + 1
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
