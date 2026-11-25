package ir.wazhebox.data.settings

import ir.wazhebox.domain.model.Level

/** How the app picks its light/dark palette. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** Everything the user can change, with the PRD defaults (§9.2, §17, §18.5). */
data class UserSettings(
    val onboardingCompleted: Boolean = false,
    val dailyMinutes: Int = 10,
    val level: Level = Level.BEGINNER,
    val dailyNewLimit: Int = 10,
    val dailyReviewLimit: Int = 200,
    val desiredRetention: Double = 0.90,
    val maximumIntervalDays: Int = 365,
    val studyDayStartHour: Int = 4,
    val speechRate: Float = 0.75f,
    val hapticsEnabled: Boolean = true,
    val swipeEnabled: Boolean = false,
    val reminderEnabled: Boolean = true,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val reminderDays: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoled: Boolean = false,
    val lastBackupAt: Long? = null,
    val lastBackupReminderAt: Long? = null,
    val ttsChecked: Boolean = false,
    val appOpenedCount: Int = 0,
) {
    /** 5/10/20 minutes maps to a card ceiling so the promise stays honest. */
    val minutesToNewCards: Int
        get() = when {
            dailyMinutes <= 5 -> 5
            dailyMinutes <= 10 -> 10
            else -> 20
        }

    fun reminderTimeLabel(): String = "%02d:%02d".format(reminderHour, reminderMinute)
}
