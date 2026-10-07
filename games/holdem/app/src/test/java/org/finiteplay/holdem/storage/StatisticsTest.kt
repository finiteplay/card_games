package org.finiteplay.holdem.storage

import org.finiteplay.core.session.computeSessionStatistics
import org.finiteplay.holdem.rules.Action
import org.finiteplay.holdem.rules.HoldemSession
import org.finiteplay.holdem.rules.HoldemState
import org.finiteplay.holdem.rules.Phase
import org.finiteplay.holdem.rules.Street
import org.finiteplay.holdem.rules.act
import org.finiteplay.holdem.rules.leave
import org.finiteplay.holdem.rules.legalActions
import org.finiteplay.holdem.rules.nextHand
import org.finiteplay.holdem.rules.startTournament
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Everyone folds when they may, and checks when they may not. */
private val foldAll: Chooser = { state -> legalActions(state)!!.let { if (it.canFold) Action.Fold else Action.Check } }

/** Calls and checks, never raises: every hand goes to showdown with nobody all in but the short. */
private val station: Chooser = { state -> legalActions(state)!!.let { if (it.canCall) Action.Call else Action.Check } }

/** The player raises the minimum when it may and stays in otherwise; everyone else folds. */
private val raiseThenFold: Chooser = { state ->
    val legal = legalActions(state)!!
    when {
        legal.seat != PLAYER_SEAT -> foldAll(state)
        legal.raise != null -> Action.Raise(legal.raise!!.first)
        legal.canCall -> Action.Call
        else -> Action.Check
    }
}

/** The player shoves at every turn; everyone else calls. */
private val shoveVersusStations: Chooser = { state ->
    val legal = legalActions(state)!!
    when {
        legal.seat == PLAYER_SEAT && legal.allInTo != null -> Action.AllIn
        legal.canCall -> Action.Call
        else -> Action.Check
    }
}

private fun playHand(session: HoldemSession, choose: Chooser): HoldemSession = playOut(session, choose)

/** The statistics over [finishes], as `core/session` computes the figures both can. */
private fun sessionStatisticsOf(finishes: List<Int>) = computeSessionStatistics(
    records = finishes.withIndex().toList(),
    hasUnfinishedPlayedGame = false,
    timestampMillis = { it.index.toLong() },
    isWin = { it.value == 1 },
    elapsedMillis = { 0L },
    moveCount = { 0 },
)

class StatisticsTest {

    // ---- one hand's facts, against hands whose answer is known by construction --------------

    @Test
    fun `a hand folded before the flop put nothing in voluntarily and won nothing`() {
        // Hand 1 for a seed where the player is first to act and is not a blind.
        val session = firstHand { it.toAct == PLAYER_SEAT }
        val folded = session.act(PLAYER_SEAT, Action.Fold)!!
        val finished = playHand(folded, station)
        assertEquals(HandFacts(false, false, false, false, false, 0), handFacts(finished.state))
    }

    @Test
    fun `raising and taking the pot unopposed counts as voluntary, a raise and a win without a showdown`() {
        val session = firstHand { it.toAct == PLAYER_SEAT }
        val finished = playHand(session, raiseThenFold)
        assertEquals(false, finished.state.result!!.showdown)
        // Blinds 10 and 20 are in; the player raised to 40 from under the gun and everyone folded.
        // The uncalled 20 comes back; the pot is the blinds and the 20 that was matched.
        assertEquals(HandFacts(true, true, false, true, false, 50), handFacts(finished.state))
    }

    @Test
    fun `winning the pot as the big blind by walk is neither voluntary nor a raise`() {
        val session = firstHand { it.bigBlindSeat == PLAYER_SEAT && it.toAct != PLAYER_SEAT }
        val finished = playHand(session, foldAll)
        assertEquals(false, finished.state.result!!.showdown)
        assertEquals(HandFacts(false, false, false, true, false, 20), handFacts(finished.state))
    }

