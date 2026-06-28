package app.mystery0.nodeflow.data.notification

import app.mystery0.nodeflow.core.model.Notification
import app.mystery0.nodeflow.domain.notification.NotificationRepository

class NotificationRepositoryImpl : NotificationRepository {
    override suspend fun notifications(): Result<List<Notification>> =
        Result.success(emptyList())
}
