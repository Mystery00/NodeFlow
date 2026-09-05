package app.mystery0.nodeflow.feature.account

import app.mystery0.nodeflow.core.model.DailyCheckIn
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class AccountScreenContentTest {
    @Test
    fun joinDateUsesLocalCalendarDateWithoutTime() {
        val timestamp = Instant.parse("2020-06-01T20:00:00Z").epochSecond
        assertThat(formatAccountJoinDate(timestamp, ZoneId.of("Asia/Shanghai"))).isEqualTo("2020-06-02")
        assertThat(formatAccountJoinDate(null)).isNull()
        assertThat(formatAccountJoinDate(0)).isNull()
    }

    @Test
    fun usesCheckInActionLine_returnsFalseAfterCheckedIn() {
        val checkIn = DailyCheckIn(
            checkedIn = true,
            continuousDays = 1780,
            canCheckIn = false,
        )

        assertThat(usesCheckInActionLine(checkIn)).isFalse()
    }

    @Test
    fun usesCheckInActionLine_returnsTrueWhenRewardCanBeClaimed() {
        val checkIn = DailyCheckIn(
            checkedIn = false,
            continuousDays = 1780,
            canCheckIn = true,
        )

        assertThat(usesCheckInActionLine(checkIn)).isTrue()
    }
}
