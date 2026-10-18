package org.finiteplay.holdem.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import org.finiteplay.cards.Animal
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.holdem.rules.Phase
import org.finiteplay.holdem.rules.act
import org.finiteplay.holdem.rules.startTournament
import org.finiteplay.holdem.testing.Scenarios
import org.finiteplay.holdem.testing.scriptedAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A hand that knocks an opponent out turns their cards over like any other showdown, and those cards stay on the
 * table, face up, until the next hand: the player may look at what beat them, or what they beat, before dealing again.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
class KnockedOutSeatTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val profiles = Animal.entries.take(5).map(::OpponentProfile)

    /** The table as the first hand of the check-down scenario ends: everyone's cards that showdown turned over are up. */
    private fun handOver(): TableModel {
        val scenario = Scenarios.checkDown
        var session = startTournament(scenario.seed)
        var shown = TablePresenter.dealt(session.state)
        while (session.state.phase == Phase.BETTING) {
            val seat = session.state.toAct
            val action = scriptedAction(session.state, seat, if (seat == 0) scenario.player else scenario.opponents(seat))
            session = checkNotNull(session.act(seat, action))
            for (step in TablePresenter.afterAction(shown, session.state)) shown = step.model
        }
        assertEquals(Stage.HAND_OVER, shown.stage)
        return shown
    }

    private fun show(table: TableModel) {
        composeRule.setContent {
            FinitePlayTheme { Box(Modifier.size(360.dp, 780.dp)) { TableView(table, profiles, landscape = false, animate = false) } }
        }
        composeRule.waitForIdle()
    }

    private fun cardsOf(seat: Int) = composeRule.onAllNodesWithTag("seat_${seat}_cards", useUnmergedTree = true).fetchSemanticsNodes()

    @Test
    fun `an opponent knocked out at showdown keeps the cards they turned over`() {
        val over = handOver()
        val seat = (1..5).first { over.seats[it].contesting && over.seats[it].cards != null }
        val knockedOut = over.copy(seats = over.seats.mapIndexed { i, s -> if (i == seat) s.copy(place = 6) else s })

        show(knockedOut)

        assertEquals("the cards of a seat knocked out on this hand are still up", 1, cardsOf(seat).size)
    }

    @Test
    fun `a seat knocked out earlier shows no cards`() {
        val over = handOver()
        val seat = (1..5).first { over.seats[it].contesting && over.seats[it].cards != null }
        val earlier = over.copy(seats = over.seats.mapIndexed { i, s -> if (i == seat) s.copy(place = 6, inHand = false, cards = null) else s })

        show(earlier)

        assertEquals(0, cardsOf(seat).size)
        assertNotNull(earlier.seats[seat].place)
    }
}
