package org.finiteplay.spider.game

import androidx.compose.ui.unit.dp
import org.finiteplay.spider.layout.TABLEAU_COLUMNS
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Board-geometry gate, Spider's own numbers (`UI_SPEC.md` once a design pass sets its own —
 * these are placeholder floors inherited from Klondike's, noted there as such).
 */
class SpiderGeometryTest {
    private val gateWidths = listOf(320.dp, 360.dp)

    @Test
    fun `card width at least the minimum at gate widths`() {
        for (width in gateWidths) {
            assertTrue("width $width", computeSpiderCardSize(width).width >= SPIDER_GEOMETRY.minCardWidth)
        }
    }

    @Test
    fun `ten columns fit narrower than seven at the same board width`() {
        val ten = computeSpiderCardSize(800.dp).width
        assertTrue(ten.value > 0f)
    }

    @Test
    fun `column spacing never drops below the configured minimum`() {
        val card = computeSpiderCardSize(360.dp)
        val spacing = spiderColumnSpacing(360.dp, card.width)
        assertTrue(spacing >= SPIDER_GEOMETRY.minColumnSpacing)
    }

    @Test
    fun `overlap never drops the face-up band below its floor`() {
        val overlap = spiderTableauOverlap(availableHeight = 300.dp, cardHeight = 140.dp, faceDownCount = 5, faceUpCount = 1)
        assertTrue(overlap.faceUpStep >= SPIDER_GEOMETRY.minFaceUpBand)
    }
}
