package org.finiteplay.spider.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `columnWithMostOverlap` is what decides where a drag drops (`SpiderBoard.kt`) — a player
 * reported drops cancelling when they expected a column, because the old point-in-rect test
 * demanded the pointer land exactly inside a column's own frame. This is the fix: the column
 * whose horizontal span overlaps the dragged card's the most, on the x-axis alone.
 */
class ColumnOverlapTest {
    private val cardWidth = 100f

    /** Ten 100px-wide columns side by side, each spanning the same vertical range. */
    private val columnRects = (0 until 10).associateWith { column ->
        Rect(left = column * 100f, top = 0f, right = (column + 1) * 100f, bottom = 1000f)
    }

    private fun dragAt(pointerX: Float, pointerY: Float = 500f, grabX: Float = 50f, grabY: Float = 50f) =
        DragInfo(
            fromColumn = 0,
            fromIndex = 0,
            cards = emptyList(),
            grabOffset = Offset(grabX, grabY),
            pointerInBoard = Offset(pointerX, pointerY),
        )

    @Test
    fun `a card squarely over one column lands there`() {
        // cardLeft = 350 - 50 = 300, spanning 300-400: entirely column 3.
        val drag = dragAt(pointerX = 350f)
        assertEquals(3, columnWithMostOverlap(drag, cardWidth, columnRects))
    }

    @Test
    fun `a card straddling two columns lands on the one it covers more of`() {
        // cardLeft = 310 - 50 = 260, spanning 260-360: 40px in column 2, 60px in column 3.
        val drag = dragAt(pointerX = 310f)
        assertEquals(3, columnWithMostOverlap(drag, cardWidth, columnRects))
    }

    @Test
    fun `vertical position never affects the result`() {
        // Same horizontal placement as the squarely-over-one-column case, but the pointer's y is
        // nowhere near any column's own vertical range — the old point-in-rect test would have
        // found no match here at all.
        val drag = dragAt(pointerX = 350f, pointerY = 999_999f)
        assertEquals(3, columnWithMostOverlap(drag, cardWidth, columnRects))
    }

    @Test
    fun `a card dragged clear of every column has no destination`() {
        // cardLeft = -450 - 50 = -500, spanning -500 to -400: short of column 0's left edge (0).
        val drag = dragAt(pointerX = -450f)
        assertNull(columnWithMostOverlap(drag, cardWidth, columnRects))
    }

    @Test
    fun `a card that just barely crosses into a column lands there over the one it left`() {
        // cardLeft = 291 - 50 = 241, spanning 241-341: 59px in column 2, 41px in column 3 — most
        // of the card is still over column 2 even though it has crossed the boundary.
        val drag = dragAt(pointerX = 291f)
        assertEquals(2, columnWithMostOverlap(drag, cardWidth, columnRects))
    }
}
