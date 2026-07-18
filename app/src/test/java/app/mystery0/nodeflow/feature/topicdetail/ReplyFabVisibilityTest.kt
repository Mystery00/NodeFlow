package app.mystery0.nodeflow.feature.topicdetail

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReplyFabVisibilityTest {
    @Test fun scrollingDown_hidesFab() {
        assertThat(replyFabVisibleAfterScroll(ScrollPosition(2, 8), ScrollPosition(2, 30), true)).isFalse()
    }

    @Test fun scrollingUp_showsFab() {
        assertThat(replyFabVisibleAfterScroll(ScrollPosition(3, 2), ScrollPosition(2, 80), false)).isTrue()
    }
}
