package app.mystery0.nodeflow.core.ui

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
}