    @Test
    fun `calling the big blind from the small blind is voluntary, and checking the option is not`() {
        var small = firstHand { it.smallBlindSeat == PLAYER_SEAT }
        while (small.state.toAct != PLAYER_SEAT) small = small.act(small.state.toAct, Action.Fold)!!
        val called = playHand(small.act(PLAYER_SEAT, Action.Call)!!, foldAll)
        val calledFacts = handFacts(called.state)
        assertEquals(true, calledFacts.voluntarilyIn)
        assertEquals(false, calledFacts.raisedPreflop)

        val big = firstHand { it.bigBlindSeat == PLAYER_SEAT && it.toAct != PLAYER_SEAT }
        // Everyone else limps to the big blind, who checks the option.
        var session = big
        while (session.state.phase == Phase.BETTING && session.state.toAct != PLAYER_SEAT) {
            session = session.act(session.state.toAct, if (session.state.toCall(session.state.toAct) > 0) Action.Call else Action.Check)!!
        }
        assertEquals(PLAYER_SEAT, session.state.toAct)
        assertEquals(Street.PREFLOP, session.state.street)
        val checked = playHand(session, foldAll)
        assertEquals(false, handFacts(checked.state).voluntarilyIn)
        assertEquals(false, handFacts(checked.state).raisedPreflop)
    }

    @Test
    fun `a showdown is reached by staying in, and won only with a share of a pot`() {
        var reachedLost = false
        var reachedWon = false
        for (seed in 1L..300L) {
            val finished = playHand(startTournament(seed), station)
            val facts = handFacts(finished.state)
            assertEquals(true, facts.reachedShowdown)
            assertEquals(facts.chipsWon > 0, facts.won)
            assertEquals(facts.won, facts.wonShowdown)
            if (facts.won) reachedWon = true else reachedLost = true
            if (reachedWon && reachedLost) break
        }
        assertTrue(reachedWon && reachedLost)
    }

    // ---- the committed fixture sequence ----------------------------------------------------

    /** The expected figures, counted in the test from each hand's history and awards rather than by [handFacts]. */
    private class Oracle {
        var played = 0
        var won = 0
        var reached = 0
        var reachedWon = 0
        var largest = 0
        var voluntary = 0
        var raised = 0
        val finishes = mutableListOf<Int>()

        fun hand(state: HoldemState) {
            if (state.holeCards[PLAYER_SEAT].isEmpty()) return
            played++
            var level = state.blinds.big
            var putInVoluntarily = false
            var raisedPreflop = false
            for (entry in state.history.filter { it.street == Street.PREFLOP }) {
                if (entry.seat == PLAYER_SEAT) {
                    if (entry.chips > 0) putInVoluntarily = true
                    if (entry.streetTotal > level) raisedPreflop = true
                }
                level = maxOf(level, entry.streetTotal)
            }
            if (putInVoluntarily) voluntary++
            if (raisedPreflop) raised++
            val result = state.result ?: return
            val mine = result.awards.sumOf { award ->
                val at = award.winners.indexOf(PLAYER_SEAT)
                if (at >= 0) award.shares[at] else 0
            }
            val showdown = result.showdown && !state.folded[PLAYER_SEAT]
            if (mine > 0) won++
            if (showdown) reached++
            if (showdown && mine > 0) reachedWon++
            largest = maxOf(largest, mine)
        }
    }

    private fun splitPotSeed(): Long {
        for (seed in 1L..5_000L) {
            val finished = playHand(startTournament(seed), station).state
            val awards = finished.result!!.awards
            if (awards.any { PLAYER_SEAT in it.winners && it.winners.size > 1 }) return seed
        }
        error("no split pot with the player in the first 5,000 hands")
    }

