package app.mystery0.nodeflow.core.model

/** 测试断言使用的文本投影，不参与生产渲染。 */
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
