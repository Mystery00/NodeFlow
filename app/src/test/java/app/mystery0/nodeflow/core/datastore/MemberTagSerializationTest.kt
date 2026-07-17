package app.mystery0.nodeflow.core.datastore

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MemberTagSerializationTest {
    @Test
    fun roundTrip() {
        val tags = mapOf("Alice" to listOf("大佬", "前端"), "bob" to listOf("后端"))
        assertThat(decodeMemberTags(encodeMemberTags(tags))).isEqualTo(tags)
    }

    @Test
    fun decode_invalidOrNullFallsBackToEmpty() {
        assertThat(decodeMemberTags(null)).isEmpty()
        assertThat(decodeMemberTags("")).isEmpty()
        assertThat(decodeMemberTags("not json")).isEmpty()
    }
}