    private fun wonTournamentSeed(): Long {
        for (seed in 1L..2_000L) {
            var session = startTournament(seed)
            while (true) {
                session = playHand(session, shoveVersusStations)
                if (session.state.phase == Phase.TOURNAMENT_OVER) break
                session = session.nextHand()!!
            }
            if (session.state.places[PLAYER_SEAT] == 1) return seed
        }
        error("no tournament won by shoving in 2,000 seeds")
    }

    @Test
    fun `statistics after a win, a leave, a split pot and a hand won without showdown match an independent count`() {
        val oracle = Oracle()
        var stored = StoredTournament()
        var tournaments = 0

        /** Plays a tournament on [seed], settling each hand as the view model does, until the player is out. */
        fun playTournament(seed: Long, firstHand: Chooser? = null, choose: Chooser) {
            stored = stored.started(seed)
            var session = startTournament(seed)
            var hand = 0
            while (true) {
                session = playHand(session, if (hand++ == 0 && firstHand != null) firstHand else choose)
                oracle.hand(session.state)
                stored = settleHand(stored, session.state)
                // Settling twice is settling once.
                assertSame(stored, settleHand(stored, session.state))
                if (stored.tournament == null) {
                    oracle.finishes += session.state.places[PLAYER_SEAT]!!
                    tournaments++
                    return
                }
                session = session.nextHand()!!
            }
        }

        // A tournament won, by shoving into callers.
        playTournament(wonTournamentSeed(), choose = shoveVersusStations)
        assertEquals(1, oracle.finishes.single())
        assertEquals(1, stored.statistics.tournamentsWon)

        // A tournament whose first hand is won without a showdown, then left in the second.
        val leaveSeed = (1L..200L).first { seed -> startTournament(seed).state.toAct == PLAYER_SEAT }
        stored = stored.started(leaveSeed)
        var session = playHand(startTournament(leaveSeed), raiseThenFold)
        assertEquals(false, session.state.result!!.showdown)
        oracle.hand(session.state)
        stored = settleHand(stored, session.state)
        assertEquals(2, stored.tournament!!.handNumber)
        session = session.nextHand()!!
        val left = session.leave()!!
        oracle.hand(left.state) // the hand it was left in counts as played and nothing else
        stored = settleTournament(stored, left.state)
        assertSame(stored, settleTournament(stored, left.state))
        oracle.finishes += left.state.places[PLAYER_SEAT]!!
        tournaments++
        assertNull(stored.tournament)

        // A tournament whose first hand is a split pot with the player in it, played out calmly until the player is knocked out.
        playTournament(splitPotSeed(), firstHand = station, choose = randomChooser(wild = true))

        val s = stored.statistics
        assertEquals(oracle.played, s.handsPlayed)
        assertEquals(oracle.won, s.handsWon)
        assertEquals(oracle.reached, s.showdownsReached)
        assertEquals(oracle.reachedWon, s.showdownsWon)
        assertEquals(oracle.largest, s.largestPot)
        assertEquals(oracle.voluntary, s.voluntaryHands)
        assertEquals(oracle.raised, s.preflopRaiseHands)
        assertEquals(tournaments, s.tournamentsPlayed)
        assertEquals(oracle.finishes.count { it == 1 }, s.tournamentsWon)
        assertEquals(oracle.finishes.average(), s.averageFinish!!, 1e-9)
        for (place in 1..6) assertEquals(oracle.finishes.count { it == place }, s.placeCounts[place - 1])

        // The fixtures did what they were built for.
        assertTrue("a hand won without showdown", oracle.won > oracle.reachedWon)
        assertTrue(oracle.reached >= 1 && oracle.voluntary >= 1 && oracle.raised >= 1)
        assertEquals(1, oracle.finishes.count { it == 1 })

        // The tournament figures are `core/session`'s over the same finishes.
        val session3 = sessionStatisticsOf(oracle.finishes)
        assertEquals(s.tournamentsWon, session3.wins)
        assertEquals(s.tournamentsPlayed, session3.gamesPlayed)
        assertEquals(s.winRate!!, session3.winRate!!, 1e-9)
        assertEquals(s.currentStreak, session3.currentStreak)
        assertEquals(s.bestStreak, session3.longestStreak)
    }

