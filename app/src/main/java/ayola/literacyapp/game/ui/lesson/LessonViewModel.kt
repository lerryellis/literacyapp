package ayola.literacyapp.game.ui.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ayola.literacyapp.game.api.RetrofitClient
import ayola.literacyapp.game.models.Story
import ayola.literacyapp.game.utils.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LessonUiState(
    val isLoading: Boolean = true,
    val story: Story? = null,
    val error: String? = null
)

class LessonViewModel(
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LessonUiState())
    val uiState: StateFlow<LessonUiState> = _uiState.asStateFlow()

    fun loadStory(storyId: Int) {
        if (_uiState.value.story?.id == storyId) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val ageGroup = sessionManager.getAgeGroup()
                val response = RetrofitClient.apiService.getStories(ageGroup)
                val story = response.body()?.firstOrNull { it.id == storyId }
                when {
                    !response.isSuccessful ->
                        _uiState.value = LessonUiState(isLoading = false, error = "Could not load lesson (HTTP ${response.code()}).")
                    story == null ->
                        _uiState.value = LessonUiState(isLoading = false, error = "Story not found.")
                    else ->
                        _uiState.value = LessonUiState(isLoading = false, story = story)
                }
            } catch (e: Exception) {
                _uiState.value = LessonUiState(isLoading = false, error = e.message ?: "Could not reach the server.")
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
