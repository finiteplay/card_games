package org.finiteplay.spider.ui.game

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
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
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

private val VERSIONS = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

/**
 * S3a's gate calls for the row-deal-refusal and win paths verified on device, on top of the
 * gesture coverage `GameScreenTest` already exercises (`docs/games/spider/EXECUTION_PLAN.md`).
 * Both need a specific board neither a fresh deal nor a short sequence of real gestures reaches
 * reliably — an empty column, or a game one move from won — so each test builds its own
 * [SpiderState] directly through the real reducer's own types and assigns it to the view model,
 * the same way `AutoFinishCostTest` and `CertifyTest` build fixtures for the rules layer.
 */
class RowDealAndWinTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var viewModel: SpiderViewModel

    private fun setContent(state: SpiderState, landscape: Boolean = false) {
        viewModel = SpiderViewModel(initialSeed = 1L)
        viewModel.loadFixtureForDebugging(state)
        composeRule.setContent {
            val configuration = if (landscape) {
                Configuration(LocalConfiguration.current).apply { orientation = Configuration.ORIENTATION_LANDSCAPE }
            } else {
                LocalConfiguration.current
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                FinitePlayTheme { GameScreen(viewModel = viewModel) }
            }
        }
    }

    // A bank is always a single-suit King-to-Ace run and completing the eighth one wins
    // regardless of suit count (SEQUENCES_TO_WIN is a flat 8, independent of how many suits the
    // deck was dealt from) — so the same fixture shape proves the win path at TWO and FOUR too,
    // just by naming a different [suitCount] here, not by rebuilding it.
    private fun boardOf(columns: List<List<Card>>, banked: Int, stockRows: Int, suitCount: SuitCount = SuitCount.ONE): SpiderState = SpiderState(
        tableau = (0 until TABLEAU_COLUMNS).map { i ->
            columns.getOrElse(i) { emptyList() }.map { TableauCard(it, faceUp = true) }
        },
        stock = List(stockRows * TABLEAU_COLUMNS) { Card(Suit.SPADES, Rank.ACE) },
        banked = Suit.entries.associateWith { if (it == Suit.SPADES) banked else 0 },
        suitCount = suitCount,
        seed = 0L,
        versions = VERSIONS,
        moveCount = 0,
        status = GameStatus.IN_PROGRESS,
    )

    @Test
    fun tappingStockWithAnEmptyColumnRefusesRatherThanDealing() {
        // Column 0 is empty; the row deal is illegal while any column is (`RULES.md` "The stock"),
        // so tapping stock must refuse rather than silently dealing onto nine columns and leaving
        // the tenth without a card the row-deal invariant requires every column to have.
        val state = boardOf(
            columns = listOf(emptyList(), listOf(Card(Suit.SPADES, Rank.KING))),
            banked = 0,
            stockRows = 1,
        )
        setContent(state)
        val rowDealsBefore = viewModel.session.state.rowDealsRemaining

        composeRule.onNodeWithTag("stock").performClick()
        composeRule.waitForIdle()

        assertEquals(rowDealsBefore, viewModel.session.state.rowDealsRemaining)
        assertEquals(0, viewModel.session.state.moveCount)
        assertFalse("column 0 must still be empty; a deal must not have happened", viewModel.session.state.tableau[0].isNotEmpty())
    }

    @Test
    fun tappingACardWithNowhereLegalToGoLeavesTheBoardUnchanged() {
        // Ten columns, each a lone King face up: no empty column exists for it to move to, and
        // nothing on the board accepts a King (`RULES.md` "Building"), so every column's tap has
        // no legal destination at all. `RULES.md` "The stock" / `DESIGN.md` "Interaction": an
        // invalid tap is a no-op, not a partial or silently-corrected move.
        val king = listOf(Card(Suit.SPADES, Rank.KING))
        val state = boardOf(columns = List(TABLEAU_COLUMNS) { king }, banked = 0, stockRows = 0)
        setContent(state)
        val before = viewModel.session.state

        composeRule.onNodeWithTag("card_0_0").performClick()
        composeRule.waitForIdle()

        assertEquals(before.tableau, viewModel.session.state.tableau)
        assertEquals(before.moveCount, viewModel.session.state.moveCount)
    }

    @Test
    fun completingTheLastSequenceShowsTheWinDialog() {
        // Seven of eight sequences already banked; column 0 holds King down to Two of spades
        // face up, column 1 holds only the Ace. Moving the Ace onto the Two completes the eighth
        // King-to-Ace run, which the reducer banks immediately — the game is won by that move
        // alone, with no auto-finish or cascade needed to get there. Every other column holds a
        // lone King, which an Ace can never legally land on, so the Two in column 0 is the tap's
        // only legal destination regardless of which direction it searches first.
        val run = (Rank.KING.value downTo Rank.TWO.value).map { Card(Suit.SPADES, Rank.entries.first { r -> r.value == it }) }
        val blocker = listOf(Card(Suit.SPADES, Rank.KING))
        val state = boardOf(
            columns = listOf(run, listOf(Card(Suit.SPADES, Rank.ACE))) + List(TABLEAU_COLUMNS - 2) { blocker },
            banked = 7,
            stockRows = 0,
        )
        setContent(state)

        composeRule.onNodeWithTag("card_1_0").performClick()
        composeRule.waitForIdle()

        assertEquals(GameStatus.WON, viewModel.session.state.status)
        assertEquals(8, viewModel.session.state.banked.getValue(Suit.SPADES))
        composeRule.onNodeWithTag("win_dialog").assertIsDisplayed()
    }

    @Test
    fun theWinConfettiNeverBlocksTheWinDialogsButtons() {
        // Confetti is a second, transparent window over the win dialog. It must pass every touch
        // through, or the player could not start another game while it falls.
        val run = (Rank.KING.value downTo Rank.TWO.value).map { Card(Suit.SPADES, Rank.entries.first { r -> r.value == it }) }
        val blocker = listOf(Card(Suit.SPADES, Rank.KING))
        val state = boardOf(
            columns = listOf(run, listOf(Card(Suit.SPADES, Rank.ACE))) + List(TABLEAU_COLUMNS - 2) { blocker },
            banked = 7,
            stockRows = 0,
        )
        setContent(state)
        composeRule.onNodeWithTag("card_1_0").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("win_dialog").assertIsDisplayed()
        // The win banner is up beside the dialog, and stays up for as long as the dialog does.
        composeRule.onNodeWithTag("result_banner").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(10_000)
        composeRule.onNodeWithTag("result_banner").assertIsDisplayed()

        // Straight away, while the confetti is still in the air.
        composeRule.onNodeWithText("New Game").performClick()
        composeRule.waitForIdle()

        assertEquals(0, composeRule.onAllNodesWithTag("win_dialog").fetchSemanticsNodes().size)
        assertEquals("the banner goes with the dialog", 0, composeRule.onAllNodesWithTag("result_banner").fetchSemanticsNodes().size)
    }

    @Test
    fun completingTheLastSequenceFliesEachCardToTheFoundationBeforeTheColumnEmpties() {
        // Same fixture as completingTheLastSequenceShowsTheWinDialog: moving the Ace onto column
        // 0's Two completes the run there. The King (index 0, launches last per "ace first, king
        // last") should still be on screen the instant after the move commits — the run has not
        // vanished all at once — and gone once the whole flight has had time to finish.
        val run = (Rank.KING.value downTo Rank.TWO.value).map { Card(Suit.SPADES, Rank.entries.first { r -> r.value == it }) }
        val blocker = listOf(Card(Suit.SPADES, Rank.KING))
        val state = boardOf(
            columns = listOf(run, listOf(Card(Suit.SPADES, Rank.ACE))) + List(TABLEAU_COLUMNS - 2) { blocker },
            banked = 7,
            stockRows = 0,
        )
        setContent(state)
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag("card_1_0").performClick()
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag("card_0_0").assertIsDisplayed()

        // Comfortably past even a full-board flight length (13 cards, staggered, each bounded to
        // 450ms): the whole run should be banked and gone from the board by now.
        composeRule.mainClock.advanceTimeBy(5_000)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("card_0_0").assertDoesNotExist()
        assertEquals(GameStatus.WON, viewModel.session.state.status)
    }

    @Test
    fun completingTheLastSequenceShowsTheWinDialogInLandscapeToo() {
        // Landscape composes a different branch of GameScreen entirely — stock beside the
        // columns, action rails instead of a bottom bar — so the win path needs its own check
        // there rather than trusting the portrait test covers both.
        val run = (Rank.KING.value downTo Rank.TWO.value).map { Card(Suit.SPADES, Rank.entries.first { r -> r.value == it }) }
        val blocker = listOf(Card(Suit.SPADES, Rank.KING))
        val state = boardOf(
            columns = listOf(run, listOf(Card(Suit.SPADES, Rank.ACE))) + List(TABLEAU_COLUMNS - 2) { blocker },
            banked = 7,
            stockRows = 0,
        )
        setContent(state, landscape = true)

        composeRule.onNodeWithTag("card_1_0").performClick()
        composeRule.waitForIdle()

        assertEquals(GameStatus.WON, viewModel.session.state.status)
        composeRule.onNodeWithTag("win_dialog").assertIsDisplayed()
    }

    // S3a's gate calls for "a deal at each suit count ... played to a win by tap and drag, in
    // both orientations" — the four tests below are TWO's and FOUR's own copies of the two ONE
    // tests above, closing the "for every suit count" half of that wording the same way the
    // rest of this file already does it: a hand-built near-win fixture and a real tap, not a
    // played-out ~100-500 move game (this codebase's solved lines are that long at every suit
    // count — no game here proves a win path with a full-length real deal; Klondike's and
    // FreeCell's own win tests use the identical short-fixture shape).

    @Test
    fun completingTheLastSequenceShowsTheWinDialogAtTwoSuits() {
        val run = (Rank.KING.value downTo Rank.TWO.value).map { Card(Suit.SPADES, Rank.entries.first { r -> r.value == it }) }
        val blocker = listOf(Card(Suit.SPADES, Rank.KING))
        val state = boardOf(
            columns = listOf(run, listOf(Card(Suit.SPADES, Rank.ACE))) + List(TABLEAU_COLUMNS - 2) { blocker },
            banked = 7,
            stockRows = 0,
            suitCount = SuitCount.TWO,
        )
        setContent(state)

        composeRule.onNodeWithTag("card_1_0").performClick()
        composeRule.waitForIdle()

        assertEquals(GameStatus.WON, viewModel.session.state.status)
        assertEquals(8, viewModel.session.state.banked.getValue(Suit.SPADES))
        composeRule.onNodeWithTag("win_dialog").assertIsDisplayed()
    }

    @Test
    fun completingTheLastSequenceShowsTheWinDialogAtTwoSuitsInLandscape() {
        val run = (Rank.KING.value downTo Rank.TWO.value).map { Card(Suit.SPADES, Rank.entries.first { r -> r.value == it }) }
        val blocker = listOf(Card(Suit.SPADES, Rank.KING))
        val state = boardOf(
            columns = listOf(run, listOf(Card(Suit.SPADES, Rank.ACE))) + List(TABLEAU_COLUMNS - 2) { blocker },
            banked = 7,
            stockRows = 0,
            suitCount = SuitCount.TWO,
        )
        setContent(state, landscape = true)

        composeRule.onNodeWithTag("card_1_0").performClick()
        composeRule.waitForIdle()

        assertEquals(GameStatus.WON, viewModel.session.state.status)
        composeRule.onNodeWithTag("win_dialog").assertIsDisplayed()
    }

    @Test
    fun completingTheLastSequenceShowsTheWinDialogAtFourSuits() {
        val run = (Rank.KING.value downTo Rank.TWO.value).map { Card(Suit.SPADES, Rank.entries.first { r -> r.value == it }) }
        val blocker = listOf(Card(Suit.SPADES, Rank.KING))
        val state = boardOf(
            columns = listOf(run, listOf(Card(Suit.SPADES, Rank.ACE))) + List(TABLEAU_COLUMNS - 2) { blocker },
            banked = 7,
            stockRows = 0,
            suitCount = SuitCount.FOUR,
        )
        setContent(state)

        composeRule.onNodeWithTag("card_1_0").performClick()
        composeRule.waitForIdle()

        assertEquals(GameStatus.WON, viewModel.session.state.status)
        assertEquals(8, viewModel.session.state.banked.getValue(Suit.SPADES))
        composeRule.onNodeWithTag("win_dialog").assertIsDisplayed()
    }

    @Test
    fun completingTheLastSequenceShowsTheWinDialogAtFourSuitsInLandscape() {
        val run = (Rank.KING.value downTo Rank.TWO.value).map { Card(Suit.SPADES, Rank.entries.first { r -> r.value == it }) }
        val blocker = listOf(Card(Suit.SPADES, Rank.KING))
        val state = boardOf(
            columns = listOf(run, listOf(Card(Suit.SPADES, Rank.ACE))) + List(TABLEAU_COLUMNS - 2) { blocker },
            banked = 7,
            stockRows = 0,
            suitCount = SuitCount.FOUR,
        )
        setContent(state, landscape = true)

        composeRule.onNodeWithTag("card_1_0").performClick()
        composeRule.waitForIdle()

        assertEquals(GameStatus.WON, viewModel.session.state.status)
        composeRule.onNodeWithTag("win_dialog").assertIsDisplayed()
    }
}
