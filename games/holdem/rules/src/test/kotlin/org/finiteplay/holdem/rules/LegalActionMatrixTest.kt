package org.finiteplay.holdem.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The legal-action matrix (`EXECUTION_PLAN.md` H2 gate): street x facing a bet or not x whether
 * the betting is open to the seat x the stack against the call and against the minimum raise, each
 * case held to [LegalOracle].
 */
class LegalActionMatrixTest {
    private enum class Facing { NO_BET, BIG_BLIND_OPTION, FACING }

    private class Case(
        val street: Street,
        val facing: Facing,
        val open: Boolean,
        val increment: Int,
        val streetBet: Int,
        val currentBet: Int,
        val stack: Int,
    )

    private val bigBlind = 20

    private fun cases(): List<Case> {
        val out = ArrayList<Case>()
        for (street in Street.entries) for (facing in Facing.entries) {
            // No bet is only reachable after the flop; the big blind's option only before it.
            if (facing == Facing.NO_BET && street == Street.PREFLOP) continue
            if (facing == Facing.BIG_BLIND_OPTION && street != Street.PREFLOP) continue
            for (open in listOf(true, false)) for (increment in listOf(bigBlind, 60)) {
                val positions = when (facing) {
                    Facing.NO_BET -> listOf(0 to 0)
                    Facing.BIG_BLIND_OPTION -> listOf(bigBlind to bigBlind)
                    Facing.FACING -> listOf(0 to 200, 120 to 200)
                }
                for ((streetBet, currentBet) in positions) {
                    val c = currentBet - streetBet
                    val stacks = listOf(
                        1, c - 1, c, c + 1, c + increment - 1, c + increment, c + increment + 1,
                        bigBlind - 1, bigBlind, bigBlind + 1, 1_500,
                    ).filter { it > 0 }.distinct()
                    for (stack in stacks) out += Case(street, facing, open, increment, streetBet, currentBet, stack)
                }
            }
        }
        return out
    }

    private fun stateFor(case: Case): HoldemState {
        val base = dealHand(Tournament.start(3L))
        val last = if (case.open) -1 else (case.currentBet - (case.increment - 1)).coerceAtLeast(0)
        val boardSize = when (case.street) { Street.PREFLOP -> 0; Street.FLOP -> 3; Street.TURN -> 4; Street.RIVER -> 5 }
        return base.copy(
            street = case.street,
            board = base.deck.subList(30, 30 + boardSize),
            toAct = 0,
            stacks = base.stacks.toMutableList().also { it[0] = case.stack },
            streetBets = base.streetBets.toMutableList().also { it[0] = case.streetBet },
            allIn = base.allIn.toMutableList().also { it[0] = false },
            lastActionBet = base.lastActionBet.toMutableList().also { it[0] = last },
            currentBet = case.currentBet,
            raiseIncrement = case.increment,
        )
    }

    @Test
    fun `the matrix has the committed number of cases`() {
        assertEquals(CASE_COUNT, cases().size)
    }

    @Test
    fun `every case offers exactly what the rules text says`() {
        for (case in cases()) {
            val state = stateFor(case)
            val label = "${case.street} ${case.facing} open=${case.open} inc=${case.increment} bet=${case.streetBet}/${case.currentBet} stack=${case.stack}"
            val expected = LegalOracle.expected(0, case.stack, case.streetBet, case.currentBet, case.increment, bigBlind, case.open)
            assertEquals(label, expected, legalActions(state))
        }
    }

    @Test
    fun `nothing is offered that the reducer refuses and nothing is refused that is offered`() {
        for (case in cases()) {
            val state = stateFor(case)
            val legal = legalActions(state)!!
            val label = "${case.street} ${case.facing} open=${case.open} stack=${case.stack}"
            val candidates = buildList {
                add(Action.Fold); add(Action.Check); add(Action.Call); add(Action.AllIn)
                val top = case.streetBet + case.stack
                for (n in listOf(0, 1, bigBlind - 1, bigBlind, bigBlind + 1, case.currentBet, case.currentBet + 1, top - 1, top, top + 1)) {
                    add(Action.Bet(n)); add(Action.Raise(n))
                }
                legal.raise?.let { add(Action.Raise(it.first - 1)); add(Action.Raise(it.first)); add(Action.Raise(it.last)); add(Action.Raise(it.last + 1)) }
                legal.bet?.let { add(Action.Bet(it.first - 1)); add(Action.Bet(it.first)); add(Action.Bet(it.last)); add(Action.Bet(it.last + 1)) }
            }
            for (action in candidates) {
                val result = apply(state, 0, action)
                if (legal.allows(action)) assertNotNull("$label: $action is offered", result) else assertNull("$label: $action is not offered", result)
            }
            assertNull("$label: the wrong seat", apply(state, 1, Action.Fold))
        }
    }

    @Test
    fun `fold is never offered when nothing is to be called, and all in only with a bet or raise`() {
        for (case in cases()) {
            val legal = legalActions(stateFor(case))!!
            if (legal.toCall == 0) assertFalse(legal.canFold)
            if (legal.allInTo != null) assertEquals(case.streetBet + case.stack, legal.allInTo)
            if (legal.bet != null || legal.raise != null) assertNotNull(legal.allInTo)
        }
    }

    @Test
    fun `a call that covers the stack is a call all in for the stack`() {
        val case = Case(Street.FLOP, Facing.FACING, open = true, increment = bigBlind, streetBet = 0, currentBet = 200, stack = 150)
        val legal = legalActions(stateFor(case))!!
        assertEquals(150, legal.callAmount)
        assertNull(legal.allInTo)
        val after = apply(stateFor(case), 0, Action.Call)!!
        assertEquals(0, after.stacks[0])
        assertEquals(true, after.allIn[0])
    }

    private companion object {
        /** The size of the matrix above; a change to its axes changes this on purpose. */
        const val CASE_COUNT = 456
    }
}
