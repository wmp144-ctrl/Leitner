package ir.wazhebox.core.ui

import android.content.ActivityNotFoundException
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Wraps the launch of a system file picker.
 *
 * `ActivityResultLauncher.launch` throws when no activity can handle the
 * intent, and every picker callback runs on the main thread, so an image
 * without a document provider (minimal ROMs, a device whose Files app was
 * disabled) used to crash the whole app the moment the button was tapped.
 * The failure is a state to show, not a crash.
 */
@Stable
class PickerLauncher {
    /** True after a launch failed because the device has no picker. */
    var unavailable by mutableStateOf(false)
        private set

    fun launch(action: () -> Unit) {
        try {
            action()
            unavailable = false
        } catch (error: ActivityNotFoundException) {
            unavailable = true
        }
    }
}

@Composable
fun rememberPickerLauncher(): PickerLauncher = remember { PickerLauncher() }
