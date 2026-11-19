package ir.wazhebox.data.tts

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TtsStatus {
    UNKNOWN,
    READY,
    MISSING_DATA,
    NOT_SUPPORTED,
    ERROR,
}

/**
 * System TTS only (PRD §15): lazy initialization, no bundled audio, no network.
 * A missing English voice is a state, never a crash: the UI greys the speaker
 * button and offers the system install action.
 */
@Singleton
class TtsController @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private var engine: TextToSpeech? = null
    private var initializing = false
    private var pendingText: String? = null
    private var pendingRate: Float = 0.75f

    private val _status = MutableStateFlow(TtsStatus.UNKNOWN)
    val status: StateFlow<TtsStatus> = _status.asStateFlow()

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    fun speak(text: String, rate: Float = 0.75f) {
        val current = engine
        if (current == null) {
            pendingText = text
            pendingRate = rate
            initialize()
            return
        }
        if (_status.value == TtsStatus.READY) {
            current.setSpeechRate(rate)
            current.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        }
    }

    private fun initialize() {
        if (initializing) return
        initializing = true
        engine = TextToSpeech(context) { result ->
            initializing = false
            val tts = engine
            if (result != TextToSpeech.SUCCESS || tts == null) {
                _status.value = TtsStatus.ERROR
                return@TextToSpeech
            }
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            val availability = tts.setLanguage(Locale.US)
            _status.value = when (availability) {
                TextToSpeech.LANG_MISSING_DATA -> TtsStatus.MISSING_DATA
                TextToSpeech.LANG_NOT_SUPPORTED -> TtsStatus.NOT_SUPPORTED
                else -> TtsStatus.READY
            }
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _speaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _speaking.value = false
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _speaking.value = false
                }
            })
            if (_status.value == TtsStatus.READY) {
                pendingText?.let { text ->
                    tts.setSpeechRate(pendingRate)
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
                }
            }
            pendingText = null
        }
    }

    fun checkAvailability() {
        if (engine == null) initialize()
    }

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        initializing = false
        _status.value = TtsStatus.UNKNOWN
    }

    private companion object {
        const val UTTERANCE_ID = "wazhebox_speech"
    }
}
