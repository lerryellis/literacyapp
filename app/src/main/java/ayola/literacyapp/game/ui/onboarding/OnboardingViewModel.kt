package ayola.literacyapp.game.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ayola.literacyapp.game.api.RetrofitClient
import ayola.literacyapp.game.models.Student
import ayola.literacyapp.game.utils.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Drives the onboarding screen: form validation, the login network call, and navigation signal. */
data class OnboardingUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val loginSuccess: Boolean = false
)

class OnboardingViewModel(
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun loginStudent(name: String, school: String, ageGroup: String) {
        if (name.isBlank() || school.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter both a name and a school.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val deviceId = sessionManager.getDeviceId()
                val response = RetrofitClient.apiService.createStudent(
                    Student(
                        name = name.trim(),
                        deviceId = deviceId,
                        school = school.trim(),
                        ageGroup = ageGroup
                    )
                )

                // Backend returns 201 (new child) or 200 (returning) — both succeed.
                val student = response.body()
                if (response.isSuccessful && student?.id != null) {
                    sessionManager.saveStudentId(student.id)
                    _uiState.value = _uiState.value.copy(isLoading = false, loginSuccess = true)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Login failed (HTTP ${response.code()})."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Could not reach the server."
                )
            }
        }
    }

    /** Lets the UI clear a shown error after the user acts. */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    companion object {
        /** SessionManager needs a Context, so the screen supplies it via this factory. */
        fun factory(sessionManager: SessionManager): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    OnboardingViewModel(sessionManager) as T
            }
    }
}
