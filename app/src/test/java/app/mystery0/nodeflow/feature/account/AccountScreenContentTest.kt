package app.mystery0.nodeflow.feature.account

import app.mystery0.nodeflow.core.model.DailyCheckIn
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AccountScreenContentTest {
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
