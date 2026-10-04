package app.mystery0.nodeflow.feature.account

sealed interface AccountUiEvent {
    data object Refresh : AccountUiEvent
    data object Retry : AccountUiEvent
    data object CheckIn : AccountUiEvent
    data object ToastShown : AccountUiEvent
    data object Logout : AccountUiEvent
}
