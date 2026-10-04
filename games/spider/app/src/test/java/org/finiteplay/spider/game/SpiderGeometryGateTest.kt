package org.finiteplay.spider.game

import androidx.compose.ui.unit.dp
import org.finiteplay.solitaire.ui.boardSideMarginFor
import org.finiteplay.solitaire.ui.columnGapFor
import org.finiteplay.spider.layout.TABLEAU_COLUMNS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Board-geometry gate from `docs/games/spider/EXECUTION_PLAN.md` S3a: at 320 dp and 360 dp
 * widths, ten equal columns, the 5:7 card ratio, at least 4 dp column gaps, card width at least
 * 40 dp, and an exposed face-up band of at least 19 dp (`docs/games/spider/UI_SPEC.md` "Board
 * Geometry").
 */
class SpiderGeometryGateTest {
    private val gateWidths = listOf(320.dp, 360.dp)

    @Test
    fun cardWidthAtLeastMinimumAtGateWidths() {
        for (width in gateWidths) {
            assertTrue("card width at $width", computeSpiderCardSize(width).width >= SPIDER_GEOMETRY.minCardWidth)
        }
    }

    @Test
    fun cardRatioIsFiveToSeven() {
        for (width in gateWidths) {
            val size = computeSpiderCardSize(width)
            val expectedHeight = size.width.value * SPIDER_GEOMETRY.cardWidthToHeightRatio
            assertTrue("ratio at $width", kotlin.math.abs(expectedHeight - size.height.value) < 0.01f)
        }
    }

    @Test
    fun columnSpacingAtLeastMinimumAtGateWidths() {
        for (width in gateWidths) {
            val cardSize = computeSpiderCardSize(width)
            val spacing = spiderColumnSpacing(width, cardSize.width)
            assertTrue("spacing at $width", spacing >= SPIDER_GEOMETRY.minColumnSpacing)
        }
    }

    @Test
    fun tenColumnsFitWithinAvailableWidthAtGateWidths() {
        for (width in gateWidths) {
            val cardSize = computeSpiderCardSize(width)
            val spacing = spiderColumnSpacing(width, cardSize.width)
            val totalWidth = cardSize.width * TABLEAU_COLUMNS + spacing * (TABLEAU_COLUMNS - 1)
            assertTrue("total width at $width was $totalWidth", totalWidth <= width + 1.dp)
        }
    }

    @Test
    fun guttersStayVisibleAsTheScreenGrows() {
        // A 4 dp gap reads as a gap beside a phone-sized card and as nothing beside a tablet
        // card: both gutters are a share of the card width, so the board looks the same at
        // every size, the same reasoning Klondike's own gate checks.
        for (width in listOf(360.dp, 800.dp, 1030.dp)) {
            val card = computeSpiderCardSize(width)
            val gap = SPIDER_GEOMETRY.columnGapFor(card.width)
            val margin = SPIDER_GEOMETRY.boardSideMarginFor(card.width)
            assertTrue(
                "gap $gap beside a $card card at $width",
                gap >= minOf(card.width * 0.08f, SPIDER_GEOMETRY.minColumnSpacing * 2),
            )
            assertTrue("margin $margin at $width", margin >= gap)
            val used = card.width * TABLEAU_COLUMNS + gap * (TABLEAU_COLUMNS - 1) + margin * 2
            assertTrue("board uses $used of $width", used <= width + 1.dp)
        }
    }

    @Test
    fun faceUpBandNeverBelowMinimumUnderTightHeight() {
        // Deliberately too little height for a thirteen-card column even at minimum bands.
        val overlap = spiderTableauOverlap(availableHeight = 120.dp, cardHeight = 200.dp, faceDownCount = 5, faceUpCount = 8)
        assertTrue(overlap.faceUpStep >= SPIDER_GEOMETRY.minFaceUpBand)
    }

    @Test
    fun faceDownStepNeverExceedsFaceUpStep() {
        val overlap = spiderTableauOverlap(availableHeight = 900.dp, cardHeight = 140.dp, faceDownCount = 3, faceUpCount = 4)
        assertTrue(overlap.faceDownStep <= overlap.faceUpStep)
    }

    @Test
    fun theStepOntoTheFirstFaceUpCardUsesTheFaceDownBandNotFaceUp() {
        val overlap = spiderTableauOverlap(availableHeight = 900.dp, cardHeight = 140.dp, faceDownCount = 3, faceUpCount = 1)
        assertEquals(SPIDER_GEOMETRY.minFaceUpBand, overlap.faceUpStep)
    }

    @Test
    fun aShortColumnDoesNotShrinkTheCard() {
        val forOneCard = spiderCardHeightFittingTallestColumn(360.dp, 1)
        val forADeepColumn = spiderCardHeightFittingTallestColumn(360.dp, 20)
        assertTrue(forOneCard > forADeepColumn)
    }
}
