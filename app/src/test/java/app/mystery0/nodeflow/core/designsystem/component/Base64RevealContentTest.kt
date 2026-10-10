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

    @Test fun mode_usesBothRepresentationsAndActualAvailableWidth() {
        assertThat(canPlaceBase64Inline("encoded", "decoded", 320, 180, 360)).isTrue()
        assertThat(canPlaceBase64Inline("encoded", "decoded", 400, 180, 360)).isFalse()
        assertThat(canPlaceBase64Inline("encoded", "decoded", 180, 400, 360)).isFalse()
        assertThat(canPlaceBase64Inline("encoded", "line one\nline two", 180, 180, 360)).isFalse()
    }

    @Test fun blockSplit_preservesBreaksWithoutDuplicatingBoundaryLines() {
        val before = RichInline.Text("前面")
        val after = RichInline.Text("后面")
        val br = RichInline.LineBreak
        assertThat(splitRichTextChunks(listOf(before, br, token, br, after), emptySet())).containsExactly(
            RichTextChunk.Inline(listOf(before)), RichTextChunk.Block(token), RichTextChunk.Inline(listOf(after)),
        ).inOrder()
        assertThat(splitRichTextChunks(listOf(before, br, br, token, br, br, after), emptySet())).containsExactly(
            RichTextChunk.Inline(listOf(before)), RichTextChunk.Inline(emptyList()), RichTextChunk.Block(token),
            RichTextChunk.Inline(emptyList()), RichTextChunk.Inline(listOf(after)),
        ).inOrder()
        assertThat(splitRichTextChunks(listOf(br, token, br), emptySet())).containsExactly(
            RichTextChunk.Inline(emptyList()), RichTextChunk.Block(token), RichTextChunk.Inline(emptyList()),
        ).inOrder()
    }

    @Test fun blockSplit_doesNotTurnHtmlIndentationIntoBlankRows() {
        val br = RichInline.LineBreak
        val before = RichInline.Text("前面")
        val after = RichInline.Text("后面")
        assertThat(splitRichTextChunks(listOf(before, br, RichInline.Text(" "), token, RichInline.Text(" "), br, after), emptySet()))
            .containsExactly(RichTextChunk.Inline(listOf(before)), RichTextChunk.Block(token), RichTextChunk.Inline(listOf(after)))
            .inOrder()
    }

}
