package app.mystery0.nodeflow.data.topic

import app.mystery0.nodeflow.core.model.Reply
import app.mystery0.nodeflow.core.model.User
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReplyReferenceBuilderTest {
    @Test
    fun withReferencePreviews_linksReplyToMentionedFloor() {
        val replies = listOf(
            Reply(
                id = 101,
                topicId = 1,
                floor = 1,
                author = User(username = "jonty"),
                content = "价格上 openai 和 glm 也差不多了，可以的话找代开去开 openai 也行啊。",
                contentRendered = "价格上 openai 和 glm 也差不多了，可以的话找代开去开 openai 也行啊。",
            ),
            Reply(
                id = 105,
                topicId = 1,
                floor = 5,
                author = User(username = "dingawm"),
                content = "@jonty #1\n> 价格上 openai 和 glm 也差不多了\n这是怎么个差不多法？",
                contentRendered = """@<a href="/member/jonty">jonty</a> #1 <br />&gt; 价格上 openai 和 glm 也差不多了<br /><br />这是怎么个差不多法？""",
            ),
        )

        val enriched = replies.withReferencePreviews()

        val reference = enriched[1].reference
        assertThat(reference).isNotNull()
        assertThat(reference!!.replyId).isEqualTo(101)
        assertThat(reference.floor).isEqualTo(1)
        assertThat(reference.author.username).isEqualTo("jonty")
        assertThat(reference.excerpt).isEqualTo("价格上 openai 和 glm 也差不多了，可以的话找代开去开 openai 也行啊。")
    }

    @Test
    fun withReferencePreviews_linksCompactMentionFloor() {
        val replies = listOf(
            Reply(
                id = 101,
                topicId = 1,
                floor = 1,
                author = User(username = "alice"),
                content = "first reply",
                contentRendered = "first reply",
            ),
            Reply(
                id = 102,
                topicId = 1,
                floor = 2,
                author = User(username = "bob"),
                content = "@alice#1 thanks",
                contentRendered = """<a href="/member/alice">@alice</a>#1 thanks""",
            ),
        )

        val enriched = replies.withReferencePreviews()

        val reference = enriched[1].reference
        assertThat(reference).isNotNull()
        assertThat(reference!!.replyId).isEqualTo(101)
        assertThat(reference.floor).isEqualTo(1)
        assertThat(reference.author.username).isEqualTo("alice")
    }

    @Test
    fun withReferencePreviews_linksMentionWithoutFloorToNearestPreviousAuthor() {
        val replies = listOf(
            Reply(
                id = 101,
                topicId = 1,
                floor = 1,
                author = User(username = "alice"),
                content = "old reply",
                contentRendered = "old reply",
            ),
            Reply(
                id = 102,
                topicId = 1,
                floor = 2,
                author = User(username = "alice"),
                content = "new reply",
                contentRendered = "new reply",
            ),
            Reply(
                id = 103,
                topicId = 1,
                floor = 3,
                author = User(username = "bob"),
                content = "@alice good point",
                contentRendered = """<a href="/member/alice">@alice</a> good point""",
            ),
        )

        val enriched = replies.withReferencePreviews()

        val reference = enriched[2].reference
        assertThat(reference).isNotNull()
        assertThat(reference!!.replyId).isEqualTo(102)
        assertThat(reference.floor).isEqualTo(2)
        assertThat(reference.author.username).isEqualTo("alice")
    }

    @Test
    fun withReferencePreviews_fallsBackToUsernameWhenFloorAuthorMismatches() {
        val replies = listOf(
            Reply(
                id = 101,
                topicId = 1,
                floor = 1,
                author = User(username = "alice"),
                content = "alice first reply",
                contentRendered = "alice first reply",
            ),
            Reply(
                id = 102,
                topicId = 1,
                floor = 2,
                author = User(username = "bob"),
                content = "bob reply",
                contentRendered = "bob reply",
            ),
            Reply(
                id = 103,
                topicId = 1,
                floor = 3,
                author = User(username = "carol"),
                content = "@alice #2 楼层号写错了",
                contentRendered = """<a href="/member/alice">@alice</a> #2 楼层号写错了""",
            ),
        )

        val enriched = replies.withReferencePreviews()

        val reference = enriched[2].reference
        assertThat(reference).isNotNull()
        assertThat(reference!!.replyId).isEqualTo(101)
        assertThat(reference.floor).isEqualTo(1)
        assertThat(reference.author.username).isEqualTo("alice")
    }

    @Test
    fun withReferencePreviews_usesImagePlaceholderForImageOnlyReply() {
        val replies = listOf(
            Reply(
                id = 101,
                topicId = 1,
                floor = 11,
                author = User(username = "alice"),
                content = "https://i.imgur.com/example.png",
                contentRendered = """<a href="https://i.imgur.com/example.png"><img src="https://i.imgur.com/example.png" class="embedded_image" /></a>""",
            ),
            Reply(
                id = 102,
                topicId = 1,
                floor = 15,
                author = User(username = "bob"),
                content = "@alice 回复图片",
                contentRendered = """@<a href="/member/alice">alice</a> 回复图片""",
            ),
        )

        val enriched = replies.withReferencePreviews()

        assertThat(enriched[1].reference?.excerpt).isEqualTo("[图片]")
    }
    @Test
    fun withReferencePreviews_ignoresUnknownOrFutureFloor() {
        val replies = listOf(
            Reply(
                id = 101,
                topicId = 1,
                floor = 1,
                author = User(username = "first"),
                content = "@future #9",
                contentRendered = "@future #9",
            ),
        )

        val enriched = replies.withReferencePreviews()

        assertThat(enriched.single().reference).isNull()
    }
}
