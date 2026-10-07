package org.finiteplay.holdem.ui

import org.finiteplay.cards.Card
import org.finiteplay.holdem.rules.Contract
import org.finiteplay.holdem.rules.HandCategory
import org.finiteplay.holdem.rules.Phase
import org.finiteplay.holdem.rules.act
import org.finiteplay.holdem.rules.evaluate
import org.finiteplay.holdem.rules.nextHand
import org.finiteplay.holdem.rules.startTournament
import org.finiteplay.holdem.testing.Scenario
import org.finiteplay.holdem.testing.Scenarios
import org.finiteplay.holdem.testing.scriptedAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TablePresenterTest {
    /** Every step of every hand the scenario plays, paired with the engine state it was computed from. */
    private fun stepsOf(scenario: Scenario): List<Pair<Step, org.finiteplay.holdem.rules.HoldemState>> {
        val out = ArrayList<Pair<Step, org.finiteplay.holdem.rules.HoldemState>>()
        var session = startTournament(scenario.seed)
        var shown = TablePresenter.dealt(session.state)
        var hands = 0
        while (true) {
            val state = session.state
            if (state.phase == Phase.BETTING) {
                val seat = state.toAct
                val action = scriptedAction(state, seat, if (seat == 0) scenario.player else scenario.opponents(seat))
                session = checkNotNull(session.act(seat, action))
                for (step in TablePresenter.afterAction(shown, session.state)) {
                    out += step to session.state
                    shown = step.model
                }
            } else {
                hands++
                if (state.phase == Phase.TOURNAMENT_OVER || state.playerOut || hands >= scenario.maxHands) return out
                session = checkNotNull(session.nextHand())
                shown = TablePresenter.dealt(session.state)
            }
        }
    }

    @Test
    fun `chips are never created or lost on any step`() {
        for (scenario in Scenarios.all) {
            for ((step, _) in stepsOf(scenario)) {
                val m = step.model
                assertEquals(scenario.name, Contract.TOTAL_CHIPS, m.seats.sumOf { it.stack } + m.pot)
            }
        }
    }

    @Test
    fun `a step never shows more of the board or hand than the state it came from`() {
        for (scenario in Scenarios.all) {
            for ((step, state) in stepsOf(scenario)) {
                assertEquals(state.board.take(step.model.board.size), step.model.board)
                for ((seat, shown) in step.model.seats.withIndex()) {
                    if (seat != 0 && shown.cards != null) assertEquals(state.holeCards[seat], shown.cards)
                    if (shown.cards != null && seat != 0) assertTrue(state.result?.showdown == true)
                }
            }
        }
    }

    @Test
    fun `the final step of a hand has the engine's stacks and no pot`() {
        for (scenario in Scenarios.all) {
            val steps = stepsOf(scenario)
            val finals = steps.filter { it.first.model.stage == Stage.HAND_OVER || it.first.model.stage == Stage.TOURNAMENT_OVER }
            assertTrue(scenario.name, finals.isNotEmpty())
            for ((step, state) in finals) {
                assertEquals(scenario.name, state.stacks, step.model.seats.map { it.stack })
                assertEquals(0, step.model.pot)
                assertEquals(state.places, step.model.seats.map { it.place })
            }
        }
    }

    @Test
    fun `side pots are paid one at a time, the main pot last`() {
        val steps = stepsOf(Scenarios.allInWithSidePot)
        val withSide = steps.first { (step, state) -> step.model.banner?.kind == PotKind.SIDE && state.result!!.awards.size >= 2 }
        val state = withSide.second
        val awardsShown = steps.filter { it.second === state && it.first.model.banner != null }.map { it.first.model.banner!! }
        assertEquals(state.result!!.awards.size, awardsShown.size)
        assertEquals(PotKind.MAIN, awardsShown.last().kind)
        assertEquals(state.result!!.awards.reversed().map { it.pot.amount }, awardsShown.map { it.amount })
        assertTrue(awardsShown.dropLast(1).all { it.kind == PotKind.SIDE })
    }

    @Test
    fun `a split pot shows each share on its own seat`() {
        val steps = stepsOf(Scenarios.splitPot)
        val state = steps.last().second
        val award = state.result!!.awards.first { it.winners.size >= 2 }
        val final = steps.last().first.model
        for ((i, seat) in award.winners.withIndex()) assertTrue(final.seats[seat].award >= award.shares[i])
    }

    @Test
    fun `opponents turn over clockwise from the last aggressor and only at showdown`() {
        val steps = stepsOf(Scenarios.checkDown)
        val reveals = steps.filter { it.first.announce is Announcement.Showed }.map { (it.first.announce as Announcement.Showed).seat }
        val state = steps.last().second
        assertEquals(state.result!!.values.keys, reveals.toSet())
        // Nobody bet: the first seat left of the button turns first.
        val start = (state.button + 1) % Contract.SEATS
        assertEquals(reveals.sortedBy { (it - start + Contract.SEATS) % Contract.SEATS }, reveals)
        val folded = stepsOf(Scenarios.fold)
        assertTrue(folded.none { it.first.announce is Announcement.Showed } || folded.last().second.result!!.showdown)
    }

    @Test
    fun `the player's result mark comes from their net, and a folded hand has none`() {
        val folded = stepsOf(Scenarios.fold).last().first.model
        assertNull(folded.mark)
        val split = stepsOf(Scenarios.splitPot).last()
        val net = split.second.stacks[0] - split.second.tournament.stacks[0]
        assertEquals(net, split.first.model.mark!!.net)
        val won = stepsOf(Scenarios.tournamentWon).last().first.model
        assertEquals(MarkKind.WIN, won.mark!!.kind)
        assertEquals(1, won.finishingPlace)
    }

    @Test
    fun `a knocked-out opponent reads its place at the hand's end`() {
        val final = stepsOf(Scenarios.eliminated).last()
        val out = final.first.model.seats.filter { it.place != null }
        assertTrue(out.isNotEmpty())
        assertEquals(final.second.places[0], final.first.model.finishingPlace)
        assertEquals(Stage.TOURNAMENT_OVER, final.first.model.stage)
    }

    @Test
    fun `hand names quote the ranks that make the hand`() {
        fun name(hole: String, board: String): HandName {
            val cards = (hole + " " + board).split(' ').map(::card)
            return handName(cards, evaluate(cards))
        }
        assertEquals(HandName(HandCategory.FLUSH, org.finiteplay.cards.Rank.KING), name("KH 2H", "9H 5H 3H 7C 8D"))
        assertEquals(HandName(HandCategory.STRAIGHT, org.finiteplay.cards.Rank.FIVE), name("AS 2D", "3C 4H 5S KD 9C"))
        assertEquals(HandName(HandCategory.TWO_PAIR, org.finiteplay.cards.Rank.ACE, org.finiteplay.cards.Rank.SEVEN), name("AS AD", "7C 7H 2S 9D KC"))
        assertEquals(HandName(HandCategory.FULL_HOUSE, org.finiteplay.cards.Rank.NINE, org.finiteplay.cards.Rank.TWO), name("9S 9D", "9C 2H 2S KD 4C"))
        assertTrue(name("AS KS", "QS JS TS 2D 3C").royal)
    }

    private fun card(text: String): Card {
        val rank = when (text[0]) {
            'A' -> org.finiteplay.cards.Rank.ACE
            'T' -> org.finiteplay.cards.Rank.TEN
            'J' -> org.finiteplay.cards.Rank.JACK
            'Q' -> org.finiteplay.cards.Rank.QUEEN
            'K' -> org.finiteplay.cards.Rank.KING
            else -> org.finiteplay.cards.Rank.fromValue(text[0].digitToInt())
        }
        val suit = when (text[1]) {
            'C' -> org.finiteplay.cards.Suit.CLUBS
            'D' -> org.finiteplay.cards.Suit.DIAMONDS
            'H' -> org.finiteplay.cards.Suit.HEARTS
            else -> org.finiteplay.cards.Suit.SPADES
        }
        return Card(suit, rank)
    }
}
