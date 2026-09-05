package app.mystery0.nodeflow.core.designsystem.component

import app.mystery0.nodeflow.core.model.RichInline
import app.mystery0.nodeflow.core.model.RichInlineStyle
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RichContentRendererTest {
    @Test
    fun buildRichTextPlan_keepsAllInlineNodesInOneTextLayout() {
        val plan = buildRichTextPlan(
            listOf(
                RichInline.Text("普通"),
                RichInline.Text("粗体", RichInlineStyle(bold = true)),
                RichInline.LineBreak,
                RichInline.Text("链接", linkUrl = "https://www.v2ex.com/t/1"),
            ),
        )

        assertThat(plan.text).isEqualTo("普通粗体\n链接")
        assertThat(plan.ranges.map { it.value }).containsExactly("普通", "粗体", "链接").inOrder()
        assertThat(plan.ranges.single { it.value == "链接" }.linkUrl)
            .isEqualTo("https://www.v2ex.com/t/1")
    }

    @Test
    fun buildRichTextPlan_assignsStableInlineImageIds() {
        val plan = buildRichTextPlan(
            listOf(
                RichInline.Text("状态"),
                RichInline.InlineImage(
                    app.mystery0.nodeflow.core.model.RichImage(
                        url = "https://www.v2ex.com/smile.png",
                        compact = true,
                    ),
                ),
                RichInline.Text("正常"),
            ),
        )

        assertThat(plan.inlineImages).hasSize(1)
        assertThat(plan.inlineImages.single().id).isEqualTo("rich-inline-image-0")
    }

    @Test
    fun parseRichCssColor_acceptsSafeFormatsAndRejectsInvalidInput() {
        assertThat(parseRichCssColor("#336699")).isNotNull()
        assertThat(parseRichCssColor("rgb(51, 102, 153)")).isNotNull()
        assertThat(parseRichCssColor("red")).isNotNull()
        assertThat(parseRichCssColor("#1234")).isEqualTo(0x44112233L)
        assertThat(parseRichCssColor("#11223344")).isEqualTo(0x44112233L)
        assertThat(parseRichCssColor("javascript:red")).isNull()
        assertThat(parseRichCssColor("transparent")).isNull()
    }
}
