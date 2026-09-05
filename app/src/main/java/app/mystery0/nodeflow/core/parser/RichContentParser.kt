package app.mystery0.nodeflow.core.parser

import app.mystery0.nodeflow.core.link.ImageHostMatcher
import app.mystery0.nodeflow.core.model.RichBaseline
import app.mystery0.nodeflow.core.model.RichContentBlock
import app.mystery0.nodeflow.core.model.RichContentDocument
import app.mystery0.nodeflow.core.model.RichEmbed
import app.mystery0.nodeflow.core.model.RichImage
import app.mystery0.nodeflow.core.model.RichInline
import app.mystery0.nodeflow.core.model.RichInlineStyle
import app.mystery0.nodeflow.core.model.RichListItem
import app.mystery0.nodeflow.core.model.RichTableCell
import app.mystery0.nodeflow.core.model.RichTableRow
import app.mystery0.nodeflow.core.model.RichTextAlignment
import app.mystery0.nodeflow.core.model.RichVideo
import app.mystery0.nodeflow.core.model.RichVideoSource
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.net.URI
import kotlin.math.ceil

/** 将 V2EX 正文 HTML 转换为安全、可测试的原生富文本模型。 */
object RichContentParser {
    fun parse(
        html: String,
        customImageHosts: Collection<String> = emptySet(),
    ): RichContentDocument {
        if (html.isBlank()) return RichContentDocument(emptyList())
        return runCatching {
            val document = Jsoup.parseBodyFragment(html, V2EX_BASE_URL)
            document.select(DROPPED_TAGS.joinToString(",")).remove()
            document.body().linkifyPlainV2exTopicLinks()
            BlockCollector(customImageHosts).parse(document.body().childNodes())
        }.getOrElse {
            val plainText = Jsoup.parseBodyFragment(html).text().trim()
            RichContentDocument(
                plainText.takeIf(String::isNotEmpty)
                    ?.let { listOf(RichContentBlock.Paragraph(listOf(RichInline.Text(it)))) }
                    .orEmpty(),
            )
        }
    }
}

