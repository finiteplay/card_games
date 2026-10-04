package org.finiteplay.freecell.ui.game

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.freecell.layout.GameStatus
import org.finiteplay.freecell.layout.GameVersions
import org.finiteplay.freecell.rules.Move
import org.finiteplay.freecell.rules.isLegal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * `docs/games/klondike/DESIGN.md` "Interaction": tapping the exact pile a shown hint currently
 * highlights commits the hint's own move instead of the standard tap-priority pick, since the two
 * can disagree. Klondike's own `BoardHintTapOverrideTest` proves this for Klondike; this is
 * FreeCell's copy of the same proof, now that `FreeCellBoard`'s tap handlers carry the same
 * override.
 */
class BoardHintTapOverrideTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun tableauCardWithTwoLegalDestinationsState(): FreeCellState {
        val tableau = List(8) { column ->
            when (column) {
                0 -> listOf(Card(Suit.CLUBS, Rank.SIX))
                2 -> listOf(Card(Suit.HEARTS, Rank.SEVEN)) // lowest-index legal rightward destination
                4 -> listOf(Card(Suit.DIAMONDS, Rank.SEVEN)) // a further, also-legal destination
                else -> emptyList()
            }
        }
        return FreeCellState(
            seed = 0L,
            versions = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1),
            tableau = tableau,
            freeCells = listOf(null, null, null, null),
            foundations = Suit.entries.associateWith { 0 },
            status = GameStatus.IN_PROGRESS,
        )
    }

    @Test
    fun tappingTheHintHighlightedTableauCardCommitsTheHintsDestinationInsteadOfTheLowestIndexRightwardOne() {
        val fixture = tableauCardWithTwoLegalDestinationsState()
        val standardPick = Move.TableauToTableau(fromColumn = 0, fromIndex = 0, toColumn = 2)
        val hintMove = Move.TableauToTableau(fromColumn = 0, fromIndex = 0, toColumn = 4)
        check(isLegal(fixture, standardPick))
        check(isLegal(fixture, hintMove))

        lateinit var viewModel: FreeCellViewModel
        composeRule.setContent {
            viewModel = FreeCellViewModel(initialSeed = 0L)
            FinitePlayTheme { GameScreen(viewModel = viewModel) }
        }
        composeRule.runOnIdle {
            viewModel.loadFixtureForDebugging(fixture)
            viewModel.showHintForDebugging(hintMove)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("card_0_0").performClick()
        composeRule.waitForIdle()

        val state = viewModel.session.state
        assertEquals(true, state.tableau[0].isEmpty())
        assertEquals(1, state.tableau[2].size) // the standard rightward pick was not taken
        assertEquals(2, state.tableau[4].size) // the hinted destination was taken instead
    }

    private fun freeCellCardWithTwoLegalDestinationsState(): FreeCellState {
        val tableau = List(8) { column ->
            when (column) {
                2 -> listOf(Card(Suit.HEARTS, Rank.SEVEN)) // lowest-index legal destination
                4 -> listOf(Card(Suit.DIAMONDS, Rank.SEVEN)) // a further, also-legal destination
                else -> emptyList()
            }
        }
        return FreeCellState(
            seed = 0L,
            versions = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1),
            tableau = tableau,
            freeCells = listOf(Card(Suit.CLUBS, Rank.SIX), null, null, null),
            foundations = Suit.entries.associateWith { 0 },
            status = GameStatus.IN_PROGRESS,
        )
    }

    @Test
    fun tappingTheHintHighlightedFreeCellCardCommitsTheHintsDestinationInsteadOfTheLowestIndexOne() {
        val fixture = freeCellCardWithTwoLegalDestinationsState()
        val standardPick = Move.FreeCellToTableau(cell = 0, toColumn = 2)
        val hintMove = Move.FreeCellToTableau(cell = 0, toColumn = 4)
        check(isLegal(fixture, standardPick))
        check(isLegal(fixture, hintMove))

        lateinit var viewModel: FreeCellViewModel
        composeRule.setContent {
            viewModel = FreeCellViewModel(initialSeed = 0L)
            FinitePlayTheme { GameScreen(viewModel = viewModel) }
        }
        composeRule.runOnIdle {
            viewModel.loadFixtureForDebugging(fixture)
            viewModel.showHintForDebugging(hintMove)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("free_cell_0").performClick()
        composeRule.waitForIdle()

        val state = viewModel.session.state
        assertEquals(null, state.freeCells[0])
        assertEquals(1, state.tableau[2].size) // the standard pick was not taken
        assertEquals(2, state.tableau[4].size) // the hinted destination was taken instead
    }
}
