package ayola.literacyapp.game.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ayola.literacyapp.game.api.RetrofitClient
import ayola.literacyapp.game.models.SessionRequest
import ayola.literacyapp.game.models.Story
import ayola.literacyapp.game.models.getSentences
import ayola.literacyapp.game.utils.ErrorMessages
import ayola.literacyapp.game.utils.SessionManager
import ayola.literacyapp.game.utils.SpeechRecognizerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val heardText: String = "",
    val matchConfidence: Float = 0f,   // live 0..1 match of heard text vs current sentence
    val isStruggling: Boolean = false, // mic on but nothing heard for a while
    val characterName: String = "Kofi", // age-mapped reading buddy
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

    // Accumulates Vosk's finalized segments across one listening window, so the child can pause
    // mid-sentence without the recognized words being lost.
    private val heardBuffer = StringBuilder()
    private var lastEvaluatedResult: String? = null
    private var sentenceGraded = false

    // Auto-stops listening after a length-based window; flips the buddy to "struggling" on silence.
    private var listenJob: Job? = null
    private var struggleJob: Job? = null

    init {
        // Pick the age-appropriate reading buddy up front.
        _state.value = _state.value.copy(characterName = characterFor(sessionManager.getAgeGroup()))
        observeSpeech()
        initSpeechEngine()
    }

    /** Unpack + load the Vosk model off the main thread. */
    private fun initSpeechEngine() {
        viewModelScope.launch(Dispatchers.IO) {
            speechManager.initModel()
        }
    }

    /** Mirror the recognizer's state into GameState, accumulating heard words across the window. */
    private fun observeSpeech() {
        viewModelScope.launch {
            speechManager.state.collect { speech ->
                // Append each newly-finalized segment so pauses don't erase earlier words.
                val result = speech.resultText
                if (result.isNotBlank() && result != lastEvaluatedResult) {
                    lastEvaluatedResult = result
                    if (heardBuffer.isNotEmpty()) heardBuffer.append(' ')
                    heardBuffer.append(result)
                }

                val combined = (heardBuffer.toString() + " " + speech.partialText).trim()
                val expected = currentExpectedSentence()
                val liveConfidence = if (expected != null) wordMatchRatio(combined, expected) else 0f

                _state.value = _state.value.copy(
                    isSpeechEngineReady = speech.isReady,
                    isListening = speech.isListening,
                    partialText = speech.partialText,
                    heardText = combined,
                    matchConfidence = liveConfidence,
                    // Clear the struggling face as soon as we actually hear something.
                    isStruggling = if (combined.isBlank()) _state.value.isStruggling else false,
                    error = speech.error ?: _state.value.error
                )
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
                        _state.value = _state.value.copy(error = ErrorMessages.forHttp("getStories(game)", response.code()))
                    story == null ->
                        _state.value = _state.value.copy(error = "We couldn't find this story. Please go back and try again.")
                    else -> {
                        startTimeMillis = System.currentTimeMillis()
                        resetSentenceListening()
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
                _state.value = _state.value.copy(error = ErrorMessages.forException("getStories(game)", e))
            }
        }
    }

    /** Toggle the microphone. Called from the mic FAB. */
    fun onMicPressed() {
        if (!_state.value.isSpeechEngineReady) return
        if (_state.value.isListening) {
            stopListeningAndGrade()
            return
        }

        // Fresh listening session for this sentence.
        heardBuffer.clear()
        lastEvaluatedResult = null
        _state.value = _state.value.copy(heardText = "", matchConfidence = 0f, isStruggling = false)
        speechManager.startListening()

        // Listening window scales with how much there is to read (longer sentence -> more time).
        val window = listeningWindowMs()
        listenJob?.cancel()
        listenJob = viewModelScope.launch {
            delay(window)
            stopListeningAndGrade()
        }
        // Supportive "struggling" face only if nothing is heard well into the window.
        struggleJob?.cancel()
        struggleJob = viewModelScope.launch {
            delay((window * 3) / 5)
            if (heardBuffer.isBlank() && _state.value.partialText.isBlank()) {
                _state.value = _state.value.copy(isStruggling = true)
            }
        }
    }

    /**
     * Listening window for the current sentence: a base time plus extra per word, capped.
     * Recomputed each time the child starts a sentence, so longer sentences get an adequate window.
     */
    private fun listeningWindowMs(): Long {
        val words = currentExpectedSentence()?.let { normalize(it).size } ?: 0
        return (LISTEN_BASE_MS + LISTEN_PER_WORD_MS * words).coerceAtMost(LISTEN_MAX_MS)
    }

    /** Stop the mic and grade the whole accumulated read once per sentence. Does NOT auto-advance. */
    private fun stopListeningAndGrade() {
        listenJob?.cancel()
        struggleJob?.cancel()
        speechManager.stopListening()
        val expected = currentExpectedSentence()
        val heard = _state.value.heardText
        if (expected != null && heard.isNotBlank() && !sentenceGraded) {
            sentenceGraded = true
            evaluateReading(heard, expected)
        }
        _state.value = _state.value.copy(isListening = false, isStruggling = false)
    }

    /** Clears per-sentence listening state (buffer, grading guard, timers). */
    private fun resetSentenceListening() {
        listenJob?.cancel()
        struggleJob?.cancel()
        heardBuffer.clear()
        lastEvaluatedResult = null
        sentenceGraded = false
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

    private fun currentExpectedSentence(): String? =
        _state.value.story?.getSentences()?.getOrNull(_state.value.currentSentenceIndex)

    /** Fraction (0..1) of the expected words that appear in the spoken text. */
    private fun wordMatchRatio(spokenText: String, expectedSentence: String): Float {
        val expectedWords = normalize(expectedSentence)
        if (expectedWords.isEmpty()) return 0f
        val spokenWords = normalize(spokenText).toSet()
        val matched = expectedWords.count { it in spokenWords }
        return matched.toFloat() / expectedWords.size
    }

    fun advanceSentence() {
        val current = _state.value
        val story = current.story ?: return
        val sentenceCount = story.getSentences().size
        val nextIndex = current.currentSentenceIndex + 1

        // Reset listening state so the next sentence starts fresh.
        speechManager.stopListening()
        resetSentenceListening()

        if (nextIndex >= sentenceCount) {
            val averageAccuracy = if (sentencesEvaluated > 0) accuracySum / sentencesEvaluated else 0.0
            val roundedAccuracy = (averageAccuracy * 100).roundToInt() / 100.0 // 2 d.p.
            _state.value = current.copy(
                currentSentenceIndex = nextIndex,
                isFinished = true,
                partialText = "",
                heardText = "",
                matchConfidence = 0f,
                isStruggling = false,
                finalAccuracy = roundedAccuracy.toFloat()
            )
            val durationSeconds = ((System.currentTimeMillis() - startTimeMillis) / 1000).toInt()
            submitSession(story, durationSeconds, roundedAccuracy)
        } else {
            _state.value = current.copy(
                currentSentenceIndex = nextIndex,
                partialText = "",
                heardText = "",
                matchConfidence = 0f,
                isStruggling = false
            )
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
                // Best-effort: don't block the lesson screen if the POST fails — but do log it.
                ErrorMessages.forException("createSession", e)
            }
        }
    }

    private fun normalize(text: String): List<String> =
        text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

    /**
     * Maps the saved age group to its reading-buddy character. Only "5-7" (Kofi) and "8-10" (Ama)
     * are reachable today; the rest are wired for a future age-group expansion of onboarding + backend.
     */
    private fun characterFor(ageGroup: String?): String = when (ageGroup) {
        "5-7" -> "Kofi"
        "8-10" -> "Ama"
        "11-13" -> "Yaw"
        "14-15" -> "Esi"
        "16-18" -> "Musa"
        else -> "Kofi"
    }

    override fun onCleared() {
        super.onCleared()
        listenJob?.cancel()
        struggleJob?.cancel()
        speechManager.destroy()
    }

    companion object {
        // Listening window = base + per-word, capped. A 5-word line ≈ 13.5s; a 12-word line ≈ 24s.
        private const val LISTEN_BASE_MS = 6_000L
        private const val LISTEN_PER_WORD_MS = 1_500L
        private const val LISTEN_MAX_MS = 30_000L

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
