package app.mystery0.nodeflow.feature.notification

sealed interface NotificationUiEvent {
    data object Refresh : NotificationUiEvent
}
