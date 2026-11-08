package app.mystery0.nodeflow.feature.account

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

}
