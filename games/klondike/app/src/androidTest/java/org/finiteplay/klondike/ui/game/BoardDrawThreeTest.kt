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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.finiteplay.klondike.R
import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.board.GameVersions
import org.finiteplay.klondike.rules.isLegal
import org.finiteplay.klondike.session.GameSession
import org.finiteplay.klondike.session.commitMove
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.junit.Rule
import org.junit.Test

/**
 * `docs/games/klondike/DESIGN.md` "Draw-Three Mode": tapping stock draws up to 3 cards at once, and
 * the resulting waste top is the last one drawn — end-to-end through [Board]'s real
 * tap-to-commit path (`resolveStockTap` -> `onCommitMove` -> the real reducer), not
 * just the pure `:game`/`:solver` layer this mirrors.
 */
class BoardDrawThreeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tappingStockInDrawThreeMovesUpToThreeCardsAndExposesTheLastOneDrawn() {
        var session = GameSession.start(
            seed = 42L,
            versions = GameVersions(1, 1, 1),
            automaticMovesEnabled = false,
            drawMode = DrawMode.THREE,
        )
        val expectedThirdCard = session.state.stock[2]

        composeRule.setContent {
            var state by remember { mutableStateOf(session.state) }
            Box(Modifier.width(360.dp).height(640.dp)) {
                Board(
                    state = state,
                    onCommitMove = { move ->
                        if (!isLegal(session.state, move)) return@Board false
                        session = session.commitMove(move)
                        state = session.state
                        true
                    },
                    skipAnimations = true, // no slide to wait out; the compose test rule still idles through the (near-instant) queued step
                )
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithContentDescription(context.getString(R.string.cd_stock_with_cards, 24)).performClick()

        // Stock now holds 21; waste's top is the third card originally in stock —
        // draw-three's last-drawn-ends-up-on-top, exactly like ApplyMove.kt's Move.Draw.
        composeRule.onNodeWithContentDescription(context.getString(R.string.cd_stock_with_cards, 21)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.cd_waste_card, rankName(expectedThirdCard.rank), suitName(expectedThirdCard.suit)),
        ).assertIsDisplayed()
    }

    private fun rankName(rank: org.finiteplay.cards.Rank): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resId = when (rank) {
            org.finiteplay.cards.Rank.ACE -> CoreR.string.rank_ace
            org.finiteplay.cards.Rank.TWO -> CoreR.string.rank_two
            org.finiteplay.cards.Rank.THREE -> CoreR.string.rank_three
            org.finiteplay.cards.Rank.FOUR -> CoreR.string.rank_four
            org.finiteplay.cards.Rank.FIVE -> CoreR.string.rank_five
            org.finiteplay.cards.Rank.SIX -> CoreR.string.rank_six
            org.finiteplay.cards.Rank.SEVEN -> CoreR.string.rank_seven
            org.finiteplay.cards.Rank.EIGHT -> CoreR.string.rank_eight
            org.finiteplay.cards.Rank.NINE -> CoreR.string.rank_nine
            org.finiteplay.cards.Rank.TEN -> CoreR.string.rank_ten
            org.finiteplay.cards.Rank.JACK -> CoreR.string.rank_jack
            org.finiteplay.cards.Rank.QUEEN -> CoreR.string.rank_queen
            org.finiteplay.cards.Rank.KING -> CoreR.string.rank_king
        }
        return context.getString(resId)
    }

    private fun suitName(suit: org.finiteplay.cards.Suit): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resId = when (suit) {
            org.finiteplay.cards.Suit.CLUBS -> CoreR.string.suit_clubs
            org.finiteplay.cards.Suit.DIAMONDS -> CoreR.string.suit_diamonds
            org.finiteplay.cards.Suit.HEARTS -> CoreR.string.suit_hearts
            org.finiteplay.cards.Suit.SPADES -> CoreR.string.suit_spades
        }
        return context.getString(resId)
    }
}
