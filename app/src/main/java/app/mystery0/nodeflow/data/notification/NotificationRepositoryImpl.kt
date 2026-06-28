package app.mystery0.nodeflow.data.notification

import app.mystery0.nodeflow.core.model.Notification
import app.mystery0.nodeflow.domain.notification.NotificationRepository
import javax.inject.Inject

class NotificationRepositoryImpl @Inject constructor() : NotificationRepository {
    override suspend fun notifications(): Result<List<Notification>> =
        Result.success(emptyList())
}
