package app.mystery0.nodeflow.feature.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mystery0.nodeflow.core.common.toUserMessage
import app.mystery0.nodeflow.domain.user.GetUserProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getUserProfile: GetUserProfileUseCase,
) : ViewModel() {
    private val username: String = checkNotNull(savedStateHandle["username"])
    private val _uiState = MutableStateFlow(ProfileUiState(username = username))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        load(forceRefresh = false)
    }

    fun onEvent(event: ProfileUiEvent) {
        when (event) {
            ProfileUiEvent.Refresh -> load(forceRefresh = true)
            ProfileUiEvent.Retry -> load(forceRefresh = true)
        }
    }

    private fun load(forceRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.user == null, errorMessage = null) }
            val result = getUserProfile(username, forceRefresh)
            _uiState.update { current ->
                result.fold(
                    onSuccess = { user -> current.copy(isLoading = false, user = user) },
                    onFailure = { error ->
                        current.copy(isLoading = false, errorMessage = error.toUserMessage())
                    },
                )
            }
        }
    }
}
