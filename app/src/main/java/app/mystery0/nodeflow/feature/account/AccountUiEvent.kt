package app.mystery0.nodeflow.feature.account

sealed interface AccountUiEvent {
    data object Refresh : AccountUiEvent
    data object Retry : AccountUiEvent
    data object Logout : AccountUiEvent
}
