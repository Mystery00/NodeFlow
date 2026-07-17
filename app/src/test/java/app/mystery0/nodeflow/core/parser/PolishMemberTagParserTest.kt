package app.mystery0.nodeflow.core.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PolishMemberTagParserTest {
    @Test
    fun parse_extractsMemberTags() {
        val content = "V2EX_Polish_settings" +
            """{"settings-sync":{"version":46},"options":{"foo":1},""" +
            """"member-tag":{"Alice":{"tags":["大佬","前端"],"avatar":"https://cdn.v2ex.com/a.png"},""" +
            """"bob":{"tags":["后端"],"avatar":"https://cdn.v2ex.com/b.png"}}}"""

        val tags = PolishMemberTagParser.parse(content)

        assertThat(tags).containsExactly(
            "Alice", listOf("大佬", "前端"),
            "bob", listOf("后端"),
        )
    }

    @Test
    fun parse_skipsEntriesWithoutTags() {
        val content = "V2EX_Polish_settings" +
            """{"member-tag":{"Alice":{"tags":[],"avatar":""},"bob":{"avatar":""}}}"""

        assertThat(PolishMemberTagParser.parse(content)).isEmpty()
    }

    @Test
    fun parse_returnsEmptyOnInvalidInput() {
        assertThat(PolishMemberTagParser.parse("")).isEmpty()
        assertThat(PolishMemberTagParser.parse("随便一条笔记")).isEmpty()
        assertThat(PolishMemberTagParser.parse("V2EX_Polish_settings not json")).isEmpty()
        assertThat(PolishMemberTagParser.parse("V2EX_Polish_settings{\"member-tag\":123}")).isEmpty()
        assertThat(PolishMemberTagParser.parse("V2EX_Polish_settings{}")).isEmpty()
    }
}
