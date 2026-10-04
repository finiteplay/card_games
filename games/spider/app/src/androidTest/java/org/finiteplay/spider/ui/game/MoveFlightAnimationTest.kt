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
 * An ordinary tableau move (no bank involved) now flies its cards from source to destination the
 * same way a completed run flies to its banked counter (`RowDealAndWinTest`'s own bank-flight
 * test), rather than jumping the board straight to the post-move board. Column 0 holds a lone Nine
 * of spades, column 1 a lone Ten of spades — the only legal destination for the Nine, and the
 * nearest column to the right of it, so tapping it resolves unambiguously (`resolveTap`).
 */
class MoveFlightAnimationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var viewModel: SpiderViewModel

    @Test
    fun tappingACardFliesItToItsDestinationBeforeItLandsThere() {
        val state = SpiderState(
            tableau = (0 until TABLEAU_COLUMNS).map { i ->
                when (i) {
                    0 -> listOf(TableauCard(Card(Suit.SPADES, Rank.NINE), faceUp = true))
                    1 -> listOf(TableauCard(Card(Suit.SPADES, Rank.TEN), faceUp = true))
                    else -> emptyList()
                }
            },
            stock = emptyList(),
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

        composeRule.onNodeWithTag("card_0_0").performClick()
        composeRule.mainClock.advanceTimeByFrame()

        // Committed already (the reducer is synchronous), but the Nine has not visibly landed on
        // the Ten yet — the flight has barely started.
        composeRule.onNodeWithTag("card_1_1").assertDoesNotExist()

        // Comfortably past even a full-board-diagonal flight (bounded to 450 ms).
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("card_1_1").assertIsDisplayed()
        composeRule.onNodeWithTag("card_0_0").assertDoesNotExist()
    }
}
