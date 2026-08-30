package app.mystery0.nodeflow.core.crash

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CrashHandlerDelegationTest {
    @Test
    fun coordinator_launchesReportThenDelegatesThenTerminates() {
        val events = mutableListOf<String>()
        val previous = Thread.UncaughtExceptionHandler { _, _ -> events += "delegate" }
        val coordinator = CrashHandlerCoordinator(
            previousHandler = previous,
            terminateProcess = { events += "terminate" },
        )

        coordinator.handle(Thread.currentThread(), IllegalStateException()) {
            events += "launch"
        }

        assertThat(events).containsExactly("launch", "delegate", "terminate").inOrder()
    }

    @Test
    fun coordinator_terminatesWhenLauncherOrPreviousHandlerThrows() {
        val launchFailureEvents = mutableListOf<String>()
        CrashHandlerCoordinator(
            previousHandler = Thread.UncaughtExceptionHandler { _, _ ->
                launchFailureEvents += "delegate"
            },
            terminateProcess = { launchFailureEvents += "terminate" },
        ).handle(Thread.currentThread(), IllegalStateException()) {
            launchFailureEvents += "launch"
            error("launcher")
        }
        assertThat(launchFailureEvents).containsExactly("launch", "delegate", "terminate").inOrder()

        val delegateFailureEvents = mutableListOf<String>()
        CrashHandlerCoordinator(
            previousHandler = Thread.UncaughtExceptionHandler { _, _ ->
                delegateFailureEvents += "delegate"
                error("delegate")
            },
            terminateProcess = { delegateFailureEvents += "terminate" },
        ).handle(Thread.currentThread(), IllegalStateException()) {
            delegateFailureEvents += "launch"
        }
        assertThat(delegateFailureEvents).containsExactly("launch", "delegate", "terminate").inOrder()
    }

    @Test
    fun coordinator_reentrySkipsSecondReportAndDelegation() {
        val events = mutableListOf<String>()
        lateinit var coordinator: CrashHandlerCoordinator
        coordinator = CrashHandlerCoordinator(
            previousHandler = Thread.UncaughtExceptionHandler { _, _ -> events += "delegate" },
            terminateProcess = { events += "terminate" },
        )

        coordinator.handle(Thread.currentThread(), IllegalStateException()) {
            events += "launch"
            coordinator.handle(Thread.currentThread(), IllegalArgumentException()) {
                events += "second-launch"
            }
        }

        assertThat(events).doesNotContain("second-launch")
        assertThat(events.count { it == "delegate" }).isEqualTo(1)
        assertThat(events.first()).isEqualTo("launch")
    }

    @Test
    fun installGuard_allowsOnlyFirstInstallation() {
        val guard = CrashHandlerInstallGuard()

        assertThat(guard.tryAcquire()).isTrue()
        assertThat(guard.tryAcquire()).isFalse()
    }
}
