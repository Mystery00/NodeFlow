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

    @Test
    fun patch_replacesTagsPreservingOtherKeysOrderAndEntryFields() {
        val content = "V2EX_Polish_settings" +
            """{"settings-sync":{"version":46},""" +
            """"member-tag":{"Alice":{"tags":["旧标签"],"avatar":"https://cdn.v2ex.com/a.png","note":"备注"}},""" +
            """"options":{"foo":1}}"""

        val patched = PolishMemberTagParser.patch(content, "Alice", listOf("新标签", "前端"))

        assertThat(patched).isNotNull()
        assertThat(PolishMemberTagParser.parse(patched!!))
            .containsExactly("Alice", listOf("新标签", "前端"))
        // 其他设置键与键顺序保留
        assertThat(patched.indexOf("settings-sync")).isLessThan(patched.indexOf("member-tag"))
        assertThat(patched.indexOf("member-tag")).isLessThan(patched.indexOf("options"))
        assertThat(patched).contains("\"version\":46")
        assertThat(patched).contains("\"foo\":1")
        // 条目内未知字段与 avatar 保留
        assertThat(patched).contains("\"note\":\"备注\"")
        assertThat(patched).contains("https://cdn.v2ex.com/a.png")
    }

    @Test
    fun patch_addsNewUserWithAvatar() {
        val content = "V2EX_Polish_settings" +
            """{"member-tag":{"Alice":{"tags":["大佬"]}}}"""

        val patched = PolishMemberTagParser.patch(
            content, "bob", listOf("后端"), "https://cdn.v2ex.com/b.png",
        )

        assertThat(PolishMemberTagParser.parse(patched!!)).containsExactly(
            "Alice", listOf("大佬"),
            "bob", listOf("后端"),
        )
        assertThat(patched).contains("https://cdn.v2ex.com/b.png")
    }

    @Test
    fun patch_keepsExistingAvatarOverProvidedOne() {
        val content = "V2EX_Polish_settings" +
            """{"member-tag":{"Alice":{"tags":["大佬"],"avatar":"https://cdn.v2ex.com/old.png"}}}"""

        val patched = PolishMemberTagParser.patch(
            content, "Alice", listOf("新"), "https://cdn.v2ex.com/new.png",
        )

        assertThat(patched).contains("https://cdn.v2ex.com/old.png")
        assertThat(patched).doesNotContain("https://cdn.v2ex.com/new.png")
    }

    @Test
    fun patch_matchesUsernameCaseInsensitivelyKeepingOriginalKey() {
        val content = "V2EX_Polish_settings" +
            """{"member-tag":{"Alice":{"tags":["旧"]}}}"""

        val patched = PolishMemberTagParser.patch(content, "alice", listOf("新"))

        assertThat(PolishMemberTagParser.parse(patched!!))
            .containsExactly("Alice", listOf("新"))
        assertThat(patched).doesNotContain("\"alice\"")
    }

    @Test
    fun patch_emptyTagsRemovesEntry() {
        val content = "V2EX_Polish_settings" +
            """{"member-tag":{"Alice":{"tags":["旧"]},"bob":{"tags":["后端"]}},"options":{"foo":1}}"""

        val patched = PolishMemberTagParser.patch(content, "Alice", emptyList())

        assertThat(PolishMemberTagParser.parse(patched!!))
            .containsExactly("bob", listOf("后端"))
        assertThat(patched).doesNotContain("Alice")
        assertThat(patched).contains("\"foo\":1")
    }

    @Test
    fun patch_normalizesTags() {
        val content = "V2EX_Polish_settings{\"member-tag\":{}}"

        val patched = PolishMemberTagParser.patch(
            content, "Alice", listOf(" 前端 ", "", "前端", "  ", "后端"),
        )

        assertThat(PolishMemberTagParser.parse(patched!!))
            .containsExactly("Alice", listOf("前端", "后端"))
    }

    @Test
    fun patch_addsMemberTagKeyWhenMissing() {
        val content = "V2EX_Polish_settings{\"options\":{\"foo\":1}}"

        val patched = PolishMemberTagParser.patch(content, "Alice", listOf("大佬"))

        assertThat(PolishMemberTagParser.parse(patched!!))
            .containsExactly("Alice", listOf("大佬"))
        assertThat(patched).contains("\"foo\":1")
    }

    @Test
    fun patch_rejectsInvalidContent() {
        assertThat(PolishMemberTagParser.patch("随便一条笔记", "Alice", listOf("x"))).isNull()
        assertThat(PolishMemberTagParser.patch("V2EX_Polish_settings not json", "Alice", listOf("x"))).isNull()
        assertThat(PolishMemberTagParser.patch("V2EX_Polish_settings{\"member-tag\":123}", "Alice", listOf("x"))).isNull()
        assertThat(PolishMemberTagParser.patch("V2EX_Polish_settings[1,2]", "Alice", listOf("x"))).isNull()
    }

    @Test
    fun buildInitial_buildsMinimalNote() {
        val content = PolishMemberTagParser.buildInitial(
            "Alice", listOf("大佬"), "https://cdn.v2ex.com/a.png",
        )

        assertThat(content).isNotNull()
        assertThat(content!!).startsWith("V2EX_Polish_settings")
        assertThat(PolishMemberTagParser.parse(content))
            .containsExactly("Alice", listOf("大佬"))
        assertThat(content).contains("https://cdn.v2ex.com/a.png")
    }

    @Test
    fun buildInitial_returnsNullForEmptyTags() {
        assertThat(PolishMemberTagParser.buildInitial("Alice", listOf(" ", ""))).isNull()
    }
}
