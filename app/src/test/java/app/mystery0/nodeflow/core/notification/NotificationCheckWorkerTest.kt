package app.mystery0.nodeflow.core.notification

import app.mystery0.nodeflow.core.common.NodeFlowException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NotificationCheckWorkerTest {
    @Test
    fun authenticationFailureCompletesWithoutRetry() {
        assertThat(NodeFlowException(NodeFlowException.Kind.Auth, "expired").workerOutcome())
            .isEqualTo(NotificationWorkerOutcome.Success)
    }

    @Test
    fun notificationIsSuppressedWhenReminderIsDisabledAfterCheckerReturns() = runTest {
        var published = false
        val decision = NotificationReminderDecision(unreadCount = 3, shouldNotify = true)

        publishNotificationIfEnabled(
            decision = decision,
            isEnabled = { false },
            publish = { published = true },
        )

        assertThat(published).isFalse()
    }

    @Test
    fun accessDeniedIsRetried() {
        assertThat(NodeFlowException(NodeFlowException.Kind.AccessDenied, "temporary").workerOutcome())
            .isEqualTo(NotificationWorkerOutcome.Retry)
    }

    @Test
    fun parseFailureDoesNotRetry() {
        assertThat(NodeFlowException(NodeFlowException.Kind.Parse, "invalid").workerOutcome())
            .isEqualTo(NotificationWorkerOutcome.Failure)
    }
}
