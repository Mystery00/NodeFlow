package app.mystery0.nodeflow.feature.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mystery0.nodeflow.core.common.isNotFound
import app.mystery0.nodeflow.core.common.toUserMessage
import app.mystery0.nodeflow.domain.membertag.ObserveMemberTagsUseCase
import app.mystery0.nodeflow.domain.membertag.UpdateMemberTagsForUserUseCase
import app.mystery0.nodeflow.domain.user.GetUserProfileUseCase
import app.mystery0.nodeflow.domain.user.GetUserRecentActivityUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileViewModel(
    savedStateHandle: SavedStateHandle,
    private val getUserProfile: GetUserProfileUseCase,
    private val getUserRecentActivity: GetUserRecentActivityUseCase,
    observeMemberTags: ObserveMemberTagsUseCase,
    private val updateMemberTags: UpdateMemberTagsForUserUseCase,
) : ViewModel() {
    private val username: String = checkNotNull(savedStateHandle["username"])
    private val _uiState = MutableStateFlow(ProfileUiState(username = username))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        load(forceRefresh = false)
        viewModelScope.launch {
            observeMemberTags().collectLatest { tags ->
                val editable = tags.entries
                    .firstOrNull { it.key.equals(username, ignoreCase = true) }
                    ?.value.orEmpty()
                _uiState.update { it.copy(editableMemberTags = editable) }
            }
        }
    }

    fun onEvent(event: ProfileUiEvent) {
        when (event) {
            ProfileUiEvent.Refresh -> load(forceRefresh = true)
            ProfileUiEvent.Retry -> load(forceRefresh = true)
            ProfileUiEvent.EditMemberTags -> _uiState.update {
                it.copy(isTagDialogVisible = true, tagEditError = null)
            }
            ProfileUiEvent.DismissTagDialog -> _uiState.update {
                it.copy(isTagDialogVisible = false, tagEditError = null)
            }
            is ProfileUiEvent.SaveMemberTags -> saveMemberTags(event.tags)
        }
    }

    private fun saveMemberTags(tags: List<String>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingTags = true, tagEditError = null) }
            val result = updateMemberTags(username, tags, _uiState.value.user?.avatarUrl)
            _uiState.update { current ->
                result.fold(
                    onSuccess = {
                        current.copy(isSavingTags = false, isTagDialogVisible = false)
                    },
                    onFailure = { error ->
                        current.copy(isSavingTags = false, tagEditError = error.toUserMessage())
                    },
                )
            }
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
