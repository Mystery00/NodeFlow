package app.mystery0.nodeflow.feature.topicdetail

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TopicDetailMetadataTest {
    @Test
    fun topicMetadataText_joinsAuthorTimeAndViewsWithMiddleDots() {
        val text = topicMetadataText(
            username = "hiboshi",
            time = "13 小时 25 分钟前",
            viewCount = 5653,
        )

        assertThat(text).isEqualTo("hiboshi · 13 小时 25 分钟前 · 5653 次点击")
    }

    @Test
    fun topicMetadataText_omitsMissingViews() {
        val text = topicMetadataText(
            username = "hiboshi",
            time = "13 小时 25 分钟前",
            viewCount = null,
        )

        assertThat(text).isEqualTo("hiboshi · 13 小时 25 分钟前")
    }

    @Test
    fun formatTopicMetadataTime_returnsHoursAndMinutesWithinOneDay() {
        val text = formatTopicMetadataTime(
            epochSeconds = 100_000L,
            nowEpochSeconds = 100_000L + 13 * 60 * 60 + 25 * 60,
        )

        assertThat(text).isEqualTo("13 小时 25 分钟前")
    }
}
