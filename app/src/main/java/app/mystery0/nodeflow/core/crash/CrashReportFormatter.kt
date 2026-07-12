package app.mystery0.nodeflow.core.crash

import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CrashReportFormatter {
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    // Intent extra 走 Binder 传输有 1MB 上限，超长堆栈截断保底
    private const val MAX_REPORT_LENGTH = 128 * 1024

    fun format(
        versionName: String,
        versionCode: Int,
        deviceManufacturer: String,
        deviceModel: String,
        osVersion: String,
        sdkInt: Int,
        threadName: String,
        crashEpochMillis: Long,
        throwable: Throwable,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val crashTime = timeFormatter.format(Instant.ofEpochMilli(crashEpochMillis).atZone(zoneId))
        val report = buildString {
            appendLine("NodeFlow 崩溃报告")
            appendLine("时间: $crashTime")
            appendLine("版本: $versionName ($versionCode)")
            appendLine("设备: $deviceManufacturer $deviceModel")
            appendLine("系统: Android $osVersion (API $sdkInt)")
            appendLine("线程: $threadName")
            appendLine()
            append(stackTraceOf(throwable))
        }
        if (report.length <= MAX_REPORT_LENGTH) return report
        return report.take(MAX_REPORT_LENGTH) + "\n…（日志过长已截断）"
    }

    private fun stackTraceOf(throwable: Throwable): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        return writer.toString()
    }
}
