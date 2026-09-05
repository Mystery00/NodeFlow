package app.mystery0.nodeflow.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mystery0.nodeflow.core.model.RichContentBlock
import app.mystery0.nodeflow.core.model.RichTableCell
import app.mystery0.nodeflow.core.model.RichTableRow

internal data class RichTableLayout(
    val columnCount: Int,
    val rowCount: Int,
    val cells: List<RichTableCellPlacement>,
)

internal data class RichTableCellPlacement(
    val row: Int,
    val column: Int,
    val rowSpan: Int,
    val colSpan: Int,
    val cell: RichTableCell,
)

internal fun buildRichTableLayout(rows: List<RichTableRow>): RichTableLayout {
    val occupied = mutableSetOf<Pair<Int, Int>>()
    val placements = mutableListOf<RichTableCellPlacement>()
    var columnCount = 0
    rows.forEachIndexed { rowIndex, row ->
        var column = 0
        row.cells.forEach { cell ->
            val rowSpan = cell.rowSpan.coerceIn(1, minOf(MAX_TABLE_SPAN, rows.size - rowIndex))
            val colSpan = cell.colSpan.coerceIn(1, MAX_TABLE_SPAN)
            while ((0 until colSpan).any { offset -> rowIndex to (column + offset) in occupied }) {
                column += 1
            }
            placements += RichTableCellPlacement(
                row = rowIndex,
                column = column,
                rowSpan = rowSpan,
                colSpan = colSpan,
                cell = cell,
            )
            repeat(rowSpan) { rowOffset ->
                repeat(colSpan) { columnOffset ->
                    occupied += (rowIndex + rowOffset) to (column + columnOffset)
                }
            }
            column += colSpan
            columnCount = maxOf(columnCount, column)
        }
    }
    return RichTableLayout(columnCount, rows.size, placements)
}

internal fun resolveRichTableRowHeights(
    layout: RichTableLayout,
    measuredCellHeights: IntArray,
    minimumRowHeight: Int,
): IntArray {
    val heights = IntArray(layout.rowCount) { minimumRowHeight }
    layout.cells.forEachIndexed { index, placement ->
        if (placement.rowSpan == 1) {
            heights[placement.row] = maxOf(heights[placement.row], measuredCellHeights[index])
        }
    }
    layout.cells.forEachIndexed { index, placement ->
        if (placement.rowSpan <= 1) return@forEachIndexed
        val coveredRows = placement.row until placement.row + placement.rowSpan
        val missingHeight = measuredCellHeights[index] - coveredRows.sumOf { heights[it] }
        if (missingHeight > 0) {
            val heightPerRow = missingHeight / placement.rowSpan
            val remainder = missingHeight % placement.rowSpan
            coveredRows.forEachIndexed { offset, row ->
                heights[row] += heightPerRow + if (offset < remainder) 1 else 0
            }
        }
    }
    return heights
}

internal fun resolveRichTableColumnWidths(
    layout: RichTableLayout,
    preferredCellWidths: IntArray,
    viewportWidth: Int,
    minimumColumnWidth: Int,
    maximumPreferredColumnWidth: Int,
): IntArray {
    val widths = IntArray(layout.columnCount) { minimumColumnWidth }
    layout.cells.forEachIndexed { index, placement ->
        if (placement.colSpan == 1) {
            widths[placement.column] = maxOf(
                widths[placement.column],
                preferredCellWidths[index].coerceAtMost(maximumPreferredColumnWidth),
            )
        }
    }
    layout.cells.forEachIndexed { index, placement ->
        if (placement.colSpan <= 1) return@forEachIndexed
        val columns = placement.column until placement.column + placement.colSpan
        val preferredWidth = preferredCellWidths[index]
            .coerceAtMost(maximumPreferredColumnWidth * placement.colSpan)
        distributeWidthDeficit(widths, columns, preferredWidth - columns.sumOf { widths[it] })
    }
    distributeWidthDeficit(widths, widths.indices, viewportWidth - widths.sum())
    return widths
}

private fun distributeWidthDeficit(widths: IntArray, columns: IntRange, deficit: Int) {
    if (deficit <= 0 || columns.isEmpty()) return
    val widthPerColumn = deficit / columns.count()
    val remainder = deficit % columns.count()
    columns.forEachIndexed { offset, column ->
        widths[column] += widthPerColumn + if (offset < remainder) 1 else 0
    }
}

@Composable
internal fun RichContentTable(
    table: RichContentBlock.Table,
    renderBlocks: @Composable (List<RichContentBlock>) -> Unit,
    onUrlClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (table.rows.isEmpty()) return
    val layout = buildRichTableLayout(table.rows)
    if (layout.columnCount == 0) return
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val viewportWidth = maxWidth
        Column {
            if (table.caption.isNotEmpty()) {
                RichContentText(
                    content = table.caption,
                    style = MaterialTheme.typography.titleSmall,
                    onUrlClick = onUrlClick,
                    onImageClick = onImageClick,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                RichTableGrid(
                    layout = layout,
                    viewportWidth = viewportWidth,
                    renderBlocks = renderBlocks,
                )
            }
        }
    }
}

