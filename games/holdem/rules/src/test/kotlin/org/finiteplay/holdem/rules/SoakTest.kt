package org.finiteplay.holdem.rules

import org.finiteplay.cards.Card
import org.finiteplay.cards.shuffleDeckIndices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * A random legal-action soak (`EXECUTION_PLAN.md` H2 gate): every seat of many seeded tournaments
 * chooses random legal actions, and the contract's invariants are checked after every action.
 * Everything the soak expects it derives itself — the schedule, the deal order, the button — rather
 * than by asking the engine.
 */
class SoakTest {
    private val schedule = listOf(
        10 to 20, 15 to 30, 25 to 50, 50 to 100, 75 to 150, 100 to 200, 150 to 300,
        200 to 400, 300 to 600, 400 to 800, 600 to 1_200, 800 to 1_600, 1_000 to 2_000,
    )

    private fun expectedBlinds(hand: Int): Pair<Int, Int> {
        val level = (hand - 1) / 10 + 1
        if (level <= schedule.size) return schedule[level - 1]
        val doublings = level - schedule.size
        return schedule.last().let { (it.first shl doublings) to (it.second shl doublings) }
    }

    private fun mix(x: ULong): Long {
        var z = x
        z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9uL
        z = (z xor (z shr 27)) * 0x94D049BB133111EBuL
        return (z xor (z shr 31)).toLong()
    }

    private fun expectedHandSeed(tournament: Long, hand: Int): Long = mix(tournament.toULong() + (hand + 1).toULong() * 0x9E3779B97F4A7C15uL)

    private class Stats {
        var hands = 0
        var actions = 0
        var showdowns = 0
        var withoutShowdown = 0
        var sidePotHands = 0
        var allInActions = 0
        var multiKnockouts = 0
        var headsUpHands = 0
        var oddChipPots = 0
        var bigBlindOptionRaises = 0
        var runOuts = 0
        var shortAllIns = 0
        var tiedPlaces = 0
        var tournamentsEnded = 0
        var playerLastPlace = HashSet<Int>()
    }

    @Test
    fun `random legal play preserves every invariant`() {
        val master = Random(20261006)
        val stats = Stats()
        repeat(TOURNAMENTS) { index -> soakTournament(master.nextLong(), Random(master.nextLong()), index, stats, RandomPlay.Style.CALM) }
        repeat(WILD_TOURNAMENTS) { index -> soakTournament(master.nextLong(), Random(master.nextLong()), TOURNAMENTS + index, stats, RandomPlay.Style.WILD) }

        assertEquals(TOURNAMENTS + WILD_TOURNAMENTS, stats.tournamentsEnded)
        // The soak is only worth something if it reached the interesting hands.
        assertTrue("showdowns: ${stats.showdowns}", stats.showdowns > 1_000)
        assertTrue("without a showdown: ${stats.withoutShowdown}", stats.withoutShowdown > 1_000)
        assertTrue("side pots: ${stats.sidePotHands}", stats.sidePotHands > 100)
        assertTrue("all-ins: ${stats.allInActions}", stats.allInActions > 500)
        assertTrue("short all-ins: ${stats.shortAllIns}", stats.shortAllIns > 10)
        assertTrue("multiple knockouts: ${stats.multiKnockouts}", stats.multiKnockouts > 20)
        assertTrue("heads-up hands: ${stats.headsUpHands}", stats.headsUpHands > 100)
        assertTrue("odd-chip pots: ${stats.oddChipPots}", stats.oddChipPots > 20)
        assertTrue("big blind option raises: ${stats.bigBlindOptionRaises}", stats.bigBlindOptionRaises > 50)
        assertTrue("run-outs: ${stats.runOuts}", stats.runOuts > 100)
        assertEquals("every finishing place for the player occurs", (1..6).toSet(), stats.playerLastPlace)
    }

    private fun soakTournament(seed: Long, random: Random, index: Int, stats: Stats, style: RandomPlay.Style) {
        var session = startTournament(seed)
        var previous: HoldemState? = null
        var hands = 0
        while (true) {
            hands++
            assertTrue("tournament $index ($seed) did not end", hands < 5_000)
            val dealt = session.state
            checkDeal(dealt, seed, previous, "tournament $index hand $hands")
            val handStart = dealt.tournament
            session = playHand(session, random, style, "tournament $index hand $hands", stats)
            val over = session.state
            assertEquals("tournament $index hand $hands: replay", session, replayHand(handStart, session.log))
            collect(over, stats)
            previous = over
            stats.hands++
            if (over.phase == Phase.TOURNAMENT_OVER) {
                checkTournamentEnd(over, "tournament $index")
                stats.tournamentsEnded++
                stats.playerLastPlace += over.places[0]!!
                break
            }
            session = session.nextHand()!!
        }
    }

