package app.mystery0.nodeflow.core.notification

import app.mystery0.nodeflow.core.security.EncryptedKeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

fun interface NotificationUnreadCountProvider {
    suspend fun unreadCount(): Int
}

fun interface NotificationReminderEnabledProvider {
    suspend fun isEnabled(): Boolean
}

class NotificationReminderChecker(
    private val unreadCountProvider: NotificationUnreadCountProvider,
    private val store: EncryptedKeyValueStore,
    private val reminderEnabled: Flow<Boolean> = flowOf(true),
) {
    suspend fun check(): NotificationReminderDecision {
        val unreadCount = unreadCountProvider.unreadCount().coerceAtLeast(0)
        val previous = store.read(LAST_UNREAD_COUNT_KEY)?.toIntOrNull()
        val shouldNotify = unreadCount > 0 && (previous == null || previous == 0 || unreadCount > previous)
        if (!reminderEnabled.first()) {
            return NotificationReminderDecision(unreadCount, shouldNotify = false)
        }

        store.write(LAST_UNREAD_COUNT_KEY, unreadCount.toString())
        if (!reminderEnabled.first()) {
            store.remove(LAST_UNREAD_COUNT_KEY)
            return NotificationReminderDecision(unreadCount, shouldNotify = false)
        }
        return NotificationReminderDecision(unreadCount, shouldNotify)
    }

    companion object {
        const val LAST_UNREAD_COUNT_KEY = "notification_last_unread_count"
    }
}

data class NotificationReminderDecision(
    val unreadCount: Int,
    val shouldNotify: Boolean,
)