private class BlockCollector(
    private val customImageHosts: Collection<String>,
) {
    private val blocks = mutableListOf<RichContentBlock>()
    private val inline = mutableListOf<RichInline>()
    private var paragraphAlignment: RichTextAlignment? = null

    fun parse(nodes: List<Node>): RichContentDocument {
        parseNodes(nodes, InlineContext())
        flushParagraph()
        return RichContentDocument(blocks.toList())
    }

    private fun parseNodes(nodes: List<Node>, context: InlineContext) {
        nodes.forEach { node ->
            when (node) {
                is TextNode -> appendText(node.wholeText, context)
                is Element -> parseElement(node, context)
            }
        }
    }

    private fun parseElement(element: Element, context: InlineContext) {
        val tag = element.normalName()
        when {
            tag in DROPPED_TAGS -> Unit
            tag == "br" -> inline += RichInline.LineBreak
            tag == "img" -> emitImage(element, context.linkUrl)
            tag in INLINE_TAGS -> parseInlineElement(element, context)
            tag in HEADING_TAGS -> emitHeading(element, tag.removePrefix("h").toIntOrNull() ?: 6)
            tag == "blockquote" -> emitQuote(element)
            tag == "ul" || tag == "ol" -> emitList(element, ordered = tag == "ol")
            tag == "pre" -> emitCodeBlock(element)
            tag == "table" -> emitTable(element)
            tag == "video" -> emitVideo(element)
            tag == "iframe" -> emitIframe(element)
            tag == "hr" -> {
                flushParagraph()
                blocks += RichContentBlock.Divider
            }
            tag in CONTAINER_TAGS -> {
                flushParagraph()
                val previousAlignment = paragraphAlignment
                paragraphAlignment = element.textAlignment() ?: previousAlignment
                parseNodes(element.childNodes(), context)
                flushParagraph()
                paragraphAlignment = previousAlignment
            }
            else -> parseNodes(element.childNodes(), context)
        }
    }

    private fun parseInlineElement(element: Element, context: InlineContext) {
        val tag = element.normalName()
        val nextContext = context.copy(
            style = context.style.withElementStyle(element),
            linkUrl = if (tag == "a") element.safeUrl("href", allowMailTo = true) else context.linkUrl,
        )
        parseNodes(element.childNodes(), nextContext)
        if (
            tag == "a" &&
            element.selectFirst("img") == null &&
            nextContext.linkUrl != null &&
            ImageHostMatcher.shouldLoadAsImage(nextContext.linkUrl, customImageHosts)
        ) {
            flushParagraph()
            blocks += RichContentBlock.Image(
                RichImage(
                    url = nextContext.linkUrl,
                    alt = element.text().trim().takeIf(String::isNotEmpty),
                    linkUrl = nextContext.linkUrl,
                ),
            )
        }
    }

    private fun emitHeading(element: Element, level: Int) {
        flushParagraph()
        val content = parseInlineContent(element.childNodes(), InlineContext())
        if (content.isNotEmpty()) blocks += RichContentBlock.Heading(level.coerceIn(1, 6), content)
    }

    private fun emitQuote(element: Element) {
        flushParagraph()
        val content = BlockCollector(customImageHosts).parse(element.childNodes()).blocks
        if (content.isNotEmpty()) blocks += RichContentBlock.Quote(content)
    }

    private fun emitList(element: Element, ordered: Boolean) {
        flushParagraph()
        val items = element.children()
            .filter { it.normalName() == "li" }
            .mapNotNull { item ->
                BlockCollector(customImageHosts)
                    .parse(item.childNodes())
                    .blocks
                    .takeIf(List<RichContentBlock>::isNotEmpty)
                    ?.let(::RichListItem)
            }
        if (items.isNotEmpty()) {
            blocks += RichContentBlock.ListBlock(
                ordered = ordered,
                start = element.attr("start").toIntOrNull()?.coerceAtLeast(1) ?: 1,
                items = items,
            )
        }
    }

    private fun emitCodeBlock(element: Element) {
        flushParagraph()
        val code = element.wholeText().trimEnd()
        if (code.isNotEmpty()) blocks += RichContentBlock.CodeBlock(code)
    }

    private fun emitTable(element: Element) {
        flushParagraph()
        val caption = element.children()
            .firstOrNull { it.normalName() == "caption" }
            ?.let { parseInlineContent(it.childNodes(), InlineContext()) }
            .orEmpty()
        val rows = element.select("tr")
            .filter { row -> row.parents().firstOrNull { it.normalName() == "table" } == element }
            .mapNotNull { row ->
                row.children()
                    .filter { it.normalName() == "th" || it.normalName() == "td" }
                    .map { cell ->
                        RichTableCell(
                            blocks = BlockCollector(customImageHosts).parse(cell.childNodes()).blocks,
                            isHeader = cell.normalName() == "th",
                            colSpan = cell.attr("colspan").toIntOrNull()?.coerceIn(1, MAX_TABLE_SPAN) ?: 1,
                            rowSpan = cell.attr("rowspan").toIntOrNull()?.coerceIn(1, MAX_TABLE_SPAN) ?: 1,
                        )
                    }
                    .takeIf(List<RichTableCell>::isNotEmpty)
                    ?.let(::RichTableRow)
            }
        if (rows.isNotEmpty()) blocks += RichContentBlock.Table(caption = caption, rows = rows)
    }

    private fun emitVideo(element: Element) {
        flushParagraph()
        val ownSource = element.safeUrl("src")?.let {
            RichVideoSource(it, element.normalizedMimeType())
        }
        val childSources = element.select("source[src]").mapNotNull { source ->
            source.safeUrl("src")?.let { url ->
                RichVideoSource(url, source.normalizedMimeType())
            }
        }
        val sources = listOfNotNull(ownSource).plus(childSources).distinctBy(RichVideoSource::url)
        blocks += RichContentBlock.Video(
            RichVideo(
                sources = sources,
                posterUrl = element.safeUrl("poster"),
                title = element.attr("title").trim().takeIf(String::isNotEmpty),
            ),
        )
    }

    private fun emitIframe(element: Element) {
        flushParagraph()
        blocks += RichContentBlock.IframePlaceholder(
            RichEmbed(
                url = element.safeUrl("src"),
                title = element.attr("title").trim().takeIf(String::isNotEmpty),
            ),
        )
    }

    private fun emitImage(element: Element, linkUrl: String?) {
        val url = element.safeUrl("src") ?: return
        val width = element.imageDimension("width")
        val height = element.imageDimension("height")
        val compact = element.isCompactImage(width, height)
        val image = RichImage(
            url = url,
            alt = element.attr("alt").trim().takeIf(String::isNotEmpty),
            widthPx = width,
            heightPx = height,
            compact = compact,
            linkUrl = linkUrl,
        )
        if (compact) {
            inline += RichInline.InlineImage(image)
        } else {
            flushParagraph()
            blocks += RichContentBlock.Image(image)
        }
    }

    private fun parseInlineContent(nodes: List<Node>, context: InlineContext): List<RichInline> {
        val collector = BlockCollector(customImageHosts)
        collector.parseNodes(nodes, context)
        collector.flushParagraph()
        return collector.blocks
            .filterIsInstance<RichContentBlock.Paragraph>()
            .flatMapIndexed { index, paragraph ->
                if (index == 0) paragraph.content else listOf(RichInline.LineBreak) + paragraph.content
            }
    }

    private fun appendText(rawText: String, context: InlineContext) {
        val text = rawText.replace(WHITESPACE_REGEX, " ")
        if (text.isEmpty()) return
        inline += RichInline.Text(value = text, style = context.style, linkUrl = context.linkUrl)
    }

    private fun flushParagraph() {
        val content = inline.trimBoundaries()
        inline.clear()
        if (content.isNotEmpty()) blocks += RichContentBlock.Paragraph(content, paragraphAlignment)
    }
}

