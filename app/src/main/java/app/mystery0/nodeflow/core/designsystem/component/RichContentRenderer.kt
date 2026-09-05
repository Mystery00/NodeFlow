package app.mystery0.nodeflow.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.mystery0.nodeflow.core.model.RichContentBlock
import app.mystery0.nodeflow.core.model.RichContentDocument
import app.mystery0.nodeflow.core.model.RichEmbed
import app.mystery0.nodeflow.R
import androidx.compose.ui.res.stringResource

/** 使用 Compose 原生组件渲染已经清洗、结构化的正文。 */
@Composable
internal fun RichContent(
    document: RichContentDocument,
    modifier: Modifier = Modifier,
    onUrlClick: (String) -> Boolean = { false },
    onImageClick: (String) -> Unit = {},
    imageSizeCache: RichContentImageSizeCache = rememberRichContentImageSizeCache(document),
) {
    RichContentBlocks(
        blocks = document.blocks,
        onUrlClick = onUrlClick,
        onImageClick = onImageClick,
        imageSizeCache = imageSizeCache,
        modifier = modifier,
    )
}

@Composable
internal fun RichContentBlocks(
    blocks: List<RichContentBlock>,
    onUrlClick: (String) -> Boolean,
    onImageClick: (String) -> Unit,
    imageSizeCache: RichContentImageSizeCache,
    modifier: Modifier = Modifier,
    insideTable: Boolean = false,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(BLOCK_SPACING),
    ) {
        blocks.forEach { block ->
            RichContentBlockView(
                block = block,
                onUrlClick = onUrlClick,
                onImageClick = onImageClick,
                imageSizeCache = imageSizeCache,
                insideTable = insideTable,
            )
        }
    }
}

@Composable
internal fun RichContentBlockView(
    block: RichContentBlock,
    onUrlClick: (String) -> Boolean,
    onImageClick: (String) -> Unit,
    imageSizeCache: RichContentImageSizeCache,
    modifier: Modifier = Modifier,
    insideTable: Boolean = false,
) {
    val uriHandler = LocalUriHandler.current
    val openUrl: (String) -> Unit = { url ->
        if (!onUrlClick(url)) runCatching { uriHandler.openUri(url) }
    }
    when (block) {
        is RichContentBlock.Paragraph -> SelectionContainer {
            RichContentText(
                content = block.content,
                alignment = block.alignment,
                onUrlClick = openUrl,
                onImageClick = onImageClick,
                modifier = modifier.fillMaxWidth(),
            )
        }
        is RichContentBlock.Heading -> SelectionContainer {
            RichContentText(
                content = block.content,
                style = when (block.level) {
                    1 -> MaterialTheme.typography.headlineMedium
                    2 -> MaterialTheme.typography.headlineSmall
                    3 -> MaterialTheme.typography.titleLarge
                    4 -> MaterialTheme.typography.titleMedium
                    else -> MaterialTheme.typography.titleSmall
                },
                onUrlClick = openUrl,
                onImageClick = onImageClick,
                modifier = modifier.fillMaxWidth(),
            )
        }
        is RichContentBlock.Quote -> Box(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        0f to MaterialTheme.colorScheme.primary,
                        QUOTE_BORDER_STOP to MaterialTheme.colorScheme.primary,
                        QUOTE_BORDER_STOP to MaterialTheme.colorScheme.surfaceVariant,
                        1f to MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    shape = RoundedCornerShape(6.dp),
                )
                .padding(start = 14.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
        ) {
            RichContentBlocks(
                block.blocks,
                onUrlClick,
                onImageClick,
                imageSizeCache,
                insideTable = insideTable,
            )
        }
        is RichContentBlock.ListBlock -> Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            block.items.forEachIndexed { index, item ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (block.ordered) "${block.start + index}." else "•",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.width(8.dp))
                    RichContentBlocks(
                        blocks = item.blocks,
                        onUrlClick = onUrlClick,
                        onImageClick = onImageClick,
                        imageSizeCache = imageSizeCache,
                        modifier = Modifier.weight(1f),
                        insideTable = insideTable,
                    )
                }
            }
        }
        is RichContentBlock.CodeBlock -> SelectionContainer {
            Text(
                text = block.code,
                modifier = modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    .padding(12.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
        }
        is RichContentBlock.Table -> if (insideTable) {
            RichEmbedPlaceholder(
                embed = RichEmbed(null, stringResource(R.string.rich_nested_table_unsupported)),
                onOpenUrl = openUrl,
                showUnsupportedDescription = false,
                modifier = modifier,
            )
        } else {
            RichContentTable(
                table = block,
                renderBlocks = { cells ->
                    RichContentBlocks(
                        cells,
                        onUrlClick,
                        onImageClick,
                        imageSizeCache,
                        insideTable = true,
                    )
                },
                onUrlClick = openUrl,
                onImageClick = onImageClick,
                modifier = modifier,
            )
        }
        is RichContentBlock.Image -> RichBlockImage(
            image = block.image,
            sizeCache = imageSizeCache,
            onImageClick = onImageClick,
            onOpenUrl = openUrl,
            modifier = modifier,
        )
        is RichContentBlock.Video -> if (insideTable) {
            RichEmbedPlaceholder(
                embed = RichEmbed(
                    url = selectVideoSource(block.video.sources)?.url ?: block.video.openUrl,
                    title = stringResource(R.string.rich_table_video_unsupported),
                ),
                onOpenUrl = openUrl,
                showUnsupportedDescription = false,
                modifier = modifier,
            )
        } else {
            RichVideoPlayer(
                video = block.video,
                onOpenUrl = openUrl,
                modifier = modifier,
            )
        }
        is RichContentBlock.IframePlaceholder -> RichEmbedPlaceholder(
            embed = block.embed,
            onOpenUrl = openUrl,
            modifier = modifier,
        )
        RichContentBlock.Divider -> HorizontalDivider(modifier = modifier)
    }
}

private val BLOCK_SPACING = 10.dp
private const val QUOTE_BORDER_STOP = 0.012f
