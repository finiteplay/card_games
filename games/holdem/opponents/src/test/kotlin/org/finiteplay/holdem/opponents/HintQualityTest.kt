package org.finiteplay.holdem.opponents

import org.finiteplay.cards.Card
import org.finiteplay.holdem.rules.Action
import org.finiteplay.holdem.rules.SeatView
import org.finiteplay.holdem.rules.Street
import org.finiteplay.holdem.rules.seatView
import org.finiteplay.holdem.rules.startTournament
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.IntStream

/**
 * The Hint teaches, so its advice has to agree with the numbers it shows: the coach's rules
 * (`Hint.kt`) hold over thousands of real decisions from seeded tournaments, and every reason it
 * gives is true of the spot it is given for.
 */
class HintQualityTest {
    private val reasons = ConcurrentHashMap<HintReason, AtomicInteger>()
    private val failures = ConcurrentHashMap.newKeySet<String>()

    private fun fail(what: String, view: SeatView, h: HintResult) {
        failures += "$what: seed-hand ${view.handNumber} ${view.street} equity %.3f odds %.3f -> ${h.suggested} (${h.reason})".format(h.equity, h.potOdds)
    }

    private fun audit(view: SeatView) {
        val h = hint(view)
        val legal = view.legal!!
        reasons.computeIfAbsent(h.reason) { AtomicInteger() }.incrementAndGet()
        if (!legal.allows(h.suggested)) fail("illegal suggestion", view, h)
        if (view.toCall == 0 && h.suggested == Action.Fold) fail("folds when checking is free", view, h)
        if (view.street == Street.PREFLOP) return
        val facing = view.toCall > 0
        val aggressive = h.suggested is Action.Bet || h.suggested is Action.Raise || h.suggested == Action.AllIn
        if (facing && h.suggested == Action.Fold && legal.canCall && h.equity >= h.potOdds + 0.011) fail("folds a profitable call", view, h)
        if (!facing && h.suggested == Action.Check && h.equity >= 0.70 && legal.bet != null) fail("hides a strong hand", view, h)
        if (facing && h.suggested == Action.Call && h.equity >= 0.80 && legal.raise != null) fail("only calls a monster", view, h)
        if (h.reason == HintReason.CALL_GOOD_PRICE && h.equity < h.potOdds) fail("claims a good price it does not have", view, h)
        if (h.reason == HintReason.CALL_DRAW && (h.equity >= h.potOdds || h.outs == 0)) fail("claims a draw it does not have", view, h)
        if (h.reason == HintReason.SEMI_BLUFF && (h.outs == 0 || !aggressive)) fail("claims a semi-bluff without a draw", view, h)
        if ((h.reason == HintReason.VALUE_BET || h.reason == HintReason.VALUE_RAISE) && !aggressive) fail("claims value but does not bet", view, h)
        val floor = if (h.outs > 0) 0.30 else 0.40
        if (aggressive && h.equity < floor) fail("recommends aggression without the equity for it", view, h)
        // Never "you are probably ahead" beside a high chance that someone already beats you on the cards showing.
        val behind = (h.threats?.beatsYou ?: 0.0) > 0.5
        if (aggressive && behind && h.outs > 0 && h.reason != HintReason.SEMI_BLUFF) fail("calls a draw a value bet", view, h)
        if (aggressive && behind && h.outs == 0) fail("aggressive while probably behind with no draw to come", view, h)
    }

    @Test
    fun `the advice agrees with the numbers over thousands of decisions`() {
        IntStream.range(0, 120).parallel().forEach { i ->
            val seed = 0xA0D17000L + i
            val policies = listOf(ProfilePolicy(Profiles.tag)) + drawOpponents(seed).map { ProfilePolicy(it.profile) }
            TournamentRunner.play(seed, policies, observer = { view, _ -> if (view.seat == 0) audit(view) })
        }
        assertTrue("incoherent advice:\n" + failures.sorted().take(12).joinToString("\n"), failures.isEmpty())
        // Every lesson the hint can teach comes up in ordinary play, except the rare ones.
        val rare = setOf(HintReason.SEMI_BLUFF, HintReason.CALL_DRAW)
        for (r in HintReason.entries) if (r !in rare) assertTrue("$r never given", (reasons[r]?.get() ?: 0) > 0)
        println("HINT reasons " + reasons.toSortedMap().map { "${it.key}=${it.value}" })
    }

    private fun hole(c: String): Card = Card.CANONICAL_DECK.first { it.suit.name[0] == c[1] && "A23456789TJQK"[it.rank.value - 1] == c[0] }

    private fun reasonOf(a: String, b: String): HintReason {
        val state = startTournament(3L).state
        return hint(state.seatView(state.toAct).copy(holeCards = listOf(hole(a), hole(b)))).reason
    }

    @Test
    fun `a premium hand raises and trash is not played`() {
        val aces = reasonOf("AS", "AH")
        assertTrue("aces: $aces", aces == HintReason.PREMIUM_RAISE || aces == HintReason.PUSH_SHORT_STACK)
        val trash = reasonOf("7S", "2H")
        assertTrue("trash: $trash", trash == HintReason.FOLD_WEAK || trash == HintReason.FOLD_SHORT_STACK || trash == HintReason.FREE_FLOP)
        assertEquals(aces, reasonOf("AS", "AH"))
    }
}
