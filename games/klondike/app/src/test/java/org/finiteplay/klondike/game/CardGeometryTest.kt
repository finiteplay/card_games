package org.finiteplay.klondike

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Board-geometry gate from `EXECUTION_PLAN.md` A1: at 320 dp and 360 dp widths, seven
 * equal columns, 5:7 card ratio, at least 4 dp column gaps, card width at least 40 dp,
 * and an exposed face-up band of at least 24 dp.
 */
class CardGeometryTest {
    private val gateWidths = listOf(320.dp, 360.dp)

    @Test
    fun cardWidthAtLeastMinimumAtGateWidths() {
        for (width in gateWidths) {
            assertTrue("card width at $width", computeCardSize(width).width >= MIN_CARD_WIDTH)
        }
    }

    @Test
    fun cardRatioIsFiveToSeven() {
        for (width in gateWidths) {
            val size = computeCardSize(width)
            val expectedHeight = size.width.value * 7f / 5f
            assertTrue("ratio at $width", kotlin.math.abs(expectedHeight - size.height.value) < 0.01f)
        }
    }

    @Test
    fun columnSpacingAtLeastMinimumAtGateWidths() {
        for (width in gateWidths) {
            val cardSize = computeCardSize(width)
            val spacing = computeColumnSpacing(width, cardSize.width)
            assertTrue("spacing at $width", spacing >= MIN_COLUMN_SPACING)
        }
    }

    @Test
    fun sevenColumnsFitWithinAvailableWidthAtGateWidths() {
        for (width in gateWidths) {
            val cardSize = computeCardSize(width)
            val spacing = computeColumnSpacing(width, cardSize.width)
            val totalWidth = cardSize.width * TABLEAU_COLUMNS_FOR_TEST + spacing * (TABLEAU_COLUMNS_FOR_TEST - 1)
            assertTrue("total width at $width was $totalWidth", totalWidth <= width + 1.dp)
        }
    }

    @Test
    fun guttersStayVisibleAsTheScreenGrows() {
        // A 4 dp gap reads as a gap beside a 47 dp phone card and as nothing at all beside a
        // 110 dp tablet card: on a tablet the seven columns ran edge to edge with no board
        // margin and no daylight between them. Both gutters are a share of the card, so the
        // board looks the same at every size.
        for (width in listOf(360.dp, 800.dp, 1030.dp)) {
            val card = computeCardSize(width)
            val gap = columnGapFor(card.width)
            val margin = boardSideMarginFor(card.width)
            assertTrue(
                "gap $gap beside a $card card at $width",
                gap >= minOf(card.width * 0.08f, MIN_COLUMN_SPACING * 2),
            )
            assertTrue("margin $margin at $width", margin >= gap)
            val used = card.width * TABLEAU_COLUMNS_FOR_TEST +
                gap * (TABLEAU_COLUMNS_FOR_TEST - 1) + margin * 2
            assertTrue("board uses $used of $width", used <= width + 1.dp)
        }
    }

    @Test
    fun faceUpBandNeverBelowMinimumUnderTightHeight() {
        // Deliberately too little height for a 13-card column even at minimum bands.
        val overlap = computeTableauOverlap(availableHeight = 120.dp, cardHeight = 200.dp, faceDownCount = 6, faceUpCount = 7)
        assertTrue(overlap.faceUpStep >= MIN_FACE_UP_BAND)
    }

    @Test
    fun faceDownStepNeverExceedsFaceUpStep() {
        val overlap = computeTableauOverlap(availableHeight = 900.dp, cardHeight = 140.dp, faceDownCount = 3, faceUpCount = 4)
        assertTrue(overlap.faceDownStep <= overlap.faceUpStep)
    }

