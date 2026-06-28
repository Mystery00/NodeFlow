package app.mystery0.nodeflow.domain.notification

import app.mystery0.nodeflow.core.model.Notification

interface NotificationRepository {
    suspend fun notifications(): Result<List<Notification>>
}
