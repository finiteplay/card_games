package org.finiteplay.holdem.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * A reference hand set pinned by canonical state hash (`EXECUTION_PLAN.md` RF). A failure here means
 * a rule, the deal order, the seed mix or the action alphabet changed — which needs a new rules or
 * shuffle version, not an edited expected value.
 */
class ReferenceHandsTest {
    private val pinned = mapOf(
        "wheel" to -9171024696388728140L,
        "board plays" to -727570812897328280L,
        "odd chip split" to 841065244023364040L,
        "short all-in" to 5504084042193656150L,
        "big blind option" to 1312584095101157237L,
        "heads-up" to -7786350074486306897L,
        "equal knockouts" to 1783109518132630047L,
        "unequal knockouts" to -3054333870772317897L,
        "short big blind" to -5320763873511132363L,
        "short big blind folded to" to -541993896136465184L,
    )

    @Test
    fun `every reference hand reproduces its pinned state`() {
        val actual = ReferenceHands.names.associateWith { canonicalStateHash(ReferenceHands.build(it)) }
        assertEquals(pinned, actual)
    }

    @Test
    fun `the hash distinguishes states that differ in any field`() {
        val base = dealHand(Tournament.start(5L))
        val others = listOf(
            base.copy(currentBet = base.currentBet + 1),
            base.copy(toAct = (base.toAct + 1) % 6),
            base.copy(street = Street.FLOP),
            base.copy(phase = Phase.HAND_OVER),
            base.copy(raiseIncrement = base.raiseIncrement + 1),
            base.copy(stacks = base.stacks.toMutableList().also { it[0] += 1 }),
            base.copy(deck = base.deck.reversed()),
            base.copy(tournament = base.tournament.copy(button = (base.button + 1) % 6)),
            base.copy(history = base.history + HistoryEntry(Street.PREFLOP, 0, Action.Fold, 0, 0, false)),
        )
        val hashes = (listOf(base) + others).map(::canonicalStateHash)
        assertEquals(hashes.size, hashes.toSet().size)
        assertNotEquals(canonicalStateHash(base), canonicalStateHash(dealHand(Tournament.start(6L))))
    }

    @Test
    fun `the action alphabet is part of the contract`() {
        // Exhaustive: adding an action is a compile error here until the contract is revisited.
        val names = listOf(Action.Fold, Action.Check, Action.Call, Action.Bet(1), Action.Raise(2), Action.AllIn).map {
            when (it) {
                Action.Fold -> "Fold"
                Action.Check -> "Check"
                Action.Call -> "Call"
                is Action.Bet -> "Bet"
                is Action.Raise -> "Raise"
                Action.AllIn -> "AllIn"
            }
        }
        assertEquals(listOf("Fold", "Check", "Call", "Bet", "Raise", "AllIn"), names)
    }
}
