package org.finiteplay.blackjack.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Holds `UI_SPEC.md` "Geometry" to its numbers at the two narrowest widths it names. */
class TableGeometryTest {
    private val widths = listOf(320f, 360f)

    @Test
    fun `no card is ever narrower than the floor`() {
        for (screen in widths) {
            val area = TableGeometry.areaWidth(screen)
            for (hands in 1..4) {
                assertTrue("$hands hands at $screen", TableGeometry.cardWidth(area, hands) >= TableGeometry.MIN_CARD_WIDTH)
            }
        }
    }

    @Test
    fun `four split hands at 320 and 360`() {
        // The columns UI_SPEC.md names: 76 dp and 86 dp, after the 8 dp side margins.
        assertEquals(76f, TableGeometry.columnWidth(TableGeometry.areaWidth(320f), 4), 0.01f)
        assertEquals(86f, TableGeometry.columnWidth(TableGeometry.areaWidth(360f), 4), 0.01f)
        assertEquals(40f, TableGeometry.cardWidth(TableGeometry.areaWidth(320f), 4), 0.01f)
        assertEquals(43f, TableGeometry.cardWidth(TableGeometry.areaWidth(360f), 4), 0.01f)
    }

    @Test
    fun `a twenty-one card hand fits the width with every index but the last visible`() {
        for ((screen, expectedStep) in listOf(320f to 12.16f, 360f to 14.0f)) {
            val area = TableGeometry.areaWidth(screen)
            val card = TableGeometry.cardWidth(area, 1)
            val step = TableGeometry.fanStep(card, TableGeometry.handWidth(area, 1), 21)
            assertEquals("step at $screen", expectedStep, step, 0.05f)
            assertTrue("the fan fits at $screen", TableGeometry.fanWidth(card, step, 21) <= area + 0.01f)
            assertTrue("a corner index (about 10 dp) stays visible at $screen", step >= 10f)
        }
    }

    @Test
    fun `a split hand's fan fits its column until the step floor takes over`() {
        for (screen in widths) {
            val area = TableGeometry.areaWidth(screen)
            val card = TableGeometry.cardWidth(area, 4)
            val hand = TableGeometry.handWidth(area, 4)
            for (cards in 1..5) {
                val step = TableGeometry.fanStep(card, hand, cards)
                assertTrue("$cards cards at $screen", TableGeometry.fanWidth(card, step, cards) <= hand + 0.01f)
            }
            // Past the floor the fan is allowed to overflow, and the step stays at its minimum.
            assertEquals(TableGeometry.MIN_FAN_STEP, TableGeometry.fanStep(card, hand, 12), 0.001f)
        }
    }

    @Test
    fun `one card has no step`() {
        assertEquals(0f, TableGeometry.fanStep(60f, 300f, 1), 0f)
    }

    @Test
    fun `two cards overlap by under a third, so the covered card's pip reads`() {
        for (screen in widths) {
            val area = TableGeometry.areaWidth(screen)
            val card = TableGeometry.cardWidth(area, 1)
            val step = TableGeometry.fanStep(card, TableGeometry.handWidth(area, 1), 2)
            assertEquals(card * TableGeometry.MAX_STEP_FRACTION, step, 0.01f)
            assertTrue("the covered card shows more than two thirds of its width", step / card > 0.66f)
        }
    }
}
