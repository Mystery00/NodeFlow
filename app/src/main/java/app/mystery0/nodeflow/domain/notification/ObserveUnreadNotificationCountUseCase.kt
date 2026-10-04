package app.mystery0.nodeflow.domain.notification

import kotlinx.coroutines.flow.Flow

interface UnreadNotificationRepository {
    val unreadCount: Flow<Int?>
}

class ObserveUnreadNotificationCountUseCase(private val repository: UnreadNotificationRepository) {
    operator fun invoke(): Flow<Int?> = repository.unreadCount
}
