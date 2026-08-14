package app.mystery0.nodeflow.core.ui

import app.mystery0.nodeflow.core.model.Reply
import app.mystery0.nodeflow.core.model.User
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReplyItemAuthorTest {
    @Test
    fun isReplyFromTopicAuthor_matchesSameUsername() {
        assertThat(isReplyFromTopicAuthor("Livid", "Livid")).isTrue()
    }

    @Test
    fun isReplyFromTopicAuthor_matchesIgnoringCase() {
        // V2EX 用户名不区分大小写
        assertThat(isReplyFromTopicAuthor("livid", "Livid")).isTrue()
        assertThat(isReplyFromTopicAuthor("LIVID", "livid")).isTrue()
    }

    @Test
    fun isReplyFromTopicAuthor_matchesIgnoringSurroundingWhitespace() {
        assertThat(isReplyFromTopicAuthor(" Livid ", "Livid")).isTrue()
    }

    @Test
    fun isReplyFromTopicAuthor_rejectsDifferentUsername() {
        assertThat(isReplyFromTopicAuthor("coderluan", "csfreshman")).isFalse()
    }

    @Test
    fun isReplyFromTopicAuthor_rejectsBlankUsernames() {
        // 作者名解析失败为空时，不能把同样为空的回复作者误判为楼主
        assertThat(isReplyFromTopicAuthor("", "")).isFalse()
        assertThat(isReplyFromTopicAuthor(" ", " ")).isFalse()
        assertThat(isReplyFromTopicAuthor("Livid", "")).isFalse()
        assertThat(isReplyFromTopicAuthor("", "Livid")).isFalse()
    }

    @Test
    fun replyAuthorNavigationTarget_returnsTrimmedUsername() {
        val reply = reply(username = " alice ")

        assertThat(replyAuthorNavigationTarget(reply)).isEqualTo("alice")
    }

    @Test
    fun replyAuthorNavigationTarget_returnsNullForBlankUsername() {
        val reply = reply(username = "   ")

        assertThat(replyAuthorNavigationTarget(reply)).isNull()
    }

    private fun reply(username: String): Reply = Reply(
        id = 1L,
        topicId = 2L,
        floor = 1,
        author = User(username = username),
        content = "",
        contentRendered = "",
    )
}
