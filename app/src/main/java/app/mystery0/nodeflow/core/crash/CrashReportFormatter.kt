package app.mystery0.nodeflow.core.crash

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.IdentityHashMap

object CrashReportFormatter {
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    // Intent extra 走 Binder 传输有 1MB 上限，报告在构建过程中即执行长度限制。
    private const val MAX_REPORT_LENGTH = 128 * 1024
    private const val TRUNCATED_SUFFIX = "…（日志过长已截断）"

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
        val report = BoundedReportBuilder(MAX_REPORT_LENGTH, TRUNCATED_SUFFIX)
        report.appendLine("NodeFlow 崩溃报告")
        report.appendLine("时间: $crashTime")
        report.appendLine("版本: $versionName ($versionCode)")
        report.appendLine("设备: $deviceManufacturer $deviceModel")
        report.appendLine("系统: Android $osVersion (API $sdkInt)")
        report.appendLine("线程: $threadName")
        report.appendLine("")
        appendSanitizedStackTrace(report, throwable)
        return report.build()
    }

    /**
     * 只保留异常类型和代码位置，不输出 Throwable message，避免用户内容或凭据进入分享日志。
     */
    private fun appendSanitizedStackTrace(report: BoundedReportBuilder, root: Throwable) {
        val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        val pending = ArrayDeque<ThrowableNode>()
        pending.addLast(ThrowableNode(root, caption = "", indent = ""))

        while (pending.isNotEmpty() && !report.isTruncated) {
            val node = pending.removeLast()
            val throwable = node.throwable
            if (!visited.add(throwable)) {
                report.appendLine(
                    "${node.indent}${node.caption}[CIRCULAR REFERENCE: ${throwable.javaClass.name}]",
                )
                continue
            }

            if (!report.appendLine("${node.indent}${node.caption}${throwable.javaClass.name}")) break
            for (element in throwable.stackTrace) {
                if (!report.appendLine("${node.indent}\tat $element")) break
            }
            if (report.isTruncated) break

            throwable.cause?.let { cause ->
                pending.addLast(ThrowableNode(cause, caption = "Caused by: ", indent = node.indent))
            }
            throwable.suppressed.toList().asReversed().forEach { suppressed ->
                pending.addLast(
                    ThrowableNode(
                        throwable = suppressed,
                        caption = "Suppressed: ",
                        indent = "${node.indent}\t",
                    ),
                )
            }
        }
    }

    private data class ThrowableNode(
        val throwable: Throwable,
        val caption: String,
        val indent: String,
    )

    private class BoundedReportBuilder(
        private val maxLength: Int,
        private val truncatedSuffix: String,
    ) {
        private val content = StringBuilder()
        var isTruncated: Boolean = false
            private set

        fun appendLine(line: String): Boolean {
            if (isTruncated) return false
            val suffixReserve = truncatedSuffix.length + 1
            val available = maxLength - suffixReserve - content.length
            val required = line.length + 1
            if (required <= available) {
                content.appendLine(line)
                return true
            }

            if (available > 0) {
                content.append(line.take(available))
            }
            isTruncated = true
            return false
        }

        fun build(): String {
            if (!isTruncated) return content.toString()
            return content.toString().trimEnd() + "\n" + truncatedSuffix
        }
    }
}
