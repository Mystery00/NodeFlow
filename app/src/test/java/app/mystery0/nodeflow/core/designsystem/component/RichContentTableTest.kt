package app.mystery0.nodeflow.core.designsystem.component

import app.mystery0.nodeflow.core.model.RichContentBlock
import app.mystery0.nodeflow.core.model.RichTableCell
import app.mystery0.nodeflow.core.model.RichTableRow
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RichContentTableTest {
    @Test
    fun buildLayout_placesCellsAroundRowAndColumnSpans() {
        val rows = listOf(
            row(cell(colSpan = 2), cell(rowSpan = 2)),
            row(cell(), cell()),
        )

        val layout = buildRichTableLayout(rows)

        assertThat(layout.columnCount).isEqualTo(3)
        assertThat(layout.cells.map { listOf(it.row, it.column, it.rowSpan, it.colSpan) })
            .containsExactly(
                listOf(0, 0, 1, 2),
                listOf(0, 2, 2, 1),
                listOf(1, 0, 1, 1),
                listOf(1, 1, 1, 1),
            )
            .inOrder()
    }

    @Test
    fun resolveRowHeights_expandsRowsCoveredByTallSpanningCell() {
        val layout = buildRichTableLayout(
            listOf(
                row(cell(rowSpan = 2), cell()),
                row(cell()),
            ),
        )
        val heights = resolveRichTableRowHeights(
            layout = layout,
            measuredCellHeights = intArrayOf(120, 30, 40),
            minimumRowHeight = 20,
        )

        assertThat(heights.sum()).isAtLeast(120)
        assertThat(heights.toList()).containsExactly(55, 65)
    }

    @Test
    fun resolveColumnWidths_usesWideContentAndTriggersHorizontalOverflow() {
        val layout = buildRichTableLayout(listOf(row(cell(), cell())))

        val widths = resolveRichTableColumnWidths(
            layout = layout,
            preferredCellWidths = intArrayOf(400, 80),
            viewportWidth = 300,
            minimumColumnWidth = 128,
            maximumPreferredColumnWidth = 400,
        )

        assertThat(widths.toList()).containsExactly(400, 128)
        assertThat(widths.sum()).isGreaterThan(300)
    }

    @Test
    fun resolveColumnWidths_distributesSpanningCellAndViewportDeficits() {
        val layout = buildRichTableLayout(listOf(row(cell(colSpan = 2))))

        val widths = resolveRichTableColumnWidths(
            layout = layout,
            preferredCellWidths = intArrayOf(360),
            viewportWidth = 400,
            minimumColumnWidth = 100,
            maximumPreferredColumnWidth = 300,
        )

        assertThat(widths.toList()).containsExactly(200, 200)
    }

    private fun row(vararg cells: RichTableCell) = RichTableRow(cells.toList())

    private fun cell(colSpan: Int = 1, rowSpan: Int = 1) = RichTableCell(
        blocks = listOf(RichContentBlock.Paragraph(emptyList())),
        colSpan = colSpan,
        rowSpan = rowSpan,
    )
}
