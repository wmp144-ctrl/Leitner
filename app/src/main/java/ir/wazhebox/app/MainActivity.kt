package ir.wazhebox.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ir.wazhebox.core.ui.theme.WazheBoxTheme
import ir.wazhebox.data.settings.ThemeMode
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // The NavHost only reads the launching intent once, so a deep link that
    // arrives while the app is already running (for example from the widget)
    // has to be handed to it again (ADR-016).
    private val newIntents = MutableSharedFlow<Intent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // The theme lives in settings, so the activity collects it here, above
            // the NavHost; the root screen uses the same ViewModel instance.
            val rootViewModel: RootViewModel = hiltViewModel()
            val appTheme by rootViewModel.theme.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val dark = when (appTheme.mode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            WazheBoxTheme(darkTheme = dark, amoled = appTheme.amoled) {
                WazheBoxRoot(newIntents = newIntents)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        newIntents.tryEmit(intent)
    }
}
