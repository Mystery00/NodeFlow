package app.mystery0.nodeflow.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.mystery0.nodeflow.core.model.RichBaseline
import app.mystery0.nodeflow.core.model.RichImage
import app.mystery0.nodeflow.core.model.RichInline
import app.mystery0.nodeflow.core.model.RichInlineStyle
import app.mystery0.nodeflow.core.model.RichTextAlignment
import coil.compose.AsyncImage
import kotlin.math.roundToInt

internal data class RichTextPlan(
    val text: String,
    val ranges: List<RichTextRange>,
    val inlineImages: List<RichInlineImagePlan>,
)

internal data class RichTextRange(
    val value: String,
    val start: Int,
    val end: Int,
    val style: RichInlineStyle,
    val linkUrl: String?,
)

internal data class RichInlineImagePlan(
    val id: String,
    val offset: Int,
    val image: RichImage,
)

internal fun buildRichTextPlan(content: List<RichInline>): RichTextPlan {
    val text = StringBuilder()
    val ranges = mutableListOf<RichTextRange>()
    val images = mutableListOf<RichInlineImagePlan>()
    content.forEach { inline ->
        when (inline) {
            is RichInline.Text -> {
                val start = text.length
                text.append(inline.value)
                ranges += RichTextRange(
                    value = inline.value,
                    start = start,
                    end = text.length,
                    style = inline.style,
                    linkUrl = inline.linkUrl,
                )
            }
            RichInline.LineBreak -> text.append('\n')
            is RichInline.InlineImage -> {
                val id = "rich-inline-image-${images.size}"
                images += RichInlineImagePlan(id = id, offset = text.length, image = inline.image)
                text.append(INLINE_IMAGE_REPLACEMENT)
            }
        }
    }
    return RichTextPlan(text.toString(), ranges, images)
}

@Composable
internal fun RichContentText(
    content: List<RichInline>,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    alignment: RichTextAlignment? = null,
    onUrlClick: (String) -> Unit = {},
    onImageClick: (String) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val plan = remember(content) { buildRichTextPlan(content) }
    val annotated = remember(plan, style, colors, onUrlClick) {
        buildAnnotatedString {
            var rangeIndex = 0
            var imageIndex = 0
            var offset = 0
            content.forEach { inline ->
                when (inline) {
                    is RichInline.Text -> {
                        val range = plan.ranges[rangeIndex++]
                        val spanStyle = range.style.toComposeSpanStyle(
                            baseStyle = style,
                            defaultTextColor = colors.onSurface,
                            codeBackground = colors.surfaceVariant.copy(alpha = 0.55f),
                        )
                        val appendText: AnnotatedString.Builder.() -> Unit = {
                            withStyle(spanStyle) { append(range.value) }
                        }
                        if (range.linkUrl != null) {
                            withLink(
                                LinkAnnotation.Clickable(
                                    tag = "rich-link-$offset",
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                            color = colors.primary,
                                            textDecoration = TextDecoration.Underline,
                                        ),
                                    ),
                                    linkInteractionListener = { onUrlClick(range.linkUrl) },
                                ),
                                block = appendText,
                            )
                        } else {
                            appendText()
                        }
                        offset += range.value.length
                    }
                    RichInline.LineBreak -> {
                        append('\n')
                        offset += 1
                    }
                    is RichInline.InlineImage -> {
                        appendInlineContent(plan.inlineImages[imageIndex++].id, inline.image.alt ?: "图片")
                        offset += 1
                    }
                }
            }
        }
    }
    val inlineContent = remember(plan.inlineImages, onUrlClick, onImageClick) {
        plan.inlineImages.associate { imagePlan ->
            imagePlan.id to InlineTextContent(
                placeholder = Placeholder(
                    width = INLINE_IMAGE_EM.em,
                    height = INLINE_IMAGE_EM.em,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ),
            ) {
                AsyncImage(
                    model = imagePlan.image.url,
                    contentDescription = imagePlan.image.alt,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            when {
                                imagePlan.image.linkUrl != null -> Modifier.clickable {
                                    onUrlClick(imagePlan.image.linkUrl)
                                }
                                !imagePlan.image.compact -> Modifier.clickable {
                                    onImageClick(imagePlan.image.url)
                                }
                                else -> Modifier
                            },
                        ),
                )
            }
        }
    }
    Text(
        text = annotated,
        inlineContent = inlineContent,
        modifier = modifier,
        style = style,
        textAlign = when (alignment) {
            RichTextAlignment.Center -> TextAlign.Center
            RichTextAlignment.End -> TextAlign.End
            RichTextAlignment.Start -> TextAlign.Start
            null -> TextAlign.Unspecified
        },
    )
}

