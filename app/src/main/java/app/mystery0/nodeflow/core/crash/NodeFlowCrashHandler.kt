package app.mystery0.nodeflow.core.crash

import android.content.Context
import android.os.Build
import android.os.Process
import app.mystery0.nodeflow.BuildConfig
import kotlin.system.exitProcess

class NodeFlowCrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
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
        } catch (_: Throwable) {
            // 崩溃处理自身出错时不再补救，直接退出进程
        } finally {
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    companion object {
        fun install(context: Context) {
            Thread.setDefaultUncaughtExceptionHandler(NodeFlowCrashHandler(context))
        }
    }
}