    @Test
    fun overlapCanGrowWithExtraHeightButNeverPastTheOverlapCap() {
        val tight = computeTableauOverlap(availableHeight = 600.dp, cardHeight = 140.dp, faceDownCount = 3, faceUpCount = 4)
        val generous = computeTableauOverlap(availableHeight = 1200.dp, cardHeight = 140.dp, faceDownCount = 3, faceUpCount = 4)
        assertTrue(generous.faceUpStep >= tight.faceUpStep)
        // Cards always stay stacked: the step never reaches full card height, however much
        // vertical room is available, so a covered card is always partly covered. The cap
        // moved from a third of the card to about half when portrait's spare height went to
        // the fan instead of to empty table.
        assertTrue(generous.faceUpStep <= 140.dp * 0.52f)
    }

    @Test
    fun theStepOntoTheFirstFaceUpCardUsesTheFaceDownBandNotFaceUp() {
        // One face-up card sitting on a stack of face-down cards: the column's only
        // step is the reveal transition, which uses the face-down band's growth
        // rules (none) rather than the face-up band's — so even with generous
        // available height, the reported face-up step stays exactly at its floor,
        // since there is no true face-up-to-face-up step to grow.
        val overlap = computeTableauOverlap(availableHeight = 900.dp, cardHeight = 140.dp, faceDownCount = 3, faceUpCount = 1)
        assertEquals(MIN_FACE_UP_BAND, overlap.faceUpStep)
    }

    @Test
    fun singleCardColumnUsesFaceUpBand() {
        val overlap = computeTableauOverlap(availableHeight = 800.dp, cardHeight = 140.dp, faceDownCount = 0, faceUpCount = 1)
        assertTrue(overlap.faceUpStep >= MIN_FACE_UP_BAND)
    }

    @Test
    fun worstCaseColumnFitsALandscapePhoneHeight() {
        // The reported failure: in landscape, a column carrying every face-down card
        // under a full King-to-Ace sequence ran off the bottom of the screen. Card size is
        // fitted to width there, which on a wide screen leaves a card too tall for the
        // 19-card column the same board allows.
        val availableHeight = 400.dp // a typical landscape phone board area
        val widthFitted = 129.dp // what 9 column-equivalents across ~890 dp yields
        val height = minOf(widthFitted, cardHeightFittingTallestColumn(availableHeight, MAX_TABLEAU_COLUMN_CARDS))
        val overlap = computeTableauOverlap(
            availableHeight = availableHeight,
            cardHeight = height,
            faceDownCount = MAX_FACE_DOWN_IN_COLUMN,
            faceUpCount = MAX_TABLEAU_COLUMN_CARDS - MAX_FACE_DOWN_IN_COLUMN,
        )
        val used = height +
            overlap.faceDownStep * MAX_FACE_DOWN_IN_COLUMN +
            overlap.faceUpStep * (MAX_TABLEAU_COLUMN_CARDS - MAX_FACE_DOWN_IN_COLUMN - 1)
        assertTrue("column used $used of $availableHeight", used <= availableHeight)
    }

    @Test
    fun fittingTheTallestColumnNeverGoesBelowTheMinimumCardWidth() {
        // Below roughly 368 dp of board height the worst-case column cannot fit at any
        // legible card size. The fit bottoms out at the minimum card rather than
        // returning something unreadable, and the caller then overflows — the same
        // accepted trade-off computeTableauOverlap already documents.
        val height = cardHeightFittingTallestColumn(120.dp, MAX_TABLEAU_COLUMN_CARDS)
        assertEquals(MIN_CARD_WIDTH * CARD_WIDTH_TO_HEIGHT_RATIO, height)
    }

    @Test
    fun aShortColumnDoesNotShrinkTheCard() {
        // Nothing to fit: a board whose tallest column is one card imposes no cap
        // beyond what the height could hold anyway.
        val forOneCard = cardHeightFittingTallestColumn(360.dp, 1)
        val forFullColumn = cardHeightFittingTallestColumn(360.dp, MAX_TABLEAU_COLUMN_CARDS)
        assertTrue(forOneCard > forFullColumn)
    }

    private companion object {
        const val TABLEAU_COLUMNS_FOR_TEST = 7
    }
}
