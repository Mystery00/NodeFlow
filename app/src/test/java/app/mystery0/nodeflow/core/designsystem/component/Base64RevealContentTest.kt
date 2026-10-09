package app.mystery0.nodeflow.core.designsystem.component

import app.mystery0.nodeflow.core.model.RichInline
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Base64RevealContentTest {
    private val token = RichInline.Base64Text("aGVsbG8gd29ybGQ=", "reply:1:0")

    @Test fun state_togglesInPlaceAndSeparatesOccurrences() {
        val state = Base64RevealState()
        assertThat(state.decoded(token)).isNull()
        assertThat(state.toggle(token)).isTrue()
        assertThat(state.decoded(token.copy())).isEqualTo("hello world")
        assertThat(state.decoded(token.copy(key = "reply:2:0"))).isNull()
        assertThat(state.toggle(token)).isTrue()
        assertThat(state.decoded(token)).isNull()
        assertThat(state.toggle(token.copy(encoded = "bad"))).isFalse()
        assertThat(state.decoded(token)).isNull()
    }

    @Test fun layout_shortTokenStaysInlineAndLongTokenReplacesSamePosition() {
        val content = listOf(RichInline.Text("前面"), token, RichInline.Text("后面"))
        assertThat(splitRichTextChunks(content, setOf(token.key)))
            .containsExactly(RichTextChunk.Inline(content))
        assertThat(splitRichTextChunks(content, emptySet())).containsExactly(
            RichTextChunk.Inline(listOf(content[0])), RichTextChunk.Block(token), RichTextChunk.Inline(listOf(content[2])),
        ).inOrder()
        val plan = buildRichTextPlan(content)
        assertThat(plan.base64).containsExactly(token)
        assertThat(plan.text).isEqualTo("前面\uFFFC后面")
        assertThat(plan.ranges.map { it.value }).containsExactly("前面", "后面").inOrder()
    }
}
