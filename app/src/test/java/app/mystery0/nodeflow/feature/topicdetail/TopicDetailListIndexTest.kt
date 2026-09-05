package app.mystery0.nodeflow.feature.topicdetail

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TopicDetailListIndexTest {
    @Test
    fun replyListIndex_accountsForNativeBodyBlocksAndAppendSection() {
        assertThat(topicReplyListIndex(bodyBlockCount = 4, hasAppends = true, replyIndex = 0))
            .isEqualTo(7)
        assertThat(topicReplyListIndex(bodyBlockCount = 4, hasAppends = true, replyIndex = 3))
            .isEqualTo(10)
    }

    @Test
    fun replyListIndex_handlesEmptyBodyAndNoAppends() {
        assertThat(topicReplyListIndex(bodyBlockCount = 0, hasAppends = false, replyIndex = 0))
            .isEqualTo(2)
    }
}