@Composable
private fun RichTableGrid(
    layout: RichTableLayout,
    viewportWidth: Dp,
    renderBlocks: @Composable (List<RichContentBlock>) -> Unit,
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val headerColor = MaterialTheme.colorScheme.surfaceVariant
    val cellColor = MaterialTheme.colorScheme.surface
    SubcomposeLayout(
        modifier = Modifier.semantics {
            collectionInfo = CollectionInfo(layout.rowCount, layout.columnCount)
        },
    ) { constraints ->
        val minimumColumnWidthPx = MIN_TABLE_COLUMN_WIDTH.roundToPx()
        val maximumPreferredColumnWidthPx = MAX_PREFERRED_TABLE_COLUMN_WIDTH.roundToPx()
        val minimumRowHeightPx = MIN_TABLE_ROW_HEIGHT.roundToPx()
        val measurables = layout.cells.mapIndexed { index, placement ->
            subcompose("cell-$index") {
                Box(
                    modifier = Modifier
                        .semantics {
                            collectionItemInfo = CollectionItemInfo(
                                rowIndex = placement.row,
                                rowSpan = placement.rowSpan,
                                columnIndex = placement.column,
                                columnSpan = placement.colSpan,
                            )
                            if (placement.cell.isHeader) heading()
                        }
                        .padding(TABLE_CELL_PADDING),
                ) {
                    renderBlocks(placement.cell.blocks)
                }
            }.single()
        }
        val preferredCellWidths = IntArray(measurables.size) { index ->
            runCatching { measurables[index].maxIntrinsicWidth(Constraints.Infinity) }
                .getOrDefault(minimumColumnWidthPx * layout.cells[index].colSpan)
        }
        val columnWidths = resolveRichTableColumnWidths(
            layout = layout,
            preferredCellWidths = preferredCellWidths,
            viewportWidth = viewportWidth.roundToPx(),
            minimumColumnWidth = minimumColumnWidthPx,
            maximumPreferredColumnWidth = maximumPreferredColumnWidthPx,
        )
        val columnOffsets = IntArray(layout.columnCount + 1)
        columnWidths.forEachIndexed { index, width -> columnOffsets[index + 1] = columnOffsets[index] + width }
        val placeables = measurables.mapIndexed { index, measurable ->
            val placement = layout.cells[index]
            val cellWidth = columnOffsets[placement.column + placement.colSpan] - columnOffsets[placement.column]
            measurable.measure(
                constraints.copy(
                    minWidth = cellWidth,
                    maxWidth = cellWidth,
                    minHeight = 0,
                    maxHeight = constraints.maxHeight,
                ),
            )
        }
        val rowHeights = resolveRichTableRowHeights(
            layout = layout,
            measuredCellHeights = IntArray(placeables.size) { placeables[it].height },
            minimumRowHeight = minimumRowHeightPx,
        )
        val rowOffsets = IntArray(layout.rowCount + 1)
        rowHeights.forEachIndexed { index, height -> rowOffsets[index + 1] = rowOffsets[index] + height }
        val width = columnOffsets.last()
        val height = rowOffsets.last()
        val gridBackground = subcompose("grid-background") {
            Canvas(modifier = Modifier.fillMaxSize()) {
                layout.cells.forEach { placement ->
                    val top = rowOffsets[placement.row].toFloat()
                    val bottom = rowOffsets[placement.row + placement.rowSpan].toFloat()
                    val left = columnOffsets[placement.column].toFloat()
                    val cellWidth = (
                        columnOffsets[placement.column + placement.colSpan] -
                            columnOffsets[placement.column]
                        ).toFloat()
                    drawRect(
                        color = if (placement.cell.isHeader) headerColor else cellColor,
                        topLeft = Offset(left, top),
                        size = Size(cellWidth, bottom - top),
                    )
                    drawRect(
                        color = borderColor,
                        topLeft = Offset(left, top),
                        size = Size(cellWidth, bottom - top),
                        style = Stroke(width = TABLE_BORDER_WIDTH.toPx()),
                    )
                }
            }
        }.single().measure(Constraints.fixed(width, height))
        layout(width, height) {
            gridBackground.placeRelative(0, 0)
            placeables.forEachIndexed { index, placeable ->
                val placement = layout.cells[index]
                placeable.placeRelative(
                    x = columnOffsets[placement.column],
                    y = rowOffsets[placement.row],
                )
            }
        }
    }
}

private val MIN_TABLE_COLUMN_WIDTH = 128.dp
private val MAX_PREFERRED_TABLE_COLUMN_WIDTH = 360.dp
private val MIN_TABLE_ROW_HEIGHT = 40.dp
private val TABLE_CELL_PADDING = 10.dp
private val TABLE_BORDER_WIDTH = 0.5.dp
private const val MAX_TABLE_SPAN = 100