    /** The deal, the blinds and the button exactly as the contract and the schedule say. */
    private fun checkDeal(s: HoldemState, seed: Long, previous: HoldemState?, label: String) {
        val t = s.tournament
        val alive = (0 until 6).filter { t.places[it] == null }
        assertEquals(label, (previous?.handNumber ?: 0) + 1, t.handNumber)
        assertEquals("$label: stacks carry over", previous?.stacks ?: List(6) { 1_500 }, t.stacks)
        assertEquals("$label: places carry over", previous?.places ?: List(6) { null }, t.places)
        assertEquals("$label: the chips", Contract.TOTAL_CHIPS, t.stacks.sum())
        assertTrue("$label: a stack of zero is out and only then", (0 until 6).all { (t.stacks[it] == 0) == (it !in alive) })
        val expectedButton = if (previous == null) firstButton(seed) else (1..6).map { (previous.button + it) % 6 }.first { it in alive }
        assertEquals("$label: the button is the next live seat", expectedButton, s.button)
        assertTrue(label, s.button in alive)

        // The deck and the deal order.
        val deck = shuffleDeckIndices(expectedHandSeed(seed, t.handNumber), 52).map(Card::fromId)
        assertEquals("$label: deck", deck, s.deck)
        val order = (1..6).map { (s.button + it) % 6 }.filter { it in alive }
        for ((i, seat) in order.withIndex()) assertEquals("$label: seat $seat", listOf(deck[i], deck[order.size + i]), s.holeCards[seat])
        for (seat in 0 until 6) if (seat !in alive) assertTrue(s.holeCards[seat].isEmpty())
        assertTrue("$label: no card twice", s.holeCards.flatten().let { it.size == it.toSet().size })

        // The blinds and who posts them.
        val (small, big) = expectedBlinds(t.handNumber)
        val sbSeat = if (alive.size == 2) s.button else order[0]
        val bbSeat = order.first { it != sbSeat }
        assertEquals("$label: small blind seat", sbSeat, s.smallBlindSeat)
        assertEquals("$label: big blind seat", bbSeat, s.bigBlindSeat)
        // A hand can end at the deal, when the blinds are all that is in and the uncalled part goes back.
        val posted = List(6) { s.contributions[it] + s.returned[it] }
        assertEquals("$label: small blind", minOf(small, t.stacks[sbSeat]), posted[sbSeat])
        assertEquals("$label: big blind", minOf(big, t.stacks[bbSeat]), posted[bbSeat])
        assertEquals("$label: only the blinds are posted", posted[sbSeat] + posted[bbSeat], posted.sum())
        assertEquals(Blinds((t.handNumber - 1) / 10 + 1, small, big), s.blinds)
        assertEquals(Contract.TOTAL_CHIPS, s.stacks.sum() + (if (s.phase == Phase.BETTING) s.contributions.sum() else 0))
    }

    private fun playHand(start: HoldemSession, random: Random, style: RandomPlay.Style, label: String, stats: Stats): HoldemSession {
        var session = start
        var steps = 0
        while (session.state.phase == Phase.BETTING) {
            assertTrue("$label: runaway hand", steps++ < 400)
            val state = session.state
            val legal = legalActions(state)!!
            assertEquals("$label: legal actions at step $steps", LegalOracle.expected(state), legal)
            assertEquals(state.toAct, legal.seat)
            assertTrue("$label: the seat to act is live", state.active(legal.seat))
            probeIllegal(state, legal, random, label)

            val action = RandomPlay.choose(legal, random, style)
            assertTrue("$label: chosen action is offered", legal.allows(action))
            val next = session.act(legal.seat, action)
            assertNotNull("$label: $action by ${legal.seat} was refused though offered", next)
            session = next!!
            stats.actions++
            val after = session.state
            checkStep(state, after, legal.seat, action, label, stats)
        }
        return session
    }

