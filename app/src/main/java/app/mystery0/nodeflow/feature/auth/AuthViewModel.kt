package app.mystery0.nodeflow.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mystery0.nodeflow.core.model.AuthLoginResult
import app.mystery0.nodeflow.domain.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        loadChallenge()
    }

    fun onEvent(event: AuthUiEvent) {
        val state = _uiState.value
        if (event != AuthUiEvent.LoginResultConsumed && (state.isSubmitting || state.loginCompleted)) return
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
                it.copy(twoFactorCode = event.value.filter { char -> char in '0'..'9' }.take(6), errorMessage = null)
            }
            AuthUiEvent.RefreshChallenge -> loadChallenge()
            AuthUiEvent.Submit -> submit()
            AuthUiEvent.LoginResultConsumed -> _uiState.update {
                it.copy(loginCompleted = false)
            }
        }
    }

    private fun loadChallenge() {
        val state = _uiState.value
        if (state.isLoadingChallenge || state.isSubmitting || state.twoFactorChallenge != null || state.loginCompleted) return
        // 同步进入忙碌状态，避免协程开始前的连续点击创建重复请求。
        _uiState.update {
            it.copy(
                isLoadingChallenge = true,
                challenge = null,
                errorMessage = null,
                captcha = "",
            )
        }
        viewModelScope.launch {
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
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
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
                                challenge = null,
                                twoFactorChallenge = null,
                                twoFactorCode = "",
                            )
                            is AuthLoginResult.TwoFactorRequired -> current.copy(
                                isSubmitting = false,
                                errorMessage = null,
                                password = "",
                                captcha = "",
                                challenge = null,
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
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
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
