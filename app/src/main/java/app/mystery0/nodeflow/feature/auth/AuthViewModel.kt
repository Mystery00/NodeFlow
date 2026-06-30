package app.mystery0.nodeflow.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mystery0.nodeflow.core.model.AuthLoginResult
import app.mystery0.nodeflow.domain.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.session.collectLatest { session ->
                _uiState.update { it.copy(session = session) }
            }
        }
        loadChallenge()
    }

    fun onEvent(event: AuthUiEvent) {
        when (event) {
            is AuthUiEvent.UsernameChanged -> _uiState.update {
                it.copy(username = event.value, errorMessage = null)
            }
            is AuthUiEvent.PasswordChanged -> _uiState.update {
                it.copy(password = event.value, errorMessage = null)
            }
            is AuthUiEvent.CaptchaChanged -> _uiState.update {
                it.copy(captcha = event.value, errorMessage = null)
            }
            is AuthUiEvent.TwoFactorCodeChanged -> _uiState.update {
                it.copy(twoFactorCode = event.value, errorMessage = null)
            }
            AuthUiEvent.RefreshChallenge -> loadChallenge()
            AuthUiEvent.Submit -> submit()
            AuthUiEvent.ClearSession -> viewModelScope.launch {
                authRepository.clearSession()
            }
            AuthUiEvent.LoginResultConsumed -> _uiState.update {
                it.copy(loginCompleted = false)
            }
        }
    }

    private fun loadChallenge() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingChallenge = true,
                    errorMessage = null,
                    captcha = "",
                    twoFactorChallenge = null,
                    twoFactorCode = "",
                )
            }
            val result = authRepository.loginChallenge()
            _uiState.update { current ->
                result.fold(
                    onSuccess = { challenge ->
                        current.copy(
                            challenge = challenge,
                            isLoadingChallenge = false,
                            errorMessage = null,
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            challenge = null,
                            isLoadingChallenge = false,
                            errorMessage = error.message ?: "验证码加载失败",
                        )
                    },
                )
            }
        }
    }

    private fun submit() {
        val state = _uiState.value
        if (state.twoFactorChallenge != null) {
            submitTwoFactor(state)
            return
        }
        val challenge = state.challenge ?: return
        if (!state.canSubmit) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val result = authRepository.login(
                username = state.username.trim(),
                password = state.password,
                captcha = state.captcha.trim(),
                challenge = challenge,
            )
            _uiState.update { current ->
                result.fold(
                    onSuccess = { loginResult ->
                        when (loginResult) {
                            is AuthLoginResult.Completed -> current.copy(
                                isSubmitting = false,
                                errorMessage = null,
                                loginCompleted = true,
                                password = "",
                                captcha = "",
                                twoFactorChallenge = null,
                                twoFactorCode = "",
                            )
                            is AuthLoginResult.TwoFactorRequired -> current.copy(
                                isSubmitting = false,
                                errorMessage = null,
                                captcha = "",
                                twoFactorChallenge = loginResult.challenge,
                                twoFactorCode = "",
                            )
                        }
                    },
                    onFailure = { error ->
                        current.copy(
                            isSubmitting = false,
                            errorMessage = error.message ?: "登录失败",
                        )
                    },
                )
            }
        }
    }

    private fun submitTwoFactor(state: AuthUiState) {
        val challenge = state.twoFactorChallenge ?: return
        if (!state.canSubmit) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val result = authRepository.verifyTwoFactor(
                code = state.twoFactorCode.trim(),
                challenge = challenge,
            )
            _uiState.update { current ->
                result.fold(
                    onSuccess = {
                        current.copy(
                            isSubmitting = false,
                            errorMessage = null,
                            loginCompleted = true,
                            password = "",
                            captcha = "",
                            twoFactorChallenge = null,
                            twoFactorCode = "",
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            isSubmitting = false,
                            errorMessage = error.message ?: "两步验证失败",
                        )
                    },
                )
            }
        }
    }
}
