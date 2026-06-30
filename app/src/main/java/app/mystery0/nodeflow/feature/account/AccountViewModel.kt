package app.mystery0.nodeflow.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.domain.account.GetAccountOverviewUseCase
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
    private val getAccountOverview: GetAccountOverviewUseCase,
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
                        overview = if (session.username.isNullOrBlank()) null else it.overview,
                        errorMessage = null,
                    )
                }
                val username = session.username
                if (username.isNullOrBlank() || session.cookieHeader.isNullOrBlank()) {
                    loadJob?.cancel()
                    _uiState.update { it.copy(user = null, overview = null, isLoading = false) }
                } else {
                    loadAccount(username = username, forceRefresh = false)
                }
            }
        }
    }

    fun onEvent(event: AccountUiEvent) {
        when (event) {
            AccountUiEvent.Refresh,
            AccountUiEvent.Retry -> {
                val username = _uiState.value.session.username ?: return
                loadAccount(username = username, forceRefresh = true)
            }
            AccountUiEvent.Logout -> viewModelScope.launch {
                authRepository.clearSession()
            }
        }
    }

    private fun loadAccount(username: String, forceRefresh: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val userResult = getUserProfile(username, forceRefresh)
            val overviewResult = getAccountOverview()
            val overviewError = overviewResult.exceptionOrNull()
            if (overviewError.isAuthError()) {
                authRepository.clearSession()
                _uiState.update {
                    it.copy(
                        user = null,
                        overview = null,
                        isLoading = false,
                        errorMessage = overviewError?.message,
                    )
                }
                return@launch
            }
            _uiState.update { current ->
                userResult.fold(
                    onSuccess = { user ->
                        current.copy(
                            user = user,
                            overview = overviewResult.getOrNull(),
                            isLoading = false,
                            errorMessage = null,
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            isLoading = false,
                            overview = overviewResult.getOrNull(),
                            errorMessage = error.message ?: "用户信息加载失败",
                        )
                    },
                )
            }
        }
    }

    private fun Throwable?.isAuthError(): Boolean =
        this is NodeFlowException && kind == NodeFlowException.Kind.Auth
}
