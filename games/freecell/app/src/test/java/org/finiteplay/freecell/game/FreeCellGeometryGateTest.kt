package org.finiteplay.freecell.game

import androidx.compose.ui.unit.dp
import org.finiteplay.freecell.layout.TABLEAU_COLUMNS
import org.finiteplay.solitaire.ui.boardSideMarginFor
import org.finiteplay.solitaire.ui.columnGapFor
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Board-geometry gate from `docs/games/freecell/EXECUTION_PLAN.md` F2: at 320 dp and 360 dp
 * widths, eight equal columns, the 5:7 card ratio, at least 4 dp column gaps, the provisional
 * card-width and exposed-band floors from `docs/games/freecell/UI_SPEC.md` "Board Geometry" —
 * and, since a FreeCell column can grow well past its dealt depth (`RULES.md` "The layout"),
 * every card still stays whole up to the 19-card ceiling ([MAX_COLUMN_DEPTH]), not just at a
 * fresh deal's own seven.
 */
class FreeCellGeometryGateTest {
    private val gateWidths = listOf(320.dp, 360.dp)

    @Test
    fun cardWidthAtLeastMinimumAtGateWidths() {
        for (width in gateWidths) {
            assertTrue("card width at $width", computeFreeCellCardSize(width).width >= FREECELL_GEOMETRY.minCardWidth)
        }
    }

    @Test
    fun cardRatioIsFiveToSeven() {
        for (width in gateWidths) {
            val size = computeFreeCellCardSize(width)
            val expectedHeight = size.width.value * FREECELL_GEOMETRY.cardWidthToHeightRatio
            assertTrue("ratio at $width", kotlin.math.abs(expectedHeight - size.height.value) < 0.01f)
        }
    }

    @Test
    fun columnSpacingAtLeastMinimumAtGateWidths() {
        for (width in gateWidths) {
            val cardSize = computeFreeCellCardSize(width)
            val spacing = freeCellColumnSpacing(width, cardSize.width)
            assertTrue("spacing at $width", spacing >= FREECELL_GEOMETRY.minColumnSpacing)
        }
    }

    @Test
    fun eightColumnsFitWithinAvailableWidthAtGateWidths() {
        for (width in gateWidths) {
            val cardSize = computeFreeCellCardSize(width)
            val spacing = freeCellColumnSpacing(width, cardSize.width)
            val totalWidth = cardSize.width * TABLEAU_COLUMNS + spacing * (TABLEAU_COLUMNS - 1)
            assertTrue("total width at $width was $totalWidth", totalWidth <= width + 1.dp)
        }
    }

    @Test
    fun guttersStayVisibleAsTheScreenGrows() {
        for (width in listOf(360.dp, 800.dp, 1030.dp)) {
            val card = computeFreeCellCardSize(width)
            val gap = FREECELL_GEOMETRY.columnGapFor(card.width)
            val margin = FREECELL_GEOMETRY.boardSideMarginFor(card.width)
            assertTrue(
                "gap $gap beside a $card card at $width",
                gap >= minOf(card.width * 0.08f, FREECELL_GEOMETRY.minColumnSpacing * 2),
            )
            assertTrue("margin $margin at $width", margin >= gap)
            val used = card.width * TABLEAU_COLUMNS + gap * (TABLEAU_COLUMNS - 1) + margin * 2
            assertTrue("board uses $used of $width", used <= width + 1.dp)
        }
    }

    @Test
    fun aDeepColumnStillLeavesEveryCardWhole() {
        // A column at MAX_COLUMN_DEPTH cards must still fit inside an ordinary phone's tableau
        // lane at the minimum band, however small the cards get.
        val laneHeight = 500.dp
        val cardHeight = freeCellCardHeightFittingTallestColumn(laneHeight, MAX_COLUMN_DEPTH)
        val offsets = freeCellCardTopOffsets(MAX_COLUMN_DEPTH, laneHeight, cardHeight)
        val lastCardBottom = offsets.last() + cardHeight
        assertTrue("last card bottom $lastCardBottom exceeds lane $laneHeight", lastCardBottom <= laneHeight + 1.dp)
    }

    @Test
    fun aShortColumnDoesNotShrinkTheCard() {
        val forOneCard = freeCellCardHeightFittingTallestColumn(360.dp, 1)
        val forADeepColumn = freeCellCardHeightFittingTallestColumn(360.dp, MAX_COLUMN_DEPTH)
        assertTrue(forOneCard > forADeepColumn)
    }

    @Test
    fun everyStepInAColumnIsTheFaceUpBand() {
        // FreeCell has no face-down step at all — every card is face up from the deal
        // (`docs/games/freecell/RULES.md` "The layout").
        val offsets = freeCellCardTopOffsets(cardCount = 5, availableHeight = 900.dp, cardHeight = 140.dp)
        for (i in 1 until offsets.size) {
            val step = offsets[i] - offsets[i - 1]
            assertTrue("step $i was $step", step >= FREECELL_GEOMETRY.minFaceUpBand - 1.dp)
        }
    }
}
