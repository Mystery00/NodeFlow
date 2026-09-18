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

    @Test
    fun topicPinnedChip_returnsLabelOnlyForPinnedTopic() {
        assertThat(topicPinnedChip(topic(isPinned = true), label = "置顶")).isEqualTo("置顶")
        assertThat(topicPinnedChip(topic(isPinned = false), label = "置顶")).isNull()
    }

    @Test
    fun topicListMetadata_doesNotIncludeLastReplyBy() {
        val topic = topic(lastReplyBy = "bob")
        val metadata = topicListMetadata(topic)
        assertThat(metadata).doesNotContain("最后回复来自")
        assertThat(metadata).contains("alice")
    }

    @Test
    fun topicLastReplyLabel_returnsTextWhenPresent() {
        val topic = topic(lastReplyBy = "bob")
        assertThat(topicLastReplyLabel(topic)).isEqualTo("最后回复来自 bob")
    }

    @Test
    fun topicLastReplyLabel_returnsNullWhenMissing() {
        assertThat(topicLastReplyLabel(topic(lastReplyBy = null))).isNull()
        assertThat(topicLastReplyLabel(topic(lastReplyBy = "  "))).isNull()
    }

    @Test
    fun topicListMetadataWithLastReply_includesLastReplyWhenPresent() {
        val topic = topic(lastReplyBy = "bob")
        val metadata = topicListMetadataWithLastReply(topic)
        assertThat(metadata).contains("alice")
        assertThat(metadata).contains("最后回复来自 bob")
    }

    @Test
    fun topicListMetadataWithLastReply_omitsLastReplyWhenMissing() {
        val topic = topic(lastReplyBy = null)
        val metadata = topicListMetadataWithLastReply(topic)
        assertThat(metadata).contains("alice")
        assertThat(metadata).doesNotContain("最后回复来自")
    }

    @Test
    fun topicVotesChip_returnsLabelWhenVotesPositive() {
        val topic = topic(votes = 4)
        val chip = topicVotesChip(topic)
        assertThat(chip).isEqualTo("▲ 4")
    }

    @Test
    fun topicVotesChip_returnsNullWhenVotesZero() {
        val topic = topic(votes = 0)
        val chip = topicVotesChip(topic)
        assertThat(chip).isNull()
    }

    private fun topic(
        node: Node = Node(name = "android", title = "Android"),
        isPinned: Boolean = false,
        lastReplyBy: String? = null,
        votes: Int = 0,
    ): Topic = Topic(
        id = 1L,
        title = "测试主题",
        url = "https://www.v2ex.com/t/1",
        node = node,
        author = User(username = "alice"),
        isPinned = isPinned,
        lastReplyBy = lastReplyBy,
        votes = votes,
    )
}
