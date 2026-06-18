package ayola.literacyapp.game.ui.storylist

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

data class StoryListUiState(
    val isLoading: Boolean = true,
    val stories: List<Story> = emptyList(),
    val error: String? = null
)

class StoryListViewModel(
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoryListUiState())
    val uiState: StateFlow<StoryListUiState> = _uiState.asStateFlow()

    init {
        loadStories()
    }

    fun loadStories() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // Filter to the child's saved age group (null -> backend returns all).
                val ageGroup = sessionManager.getAgeGroup()
                val response = RetrofitClient.apiService.getStories(ageGroup)
                val stories = response.body()
                if (response.isSuccessful && stories != null) {
                    _uiState.value = StoryListUiState(isLoading = false, stories = stories)
                } else {
                    _uiState.value = StoryListUiState(
                        isLoading = false,
                        error = ErrorMessages.forHttp("getStories", response.code())
                    )
                }
            } catch (e: Exception) {
                _uiState.value = StoryListUiState(
                    isLoading = false,
                    error = ErrorMessages.forException("getStories", e)
                )
            }
        }
    }

    companion object {
        fun factory(sessionManager: SessionManager): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    StoryListViewModel(sessionManager) as T
            }
    }
}
