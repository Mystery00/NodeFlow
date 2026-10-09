package app.mystery0.nodeflow.core.model

/** 与具体 UI 框架无关的富文本正文。 */
data class RichContentDocument(
    val blocks: List<RichContentBlock>,
)

sealed interface RichContentBlock {
    data class Paragraph(
        val content: List<RichInline>,
        val alignment: RichTextAlignment? = null,
    ) : RichContentBlock

    data class Heading(val level: Int, val content: List<RichInline>) : RichContentBlock
    data class Quote(val blocks: List<RichContentBlock>) : RichContentBlock
    data class ListBlock(
        val ordered: Boolean,
        val start: Int = 1,
        val items: List<RichListItem>,
    ) : RichContentBlock
    data class CodeBlock(val code: String) : RichContentBlock
    data class Table(
        val caption: List<RichInline> = emptyList(),
        val rows: List<RichTableRow>,
    ) : RichContentBlock
    data class Image(val image: RichImage) : RichContentBlock
    data class Video(val video: RichVideo) : RichContentBlock
    data class IframePlaceholder(val embed: RichEmbed) : RichContentBlock
    data object Divider : RichContentBlock
}

sealed interface RichInline {
    data class Text(
        val value: String,
        val style: RichInlineStyle = RichInlineStyle(),
        val linkUrl: String? = null,
    ) : RichInline
    /** 可原位切换的编码片段；key 与来源及原文绑定，明文不进入领域模型。 */
    data class Base64Text(
        val encoded: String,
        val key: String,
        val style: RichInlineStyle = RichInlineStyle(),
    ) : RichInline
    data object LineBreak : RichInline
    data class InlineImage(val image: RichImage) : RichInline
}

data class RichInlineStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val code: Boolean = false,
    val fontScale: Float = 1f,
    val foregroundColor: String? = null,
    val backgroundColor: String? = null,
    val baseline: RichBaseline = RichBaseline.Normal,
)

enum class RichBaseline { Normal, Superscript, Subscript }
enum class RichTextAlignment { Start, Center, End }

data class RichImage(
    val url: String,
    val alt: String? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val compact: Boolean = false,
    val linkUrl: String? = null,
)

data class RichVideoSource(val url: String, val mimeType: String? = null)

data class RichVideo(
    val sources: List<RichVideoSource>,
    val posterUrl: String? = null,
    val title: String? = null,
    val openUrl: String? = sources.firstOrNull()?.url,
)

data class RichEmbed(val url: String?, val title: String? = null)
data class RichListItem(val blocks: List<RichContentBlock>)
data class RichTableRow(val cells: List<RichTableCell>)
data class RichTableCell(
    val blocks: List<RichContentBlock>,
    val isHeader: Boolean = false,
    val colSpan: Int = 1,
    val rowSpan: Int = 1,
)

fun RichContentDocument.plainText(): String = blocks.joinToString("\n") { it.plainText() }.trim()

fun RichContentBlock.plainText(): String = when (this) {
    is RichContentBlock.Paragraph -> content.plainText()
    is RichContentBlock.Heading -> content.plainText()
    is RichContentBlock.Quote -> blocks.joinToString("\n") { it.plainText() }
    is RichContentBlock.ListBlock -> items.joinToString("\n") { item ->
        item.blocks.joinToString("\n") { it.plainText() }
    }
    is RichContentBlock.CodeBlock -> code
    is RichContentBlock.Table -> rows.joinToString("\n") { row ->
        row.cells.joinToString("\t") { cell -> cell.blocks.joinToString(" ") { it.plainText() } }
    }
    is RichContentBlock.Image -> image.alt.orEmpty()
    is RichContentBlock.Video -> video.title.orEmpty()
    is RichContentBlock.IframePlaceholder -> embed.title.orEmpty()
    RichContentBlock.Divider -> ""
}

private fun List<RichInline>.plainText(): String = joinToString("") { inline ->
    when (inline) {
        is RichInline.Text -> inline.value
        is RichInline.Base64Text -> inline.encoded
        is RichInline.InlineImage -> inline.image.alt.orEmpty()
        RichInline.LineBreak -> "\n"
    }
}

/** 供旧回复渲染器选择混排路径，递归包含引用、列表和表格。 */
fun RichContentDocument.containsBase64(): Boolean = blocks.any { it.containsBase64() }

private fun RichContentBlock.containsBase64(): Boolean = when (this) {
    is RichContentBlock.Paragraph -> content.any { it is RichInline.Base64Text }
    is RichContentBlock.Heading -> content.any { it is RichInline.Base64Text }
    is RichContentBlock.Quote -> blocks.any { it.containsBase64() }
    is RichContentBlock.ListBlock -> items.any { it.blocks.any { block -> block.containsBase64() } }
    is RichContentBlock.Table -> caption.any { it is RichInline.Base64Text } ||
        rows.any { row -> row.cells.any { cell -> cell.blocks.any { it.containsBase64() } } }
    else -> false
}
