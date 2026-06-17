package ayola.literacyapp.game.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ayola.literacyapp.game.api.RetrofitClient
import ayola.literacyapp.game.models.SessionRequest
import ayola.literacyapp.game.models.Story
import ayola.literacyapp.game.models.getSentences
import ayola.literacyapp.game.utils.SessionManager
import ayola.literacyapp.game.utils.SpeechRecognizerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class GameState(
    val story: Story? = null,
    val currentSentenceIndex: Int = 0,
    val isFinished: Boolean = false,
    val error: String? = null,
    val isSpeechEngineReady: Boolean = false,
    val isListening: Boolean = false,
    val partialText: String = "",
    val finalAccuracy: Float = 0f
)

class GameViewModel(
    private val sessionManager: SessionManager,
    private val speechManager: SpeechRecognizerManager
) : ViewModel() {

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private var startTimeMillis: Long = 0L

    // Running session totals, accumulated per sentence.
    private var accuracySum: Double = 0.0
    private var sentencesEvaluated: Int = 0
    private val sessionDifficultWords = linkedSetOf<String>()

    // Guards against processing the same Vosk final result more than once.
    private var lastEvaluatedResult: String? = null

    init {
        observeSpeech()
        initSpeechEngine()
    }

    /** Unpack + load the Vosk model off the main thread. */
    private fun initSpeechEngine() {
        viewModelScope.launch(Dispatchers.IO) {
            speechManager.initModel()
        }
    }

    /** Mirror the recognizer's state into GameState and react to final results. */
    private fun observeSpeech() {
        viewModelScope.launch {
            speechManager.state.collect { speech ->
                _state.value = _state.value.copy(
                    isSpeechEngineReady = speech.isReady,
                    isListening = speech.isListening,
                    partialText = speech.partialText,
                    error = speech.error ?: _state.value.error
                )
                val result = speech.resultText
                if (result.isNotBlank() && result != lastEvaluatedResult) {
                    lastEvaluatedResult = result
                    onSentenceRecognized(result)
                }
            }
        }
    }

    fun loadStory(storyId: Int) {
        if (_state.value.story?.id == storyId) return
        viewModelScope.launch {
            try {
                val ageGroup = sessionManager.getAgeGroup()
                val response = RetrofitClient.apiService.getStories(ageGroup)
                val story = response.body()?.firstOrNull { it.id == storyId }
                when {
                    !response.isSuccessful ->
                        _state.value = _state.value.copy(error = "Could not load story (HTTP ${response.code()}).")
                    story == null ->
                        _state.value = _state.value.copy(error = "Story not found.")
                    else -> {
                        startTimeMillis = System.currentTimeMillis()
                        // Preserve the engine-ready flag we may already have received.
                        _state.value = _state.value.copy(
                            story = story,
                            currentSentenceIndex = 0,
                            isFinished = false,
                            error = null
                        )
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Could not reach the server.")
            }
        }
    }

    /** Toggle the microphone. Called from the mic FAB. */
    fun onMicPressed() {
        if (!_state.value.isSpeechEngineReady) return
        if (_state.value.isListening) {
            speechManager.stopListening()
        } else {
            lastEvaluatedResult = null
            speechManager.startListening()
        }
    }

    /**
     * A full utterance came back from Vosk: grade it and stop listening, but DO NOT auto-advance —
     * the child taps "Next" when they're ready (early readers pause unpredictably).
     */
    private fun onSentenceRecognized(spokenText: String) {
        val story = _state.value.story ?: return
        val expected = story.getSentences().getOrNull(_state.value.currentSentenceIndex) ?: return
        evaluateReading(spokenText, expected)
        speechManager.stopListening()
    }

    /**
     * Compares what the child said to the expected sentence (case-insensitive, punctuation-stripped),
     * updating the running accuracy and the master list of missed ("difficult") words.
     */
    private fun evaluateReading(spokenText: String, expectedSentence: String) {
        val expectedWords = normalize(expectedSentence)
        if (expectedWords.isEmpty()) return
        val spokenWords = normalize(spokenText).toSet()

        val matched = expectedWords.count { it in spokenWords }
        val accuracyPercent = (matched.toDouble() / expectedWords.size) * 100.0
        val missed = expectedWords.filter { it !in spokenWords }

        accuracySum += accuracyPercent
        sentencesEvaluated += 1
        sessionDifficultWords.addAll(missed)
    }

    fun advanceSentence() {
        val current = _state.value
        val story = current.story ?: return
        val sentenceCount = story.getSentences().size
        val nextIndex = current.currentSentenceIndex + 1

        // Reset listening state so the next sentence starts fresh.
        speechManager.stopListening()
        lastEvaluatedResult = null

        if (nextIndex >= sentenceCount) {
            val averageAccuracy = if (sentencesEvaluated > 0) accuracySum / sentencesEvaluated else 0.0
            val roundedAccuracy = (averageAccuracy * 100).roundToInt() / 100.0 // 2 d.p.
            _state.value = current.copy(
                currentSentenceIndex = nextIndex,
                isFinished = true,
                partialText = "",
                finalAccuracy = roundedAccuracy.toFloat()
            )
            val durationSeconds = ((System.currentTimeMillis() - startTimeMillis) / 1000).toInt()
            submitSession(story, durationSeconds, roundedAccuracy)
        } else {
            _state.value = current.copy(currentSentenceIndex = nextIndex, partialText = "")
        }
    }

    private fun submitSession(story: Story, durationSeconds: Int, accuracyPercent: Double) {
        val studentId = sessionManager.getStudentId() ?: return
        viewModelScope.launch {
            try {
                RetrofitClient.apiService.createSession(
                    SessionRequest(
                        student = studentId,
                        story = story.id,
                        durationSeconds = durationSeconds,
                        accuracyPercent = accuracyPercent,
                        difficultWords = sessionDifficultWords.toList()
                    )
                )
            } catch (e: Exception) {
                // Best-effort: don't block the lesson screen if the POST fails.
            }
        }
    }

    private fun normalize(text: String): List<String> =
        text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

    override fun onCleared() {
        super.onCleared()
        speechManager.destroy()
    }

    companion object {
        fun factory(
            sessionManager: SessionManager,
            speechManager: SpeechRecognizerManager
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    GameViewModel(sessionManager, speechManager) as T
            }
    }
}
