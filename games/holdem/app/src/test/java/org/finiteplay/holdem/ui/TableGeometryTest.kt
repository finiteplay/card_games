package org.finiteplay.holdem.ui

import org.finiteplay.holdem.ui.TableGeometry.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Holds `UI_SPEC.md` "Geometry" to its numbers at 320 dp and 360 dp wide and at the heights it names. */
class TableGeometryTest {
    private val widths = listOf(320f, 360f)

    @Test
    fun `table height is the screen less the status row, sizing row and bar`() {
        assertEquals(408f, TableGeometry.tableHeight(580f), 0f)
        assertEquals(336f, TableGeometry.tableHeight(508f), 0f)
    }

    @Test
    fun `the density follows the table's height`() {
        assertEquals(Density.Regular, TableGeometry.density(408f))
        assertEquals(Density.Compact, TableGeometry.density(407.9f))
        assertEquals(Density.Compact, TableGeometry.density(336f))
        assertEquals(Density.Squeezed, TableGeometry.density(335.9f))
    }

    @Test
    fun `seat width and board card at 320 and 360`() {
        assertEquals(96f, TableGeometry.seatWidth(TableGeometry.areaWidth(320f)), 0.01f)
        assertEquals(109.33f, TableGeometry.seatWidth(TableGeometry.areaWidth(360f)), 0.01f)
        assertEquals(112f, TableGeometry.seatWidth(TableGeometry.areaWidth(480f)), 0f)
        for (screen in widths) {
            assertEquals(52f, TableGeometry.boardCardWidth(TableGeometry.areaWidth(screen)), 0f)
        }
        // Narrow enough that (area - 16) / 5 would be under the floor.
        assertEquals(40f, TableGeometry.boardCardWidth(200f), 0f)
        assertEquals(44f, TableGeometry.boardCardWidth(236f), 0.01f)
    }

    @Test
    fun `regular table at 580 dp`() {
        for (screen in widths) {
            val t = TableGeometry.portrait(screen, TableGeometry.tableHeight(580f))
            assertEquals(Density.Regular, t.density)
            assertEquals(100f, t.seatHeight, 0f)
            assertEquals(40f, t.opponentCardWidth, 0f)
            assertEquals(52f, t.boardCardWidth, 0f)
            assertEquals(64f, t.yourCardWidth, 0f)
            assertEquals(44.8f, t.yourStep, 0.001f)
            assertEquals(73f, t.boardBandHeight, 0f)
            assertEquals(110f, t.yourSeatHeight, 0f)
            assertEquals(408f, t.totalHeight, 0.01f)
            assertTrue(t.gap >= 8f)
        }
    }

    @Test
    fun `the regular table at its minimum totals 407`() {
        val t = TableGeometry.portrait(320f, 408f)
        assertEquals(100f + 100f + 73f + 110f, t.bandsHeight, 0f)
        assertEquals(407f, t.bandsHeight + 3 * TableGeometry.REGULAR_MIN_GAP, 0f)
        assertTrue(t.bandsHeight + 3 * TableGeometry.REGULAR_MIN_GAP <= TableGeometry.REGULAR_MIN_HEIGHT)
    }

    @Test
    fun `compact table at 508 dp totals 336 with 6 dp gaps`() {
        for (screen in widths) {
            val t = TableGeometry.portrait(screen, TableGeometry.tableHeight(508f))
            assertEquals(Density.Compact, t.density)
            assertEquals(76f, t.seatHeight, 0f)
            assertEquals(22f, t.opponentCardWidth, 0f)
            assertEquals(52f, t.boardCardWidth, 0f)
            assertEquals(52f, t.yourCardWidth, 0f)
            assertEquals(36.4f, t.yourStep, 0.001f)
            assertEquals(73f, t.boardBandHeight, 0f)
            assertEquals(93f, t.yourSeatHeight, 0f)
            assertEquals(76f + 76f + 73f + 93f, t.bandsHeight, 0f)
            assertEquals(6f, t.gap, 0.001f)
            assertEquals(336f, t.totalHeight, 0.01f)
        }
    }

    @Test
    fun `a card row fits its backs and a seat is name, stack and cards`() {
        assertEquals(56f, TableGeometry.cardHeight(TableGeometry.REGULAR_OPPONENT_CARD_WIDTH), 0f)
        assertTrue(TableGeometry.cardHeight(22f) <= TableGeometry.COMPACT_SEAT_CARD_ROW)
        assertEquals(100f, TableGeometry.SEAT_NAME_ROW + TableGeometry.SEAT_STACK_ROW + TableGeometry.REGULAR_SEAT_CARD_ROW, 0f)
        assertEquals(76f, TableGeometry.SEAT_NAME_ROW + TableGeometry.SEAT_STACK_ROW + TableGeometry.COMPACT_SEAT_CARD_ROW, 0f)
    }

