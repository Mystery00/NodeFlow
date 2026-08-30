package app.mystery0.nodeflow.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import app.mystery0.nodeflow.MainActivity
import app.mystery0.nodeflow.R
import app.mystery0.nodeflow.core.common.NodeFlowException
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class NotificationCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val checker: NotificationReminderChecker by inject()
    private val reminderEnabled: NotificationReminderEnabledProvider by inject()

    override suspend fun doWork(): Result {
        return try {
            val decision = checker.check()
            publishNotificationIfEnabled(
                decision = decision,
                isEnabled = reminderEnabled::isEnabled,
                publish = ::showNotification,
            )
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: NodeFlowException) {
            e.workerOutcome().toWorkManagerResult()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun showNotification(count: Int) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("NodeFlow")
            .setContentText("你有 $count 条未读通知")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "notification_reminder"
        const val CHANNEL_NAME = "新消息提醒"
        const val NOTIFICATION_ID = 1001
    }
}

internal suspend fun publishNotificationIfEnabled(
    decision: NotificationReminderDecision,
    isEnabled: suspend () -> Boolean,
    publish: (Int) -> Unit,
): Boolean {
    if (!decision.shouldNotify || !isEnabled()) return false
    publish(decision.unreadCount)
    return true
}

internal enum class NotificationWorkerOutcome {
    Success,
    Retry,
    Failure,
}

internal fun NodeFlowException.workerOutcome(): NotificationWorkerOutcome = when (kind) {
    NodeFlowException.Kind.Auth -> NotificationWorkerOutcome.Success
    NodeFlowException.Kind.Network,
    NodeFlowException.Kind.Http,
    NodeFlowException.Kind.EmptyBody,
    NodeFlowException.Kind.AccessDenied,
    NodeFlowException.Kind.Unknown,
    -> NotificationWorkerOutcome.Retry
    else -> NotificationWorkerOutcome.Failure
}

private fun NotificationWorkerOutcome.toWorkManagerResult(): ListenableWorker.Result = when (this) {
    NotificationWorkerOutcome.Success -> ListenableWorker.Result.success()
    NotificationWorkerOutcome.Retry -> ListenableWorker.Result.retry()
    NotificationWorkerOutcome.Failure -> ListenableWorker.Result.failure()
}
