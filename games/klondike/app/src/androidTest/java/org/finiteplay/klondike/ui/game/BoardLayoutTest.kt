package org.finiteplay.klondike.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import org.finiteplay.klondike.board.GameVersions
import org.finiteplay.klondike.session.GameSession
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.junit.Rule
import org.junit.Test

/**
 * `EXECUTION_PLAN.md` A1 gate: layout assertions hold at 320 dp and 360 dp widths.
 * `CardGeometryTest` (`:app:testDebugUnitTest`) proves the underlying numbers; this
 * proves the board actually renders and stays interactive-node-discoverable at both
 * widths on a real Compose tree. Requires a device/emulator to execute.
 */
class BoardLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun rendersStockAndAllSevenColumnsAtWidth(widthDp: Int) {
        val session = GameSession.start(seed = 42L, versions = GameVersions(1, 1, 1), automaticMovesEnabled = false)
        composeRule.setContent {
            FinitePlayTheme {
                Box(Modifier.width(widthDp.dp).height(640.dp)) {
                    Board(state = session.state, onCommitMove = { false })
                }
            }
        }

        // Raw deal, automation off: stock always holds all 24 undealt cards.
        composeRule.onNodeWithContentDescription("Stock, 24 cards").assertIsDisplayed()
        for (column in 1..7) {
            composeRule.onAllNodesWithContentDescription("tableau column $column", substring = true).onFirst().assertIsDisplayed()
        }
    }

    @Test
    fun rendersAt320Dp() = rendersStockAndAllSevenColumnsAtWidth(320)

    @Test
    fun rendersAt360Dp() = rendersStockAndAllSevenColumnsAtWidth(360)
}
