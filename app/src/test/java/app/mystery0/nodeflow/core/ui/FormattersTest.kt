package app.mystery0.nodeflow.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class FormattersTest {
    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val nowEpochSeconds = LocalDateTime
        .of(2026, 6, 29, 13, 30, 0)
        .atZone(zoneId)
        .toEpochSecond()

    @Test
    fun formatEpochSeconds_returnsMinutesAgoWithinOneHour() {
        val value = formatEpochSeconds(
            epochSeconds = nowEpochSeconds - 12 * 60,
            nowEpochSeconds = nowEpochSeconds,
        )

        assertThat(value).isEqualTo("12 分钟前")
    }

    @Test
    fun formatEpochSeconds_returnsHoursAgoWithinTwentyFourHours() {
        val value = formatEpochSeconds(
            epochSeconds = nowEpochSeconds - 3 * 60 * 60,
            nowEpochSeconds = nowEpochSeconds,
        )

        assertThat(value).isEqualTo("3 小时前")
    }

    @Test
    fun formatEpochSeconds_returnsAbsoluteTimeAfterTwentyFourHours() {
        val value = formatEpochSeconds(
            epochSeconds = LocalDateTime
                .of(2026, 6, 28, 13, 29, 59)
                .atZone(zoneId)
                .toEpochSecond(),
            nowEpochSeconds = nowEpochSeconds,
        )

        assertThat(value).isEqualTo("2026-06-28 13:29:59")
    }

    @Test
    fun formatEpochSeconds_returnsEmptyForMissingValue() {
        assertThat(formatEpochSeconds(null, nowEpochSeconds)).isEmpty()
        assertThat(formatEpochSeconds(0, nowEpochSeconds)).isEmpty()
    }
}