    /** Actions the engine must refuse: each of these is outside what [legal] offers. */
    private fun probeIllegal(state: HoldemState, legal: LegalActions, random: Random, label: String) {
        val wrongSeat = (legal.seat + 1 + random.nextInt(5)) % 6
        assertNull("$label: wrong seat", apply(state, wrongSeat, Action.Check))
        if (!legal.canFold) assertNull("$label: fold with nothing to call", apply(state, legal.seat, Action.Fold))
        if (!legal.canCheck) assertNull("$label: check facing a bet", apply(state, legal.seat, Action.Check))
        if (!legal.canCall) assertNull("$label: call with nothing to call", apply(state, legal.seat, Action.Call))
        if (legal.allInTo == null) assertNull("$label: all in not offered", apply(state, legal.seat, Action.AllIn))
        legal.raise?.let {
            assertNull("$label: raise under the minimum", apply(state, legal.seat, Action.Raise(it.first - 1)))
            assertNull("$label: raise over the stack", apply(state, legal.seat, Action.Raise(it.last + 1)))
        } ?: assertNull("$label: raise not offered", apply(state, legal.seat, Action.Raise(state.currentBet + state.raiseIncrement)))
        legal.bet?.let {
            assertNull("$label: bet under the minimum", apply(state, legal.seat, Action.Bet(it.first - 1)))
            assertNull("$label: bet over the stack", apply(state, legal.seat, Action.Bet(it.last + 1)))
        } ?: assertNull("$label: bet not offered", apply(state, legal.seat, Action.Bet(state.blinds.big)))
    }

    private fun checkStep(before: HoldemState, after: HoldemState, seat: Int, action: Action, label: String, stats: Stats) {
        // Chips: stacks plus the pot are always the table's, and nothing goes negative.
        val pot = if (after.phase == Phase.BETTING) after.contributions.sum() else 0
        assertEquals("$label: the chips after $action", Contract.TOTAL_CHIPS, after.stacks.sum() + pot)
        assertTrue("$label: a negative stack", after.stacks.all { it >= 0 })
        assertTrue("$label: contributions", after.contributions.all { it >= 0 })
        assertEquals("$label: history grows by one", before.history.size + 1, after.history.size)

        // Cards: none twice, and the board is the deck's own positions.
        val dealt = after.holeCards.flatten() + after.board
        assertEquals("$label: a card dealt twice", dealt.size, dealt.toSet().size)
        val n = after.seatsInHand.size
        val expectedBoard = when (after.board.size) {
            0 -> emptyList()
            3 -> after.deck.slice(2 * n + 1..2 * n + 3)
            4 -> after.deck.slice(2 * n + 1..2 * n + 3) + after.deck[2 * n + 5]
            5 -> after.deck.slice(2 * n + 1..2 * n + 3) + after.deck[2 * n + 5] + after.deck[2 * n + 7]
            else -> error("$label: board of ${after.board.size}")
        }
        assertEquals("$label: the board follows the contract", expectedBoard, after.board)
        assertEquals("$label: the street matches the board", listOf(0, 3, 4, 5)[after.street.ordinal], after.board.size)
        assertEquals("$label: the deck is never reordered", before.deck, after.deck)

        val entry = after.history.last()
        if (entry.allIn) stats.allInActions++
        assertEquals(seat, entry.seat)
        if (entry.allIn && entry.action != Action.Call && entry.action != Action.Fold) {
            val total = entry.streetTotal
            if (total - before.currentBet in 0 until before.raiseIncrement && total > before.currentBet) stats.shortAllIns++
        }

        // A betting round never ends with an unmatched bet from a player who can still act.
        if (after.street != before.street || after.phase != Phase.BETTING) checkStreetEnd(after, before.street, label)
        if (after.phase == Phase.BETTING) {
            assertTrue("$label: someone is to act", after.toAct >= 0 && after.active(after.toAct))
            assertTrue("$label: at least two live", (0 until 6).count(after::contesting) >= 2)
        } else {
            checkAward(before, after, label, stats)
        }
    }

    private fun checkStreetEnd(after: HoldemState, street: Street, label: String) {
        if ((0 until 6).count(after::contesting) < 2) return
        val entries = after.history.filter { it.street == street }
        val top = maxOf(entries.maxOfOrNull { it.streetTotal } ?: 0, if (street == Street.PREFLOP) after.blinds.big else 0)
        // Seats that still had chips when the round ended: each acted, and each matched the highest bet.
        val live = (0 until 6).filter { after.active(it) }
        for (seat in live) {
            val own = entries.lastOrNull { it.seat == seat }
            assertNotNull("$label: seat $seat never acted on $street", own)
            assertEquals("$label: seat $seat left $street unmatched", top, own!!.streetTotal)
        }
    }

