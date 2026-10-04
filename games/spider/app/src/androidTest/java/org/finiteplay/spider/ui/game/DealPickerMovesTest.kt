package org.finiteplay.spider.ui.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import org.finiteplay.core.storage.DealProgress
import org.finiteplay.core.storage.DealStatus
import org.finiteplay.core.ui.layout.DealPickerDialog
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The shared deal picker beside each deal's status states its moves: the fewest of any win for a
 * won deal, and those of the game last played for one not won yet. Rendered directly, since the
 * list is the same in every solitaire.
 */
class DealPickerMovesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(progress: Map<Int, DealProgress>) {
        composeRule.setContent {
            FinitePlayTheme { DealPickerDialog(dealCount = 6, currentNumber = null, progress = progress, onSelect = {}, onDismiss = {}) }
        }
    }

    @Test
    fun aWonDealShowsItsBestMovesAndAPlayedDealTheMovesLastPlayed() {
        show(
            mapOf(
                1 to DealProgress(DealStatus.WON, 120),
                2 to DealProgress(DealStatus.PLAYED, 7),
            ),
        )
        composeRule.onNodeWithTag("deal_row_1_moves", useUnmergedTree = true).assertIsDisplayed().assertTextEquals("Moves, best: 120")
        composeRule.onNodeWithTag("deal_row_2_moves", useUnmergedTree = true).assertIsDisplayed().assertTextEquals("Moves, last: 7")
    }

    @Test
    fun aDealWithNoMovesKnownOrNotPlayedShowsNoMovesLine() {
        show(mapOf(1 to DealProgress(DealStatus.WON, 0), 2 to DealProgress(DealStatus.PLAYED, 0)))
        for (row in 1..3) {
            assertEquals(
                "deal $row has no moves to show",
                0,
                composeRule.onAllNodesWithTag("deal_row_${row}_moves", useUnmergedTree = true).fetchSemanticsNodes().size,
            )
        }
    }
}
