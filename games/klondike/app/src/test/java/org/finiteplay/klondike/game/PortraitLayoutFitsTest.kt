package org.finiteplay.klondike.game

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.finiteplay.klondike.MAX_FACE_DOWN_IN_COLUMN
import org.finiteplay.klondike.MAX_TABLEAU_COLUMN_CARDS
import org.finiteplay.klondike.MIN_COLUMN_SPACING
import org.finiteplay.klondike.TOP_ROW_TO_TABLEAU_GAP
import org.finiteplay.klondike.computeTableauOverlap
import org.finiteplay.klondike.portraitBoardLayout
import org.finiteplay.klondike.portraitStockWasteAtBottom
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The deepest column the rules allow must fit the board area it is drawn in, on every screen
 * the app ships to — including the nearly square ones, where cards are sized off a wide edge
 * and the board is no taller than a phone's.
 *
 * This is the shape that produced the reported bug: on an unfolded foldable the stock and waste
 * row at the foot of the board ended up against tableau columns six and seven. A screenshot
 * catches that for one deal on one screen; this catches it for the worst board the rules permit
 * on all of them, which is what the layout has to survive.
 */
class PortraitLayoutFitsTest {

    /** Width and height in dp of the board area — the space between status row and action bar. */
    private val boardAreas = listOf(
        "small phone" to (360.dp to 480.dp),
        "compact phone" to (411.dp to 560.dp),
        "modern phone" to (393.dp to 640.dp),
        "large phone" to (448.dp to 700.dp),
        "foldable inner" to (763.dp to 600.dp),
        "tablet 8in" to (800.dp to 900.dp),
        "tablet 10in" to (1029.dp to 1200.dp),
    )

    @Test
    fun `the deepest column fits the tableau lane on every shipped screen size`() {
        for ((name, size) in boardAreas) {
            val (width, height) = size
            val layout = portraitBoardLayout(width, height, tallestColumn = MAX_TABLEAU_COLUMN_CARDS)
            val card = layout.card
            val lane = layout.tableauLane

            val faceUp = MAX_TABLEAU_COLUMN_CARDS - MAX_FACE_DOWN_IN_COLUMN
            val overlap = computeTableauOverlap(lane, card.height, MAX_FACE_DOWN_IN_COLUMN, faceUp)
            val drawn = columnHeight(card.height, overlap.faceDownStep, overlap.faceUpStep, faceUp)

            // The lane is what the board reserves for the tableau; a column taller than it is a
            // column drawn over whatever the layout put below.
            assertTrue(
                "$name: a $MAX_TABLEAU_COLUMN_CARDS-card column draws $drawn into a $lane lane",
                drawn <= lane,
            )
        }
    }

    @Test
    fun `a nearly square screen seats stock and waste in the top row instead`() {
        // The foldable is the case that fails the other way: give it a bottom row and the two
        // rows plus any column at all no longer fit.
        assertTrue(portraitStockWasteAtBottom(411.dp, 560.dp))
        assertTrue(!portraitStockWasteAtBottom(763.dp, 600.dp))
    }

    private fun columnHeight(cardHeight: Dp, faceDownStep: Dp, faceUpStep: Dp, faceUpCount: Int): Dp {
        // The step onto the first face-up card uses the face-down band, matching the board.
        val faceDownSteps = MAX_FACE_DOWN_IN_COLUMN
        val faceUpSteps = (faceUpCount - 1).coerceAtLeast(0)
        return cardHeight + faceDownStep * faceDownSteps + faceUpStep * faceUpSteps
    }
}
