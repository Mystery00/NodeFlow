package app.mystery0.nodeflow.feature.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mystery0.nodeflow.core.common.isNotFound
import app.mystery0.nodeflow.core.common.toUserMessage
import app.mystery0.nodeflow.domain.user.GetUserProfileUseCase
import app.mystery0.nodeflow.domain.user.GetUserRecentActivityUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileViewModel(
    savedStateHandle: SavedStateHandle,
    private val getUserProfile: GetUserProfileUseCase,
    private val getUserRecentActivity: GetUserRecentActivityUseCase,
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
            _uiState.update { it.copy(isLoading = it.user == null, errorMessage = null, userNotFound = false) }
            val userDeferred = async { getUserProfile(username, forceRefresh) }
            val activityDeferred = async { getUserRecentActivity(username) }
            val userResult = userDeferred.await()
            val activity = activityDeferred.await().getOrNull()
            _uiState.update { current ->
                val topics = activity?.topics ?: current.recentTopics
                val replies = activity?.replies ?: current.recentReplies
                userResult.fold(
                    onSuccess = { user ->
                        current.copy(
                            isLoading = false,
                            user = user,
                            recentTopics = topics,
                            recentReplies = replies,
                            errorMessage = null,
                            userNotFound = false,
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            isLoading = false,
                            recentTopics = topics,
                            recentReplies = replies,
                            errorMessage = error.toUserMessage(),
                            userNotFound = error.isNotFound(),
                        )
                    },
                )
            }
        }
    }
}
