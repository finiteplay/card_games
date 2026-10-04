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
import org.finiteplay.spider.layout.TableauCard
import org.finiteplay.spider.rules.Move
import org.finiteplay.spider.rules.isLegal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * `docs/games/klondike/DESIGN.md` "Interaction": tapping the exact card a shown hint currently
 * highlights commits the hint's own move instead of the standard tap-priority pick, since the two
 * can disagree. Klondike's own `BoardHintTapOverrideTest` proves this for Klondike, FreeCell's
 * copy proves it there; this is Spider's.
 */
class BoardHintTapOverrideTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun cardWithTwoLegalDestinationsState(): SpiderState {
        val tableau = List(10) { column ->
            val card = when (column) {
                0 -> Card(Suit.SPADES, Rank.EIGHT)
                2 -> Card(Suit.SPADES, Rank.NINE) // nearest-to-the-right legal destination
                4 -> Card(Suit.SPADES, Rank.NINE) // a further, also-legal destination
                else -> Card(Suit.SPADES, Rank.TWO) // cannot take an eight: not a legal destination
            }
            listOf(TableauCard(card, faceUp = true))
        }
        return SpiderState(
            seed = 0L,
            versions = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1),
            suitCount = SuitCount.ONE,
            tableau = tableau,
            stock = emptyList(),
            banked = Suit.entries.associateWith { 0 },
            status = GameStatus.IN_PROGRESS,
        )
    }

    @Test
    fun tappingTheHintHighlightedCardCommitsTheHintsDestinationInsteadOfTheNearestRightwardOne() {
        val fixture = cardWithTwoLegalDestinationsState()
        val standardPick = Move.TableauToTableau(fromColumn = 0, fromIndex = 0, toColumn = 2)
        val hintMove = Move.TableauToTableau(fromColumn = 0, fromIndex = 0, toColumn = 4)
        check(isLegal(fixture, standardPick))
        check(isLegal(fixture, hintMove))

        lateinit var viewModel: SpiderViewModel
        composeRule.setContent {
            viewModel = SpiderViewModel(initialSeed = 0L)
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
}
