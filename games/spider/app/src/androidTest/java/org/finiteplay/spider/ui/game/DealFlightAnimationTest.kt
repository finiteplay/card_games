package org.finiteplay.spider.ui.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.spider.layout.GameStatus
import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.TABLEAU_COLUMNS
import org.finiteplay.spider.layout.TableauCard
import org.junit.Rule
import org.junit.Test

private val VERSIONS = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

/**
 * A row deal now flies its ten cards from the stock down to their columns, staggered left to
 * right, the same way an ordinary tableau move and a bank flight to the counter both already fly
 * (`MoveFlightAnimationTest`, `RowDealAndWinTest`'s bank-flight test), rather than the row simply
 * appearing on the board the instant the stock is tapped.
 */
class DealFlightAnimationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var viewModel: SpiderViewModel

    @Test
    fun tappingStockFliesEachDealtCardToItsColumnBeforeItLandsThere() {
        val state = SpiderState(
            tableau = (0 until TABLEAU_COLUMNS).map { listOf(TableauCard(Card(Suit.SPADES, Rank.KING), faceUp = true)) },
            stock = (0 until TABLEAU_COLUMNS).map { Card(Suit.HEARTS, Rank.ACE) },
            banked = Suit.entries.associateWith { 0 },
            suitCount = SuitCount.ONE,
            seed = 0L,
            versions = VERSIONS,
            moveCount = 0,
            status = GameStatus.IN_PROGRESS,
        )
        viewModel = SpiderViewModel(initialSeed = 1L)
        viewModel.loadFixtureForDebugging(state)
        composeRule.setContent { FinitePlayTheme { GameScreen(viewModel = viewModel) } }
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag("stock").performClick()
        composeRule.mainClock.advanceTimeByFrame()

        // Committed already (the reducer is synchronous), but the last column's own new card —
        // launched last, per the same left-to-right stagger a bank flight uses ace to king — has
        // barely had time to leave the stock.
        composeRule.onNodeWithTag("card_${TABLEAU_COLUMNS - 1}_1").assertDoesNotExist()

        // Comfortably past a full ten-card staggered group (each leg bounded to 450 ms).
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        for (column in 0 until TABLEAU_COLUMNS) {
            composeRule.onNodeWithTag("card_${column}_1").assertIsDisplayed()
        }
    }
}