private data class InlineContext(
    val style: RichInlineStyle = RichInlineStyle(),
    val linkUrl: String? = null,
)

private fun RichInlineStyle.withElementStyle(element: Element): RichInlineStyle {
    val tag = element.normalName()
    val css = element.styleDeclarations()
    val fontWeight = css["font-weight"]
    val decoration = css["text-decoration"].orEmpty().lowercase()
    return copy(
        bold = bold || tag == "strong" || tag == "b" || fontWeight == "bold" || (fontWeight?.toIntOrNull() ?: 0) >= 600,
        italic = italic || tag in setOf("em", "i", "cite") || css["font-style"].equals("italic", true),
        underline = underline || tag == "u" || "underline" in decoration,
        strikethrough = strikethrough || tag in setOf("del", "s", "strike") || "line-through" in decoration,
        code = code || tag == "code" || tag == "tt",
        fontScale = (fontScale * element.fontScale(css["font-size"])).coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE),
        foregroundColor = css["color"] ?: element.attr("color").takeIf { tag == "font" && it.isNotBlank() } ?: foregroundColor,
        backgroundColor = css["background-color"] ?: backgroundColor,
        baseline = when (tag) {
            "sup" -> RichBaseline.Superscript
            "sub" -> RichBaseline.Subscript
            else -> baseline
        },
    )
}

private fun Element.fontScale(cssSize: String?): Float = when (normalName()) {
    "small" -> 0.85f
    "big" -> 1.15f
    else -> cssSize?.trim()?.lowercase()?.let { value ->
        when {
            value.endsWith("em") -> value.removeSuffix("em").toFloatOrNull()
            value.endsWith("%") -> value.removeSuffix("%").toFloatOrNull()?.div(100f)
            value.endsWith("px") -> value.removeSuffix("px").toFloatOrNull()?.div(DEFAULT_FONT_PX)
            else -> null
        }
    } ?: 1f
}

private fun Element.textAlignment(): RichTextAlignment? {
    val value = styleDeclarations()["text-align"]?.lowercase()
        ?: attr("align").lowercase().takeIf(String::isNotBlank)
    return when (value) {
        "center" -> RichTextAlignment.Center
        "right", "end" -> RichTextAlignment.End
        "left", "start" -> RichTextAlignment.Start
        else -> null
    }
}

