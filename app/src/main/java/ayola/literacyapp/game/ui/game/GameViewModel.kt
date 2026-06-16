package ayola.literacyapp.game.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ayola.literacyapp.game.api.RetrofitClient
import ayola.literacyapp.game.models.SessionRequest
import ayola.literacyapp.game.models.Story
import ayola.literacyapp.game.models.getSentences
import ayola.literacyapp.game.utils.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GameState(
    val story: Story? = null,
    val currentSentenceIndex: Int = 0,
    val isFinished: Boolean = false,
    val error: String? = null
)

class GameViewModel(
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private var startTimeMillis: Long = 0L

    fun loadStory(storyId: Int) {
        // Avoid reloading if we already have it (e.g. on recomposition).
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
                        _state.value = GameState(story = story)
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Could not reach the server.")
            }
        }
    }

    /** Advances to the next sentence; finishes and posts the session when past the last one. */
    fun advanceSentence() {
        val current = _state.value
        val story = current.story ?: return
        val sentenceCount = story.getSentences().size
        val nextIndex = current.currentSentenceIndex + 1

        if (nextIndex >= sentenceCount) {
            _state.value = current.copy(currentSentenceIndex = nextIndex, isFinished = true)
            val durationSeconds = ((System.currentTimeMillis() - startTimeMillis) / 1000).toInt()
            submitSession(story, durationSeconds)
        } else {
            _state.value = current.copy(currentSentenceIndex = nextIndex)
        }
    }

    private fun submitSession(story: Story, durationSeconds: Int) {
        val studentId = sessionManager.getStudentId() ?: return
        viewModelScope.launch {
            try {
                // Accuracy + difficult words are hardcoded for now; Vosk fills these in Part 2.
                RetrofitClient.apiService.createSession(
                    SessionRequest(
                        student = studentId,
                        story = story.id,
                        durationSeconds = durationSeconds,
                        accuracyPercent = 100.0,
                        difficultWords = emptyList()
                    )
                )
            } catch (e: Exception) {
                // Best-effort: don't block the lesson screen if the POST fails.
            }
        }
    }

    companion object {
        fun factory(sessionManager: SessionManager): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    GameViewModel(sessionManager) as T
            }
    }
}
