package app.mystery0.nodeflow.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mystery0.nodeflow.domain.auth.AuthRepository
import app.mystery0.nodeflow.domain.auth.ObserveAuthSessionUseCase
import app.mystery0.nodeflow.domain.user.GetUserProfileUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AccountViewModel(
    observeAuthSession: ObserveAuthSessionUseCase,
    private val getUserProfile: GetUserProfileUseCase,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            observeAuthSession().collectLatest { session ->
                _uiState.update {
                    it.copy(
                        session = session,
                        user = if (session.username.isNullOrBlank()) null else it.user,
                        errorMessage = null,
                    )
                }
                val username = session.username
                if (username.isNullOrBlank() || session.cookieHeader.isNullOrBlank()) {
                    loadJob?.cancel()
                    _uiState.update { it.copy(user = null, isLoading = false) }
                } else {
                    loadUser(username = username, forceRefresh = false)
                }
            }
        }
    }

    fun onEvent(event: AccountUiEvent) {
        when (event) {
            AccountUiEvent.Refresh,
            AccountUiEvent.Retry -> {
                val username = _uiState.value.session.username ?: return
                loadUser(username = username, forceRefresh = true)
            }
            AccountUiEvent.Logout -> viewModelScope.launch {
                authRepository.clearSession()
            }
        }
    }

    private fun loadUser(username: String, forceRefresh: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = getUserProfile(username, forceRefresh)
            _uiState.update { current ->
                result.fold(
                    onSuccess = { user ->
                        current.copy(user = user, isLoading = false, errorMessage = null)
                    },
                    onFailure = { error ->
                        current.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "用户信息加载失败",
                        )
                    },
                )
            }
        }
    }
}
