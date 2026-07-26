package app.mystery0.nodeflow.core.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object NotificationScheduler {
    private const val WORK_NAME = "notification_check"

    fun updateSchedule(context: Context, enabled: Boolean) {
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<NotificationCheckWorker>(
                30, TimeUnit.MINUTES,
            ).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
