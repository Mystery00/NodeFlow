package app.mystery0.nodeflow.feature.notification

import app.mystery0.nodeflow.core.model.Notification

data class NotificationUiState(
    val isLoading: Boolean = false,
    val notifications: List<Notification> = emptyList(),
)
