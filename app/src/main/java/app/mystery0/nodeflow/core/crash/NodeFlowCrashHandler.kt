package app.mystery0.nodeflow.core.crash

import android.content.Context
import android.os.Build
import android.os.Process
import app.mystery0.nodeflow.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

class NodeFlowCrashHandler private constructor(
    private val context: Context,
    previousHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {
    private val coordinator = CrashHandlerCoordinator(
        previousHandler = previousHandler,
        terminateProcess = ::terminateCurrentProcess,
    )

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        coordinator.handle(thread, throwable) {
            val report = CrashReportFormatter.format(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                deviceManufacturer = Build.MANUFACTURER,
                deviceModel = Build.MODEL,
                osVersion = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT,
                threadName = thread.name,
                crashEpochMillis = System.currentTimeMillis(),
                throwable = throwable,
            )
            context.startActivity(CrashActivity.newIntent(context, report))
        }
    }

    companion object {
        private val installGuard = CrashHandlerInstallGuard()

        fun install(context: Context) {
            if (!installGuard.tryAcquire()) return
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(
                NodeFlowCrashHandler(
                    context = context.applicationContext,
                    previousHandler = previousHandler,
                ),
            )
        }
    }
}

internal class CrashHandlerCoordinator(
    private val previousHandler: Thread.UncaughtExceptionHandler?,
    private val terminateProcess: () -> Unit,
) {
    private val handling = AtomicBoolean(false)

    fun handle(
        thread: Thread,
        throwable: Throwable,
        launchReport: () -> Unit,
    ) {
        if (!handling.compareAndSet(false, true)) {
            terminateProcess()
            return
        }
        try {
            try {
                launchReport()
            } catch (_: Throwable) {
                // 崩溃处理自身出错时不记录可能包含敏感信息的异常，继续委托系统处理
            }
            try {
                delegateToPreviousHandler(previousHandler, thread, throwable)
            } catch (_: Throwable) {
                // 原处理器异常时仍由最终终止逻辑结束故障进程
            }
        } finally {
            // Android 默认处理器通常不会返回；若自定义处理器返回，确保故障进程不会继续运行。
            terminateProcess()
        }
    }
}

internal class CrashHandlerInstallGuard {
    private val installed = AtomicBoolean(false)

    fun tryAcquire(): Boolean = installed.compareAndSet(false, true)
}

internal fun delegateToPreviousHandler(
    previousHandler: Thread.UncaughtExceptionHandler?,
    thread: Thread,
    throwable: Throwable,
): Boolean {
    if (previousHandler == null) return false
    previousHandler.uncaughtException(thread, throwable)
    return true
}

private fun terminateCurrentProcess() {
    Process.killProcess(Process.myPid())
    exitProcess(10)
}
