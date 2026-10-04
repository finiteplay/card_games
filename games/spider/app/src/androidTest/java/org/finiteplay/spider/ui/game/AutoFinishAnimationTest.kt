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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private val VERSIONS = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

/**
 * When the automatic finish itself has more than the player's own last move left to play — unlike
 * `RowDealAndWinTest`'s win test, where the player's own tap completes the run directly — its
 * moves now commit through the same path an ordinary move does (`SpiderViewModel.commit`), one at
 * a time with a pause standing in for the flight between them, rather than jumping straight to the
 * won board the instant the search resolves (`SpiderViewModel.runAutoFinishIfAvailable`).
 *
 * Column 0 holds King down to Three face up (eleven cards, the automatic finish only ever applies
 * with thirteen or fewer left in play, so the fixture's own count has to hold to that exactly);
 * column 1 the Ace; column 2 the Two, immediately to the Ace's right — the nearest legal column
 * (`resolveTap`'s "nearest legal column to the right, or leftmost when none"), ahead of the empty
 * ones further along. That tap alone does not finish the game — the resulting Two-Ace pair still
 * needs to reach column 0 — so the automatic finish's own search, not the player's tap, is what
 * completes and banks the run.
 */
class AutoFinishAnimationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var viewModel: SpiderViewModel

    @Test
    fun theAutomaticFinishPlaysOutRatherThanJumpingStraightToWon() {
        val run = (Rank.KING.value downTo Rank.THREE.value).map { v -> Card(Suit.SPADES, Rank.entries.first { it.value == v }) }
        val state = SpiderState(
            tableau = (0 until TABLEAU_COLUMNS).map { i ->
                when (i) {
                    0 -> run.map { TableauCard(it, faceUp = true) }
                    1 -> listOf(TableauCard(Card(Suit.SPADES, Rank.ACE), faceUp = true))
                    2 -> listOf(TableauCard(Card(Suit.SPADES, Rank.TWO), faceUp = true))
                    else -> emptyList()
                }
            },
            stock = emptyList(),
            banked = Suit.entries.associateWith { if (it == Suit.SPADES) 7 else 0 },
            suitCount = SuitCount.ONE,
            seed = 0L,
            versions = VERSIONS,
            moveCount = 0,
            status = GameStatus.IN_PROGRESS,
        )
        viewModel = SpiderViewModel(initialSeed = 1L)
        viewModel.loadFixtureForDebugging(state)
        composeRule.setContent { FinitePlayTheme { GameScreen(viewModel = viewModel) } }

        composeRule.onNodeWithTag("card_1_0").performClick()

        // The automatic finish has its own move left to play (the Two-Ace pair onto column 0) —
        // it should actually be under way, not already resolved in the same instant as the tap.
        // The reducer itself commits synchronously — `status` is already WON the instant that move
        // lands, same as any other win — so it is the win dialog, not the status, that has to wait
        // on the flight: it must not appear until the automatic finish actually finishes.
        composeRule.waitUntil(timeoutMillis = 5_000) { viewModel.isAutoFinishing }
        composeRule.onNodeWithTag("win_dialog").assertDoesNotExist()

        composeRule.waitUntil(timeoutMillis = 10_000) { !viewModel.isAutoFinishing }
        composeRule.waitForIdle()

        assertEquals(GameStatus.WON, viewModel.session.state.status)
        assertEquals(8, viewModel.session.state.banked.getValue(Suit.SPADES))
        composeRule.onNodeWithTag("win_dialog").assertIsDisplayed()
    }
}
