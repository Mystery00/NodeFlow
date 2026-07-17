package app.mystery0.nodeflow.core.designsystem.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MemberTagsTest {
    @Test
    fun memberTagsFor_matchesIgnoringCase() {
        val tags = mapOf("Alice" to listOf("大佬"))
        assertThat(memberTagsFor(tags, "alice")).containsExactly("大佬")
        assertThat(memberTagsFor(tags, "ALICE")).containsExactly("大佬")
    }

    @Test
    fun memberTagsFor_returnsEmptyWhenAbsentOrBlank() {
        val tags = mapOf("Alice" to listOf("大佬"))
        assertThat(memberTagsFor(tags, "bob")).isEmpty()
        assertThat(memberTagsFor(tags, "")).isEmpty()
        assertThat(memberTagsFor(emptyMap(), "Alice")).isEmpty()
    }
}
