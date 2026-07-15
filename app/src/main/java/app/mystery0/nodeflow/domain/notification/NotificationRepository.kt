package app.mystery0.nodeflow.domain.notification

import androidx.paging.PagingData
import app.mystery0.nodeflow.core.model.Notification
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun notificationsPaging(): Flow<PagingData<Notification>>
}
