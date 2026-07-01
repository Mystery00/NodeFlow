package app.mystery0.nodeflow.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReplyItemLinkTest {
    @Test
    fun memberUsernameFromUrl_parsesRelativeMemberLink() {
        assertThat(memberUsernameFromUrl("/member/alice")).isEqualTo("alice")
    }

    @Test
    fun memberUsernameFromUrl_parsesAbsoluteMemberLink() {
        assertThat(memberUsernameFromUrl("https://www.v2ex.com/member/bob")).isEqualTo("bob")
    }

    @Test
    fun memberUsernameFromUrl_ignoresNonMemberLink() {
        assertThat(memberUsernameFromUrl("/t/123")).isNull()
    }
}
