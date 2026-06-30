package app.mystery0.nodeflow.feature.auth

sealed interface AuthUiEvent {
    data class UsernameChanged(val value: String) : AuthUiEvent
    data class PasswordChanged(val value: String) : AuthUiEvent
    data class CaptchaChanged(val value: String) : AuthUiEvent
    data class TwoFactorCodeChanged(val value: String) : AuthUiEvent
    data object RefreshChallenge : AuthUiEvent
    data object Submit : AuthUiEvent
    data object ClearSession : AuthUiEvent
    data object LoginResultConsumed : AuthUiEvent
}
