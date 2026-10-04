package org.finiteplay.blackjack.rules

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rules freeze (`EXECUTION_PLAN.md` RF): a reference round set played through the frozen
 * engine, each pinned by the canonical hash of its final state, plus the contract's constants.
 * A failure here means a rule, the deal order or the opcode alphabet changed — which needs a new
 * rules or shuffle version and a regenerated Hint table, not an edited expected value.
 */
class ReferenceRoundsTest {
    private class Reference(
        val name: String,
        val shoe: List<String>,
        val decisions: List<Decision>,
        val hash: Long,
        val phase: Phase,
        val total: Int,
    )

    private val references = listOf(
        Reference("split", listOf("8C", "6D", "8H", "TS", "3S", "2S", "9S", "TH", "5S"), listOf(Decision.SPLIT, Decision.STAND, Decision.STAND), 6282030044645387503L, Phase.SETTLED, 200),
        Reference("split Aces", listOf("AC", "6D", "AH", "TS", "KS", "9S", "2S", "2H"), listOf(Decision.SPLIT), -3236507448379674521L, Phase.SETTLED, 200),
        Reference("double", listOf("5C", "TD", "6H", "6S", "TS", "TH"), listOf(Decision.DOUBLE), 5472817841817100007L, Phase.SETTLED, 200),
        Reference("insurance won", listOf("9C", "AD", "8H", "KS"), listOf(Decision.TAKE_INSURANCE), -8727909670735524010L, Phase.SETTLED, 0),
        Reference("insurance lost", listOf("9C", "AD", "8H", "6S", "KS"), listOf(Decision.TAKE_INSURANCE, Decision.STAND), -72378182498994437L, Phase.SETTLED, -50),
        Reference("player blackjack", listOf("AC", "9D", "KH", "7S"), emptyList(), -5027334564274395279L, Phase.SETTLED, 150),
        Reference("dealer blackjack", listOf("9C", "KD", "8H", "AS"), emptyList(), -3004196696957520096L, Phase.SETTLED, -100),
        Reference("push", listOf("TC", "TD", "8H", "8S"), listOf(Decision.STAND), 8338641721471536685L, Phase.SETTLED, 0),
        Reference("bust", listOf("TC", "9D", "6H", "8S", "KS"), listOf(Decision.HIT), 3869595061837261249L, Phase.SETTLED, -100),
        Reference("dealer bust", listOf("TC", "6D", "8H", "TS", "KS"), listOf(Decision.STAND), 1106081457231093469L, Phase.SETTLED, 100),
        Reference("win", listOf("TC", "TD", "9H", "7S"), listOf(Decision.STAND), 8062576354179196622L, Phase.SETTLED, 100),
        Reference("loss", listOf("TC", "TD", "7H", "9S"), listOf(Decision.STAND), 5505805838366319432L, Phase.SETTLED, -100),
    )

    @Test
    fun `every reference round reproduces its pinned state`() {
        for (ref in references) {
            var session = startRound(1L, 100, 1_000, shoeOf(*ref.shoe.toTypedArray()))
            for (decision in ref.decisions) session = session.decide(decision, 1_000)!!
            assertEquals("${ref.name}: phase", ref.phase, session.state.phase)
            assertEquals("${ref.name}: settlement", ref.total, session.state.settlement!!.total)
            assertEquals("${ref.name}: state hash", ref.hash, canonicalStateHash(session.state))
        }
    }

    @Test
    fun `the reference set covers every settlement kind`() {
        val outcomes = references.flatMap { ref ->
            var session = startRound(1L, 100, 1_000, shoeOf(*ref.shoe.toTypedArray()))
            for (decision in ref.decisions) session = session.decide(decision, 1_000)!!
            session.state.settlement!!.hands.map { it.outcome }
        }.toSet()
        assertEquals(HandOutcome.entries.toSet(), outcomes)
    }

    @Test
    fun `the frozen contract`() {
        assertEquals(1, RULES_VERSION)
        assertEquals(1, SHUFFLE_VERSION)
        assertEquals(312, SHOE_SIZE)
        assertEquals(4, MAX_HANDS)
        assertEquals(listOf<Byte>(1, 2, 3, 4, 5, 6), Decision.entries.map { it.opcode })
        assertEquals(
            listOf("HIT", "STAND", "DOUBLE", "SPLIT", "TAKE_INSURANCE", "DECLINE_INSURANCE"),
            Decision.entries.map { it.name },
        )
        assertEquals(1_000, Chips.STARTING_BANKROLL)
        assertEquals(10, Chips.MIN_BET)
        assertEquals(500, Chips.MAX_BET)
        assertEquals(10, Chips.BET_STEP)
    }

    @Test
    fun `the hash distinguishes states that differ in any field`() {
        val base = startRound(1L, 100, 1_000, shoeOf("9C", "TD", "8H", "7S", "3S")).state
        val hashes = listOf(
            base,
            base.copy(bet = 110),
            base.copy(shoePosition = 5),
            base.copy(holeRevealed = true),
            base.copy(insuranceStake = 50),
            base.copy(phase = Phase.INSURANCE),
        ).map(::canonicalStateHash)
        assertEquals(hashes.size, hashes.toSet().size)
    }
}
