package org.finiteplay.spider.ui.game

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
import org.finiteplay.spider.rules.isLegal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private val VERSIONS = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

/**
 * The solver-backed "leads to the win" hint mode (`docs/games/spider/UI_SPEC.md` "Hint"), offered
 * only at [SuitCount.ONE] (`docs/games/spider/DESIGN.md` "Hint" explains the measurement behind
 * that limit). A near-solved one-suit board — one card from banking a whole run — resolves near-
 * instantly and deterministically, the same reason Klondike's and FreeCell's own hint-flow tests
 * use a fixture rather than a fresh deal.
 */
class HintFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** One suit, twelve of the thirteen ranks already run down King to Two in column 0; the Ace
     * sits alone in column 1, one move from banking the whole run. Seven of the eight sequences a
     * one-suit game needs are already credited, so banking this eighth one actually wins the game
     * — a hint's [HintOutcome.Guidance] is a proven step toward a real win, not merely toward
     * completing one more sequence, so a fixture with nothing banked yet is not "near-win" at all:
     * an otherwise-empty board could never reach eight bankings from here, and the search correctly
     * (if confusingly, the first time this went wrong) reports [HintOutcome.NoSolution] for it. */
    private fun nearWinOneSuitState(): SpiderState {
        val king = Rank.entries.reversed().dropLast(1) // KING..TWO, twelve cards
        return SpiderState(
            tableau = (0 until TABLEAU_COLUMNS).map { i ->
                when (i) {
                    0 -> king.map { TableauCard(Card(Suit.SPADES, it), faceUp = true) }
                    1 -> listOf(TableauCard(Card(Suit.SPADES, Rank.ACE), faceUp = true))
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
    }

    @Test
    fun requestingAHintShowsALegalGuidedMoveAndAMoveClearsIt() {
        val viewModel = SpiderViewModel(initialSeed = 1L)
        viewModel.loadFixtureForDebugging(nearWinOneSuitState())
        composeRule.setContent {
            FinitePlayTheme { GameScreen(viewModel = viewModel) }
        }

        composeRule.onNodeWithTag("action_hint").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { viewModel.hintState !is HintUiState.Loading }

        val shown = viewModel.hintState
        check(shown is HintUiState.Guided) { "expected Guided, got $shown" }
        assertTrue("the guided move must be legal against the board it was computed from", isLegal(viewModel.session.state, shown.move))
        assertTrue("a guided move highlights something on the board", viewModel.hintedCards.isNotEmpty() || viewModel.hintedStock)

        viewModel.tapCard(1, 0)
        composeRule.waitForIdle()

        assertEquals(HintUiState.Hidden, viewModel.hintState)
        assertEquals(emptySet<Any>(), viewModel.hintedCards)
    }

    @Test
    fun requestingAgainWhileOneIsAlreadyShowingIsANoOp() {
        val viewModel = SpiderViewModel(initialSeed = 1L)
        viewModel.loadFixtureForDebugging(nearWinOneSuitState())
        composeRule.setContent {
            FinitePlayTheme { GameScreen(viewModel = viewModel) }
        }

        composeRule.onNodeWithTag("action_hint").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { viewModel.hintState !is HintUiState.Loading }
        val first = viewModel.hintState

        composeRule.onNodeWithTag("action_hint").performClick()
        composeRule.waitForIdle()

        assertEquals("a second request while one is showing must change nothing", first, viewModel.hintState)
    }

    @Test
    fun undoClearsAShownHint() {
        val viewModel = SpiderViewModel(initialSeed = 1L)
        viewModel.loadFixtureForDebugging(nearWinOneSuitState())
        viewModel.tapCard(0, 0)

        composeRule.setContent {
            FinitePlayTheme { GameScreen(viewModel = viewModel) }
        }
        composeRule.onNodeWithTag("action_hint").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { viewModel.hintState !is HintUiState.Loading }

        composeRule.onNodeWithTag("action_undo").performClick()
        composeRule.waitForIdle()

        assertEquals(HintUiState.Hidden, viewModel.hintState)
    }

    /** Two suits with no certified catalog wired (`loadFixtureForDebugging` bypasses it entirely,
     * so [SpiderViewModel.dealIsCertified] is false): the guided mode is only offered at two suits
     * for a certified deal — a live search there fails more often than it succeeds (`DESIGN.md`
     * "Hint") — so an uncertified two-suit fixture still falls back to the plain highlight,
     * whatever the setting says. */
    @Test
    fun twoSuitGameUsesTheHighlightHintEvenWithTheSettingOn() {
        val viewModel = SpiderViewModel(initialSeed = 1L)
        viewModel.setHintShowsWinningMove(true)
        val twoSuitState = nearWinOneSuitState().copy(suitCount = SuitCount.TWO)
        viewModel.loadFixtureForDebugging(twoSuitState)
        composeRule.setContent {
            FinitePlayTheme { GameScreen(viewModel = viewModel) }
        }

        composeRule.onNodeWithTag("action_hint").performClick()
        composeRule.waitForIdle()

        assertEquals(HintUiState.Hidden, viewModel.hintState)
        assertTrue(viewModel.hintedCards.isNotEmpty() || viewModel.hintedStock)
    }
}
