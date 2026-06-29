package app.mystery0.nodeflow.core.ui

import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.User
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TopicListItemContentTest {
    @Test
    fun topicNodeChip_usesNodeTitleAsLabelAndNodeNameAsNavigationTarget() {
        val topic = topic(
            node = Node(
                name = "android",
                title = "Android",
            ),
        )

        val chip = topicNodeChip(topic)

        assertThat(chip?.label).isEqualTo("Android")
        assertThat(chip?.nodeName).isEqualTo("android")
    }

    @Test
    fun topicNodeChip_fallsBackToNodeNameWhenTitleIsBlank() {
        val topic = topic(
            node = Node(
                name = "jobs",
                title = "",
            ),
        )

        val chip = topicNodeChip(topic)

        assertThat(chip?.label).isEqualTo("jobs")
        assertThat(chip?.nodeName).isEqualTo("jobs")
    }

    @Test
    fun topicNodeChip_returnsNullWhenNodeNameIsBlank() {
        val topic = topic(
            node = Node(
                name = "",
                title = "未知节点",
            ),
        )

        val chip = topicNodeChip(topic)

        assertThat(chip).isNull()
    }

    private fun topic(node: Node): Topic = Topic(
        id = 1L,
        title = "测试主题",
        url = "https://www.v2ex.com/t/1",
        node = node,
        author = User(username = "alice"),
    )
}
