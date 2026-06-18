package ayola.literacyapp.game.ui.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ayola.literacyapp.game.api.RetrofitClient
import ayola.literacyapp.game.models.Story
import ayola.literacyapp.game.utils.ErrorMessages
import ayola.literacyapp.game.utils.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LessonUiState(
    val isLoading: Boolean = true,
    val story: Story? = null,
    val accuracy: Float = 0f,
    val error: String? = null
)

class LessonViewModel(
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LessonUiState())
    val uiState: StateFlow<LessonUiState> = _uiState.asStateFlow()

    fun loadStory(storyId: Int, accuracy: Float) {
        if (_uiState.value.story?.id == storyId) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, accuracy = accuracy)
            try {
                val ageGroup = sessionManager.getAgeGroup()
                val response = RetrofitClient.apiService.getStories(ageGroup)
                val story = response.body()?.firstOrNull { it.id == storyId }
                when {
                    !response.isSuccessful ->
                        _uiState.value = LessonUiState(isLoading = false, accuracy = accuracy, error = ErrorMessages.forHttp("getStories(lesson)", response.code()))
                    story == null ->
                        _uiState.value = LessonUiState(isLoading = false, accuracy = accuracy, error = "We couldn't find this story. Please go back and try again.")
                    else ->
                        _uiState.value = LessonUiState(isLoading = false, accuracy = accuracy, story = story)
                }
            } catch (e: Exception) {
                _uiState.value = LessonUiState(isLoading = false, accuracy = accuracy, error = ErrorMessages.forException("getStories(lesson)", e))
            }
        }
    }

    companion object {
        fun factory(sessionManager: SessionManager): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LessonViewModel(sessionManager) as T
            }
    }
}
