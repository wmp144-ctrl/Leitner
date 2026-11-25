package ir.wazhebox.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.wazhebox.data.settings.SettingsRepository
import ir.wazhebox.data.settings.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Theme choice for MainActivity, which sits above the NavHost. */
data class AppTheme(val mode: ThemeMode = ThemeMode.SYSTEM, val amoled: Boolean = false)

/** Decides between onboarding and home; null means "still reading settings". */
@HiltViewModel
class RootViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val onboardingCompleted: StateFlow<Boolean?> = settingsRepository.settings
        .map { it.onboardingCompleted }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val theme: StateFlow<AppTheme> = settingsRepository.settings
        .map { AppTheme(mode = it.themeMode, amoled = it.amoled) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppTheme())
}
