package ayola.literacyapp.game.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

/**
 * Wraps the offline Vosk engine so the ViewModel never touches JNA/native code directly.
 *
 * Lifecycle: [initModel] (once) -> [startListening] / [stopListening] (per sentence) -> [destroy].
 * Recognized text is published through [state] as a StateFlow.
 */
class SpeechRecognizerManager(private val context: Context) : RecognitionListener {

    data class SpeechState(
        val isReady: Boolean = false,
        val isListening: Boolean = false,
        val partialText: String = "",
        val resultText: String = "",
        val error: String? = null
    )

    private val _state = MutableStateFlow(SpeechState())
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    private var model: Model? = null
    private var speechService: SpeechService? = null

    /**
     * Unpacks the bundled model from `assets/model` into the app's files dir (Vosk needs a real
     * filesystem path, not an asset stream) and builds the [Model]. Runs async on a Vosk thread.
     */
    fun initModel() {
        if (model != null) {
            _state.value = _state.value.copy(isReady = true)
            return
        }
        try {
            StorageService.unpack(
                context,
                "model",   // source: assets/model
                "model",   // target: external files dir /model
                { unpackedModel ->
                    model = unpackedModel
                    _state.value = _state.value.copy(isReady = true, error = null)
                },
                { exception ->
                    // Surface errors that Vosk's executor would otherwise swallow silently.
                    Log.e(TAG, "Vosk model unpack failed", exception)
                    _state.value = _state.value.copy(
                        isReady = false,
                        error = "Model failed to load: ${exception.message}"
                    )
                }
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Speech init failed", t)
            _state.value = _state.value.copy(isReady = false, error = "Speech init error: ${t.message}")
        }
    }

    /** Opens the microphone and begins streaming audio into the recognizer. */
    fun startListening() {
        val readyModel = model ?: run {
            _state.value = _state.value.copy(error = "Model not ready yet.")
            return
        }
        if (speechService != null) return // already listening
        try {
            val recognizer = Recognizer(readyModel, SAMPLE_RATE)
            speechService = SpeechService(recognizer, SAMPLE_RATE).also {
                it.startListening(this)
            }
            _state.value = _state.value.copy(isListening = true, partialText = "", resultText = "")
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = "Could not start microphone: ${e.message}")
        }
    }

    /** Stops the current listening turn (keeps the model loaded for the next sentence). */
    fun stopListening() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        _state.value = _state.value.copy(isListening = false)
    }

    /** Releases native resources. Call from the ViewModel's onCleared(). */
    fun destroy() {
        speechService?.shutdown()
        speechService = null
        model?.close()
        model = null
    }

    // --- RecognitionListener callbacks (Vosk emits JSON strings) ---

    override fun onPartialResult(hypothesis: String?) {
        val text = extract(hypothesis, "partial")
        if (text.isNotBlank()) _state.value = _state.value.copy(partialText = text)
    }

    override fun onResult(hypothesis: String?) {
        val text = extract(hypothesis, "text")
        if (text.isNotBlank()) _state.value = _state.value.copy(resultText = text)
    }

    override fun onFinalResult(hypothesis: String?) {
        val text = extract(hypothesis, "text")
        _state.value = _state.value.copy(
            resultText = if (text.isNotBlank()) text else _state.value.resultText,
            isListening = false
        )
    }

    override fun onError(exception: Exception?) {
        _state.value = _state.value.copy(
            error = exception?.message ?: "Recognition error",
            isListening = false
        )
    }

    override fun onTimeout() {
        _state.value = _state.value.copy(isListening = false)
    }

    private fun extract(json: String?, key: String): String =
        if (json == null) "" else try {
            JSONObject(json).optString(key)
        } catch (e: Exception) {
            ""
        }

    companion object {
        private const val TAG = "SpeechRecognizerMgr"
        // Vosk small models are trained at 16 kHz mono.
        private const val SAMPLE_RATE = 16000.0f
    }
}
