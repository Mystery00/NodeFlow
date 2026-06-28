package app.mystery0.nodeflow.feature.auth

sealed interface AuthUiEvent {
    data object ClearSession : AuthUiEvent
}