    @Test
    fun `the split pot fixture really splits, and the player takes a share of it`() {
        val seed = splitPotSeed()
        val state = playHand(startTournament(seed), station).state
        val award = state.result!!.awards.first { PLAYER_SEAT in it.winners && it.winners.size > 1 }
        val facts = handFacts(state)
        assertEquals(true, facts.wonShowdown)
        assertTrue(facts.chipsWon >= award.shares[award.winners.indexOf(PLAYER_SEAT)])
        assertTrue(facts.chipsWon < state.contributions.sum())
    }

    @Test
    fun `streaks, the average finish and the rates follow the finishes`() {
        val finishes = listOf(1, 1, 3, 1, 1, 1, 6, 2)
        var stats = HoldemStatistics()
        for (place in finishes) stats = stats.afterTournament(place)
        assertEquals(8, stats.tournamentsPlayed)
        assertEquals(5, stats.tournamentsWon)
        assertEquals(0, stats.currentStreak)
        assertEquals(3, stats.bestStreak)
        assertEquals(16.0 / 8, stats.averageFinish!!, 1e-9)
        assertEquals(5.0 / 8, stats.winRate!!, 1e-9)
        val session = sessionStatisticsOf(finishes)
        assertEquals(stats.bestStreak, session.longestStreak)
        assertEquals(stats.currentStreak, session.currentStreak)
        assertEquals(stats.afterTournament(1).currentStreak, 1)

        assertNull(HoldemStatistics().winRate)
        assertNull(HoldemStatistics().averageFinish)
        assertNull(HoldemStatistics().voluntaryRate)
        assertEquals(listOf(5, 1, 1, 0, 0, 1), stats.placeCounts)
    }

    @Test
    fun `a hint is counted and resetting is just empty statistics`() {
        val stats = HoldemStatistics().hintUsed().hintUsed()
        assertEquals(2, stats.hintsUsed)
        assertEquals(HoldemStatistics(), HoldemStatistics())
    }

    // ---- settlement ------------------------------------------------------------------------

    @Test
    fun `settling a hand moves the tournament to the next hand as the engine deals it`() {
        val seed = 14L
        var session = playHand(startTournament(seed), randomChooser())
        val stored = StoredTournament(session.state.tournament)
        val settled = settleHand(stored, session.state)
        if (session.state.places[PLAYER_SEAT] == null) {
            assertEquals(session.nextHand()!!.state.tournament, settled.tournament)
        }
        assertEquals(1, settled.statistics.handsPlayed)
        // A hand from further on, or earlier, or another tournament's, changes nothing.
        assertSame(settled, settleHand(settled, session.state))
        assertEquals(1, settled.statistics.handsPlayed)
    }

    @Test
    fun `leaving between hands ends the tournament without counting the settled hand again`() {
        val seed = (1L..100L).first { s -> playHand(startTournament(s), station).state.places[PLAYER_SEAT] == null }
        val finished = playHand(startTournament(seed), station)
        var stored = settleHand(StoredTournament(finished.state.tournament), finished.state)
        val left = finished.leave()!!
        stored = settleTournament(stored, left.state)
        assertNull(stored.tournament)
        assertEquals(1, stored.statistics.handsPlayed)
        assertEquals(1, stored.statistics.tournamentsPlayed)
        assertEquals(left.state.places[PLAYER_SEAT], stored.statistics.placeCounts.indexOfFirst { it == 1 } + 1)
    }
}

/** Hand 1 of the first seed for which [accept] holds of the dealt hand. */
private fun firstHand(accept: (HoldemState) -> Boolean): HoldemSession {
    for (seed in 1L..500L) {
        val session = startTournament(seed)
        if (session.state.phase == Phase.BETTING && accept(session.state)) return session
    }
    error("no seed in 500 deals a hand like that")
}
