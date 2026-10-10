package app.mystery0.nodeflow.core.designsystem.component

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.mystery0.nodeflow.core.model.RichContentBlock
import app.mystery0.nodeflow.core.parser.RichContentParser
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Base64

/** 真实 Android 文本排版与 Native Graphics；样例脱敏，不把截图里的邮箱写入测试。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Base64LayoutRobolectricTest {
    @get:Rule val rule = createComposeRule()

    @Test fun inlineCard_doesNotOverlapPreviousOrNextExplicitLine() {
        showParagraph("上方正文<br>${encode("reader@demo.test")}<br>下一行正文")
        assertInlineCardDoesNotOverlap("上方正文")
        assertTouchTargetFitsCard()
        saveScreenshot("inline-lines")
        rule.onNodeWithContentDescription("显示解码内容").performClick()
        assertInlineCardDoesNotOverlap("上方正文")
        saveScreenshot("inline-lines-decoded")
    }

    @Test fun inlineCard_reservesHeightAfterSoftWrapping() {
        showParagraph("一些很长的前置正文，需要让短块自动换行 ${encode("reader@demo.test")} 后面的正文也应该完整显示")
        assertInlineCardDoesNotOverlap("一些很长的前置正文")
        assertTouchTargetFitsCard()
        saveScreenshot("soft-wrapped")
    }

    @Test fun largeFont_keepsActualCardAndTouchBoundsInsideLine() {
        showParagraph("上方正文<br>${encode("demo mail")}<br>下一行正文", fontScale = 2f)
        assertInlineCardDoesNotOverlap("上方正文")
        assertTouchTargetFitsCard()
        saveScreenshot("large-font")
        rule.onNodeWithContentDescription("显示解码内容").performClick()
        assertInlineCardDoesNotOverlap("上方正文")
        assertTouchTargetFitsCard(expanded = true)
        saveScreenshot("large-font-decoded")
    }

    @Test fun longToken_staysBlockWhenDecodedAndKeepsExactlyOneAuthoredBlankLine() {
        val decoded = "原位显示的中文内容"
        val html = "<p>前面的正文<br><br>${encode(decoded)}<br><br>后面的正文</p>"
        rule.setContent { MaterialTheme { HtmlText(html, contentKey = "reply:block", modifier = Modifier.width(360.dp)) } }
        val before = cardBounds()
        val prefix = rule.onNodeWithText("前面的正文", substring = true).fetchSemanticsNode().boundsInRoot
        assertThat(before.top - prefix.bottom).isWithin(6f).of(22f)
        assertThat(allLayouts().sumOf { it.placeholderRects.size }).isEqualTo(0)
        saveScreenshot("long-block")
        repeat(3) {
            rule.onNodeWithContentDescription("显示解码内容").performClick()
            val after = cardBounds()
            assertThat(after.top).isEqualTo(before.top)
            assertThat(after.width).isLessThan(before.width)
            assertThat(allLayouts().sumOf { it.placeholderRects.size }).isEqualTo(0)
            val suffix = rule.onNodeWithText("后面的正文", substring = true).fetchSemanticsNode().boundsInRoot
            assertThat(suffix.top - after.bottom).isWithin(6f).of(22f)
            assertTouchTargetFitsCard(expanded = true)
            if (it == 0) saveScreenshot("long-block-decoded")
            rule.onNodeWithContentDescription("隐藏解码内容").performClick()
        }
    }

    @Test fun multilineDecodedText_changesHeightWithoutAddingAnExtraDetailsArea() {
        val decoded = "第一行\n第二行"
        val html = "<p>前面的正文<br>${encode(decoded)}<br>后面的正文</p>"
        rule.setContent { MaterialTheme { HtmlText(html, contentKey = "reply:multiline", modifier = Modifier.width(360.dp)) } }
        val before = cardBounds()
        assertThat(allLayouts().sumOf { it.placeholderRects.size }).isEqualTo(0)
        rule.onNodeWithContentDescription("显示解码内容").performClick()
        val after = cardBounds()
        assertThat(after.top).isEqualTo(before.top)
        assertThat(after.height).isGreaterThan(before.height)
        assertThat(allLayouts().sumOf { it.placeholderRects.size }).isEqualTo(0)
        assertThat(rule.onNodeWithText("后面的正文", substring = true).fetchSemanticsNode().boundsInRoot.top)
            .isAtLeast(after.bottom)
        saveScreenshot("multiline-decoded")
    }

    @Test fun shortCard_wrapsContentAndDecodedEmailUsesBodyFontWithIndependentLink() {
        val decoded = "reader123@demo.test"
        val encoded = encode(decoded)
        val opened = mutableListOf<String>()
        val content = paragraph("上方正文<br>$encoded<br>后面的正文")
        rule.setContent { MaterialTheme { RichContentText(content, Modifier.width(360.dp), onUrlClick = { opened += it }) } }
        assertThat(cardBounds().width).isLessThan(360f)
        assertThat(allLayouts().single { it.layoutInput.text.text == encoded }.layoutInput.style.fontFamily)
            .isEqualTo(FontFamily.Monospace)
        rule.onNodeWithContentDescription("显示解码内容").performClick()
        assertThat(allLayouts().single { it.layoutInput.text.text == decoded }.layoutInput.style.fontFamily)
            .isNotEqualTo(FontFamily.Monospace)
        assertThat(allLayouts().sumOf { it.placeholderRects.size }).isEqualTo(1)
        assertThat(opened).isEmpty()
        rule.onNodeWithText(decoded, useUnmergedTree = true).performTouchInput { click(center) }
        assertThat(opened).containsExactly("mailto:$decoded")
        saveScreenshot("email-body-font")
    }

    @Test fun replyGallery_rendersBothThemesAndStableToggles() {
        val dark = mutableStateOf(false)
        rule.setContent {
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                Surface {
                    Column(Modifier.width(360.dp).padding(12.dp)) {
                        listOf(
                            "邮箱地址（请使用 base64 方式解码）<br><br>${encode("reader123@demo.test")}<br><br>谢谢楼主",
                            "${encode("reader456@demo.test")}<br>感谢 OP！",
                            "求个码，感谢 OP<br>${encode("reader@demo.test")}",
                            "${encode("原位显示的中文内容")}<br><br>感谢",
                        ).forEachIndexed { index, html ->
                            Text("回复 ${listOf(13, 15, 16, 17)[index]}", modifier = Modifier.padding(top = 8.dp))
                            HtmlText(html, contentKey = "gallery:$index")
                            HorizontalDivider(Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
        saveScreenshot("reply-gallery-light")
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("显示解码内容")))[0]
            .performClick()
        saveScreenshot("reply-gallery-light-decoded")
        rule.onNodeWithContentDescription("隐藏解码内容").performClick()
        rule.runOnIdle { dark.value = true }
        saveScreenshot("reply-gallery-dark")
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("显示解码内容")))[0]
            .performClick()
        saveScreenshot("reply-gallery-dark-decoded")
    }

    private fun showParagraph(html: String, fontScale: Float = 1f) {
        val content = paragraph(html)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                MaterialTheme { RichContentText(content, Modifier.width(360.dp)) }
            }
        }
    }

    private fun paragraph(html: String) = (RichContentParser.parse(html).blocks.single() as RichContentBlock.Paragraph).content

    private fun assertInlineCardDoesNotOverlap(prefix: String) {
        val results = mutableListOf<TextLayoutResult>()
        val textNode = rule.onNodeWithText(prefix, substring = true)
        textNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        val card = requireNotNull(layout.placeholderRects.single())
        val line = layout.getLineForOffset(layout.layoutInput.text.text.indexOf("Base64"))
        if (line > 0) assertThat(card.top).isAtLeast(layout.getLineBottom(line - 1))
        if (line < layout.lineCount - 1) assertThat(card.bottom).isAtMost(layout.getLineTop(line + 1))
        val actual = cardBounds()
        val textBounds = textNode.fetchSemanticsNode().boundsInRoot
        assertThat(actual.top - textBounds.top).isWithin(1f).of(card.top)
        assertThat(actual.bottom - textBounds.top).isWithin(1f).of(card.bottom)
    }

    private fun cardBounds() = rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))
        .fetchSemanticsNode().boundsInRoot

    private fun assertTouchTargetFitsCard(expanded: Boolean = false) {
        val action = rule.onNodeWithContentDescription(if (expanded) "隐藏解码内容" else "显示解码内容")
            .fetchSemanticsNode().boundsInRoot
        val card = cardBounds()
        assertThat(action.width).isAtLeast(48f)
        assertThat(action.height).isAtLeast(48f)
        assertThat(action.top).isAtLeast(card.top)
        assertThat(action.bottom).isAtMost(card.bottom)
    }

    private fun allLayouts(): List<TextLayoutResult> {
        val nodes = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        val results = mutableListOf<TextLayoutResult>()
        rule.runOnIdle { nodes.forEach { it.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) } }
        return results
    }

    private fun saveScreenshot(name: String) {
        val output = File("build/outputs/layout-screenshots/$name.png")
        requireNotNull(output.parentFile).mkdirs()
        output.outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun encode(text: String) = Base64.getEncoder().encodeToString(text.toByteArray())
}
