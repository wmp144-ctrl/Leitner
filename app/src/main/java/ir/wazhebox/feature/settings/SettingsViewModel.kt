package ir.wazhebox.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.wazhebox.data.settings.SettingsRepository
import ir.wazhebox.data.settings.UserSettings
import ir.wazhebox.data.tts.TtsController
import ir.wazhebox.data.tts.TtsStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: ir.wazhebox.data.reminder.ReminderScheduler,
    val tts: TtsController,
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    val ttsStatus: StateFlow<TtsStatus> = tts.status

    fun setNewLimit(value: Int) = launch { settingsRepository.setDailyNewLimit(value) }
    fun setReviewLimit(value: Int) = launch { settingsRepository.setDailyReviewLimit(value) }
    fun setRetention(value: Double) = launch { settingsRepository.setDesiredRetention(value) }
    fun setMaxIntervalDays(value: Int) = launch { settingsRepository.setMaximumIntervalDays(value) }
    fun setStudyDayStartHour(value: Int) = launch { settingsRepository.setStudyDayStartHour(value) }
    fun setSpeechRate(value: Float) = launch { settingsRepository.setSpeechRate(value) }
    fun setHaptics(value: Boolean) = launch { settingsRepository.setHapticsEnabled(value) }
    fun setSwipe(value: Boolean) = launch { settingsRepository.setSwipeEnabled(value) }
    fun setReminderEnabled(value: Boolean) = launch {
        settingsRepository.setReminderEnabled(value)
        reminderScheduler.schedule()
    }

    fun setReminderTime(hour: Int, minute: Int) = launch {
        settingsRepository.setReminderTime(hour, minute)
        reminderScheduler.schedule()
    }

    fun setReminderDay(day: Int, enabled: Boolean) = launch {
        val current = settings.value.reminderDays
        val next = if (enabled) current + day else current - day
        if (next.isNotEmpty()) {
            settingsRepository.setReminderDays(next)
            reminderScheduler.schedule()
        }
    }

    fun testSpeech() {
        tts.checkAvailability()
        tts.speak("WazheBox. Learn a word, keep it forever.", settings.value.speechRate)
    }

    fun toggleTtsLanguageHelp() {
        tts.checkAvailability()
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
