package app.mystery0.nodeflow.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import app.mystery0.nodeflow.core.parser.decodeReadableBase64
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
    val base64: List<RichInline.Base64Text>,
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
    val base64 = mutableListOf<RichInline.Base64Text>()
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
            is RichInline.Base64Text -> {
                base64 += inline
                text.append(INLINE_IMAGE_REPLACEMENT)
            }
            RichInline.LineBreak -> text.append('\n')
            is RichInline.InlineImage -> {
                val id = "rich-inline-image-${images.size}"
                images += RichInlineImagePlan(id = id, offset = text.length, image = inline.image)
                text.append(INLINE_IMAGE_REPLACEMENT)
            }
        }
    }
    return RichTextPlan(text.toString(), ranges, images, base64)
}

internal sealed interface RichTextChunk {
    data class Inline(val content: List<RichInline>) : RichTextChunk
    data class Block(val token: RichInline.Base64Text) : RichTextChunk
}

/** 同时检查原文和明文，只允许容器尺寸/字体变化改变模式，不随眼睛开闭切分段落。 */
internal fun canPlaceBase64Inline(
    encoded: String,
    decoded: String?,
    encodedWidthPx: Int,
    decodedWidthPx: Int,
    availableWidthPx: Int,
): Boolean = decoded != null &&
    listOf(encoded, decoded).none { text -> text.any { it == '\n' || it == '\r' || it == '\t' } } &&
    maxOf(encodedWidthPx, decodedWidthPx) <= availableWidthPx

/** 块级降级自带换行，不能再把相邻 <br> 作为 Text 的首尾空行重复渲染。 */
internal fun splitRichTextChunks(content: List<RichInline>, inlineKeys: Set<String>): List<RichTextChunk> {
    if (content.none { it is RichInline.Base64Text && it.key !in inlineKeys }) {
        return listOf(RichTextChunk.Inline(content))
    }
    val chunks = mutableListOf<RichTextChunk>()
    val line = mutableListOf<RichInline>()
    fun flushLine() {
        // 一个真正的空行仍保留，包含作者有意写出的连续、首尾换行。
        if (line.all { it is RichInline.Text && it.value.isBlank() }) {
            chunks += RichTextChunk.Inline(emptyList())
            line.clear()
            return
        }
        val pending = mutableListOf<RichInline>()
        fun flushInline() {
            if (pending.any { it !is RichInline.Text || it.value.isNotBlank() }) chunks += RichTextChunk.Inline(pending.toList())
            pending.clear()
        }
        line.forEach { inline ->
            if (inline is RichInline.Base64Text && inline.key !in inlineKeys) {
                flushInline()
                chunks += RichTextChunk.Block(inline)
            } else pending += inline
        }
        flushInline()
        line.clear()
    }
    content.forEach { if (it == RichInline.LineBreak) flushLine() else line += it }
    flushLine()
    return chunks
}

private data class Base64LayoutPlan(val inline: Boolean, val size: DpSize)

@Composable
internal fun RichContentText(
    content: List<RichInline>,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    alignment: RichTextAlignment? = null,
    onUrlClick: (String) -> Unit = {},
    onImageClick: (String) -> Unit = {},
) {
    val state = LocalBase64RevealState.current ?: remember { Base64RevealState() }
    if (content.none { it is RichInline.Base64Text }) {
        RichContentTextLayout(content, state, emptyMap(), modifier, style, alignment, onUrlClick, onImageClick)
        return
    }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val plans = content.filterIsInstance<RichInline.Base64Text>().associate { token ->
            val decoded = remember(token.encoded) { decodeReadableBase64(token.encoded) }
            val encodedStyle = base64TextStyle(token, expanded = false, base = style)
            val decodedStyle = base64TextStyle(token, expanded = true, base = style)
            fun measure(value: String, textStyle: TextStyle): DpSize {
                val measured = measurer.measure(AnnotatedString(value), style = textStyle, softWrap = false)
                return with(density) {
                    // Text、padding 和按钮与真实组件共用规格，舍入留两像素，不估算字数。
                    DpSize(
                        (measured.size.width + 2).toDp() + BASE64_TEXT_START_PADDING + BASE64_ACTION_SIZE,
                        maxOf(measured.size.height.toDp() + BASE64_TEXT_VERTICAL_PADDING * 2, BASE64_ACTION_SIZE),
                    )
                }
            }
            val originalSize = measure(token.encoded, encodedStyle)
            val decodedSize = measure(decoded.orEmpty(), decodedStyle)
            val inline = with(density) {
                canPlaceBase64Inline(token.encoded, decoded, originalSize.width.roundToPx(),
                    decodedSize.width.roundToPx(), maxWidth.roundToPx())
            }
            val currentSize = if (state.decoded(token) == null) originalSize else decodedSize
            token.key to Base64LayoutPlan(inline, currentSize.copy(width = minOf(currentSize.width, maxWidth)))
        }
        val inlineSizes = plans.filterValues { it.inline }.mapValues { it.value.size }
        Column(Modifier.fillMaxWidth()) {
            splitRichTextChunks(content, inlineSizes.keys).forEach { chunk ->
                when (chunk) {
                    is RichTextChunk.Inline -> RichContentTextLayout(
                        chunk.content, state, inlineSizes, Modifier.fillMaxWidth(), style, alignment, onUrlClick, onImageClick,
                    )
                    is RichTextChunk.Block -> Base64RevealContent(
                        chunk.token, state, onUrlClick,
                        Modifier.width(plans.getValue(chunk.token.key).size.width), bodyStyle = style,
                    )
                }
            }
        }
    }
}

