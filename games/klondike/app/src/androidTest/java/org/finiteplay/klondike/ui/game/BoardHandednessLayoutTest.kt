package org.finiteplay.klondike.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import org.finiteplay.klondike.board.GameVersions
import org.finiteplay.klondike.session.GameSession
import org.finiteplay.klondike.storage.Handedness
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.finiteplay.core.ui.layout.BoardOrientation

/**
 * `docs/games/klondike/UI_SPEC.md` "Left-Handed Layout": stock/waste and the foundation group swap
 * sides with [Handedness], in both orientations, while tableau column order (proven
 * unaffected by [BoardLayoutTest]) is untouched.
 */
class BoardHandednessLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setBoard(orientation: BoardOrientation, handedness: Handedness, widthDp: Int = 640, heightDp: Int = 360) {
        val session = GameSession.start(seed = 42L, versions = GameVersions(1, 1, 1), automaticMovesEnabled = false)
        composeRule.setContent {
            FinitePlayTheme {
                Box(Modifier.width(widthDp.dp).height(heightDp.dp)) {
                    Board(state = session.state, onCommitMove = { false }, orientation = orientation, handedness = handedness)
                }
            }
        }
    }

    private fun stockLeftOf(): Float =
        composeRule.onNodeWithContentDescription("Stock, 24 cards").fetchSemanticsNode().boundsInRoot.left

    private fun clubsFoundationLeftOf(): Float =
        composeRule.onNodeWithContentDescription("Clubs foundation, empty").fetchSemanticsNode().boundsInRoot.left

    @Test
    fun portraitRightHandedPutsStockRightOfFoundations() {
        setBoard(BoardOrientation.PORTRAIT, Handedness.RIGHT, widthDp = 360, heightDp = 640)
        assertTrue(stockLeftOf() > clubsFoundationLeftOf())
    }

    @Test
    fun portraitLeftHandedPutsStockLeftOfFoundations() {
        setBoard(BoardOrientation.PORTRAIT, Handedness.LEFT, widthDp = 360, heightDp = 640)
        assertTrue(stockLeftOf() < clubsFoundationLeftOf())
    }

    @Test
    fun landscapeRightHandedPutsStockRightOfFoundations() {
        setBoard(BoardOrientation.LANDSCAPE, Handedness.RIGHT)
        assertTrue(stockLeftOf() > clubsFoundationLeftOf())
    }

    @Test
    fun landscapeLeftHandedPutsStockLeftOfFoundations() {
        setBoard(BoardOrientation.LANDSCAPE, Handedness.LEFT)
        assertTrue(stockLeftOf() < clubsFoundationLeftOf())
    }
}