private fun RichInlineStyle.toComposeSpanStyle(
    baseStyle: TextStyle,
    defaultTextColor: Color,
    codeBackground: Color,
): SpanStyle {
    val decorations = buildList {
        if (underline) add(TextDecoration.Underline)
        if (strikethrough) add(TextDecoration.LineThrough)
    }
    val baseFontSize = baseStyle.fontSize.takeIf { it.isSp } ?: 16.sp
    return SpanStyle(
        color = foregroundColor?.let(::parseRichCssColor)?.let { Color(it.toInt()) } ?: defaultTextColor,
        background = backgroundColor?.let(::parseRichCssColor)?.let { Color(it.toInt()) }
            ?: if (code) codeBackground else Color.Unspecified,
        fontSize = baseFontSize * fontScale,
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
        fontFamily = if (code) FontFamily.Monospace else null,
        textDecoration = decorations.takeIf(List<TextDecoration>::isNotEmpty)
            ?.let(TextDecoration::combine),
        baselineShift = when (baseline) {
            RichBaseline.Superscript -> BaselineShift.Superscript
            RichBaseline.Subscript -> BaselineShift.Subscript
            RichBaseline.Normal -> null
        },
    )
}

internal fun parseRichCssColor(value: String): Long? {
    val normalized = value.trim().lowercase()
    if (normalized == "transparent" || normalized.any { it !in SAFE_COLOR_CHARS }) return null
    NAMED_COLORS[normalized]?.let { return it }
    if (normalized.startsWith('#')) return parseHexColor(normalized)
    val rgb = RGB_COLOR_REGEX.matchEntire(normalized) ?: return null
    val red = rgb.groupValues[1].toIntOrNull()?.coerceIn(0, 255) ?: return null
    val green = rgb.groupValues[2].toIntOrNull()?.coerceIn(0, 255) ?: return null
    val blue = rgb.groupValues[3].toIntOrNull()?.coerceIn(0, 255) ?: return null
    val alpha = rgb.groupValues[4]
        .takeIf(String::isNotBlank)
        ?.toFloatOrNull()
        ?.coerceIn(0f, 1f)
        ?.times(255f)
        ?.roundToInt()
        ?: 255
    return argb(alpha, red, green, blue)
}

private fun parseHexColor(value: String): Long? {
    val hex = value.removePrefix("#")
    val expanded = when (hex.length) {
        3 -> "ff" + hex.flatMap { listOf(it, it) }.joinToString("")
        4 -> {
            val rgba = hex.flatMap { listOf(it, it) }.joinToString("")
            rgba.takeLast(2) + rgba.dropLast(2)
        }
        6 -> "ff$hex"
        8 -> hex.takeLast(2) + hex.dropLast(2)
        else -> return null
    }
    return expanded.toLongOrNull(16)?.takeIf { it in 0..0xffffffffL }
}

private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Long =
    ((alpha.toLong() and 0xff) shl 24) or
        ((red.toLong() and 0xff) shl 16) or
        ((green.toLong() and 0xff) shl 8) or
        (blue.toLong() and 0xff)

private const val INLINE_IMAGE_REPLACEMENT = '\uFFFC'
private const val INLINE_IMAGE_EM = 1.2f
private val SAFE_COLOR_CHARS = ('a'..'z') + ('0'..'9') + setOf('#', '(', ')', ',', '.', ' ', '%')
private val RGB_COLOR_REGEX = Regex("""rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})(?:\s*,\s*(0(?:\.\d+)?|1(?:\.0+)?))?\s*\)""")
private val NAMED_COLORS = mapOf(
    "black" to 0xff000000,
    "white" to 0xffffffff,
    "red" to 0xffff0000,
    "green" to 0xff008000,
    "blue" to 0xff0000ff,
    "gray" to 0xff808080,
    "grey" to 0xff808080,
    "yellow" to 0xffffff00,
    "orange" to 0xffffa500,
    "purple" to 0xff800080,
)
