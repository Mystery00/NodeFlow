package app.mystery0.nodeflow.core.crash

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import org.junit.Test

class CrashReportFormatterTest {
    @Test
    fun format_containsVersionDeviceAndStackTrace() {
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
        assertThat(report).contains("java.lang.IllegalStateException: boom")
        assertThat(report).contains("Caused by: java.lang.IllegalArgumentException: root")
    }

    @Test
    fun format_truncatesOverlongReport() {
        val throwable = IllegalStateException("x".repeat(200 * 1024))

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
