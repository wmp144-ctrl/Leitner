package ir.wazhebox.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.wazhebox.data.settings.SettingsRepository
import ir.wazhebox.domain.model.Level
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val step: Int = 0,
    val minutes: Int = 10,
    val level: Level = Level.BEGINNER,
    val saving: Boolean = false,
    val finished: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun selectMinutes(minutes: Int) {
        _state.value = _state.value.copy(minutes = minutes)
    }

    fun selectLevel(level: Level) {
        _state.value = _state.value.copy(level = level)
    }

    fun next() {
        _state.value = _state.value.copy(step = (_state.value.step + 1).coerceAtMost(2))
    }

    fun back() {
        _state.value = _state.value.copy(step = (_state.value.step - 1).coerceAtLeast(0))
    }

    /**
     * Persist the choices only: the app ships with no words, the library fills
     * up from the user's own CSV import (v2).
     */
    fun finish() {
        val current = _state.value
        if (current.saving) return
        _state.value = current.copy(saving = true)
        viewModelScope.launch {
            settingsRepository.completeOnboarding(current.minutes, current.level)
            _state.value = _state.value.copy(saving = false, finished = true)
        }
    }
}
