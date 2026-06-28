package app.mystery0.nodeflow.feature.profile

sealed interface ProfileUiEvent {
    data object Refresh : ProfileUiEvent
    data object Retry : ProfileUiEvent
}