private fun Element.styleDeclarations(): Map<String, String> =
    attr("style").split(';').mapNotNull { declaration ->
        val separator = declaration.indexOf(':')
        if (separator <= 0) return@mapNotNull null
        declaration.substring(0, separator).trim().lowercase()
            .takeIf(String::isNotEmpty)
            ?.let { it to declaration.substring(separator + 1).trim() }
    }.toMap()

private fun Element.normalizedMimeType(): String? =
    attr("type").substringBefore(';').trim().lowercase().takeIf(String::isNotEmpty)

private fun Element.safeUrl(attributeName: String, allowMailTo: Boolean = false): String? {
    val absolute = absUrl(attributeName).takeIf(String::isNotBlank)
        ?: attr(attributeName).trim().takeIf(String::isNotBlank)
        ?: return null
    val uri = runCatching { URI(absolute) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    val allowed = scheme == "http" || scheme == "https" || (allowMailTo && scheme == "mailto")
    return absolute.takeIf { allowed }
}

private fun Element.imageDimension(attributeName: String): Int? =
    attr(attributeName).toCssPixels()
        ?: CSS_DIMENSION_REGEX.findAll(attr("style"))
            .firstOrNull { it.groupValues.getOrNull(1)?.equals(attributeName, ignoreCase = true) == true }
            ?.groupValues?.getOrNull(2)?.toCssPixels()

private fun Element.isCompactImage(width: Int?, height: Int?): Boolean {
    val className = className().lowercase()
    val alt = attr("alt")
    val compactByClass = COMPACT_IMAGE_CLASS_HINTS.any(className::contains)
    val compactBySize = width != null && height != null && width <= COMPACT_SOURCE_MAX_PX && height <= COMPACT_SOURCE_MAX_PX
    val compactByAlt = alt.codePointCount(0, alt.length) in 1..2 && alt.any { !it.isLetterOrDigit() }
    return compactByClass || compactBySize || compactByAlt
}

private fun String.toCssPixels(): Int? =
    trim().lowercase().removeSuffix("px").toFloatOrNull()
        ?.takeIf { it > 0f }
        ?.let { ceil(it).toInt() }

private fun List<RichInline>.trimBoundaries(): List<RichInline> {
    if (isEmpty()) return this
    val result = toMutableList()
    while (result.firstOrNull() is RichInline.Text) {
        val first = result.first() as RichInline.Text
        val trimmed = first.value.trimStart()
        if (trimmed.isNotEmpty()) {
            result[0] = first.copy(value = trimmed)
            break
        }
        result.removeAt(0)
    }
    while (result.lastOrNull() is RichInline.Text) {
        val lastIndex = result.lastIndex
        val last = result[lastIndex] as RichInline.Text
        val trimmed = last.value.trimEnd()
        if (trimmed.isNotEmpty()) {
            result[lastIndex] = last.copy(value = trimmed)
            break
        }
        result.removeAt(lastIndex)
    }
    return result
}

private const val V2EX_BASE_URL = "https://www.v2ex.com/"
private const val MAX_TABLE_SPAN = 100
private const val COMPACT_SOURCE_MAX_PX = 96
private const val DEFAULT_FONT_PX = 16f
private const val MIN_FONT_SCALE = 0.7f
private const val MAX_FONT_SCALE = 1.8f
private val WHITESPACE_REGEX = Regex("""[\t\n\r\f ]+""")
private val CSS_DIMENSION_REGEX = Regex("""(?i)(width|height)\s*:\s*([0-9.]+)px""")
private val HEADING_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
private val INLINE_TAGS = setOf(
    "a", "abbr", "b", "big", "cite", "code", "del", "em", "font", "i", "mark", "s", "small",
    "span", "strike", "strong", "sub", "sup", "tt", "u",
)
private val CONTAINER_TAGS = setOf(
    "address", "article", "aside", "dd", "div", "dl", "dt", "figcaption", "figure", "footer", "header",
    "main", "nav", "p", "section",
)
private val DROPPED_TAGS = setOf(
    "script", "style", "object", "embed", "form", "input", "button", "canvas", "noscript", "head",
)
private val COMPACT_IMAGE_CLASS_HINTS = listOf("emoji", "emoticon", "smilie", "smiley")
