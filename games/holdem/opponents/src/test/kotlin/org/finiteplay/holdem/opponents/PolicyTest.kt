package org.finiteplay.holdem.opponents

import org.finiteplay.cards.Card
import org.finiteplay.holdem.rules.Action
import org.finiteplay.holdem.rules.HoldemState
import org.finiteplay.holdem.rules.Phase
import org.finiteplay.holdem.rules.SeatView
import org.finiteplay.holdem.rules.apply
import org.finiteplay.holdem.rules.nextHand
import org.finiteplay.holdem.rules.seatView
import org.finiteplay.holdem.rules.startTournament
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.stream.IntStream
import kotlin.random.Random

class PolicyTest {
    /** Plays at random on some seats so that rare spots (short all-ins, odd stacks) are reached. */
    private class Chaos(seed: Long) : OpponentPolicy {
        private val random = Random(seed)
        override fun decide(view: SeatView, salt: Long): Action {
            val l = view.legal!!
            val options = ArrayList<Action>()
            if (l.canFold) options += Action.Fold
            if (l.canCheck) options += Action.Check
            if (l.canCall) options += Action.Call
            l.bet?.let { options += Action.Bet(it.first + random.nextInt(it.last - it.first + 1)) }
            l.raise?.let { options += Action.Raise(it.first + random.nextInt(it.last - it.first + 1)) }
            if (l.allInTo != null) options += Action.AllIn
            return options.random(random)
        }
    }

    /** The same hand with every card the seat cannot see replaced: the others' hole cards and the whole deck order. */
    private fun scrambled(state: HoldemState, seat: Int, random: Random): HoldemState {
        val unseen = Card.CANONICAL_DECK.filter { it !in state.holeCards[seat] && it !in state.board }.shuffled(random)
        var next = 0
        val holes = state.holeCards.mapIndexed { i, cards -> if (i == seat || cards.isEmpty()) cards else listOf(unseen[next++], unseen[next++]) }
        return state.copy(holeCards = holes, deck = Card.CANONICAL_DECK.shuffled(random))
    }

    @Test
    fun `permuting every hidden card never changes a decision or a hint`() {
        var checked = 0
        var differing = 0
        for (t in 0 until 40) {
            val random = Random(t)
            val opponents = drawOpponents(t.toLong())
            var state = startTournament(900L + t).state
            var steps = 0
            while (state.phase != Phase.TOURNAMENT_OVER && steps++ < 400) {
                if (state.phase == Phase.HAND_OVER) {
                    state = nextHand(state)!!
                    continue
                }
                val seat = state.toAct
                val view = state.seatView(seat)
                val policy = ProfilePolicy(opponents[(seat + t) % 5].profile)
                val salt = decisionSalt(state.tournament.handSeed, seat, state.history.size)
                val action = policy.decide(view, salt)
                if (steps % 3 == 0) {
                    val other = scrambled(state, seat, random)
                    if (other.holeCards != state.holeCards) differing++
                    val otherView = other.seatView(seat)
                    assertEquals(view, otherView)
                    assertEquals(action, policy.decide(otherView, salt))
                    assertEquals(hint(view), hint(otherView))
                    checked++
                }
                assertEquals("the same view and salt give the same action", action, policy.decide(view, salt))
                state = apply(state, seat, action)!!
            }
        }
        assertTrue("only $checked views compared", checked > 500)
        assertTrue(differing > 500)
    }

    @Test
    fun `the salt is the only source of variation`() {
        val view = startTournament(11L).state.let { it.seatView(it.toAct) }
        val policy = ProfilePolicy(Profiles.maniac)
        val actions = (0L until 300L).map { policy.decide(view, it) }.toSet()
        assertTrue("a mixed spot gives more than one action across salts: $actions", actions.size > 1)
    }

    @Test
    fun `every decision and every hint is legal with opponents in every seat`() {
        val decisions = IntStream.range(0, 600).parallel().map { t ->
            var count = 0
            val opponents = drawOpponents(t.toLong())
            val policies = List(6) { seat ->
                when {
                    t % 4 == 3 && seat % 2 == 1 -> Chaos(t * 31L + seat)
                    seat == 0 -> ProfilePolicy(Profiles.all[t % Profiles.all.size])
                    else -> ProfilePolicy(opponents[seat - 1].profile)
                }
            }
            TournamentRunner.play(
                5_000L + t,
                policies,
                observer = { view, _ ->
                    count++
                    if (count % 4 == 0) {
                        val h = hint(view)
                        assertTrue("hint ${h.suggested} not legal in ${view.legal}", view.legal!!.allows(h.suggested))
                        assertTrue(h.equity in 0.0..1.0)
                        assertTrue(h.potOdds in 0.0..1.0)
                    }
                },
            )
            count
        }.sum()
        println("LEGALITY $decisions decisions checked over 600 tournaments")
        assertTrue(decisions > 20_000)
    }

    @Test
    fun `a replayed tournament plays out identically`() {
        fun run() = mutableListOf<Action>().also { log ->
            val opponents = drawOpponents(77L)
            val policies = listOf(ProfilePolicy(Profiles.tag)) + opponents.map { ProfilePolicy(it.profile) }
            TournamentRunner.play(77L, policies, observer = { _, a -> log += a })
        }
        assertEquals(run(), run())
    }

    @Test
    fun `hints repeat and differ between spots`() {
        val seen = HashSet<Double>()
        for (seed in 1L..40L) {
            val s = startTournament(seed).state
            val view = s.seatView(s.toAct)
            val a = hint(view)
            assertEquals(a, hint(view))
            seen += a.equity
        }
        assertTrue(seen.size > 10)
    }

    @Test
    fun `strong hands are worth more than weak ones`() {
        fun equityOf(hole: String): Double {
            val s = startTournament(3L).state
            val cards = hole.split(' ').map { c -> Card.CANONICAL_DECK.first { it.suit.name[0] == c[1] && rankChar(it) == c[0] } }
            val view = s.seatView(s.toAct).copy(holeCards = cards)
            return hint(view).equity
        }
        assertTrue(equityOf("AS AH") > 0.40)
        assertTrue(equityOf("AS AH") > equityOf("KS KH"))
        assertTrue(equityOf("KS KH") > equityOf("7S 2H"))
        assertNotEquals(equityOf("7S 2H"), equityOf("AS AH"))
    }

    private fun rankChar(c: Card): Char = when (c.rank.value) {
        1 -> 'A'
        10 -> 'T'
        11 -> 'J'
        12 -> 'Q'
        13 -> 'K'
        else -> c.rank.value.toString()[0]
    }

    @Test
    fun `drawn tables always hold players to exploit and players to avoid`() {
        val names = HashSet<String>()
        for (seed in 0L until 500L) {
            val table = drawOpponents(seed)
            assertEquals(table, drawOpponents(seed))
            assertEquals(5, table.size)
            assertEquals(5, table.map { it.name }.toSet().size)
            assertEquals(5, table.map { it.profile }.toSet().size)
            assertTrue(table.count { it.profile in Profiles.strongPool } >= 2)
            assertTrue(table.count { it.profile in Profiles.exploitablePool } >= 2)
            names += table.map { it.name }
        }
        assertTrue(names.size >= 20)
        assertTrue(OPPONENT_NAMES.size >= 20)
        assertEquals(OPPONENT_NAMES.size, OPPONENT_NAMES.toSet().size)
    }
}