    @Test
    fun `spare height goes to the gaps evenly and the bands never reorder`() {
        val t = TableGeometry.portrait(360f, 500f)
        assertEquals(Density.Regular, t.density)
        assertEquals((500f - 383f) / 3f, t.gap, 0.01f)
        assertEquals(500f, t.totalHeight, 0.01f)
        val compact = TableGeometry.portrait(360f, 380f)
        assertEquals((380f - 318f) / 3f, compact.gap, 0.01f)
    }

    @Test
    fun `below 336 the squeeze shrinks the board and your cards, never the opponents' cards under 22 or the rest under 40`() {
        // Compact bands are 318 dp, the gaps 18 dp, and each dp of card costs 2.8 dp of table.
        val expectedCard = mapOf(335f to 51f, 320f to 46f, 306f to 41f, 302f to 40f)
        for (screen in widths) {
            for ((height, card) in expectedCard) {
                val t = TableGeometry.portrait(screen, height)
                assertEquals("height $height", Density.Squeezed, t.density)
                assertEquals("your card at $height", card, t.yourCardWidth, 0f)
                assertEquals("board card at $height", card, t.boardCardWidth, 0f)
                assertEquals("opponent cards at $height", 22f, t.opponentCardWidth, 0f)
                assertEquals("seats at $height", 76f, t.seatHeight, 0f)
                assertTrue("fits at $height", t.totalHeight <= height + 0.01f)
                assertTrue("gap at $height", t.gap >= 6f - 0.01f)
            }
        }
    }

    @Test
    fun `below 302 only the gaps give, down to nothing at 284`() {
        for (height in listOf(300f, 290f, 284f)) {
            val t = TableGeometry.portrait(320f, height)
            assertEquals(40f, t.yourCardWidth, 0f)
            assertEquals(40f, t.boardCardWidth, 0f)
            assertEquals(22f, t.opponentCardWidth, 0f)
            assertEquals((height - 284f) / 3f, t.gap, 0.01f)
            assertEquals(height, t.totalHeight, 0.01f)
        }
        assertEquals(0f, TableGeometry.portrait(320f, 250f).gap, 0f)
    }

    @Test
    fun `the squeeze never makes a card smaller than the floor at any height`() {
        var height = 400f
        while (height >= 250f) {
            for (screen in widths) {
                val t = TableGeometry.portrait(screen, height)
                assertTrue(t.boardCardWidth >= 40f)
                assertTrue(t.yourCardWidth >= 40f)
                assertTrue(t.opponentCardWidth >= 22f)
            }
            height -= 1f
        }
    }

    @Test
    fun `showdown cards at the floor, two with a 70 percent step, are 68 dp wide in a 96 dp box`() {
        assertEquals(68f, TableGeometry.pairWidth(40f), 0.001f)
        assertTrue(TableGeometry.pairWidth(40f) <= TableGeometry.seatWidth(TableGeometry.areaWidth(320f)))
    }

    @Test
    fun `landscape at 640 by 360`() {
        val t = TableGeometry.landscape(640f, 360f)
        assertEquals(488f, t.tableWidth, 0f)
        assertEquals(316f, t.tableHeight, 0f)
        assertEquals(96f, t.seatWidth, 0f)
        assertEquals(100f, t.seatHeight, 0f)
        assertEquals(40f, t.opponentCardWidth, 0f)
        assertEquals(52f, t.boardCardWidth, 0f)
        assertEquals(52f, t.yourCardWidth, 0f)
        assertEquals(93f, t.yourSeatHeight, 0f)
        assertEquals(276f, t.boardWidth, 0f)
        assertEquals(468f, t.middleRowWidth, 0f)
        assertTrue(t.middleRowWidth <= TableGeometry.areaWidth(t.tableWidth))
        assertEquals(100f + 100f + 93f + 2 * TableGeometry.REGULAR_MIN_GAP, 309f, 0f)
        assertEquals(316f, t.totalHeight, 0.01f)
        assertTrue(t.gap >= 8f)
        // The board and its pot line fit inside the middle band.
        assertTrue(73f + 20f <= t.seatHeight)
    }
}
