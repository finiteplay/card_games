package org.finiteplay.klondike.ui.game

import org.finiteplay.core.ui.R as CoreR
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.finiteplay.klondike.R
import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.GameStatus
import org.finiteplay.klondike.board.GameVersions
import org.finiteplay.klondike.board.TableauCard
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.rules.applyMove
import org.finiteplay.klondike.rules.isLegal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * `docs/games/klondike/DESIGN.md` "Interaction": tapping the exact pile (and, on the tableau, the
 * exact card) a shown hint currently highlights commits the hint's own move instead
 * of the standard tap-priority pick, since the two can disagree.
 */
class BoardHintTapOverrideTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun wasteCardLegalForBothFoundationAndTableauState(): GameState {
        val tableau = List(7) { column ->
            if (column == 0) listOf(TableauCard(Card(Suit.HEARTS, Rank.THREE), faceUp = true)) else emptyList()
        }
        return GameState(
            seed = 0L,
            versions = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1),
            tableau = tableau,
            // Clubs already holds its Ace, so Two of Clubs is a *legal* foundation move,
            // not just a safe one - isSafeFoundationMove treats every Two as safe
            // regardless, so the standard waste-tap priority always prefers it first.
            foundations = mapOf(Suit.CLUBS to 1, Suit.DIAMONDS to 0, Suit.HEARTS to 0, Suit.SPADES to 0),
            waste = listOf(Card(Suit.CLUBS, Rank.TWO)),
            stock = emptyList(),
            status = GameStatus.IN_PROGRESS,
        )
    }

    @Test
    fun tappingTheHintHighlightedWasteCardCommitsTheHintsMoveInsteadOfTheSafeFoundation() {
        val fixture = wasteCardLegalForBothFoundationAndTableauState()
        val hintMove = Move.WasteToTableau(toColumn = 0)
        // The fixture is only meaningful if both destinations are actually legal.
        check(isLegal(fixture, Move.WasteToFoundation))
        check(isLegal(fixture, hintMove))

        var state = fixture
        composeRule.setContent {
            var displayed by remember { mutableStateOf(state) }
            Box(Modifier.width(360.dp).height(640.dp)) {
                Board(
                    state = displayed,
                    onCommitMove = { move ->
                        if (!isLegal(state, move)) return@Board false
                        state = applyMove(state, move)
                        displayed = state
                        true
                    },
                    hintMove = hintMove,
                    skipAnimations = true,
                )
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.cd_waste_card, context.getString(CoreR.string.rank_two), context.getString(CoreR.string.suit_clubs)),
        ).performClick()

        assertEquals(1, state.foundations.getValue(Suit.CLUBS)) // untouched at its starting Ace: the safe-foundation pick was not taken
        assertEquals(listOf(Rank.THREE, Rank.TWO), state.tableau[0].map { it.card.rank }) // hinted destination was taken instead
        assertEquals(true, state.waste.isEmpty())
    }

    @Test
    fun tappingTheSameWasteCardWithNoHintShownUsesTheStandardSafeFoundationPriority() {
        val fixture = wasteCardLegalForBothFoundationAndTableauState()

        var state = fixture
        composeRule.setContent {
            var displayed by remember { mutableStateOf(state) }
            Box(Modifier.width(360.dp).height(640.dp)) {
                Board(
                    state = displayed,
                    onCommitMove = { move ->
                        if (!isLegal(state, move)) return@Board false
                        state = applyMove(state, move)
                        displayed = state
                        true
                    },
                    hintMove = null,
                    skipAnimations = true,
                )
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.cd_waste_card, context.getString(CoreR.string.rank_two), context.getString(CoreR.string.suit_clubs)),
        ).performClick()

        assertEquals(2, state.foundations.getValue(Suit.CLUBS)) // standard priority: safe foundation first
        assertEquals(1, state.tableau[0].size) // tableau untouched
    }

    private fun tableauCardWithTwoLegalDestinationsState(): GameState {
        val tableau = List(7) { column ->
            when (column) {
                0 -> listOf(TableauCard(Card(Suit.CLUBS, Rank.SIX), faceUp = true))
                2 -> listOf(TableauCard(Card(Suit.HEARTS, Rank.SEVEN), faceUp = true)) // lowest-index legal rightward destination
                4 -> listOf(TableauCard(Card(Suit.DIAMONDS, Rank.SEVEN), faceUp = true)) // a further, also-legal destination
                else -> emptyList()
            }
        }
        return GameState(
            seed = 0L,
            versions = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1),
            tableau = tableau,
            foundations = Suit.entries.associateWith { 0 },
            waste = emptyList(),
            stock = emptyList(),
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

        var state = fixture
        composeRule.setContent {
            var displayed by remember { mutableStateOf(state) }
            Box(Modifier.width(360.dp).height(640.dp)) {
                Board(
                    state = displayed,
                    onCommitMove = { move ->
                        if (!isLegal(state, move)) return@Board false
                        state = applyMove(state, move)
                        displayed = state
                        true
                    },
                    hintMove = hintMove,
                    skipAnimations = true,
                )
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithContentDescription(
            context.getString(
                R.string.cd_tableau_card,
                context.getString(CoreR.string.rank_six),
                context.getString(CoreR.string.suit_clubs),
                1,
                1,
                1,
            ),
        ).performClick()

        assertEquals(true, state.tableau[0].isEmpty())
        assertEquals(true, state.tableau[2].size == 1) // the standard rightward pick was not taken
        assertEquals(2, state.tableau[4].size) // the hinted destination was taken instead
    }
}