    private fun checkAward(before: HoldemState, after: HoldemState, label: String, stats: Stats) {
        val result = after.result!!
        assertEquals("$label: the chips after the award", Contract.TOTAL_CHIPS, after.stacks.sum())
        assertEquals("$label: every chip in the pots is paid", after.contributions.sum(), result.payouts.sum())
        assertEquals(after.contributions.sum(), result.awards.sumOf { it.pot.amount })
        // No uncalled bet is kept in a pot: the most any seat has in is matched by another's.
        val most = after.contributions.max()
        assertTrue("$label: an uncalled bet stayed in the pot ${after.contributions}", after.contributions.count { it == most } >= 2)
        val contesting = (0 until 6).filter(after::contesting)
        assertEquals("$label: showdown iff two are left", contesting.size >= 2, result.showdown)
        if (result.showdown) {
            assertEquals(5, after.board.size)
            assertEquals(contesting, result.values.keys.sorted())
        } else {
            assertTrue(result.values.isEmpty())
        }
        for (award in result.awards) {
            assertTrue("$label: eligible are live", award.pot.eligible.all { it in contesting })
            assertTrue("$label: winners are eligible", award.winners.all { it in award.pot.eligible })
            assertEquals(award.pot.amount, award.shares.sum())
            if (result.showdown) {
                val best = award.pot.eligible.maxOf { evaluate(after.holeCards[it] + after.board) }
                assertTrue("$label: a winner has the best hand", award.winners.all { evaluate(after.holeCards[it] + after.board) == best })
                assertEquals(award.pot.eligible.filter { evaluate(after.holeCards[it] + after.board) == best }.toSet(), award.winners.toSet())
            }
            if (award.pot.amount % award.winners.size != 0) stats.oddChipPots++
        }
        // Only seats with no chips are out, and a place is held by exactly those who left the tournament.
        for (seat in 0 until 6) {
            assertEquals("$label: seat $seat is out iff it has no chips", after.inHand(seat) && after.stacks[seat] == 0, seat in result.eliminated)
        }
        assertTrue("$label: knockouts are those who started the hand in", result.eliminated.all { after.tournament.places[it] == null })
        val survivors = after.tournament.alive.filter { it !in result.eliminated }
        val placed = (0 until 6).filter { after.places[it] != null }.toSet()
        val expectedPlaced = (0 until 6).filter { after.tournament.places[it] != null }.toSet() + result.eliminated +
            (if (survivors.size == 1) survivors else emptyList())
        assertEquals("$label: who has a place", expectedPlaced, placed)
        assertEquals("$label: phase", if (survivors.size == 1) Phase.TOURNAMENT_OVER else Phase.HAND_OVER, after.phase)
    }

    private fun checkTournamentEnd(over: HoldemState, label: String) {
        assertEquals("$label: one seat holds every chip", 1, over.stacks.count { it > 0 })
        assertEquals(Contract.TOTAL_CHIPS, over.stacks.max())
        val places = over.places.map { it!! }
        assertEquals("$label: the winner is first", 1, places[over.stacks.indexOf(Contract.TOTAL_CHIPS)])
        // Places rank by finish: a place is one more than the seats that finished strictly better, so
        // seats that share a place leave a gap after it.
        for (p in places) assertEquals("$label: place $p in $places", 1 + places.count { it < p }, p)
        assertTrue(places.all { it in 1..6 })
        assertNull("$label: nothing offered after the end", legalActions(over))
        assertNull(nextHand(over))
    }

    private fun collect(over: HoldemState, stats: Stats) {
        val result = over.result!!
        if (result.showdown) stats.showdowns++ else stats.withoutShowdown++
        if (result.awards.size > 1) stats.sidePotHands++
        if (over.seatsInHand.size == 2) stats.headsUpHands++
        if (result.eliminated.size > 1) stats.multiKnockouts++
        if (over.board.size == 5 && over.history.none { it.street == Street.RIVER } && result.showdown) stats.runOuts++
        val bb = over.bigBlindSeat
        if (over.history.firstOrNull { it.street == Street.PREFLOP && it.seat == bb }?.action is Action.Raise) stats.bigBlindOptionRaises++
        val places = over.places.filterNotNull()
        if (places.size != places.toSet().size) stats.tiedPlaces++
    }

    private companion object {
        /** How many seeded tournaments the soak plays; every seat of each chooses random legal actions. */
        const val TOURNAMENTS = 600

        /** Further tournaments played wildly, with all-ins and maximum bets common: short stacks, side pots, knockouts. */
        const val WILD_TOURNAMENTS = 300
    }
}
