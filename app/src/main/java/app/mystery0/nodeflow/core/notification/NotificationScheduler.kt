package app.mystery0.nodeflow.core.notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.mystery0.nodeflow.core.common.IO_DISPATCHER
import app.mystery0.nodeflow.core.security.EncryptedKeyValueStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

object NotificationScheduler : KoinComponent {
    private const val WORK_NAME = "notification_check"
    private val secureStorage: EncryptedKeyValueStore by inject()
    private val ioDispatcher: CoroutineDispatcher by inject(named(IO_DISPATCHER))

    suspend fun updateSchedule(context: Context, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<NotificationCheckWorker>(
                30, TimeUnit.MINUTES,
            )
                .setConstraints(notificationScheduleConstraints())
                .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        } else {
            workManager.cancelUniqueWork(WORK_NAME)
            clearNotificationReminderBaseline(secureStorage, ioDispatcher)
        }
    }
}

internal fun notificationScheduleConstraints(): Constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

internal suspend fun clearNotificationReminderBaseline(
    storage: EncryptedKeyValueStore,
    ioDispatcher: CoroutineDispatcher,
) = withContext(ioDispatcher) {
    storage.remove(NotificationReminderChecker.LAST_UNREAD_COUNT_KEY)
}
