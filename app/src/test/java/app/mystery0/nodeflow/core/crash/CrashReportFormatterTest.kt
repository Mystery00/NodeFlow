package app.mystery0.nodeflow.core.crash

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import org.junit.Test

class CrashReportFormatterTest {
    @Test
    fun format_containsVersionDeviceAndSanitizedStackTrace() {
        val throwable = IllegalStateException("boom", IllegalArgumentException("root"))

        val report = CrashReportFormatter.format(
            versionName = "1.0",
            versionCode = 1,
            deviceManufacturer = "Google",
            deviceModel = "Pixel 8",
            osVersion = "16",
            sdkInt = 36,
            threadName = "main",
            crashEpochMillis = 1_752_300_000_000,
            throwable = throwable,
            zoneId = ZoneId.of("Asia/Shanghai"),
        )

        assertThat(report).contains("版本: 1.0 (1)")
        assertThat(report).contains("设备: Google Pixel 8")
        assertThat(report).contains("系统: Android 16 (API 36)")
        assertThat(report).contains("线程: main")
        assertThat(report).contains("时间: 2025-07-12")
        assertThat(report).contains("java.lang.IllegalStateException")
        assertThat(report).contains("Caused by: java.lang.IllegalArgumentException")
        assertThat(report).doesNotContain("boom")
        assertThat(report).doesNotContain("root")
    }

    @Test
    fun format_removesCredentialsUrlsAndUserContentFromThrowableMessages() {
        val secretMessage = "Authorization: Bearer secret-token; Cookie: A2=session; " +
            "https://www.v2ex.com/t/1?once=123456&token=abc 用户回复正文"

        val report = CrashReportFormatter.format(
            versionName = "1.0",
            versionCode = 1,
            deviceManufacturer = "Google",
            deviceModel = "Pixel 8",
            osVersion = "16",
            sdkInt = 36,
            threadName = "main",
            crashEpochMillis = 1_752_300_000_000,
            throwable = IllegalStateException(secretMessage),
        )

        assertThat(report).contains("java.lang.IllegalStateException")
        assertThat(report).doesNotContain("secret-token")
        assertThat(report).doesNotContain("A2=session")
        assertThat(report).doesNotContain("once=123456")
        assertThat(report).doesNotContain("用户回复正文")
    }

    @Test
    fun format_preservesSuppressedAndCircularStructureWithoutMessages() {
        val root = IllegalStateException("root-secret")
        val cause = IllegalArgumentException("cause-secret")
        val suppressed = UnsupportedOperationException("suppressed-secret")
        root.initCause(cause)
        root.addSuppressed(suppressed)
        cause.addSuppressed(root)

        val report = CrashReportFormatter.format(
            versionName = "1.0",
            versionCode = 1,
            deviceManufacturer = "Google",
            deviceModel = "Pixel 8",
            osVersion = "16",
            sdkInt = 36,
            threadName = "main",
            crashEpochMillis = 1_752_300_000_000,
            throwable = root,
        )

        assertThat(report).contains("Suppressed: java.lang.UnsupportedOperationException")
        assertThat(report).contains("Caused by: java.lang.IllegalArgumentException")
        assertThat(report).contains("CIRCULAR REFERENCE: java.lang.IllegalStateException")
        assertThat(report).doesNotContain("root-secret")
        assertThat(report).doesNotContain("cause-secret")
        assertThat(report).doesNotContain("suppressed-secret")
    }

    @Test
    fun format_handlesDeepCauseChainWithinLengthBudget() {
        val root = IllegalStateException()
        var current: Throwable = root
        repeat(10_000) {
            val next = IllegalStateException()
            current.initCause(next)
            current = next
        }

        val report = CrashReportFormatter.format(
            versionName = "1.0",
            versionCode = 1,
            deviceManufacturer = "Google",
            deviceModel = "Pixel 8",
            osVersion = "16",
            sdkInt = 36,
            threadName = "main",
            crashEpochMillis = 1_752_300_000_000,
            throwable = root,
        )

        assertThat(report.length).isLessThan(130 * 1024)
        assertThat(report).endsWith("…（日志过长已截断）")
    }

    @Test
    fun format_truncatesOverlongReport() {
        val throwable = IllegalStateException().apply {
            stackTrace = Array(5_000) { index ->
                StackTraceElement("example.VeryLongClassName$index", "method$index", "Source.kt", index)
            }
        }

        val report = CrashReportFormatter.format(
            versionName = "1.0",
            versionCode = 1,
            deviceManufacturer = "Google",
            deviceModel = "Pixel 8",
            osVersion = "16",
            sdkInt = 36,
            threadName = "main",
            crashEpochMillis = 1_752_300_000_000,
            throwable = throwable,
        )

        assertThat(report.length).isLessThan(130 * 1024)
        assertThat(report).endsWith("…（日志过长已截断）")
    }
}
