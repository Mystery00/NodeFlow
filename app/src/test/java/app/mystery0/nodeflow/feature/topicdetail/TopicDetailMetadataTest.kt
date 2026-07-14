package app.mystery0.nodeflow.feature.topicdetail

import androidx.compose.ui.unit.dp
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.TopicDetail
import app.mystery0.nodeflow.core.model.User
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

    @Test
    fun topicDetailNodeChip_usesNodeTitleAndNameLikeTopicList() {
        val chip = topicDetailNodeChip(
            topicDetail(
                node = Node(
                    name = "android",
                    title = "Android",
                ),
            ),
        )

        assertThat(chip?.label).isEqualTo("Android")
        assertThat(chip?.nodeName).isEqualTo("android")
    }

    @Test
    fun topicDetailNodeChip_returnsNullWhenNodeNameIsBlank() {
        val chip = topicDetailNodeChip(
            topicDetail(
                node = Node(
                    name = "",
                    title = "未知节点",
                ),
            ),
        )

        assertThat(chip).isNull()
    }

    @Test
    fun topicDetailMetadataLayout_limitsNodeChipWidth() {
        val layout = topicDetailMetadataLayout()

        assertThat(layout.nodeChipMaxWidth).isEqualTo(120.dp)
    }

    private fun topicDetail(node: Node): TopicDetail = TopicDetail(
        topic = Topic(
            id = 1L,
            title = "测试主题",
            url = "https://www.v2ex.com/t/1",
            node = node,
            author = User(username = "alice"),
        ),
        content = "",
        contentRendered = "",
        replies = emptyList(),
    )
}