@Composable
private fun RichContentTextLayout(
    content: List<RichInline>,
    state: Base64RevealState,
    sizes: Map<String, DpSize>,
    modifier: Modifier,
    style: TextStyle,
    alignment: RichTextAlignment?,
    onUrlClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val plan = remember(content) { buildRichTextPlan(content) }
    val annotated = remember(plan, style, colors, onUrlClick) {
        buildAnnotatedString {
            var imageIndex = 0
            content.forEachIndexed { index, inline ->
                when (inline) {
                    is RichInline.Text -> {
                        val spanStyle = inline.style.toComposeSpanStyle(
                            baseStyle = style,
                            defaultTextColor = colors.onSurface,
                            codeBackground = colors.surfaceVariant.copy(alpha = 0.55f),
                        )
                        if (inline.linkUrl != null) {
                            withLink(LinkAnnotation.Clickable(
                                tag = "rich-link-$index",
                                styles = TextLinkStyles(SpanStyle(
                                    color = colors.primary,
                                    textDecoration = TextDecoration.Underline,
                                )),
                                linkInteractionListener = { onUrlClick(inline.linkUrl) },
                            )) {
                                withStyle(spanStyle.copy(color = colors.primary)) { append(inline.value) }
                            }
                        } else withStyle(spanStyle) { append(inline.value) }
                    }
                    RichInline.LineBreak -> append('\n')
                    is RichInline.InlineImage -> appendInlineContent(
                        plan.inlineImages[imageIndex++].id, inline.image.alt ?: "图片",
                    )
                    is RichInline.Base64Text -> appendInlineContent(inline.key, "Base64")
                }
            }
        }
    }
    val inlineContent = buildMap {
        plan.inlineImages.forEach { imagePlan ->
            put(imagePlan.id, InlineTextContent(
                Placeholder(INLINE_IMAGE_EM.em, INLINE_IMAGE_EM.em, PlaceholderVerticalAlign.Center),
            ) {
                AsyncImage(
                    model = imagePlan.image.url,
                    contentDescription = imagePlan.image.alt,
                    modifier = Modifier.fillMaxSize().then(when {
                        imagePlan.image.linkUrl != null -> Modifier.clickable { onUrlClick(imagePlan.image.linkUrl) }
                        !imagePlan.image.compact -> Modifier.clickable { onImageClick(imagePlan.image.url) }
                        else -> Modifier
                    }),
                )
            })
        }
        plan.base64.forEach { token ->
            val size = sizes.getValue(token.key)
            put(token.key, InlineTextContent(
                with(density) { Placeholder(size.width.toSp(), size.height.toSp(), PlaceholderVerticalAlign.Center) },
            ) {
                Base64RevealContent(token, state, onUrlClick, Modifier.fillMaxSize(), singleLine = true, bodyStyle = style)
            })
        }
    }
    Text(
        text = annotated,
        inlineContent = inlineContent,
        modifier = modifier,
        // 当前 Compose 的固定行高 span 会复用首行 metrics，盖过后续高占位的 metrics。
        // 含真实组件的段落使用自然行高，让每一行按占位的实际高度排版；不缩小文字。
        style = if (plan.base64.isEmpty()) style else style.copy(lineHeight = TextUnit.Unspecified, lineHeightStyle = null),
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
