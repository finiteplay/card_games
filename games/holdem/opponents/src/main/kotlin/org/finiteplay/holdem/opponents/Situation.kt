package org.finiteplay.holdem.opponents

import org.finiteplay.holdem.rules.Action
import org.finiteplay.holdem.rules.Contract
import org.finiteplay.holdem.rules.HistoryEntry
import org.finiteplay.holdem.rules.SeatView
import org.finiteplay.holdem.rules.Street

/** The state of the betting just before one entry of the hand's history, as the actor saw it. */
internal class Step(
    val entry: HistoryEntry,
    val potBefore: Int,
    val toCallBefore: Int,
    val currentBetBefore: Int,
    val streetBetBefore: Int,
    val betsBefore: Int,
    val limpersBefore: Int,
    val stackBefore: Int,
    val liveBefore: Int,
    val lastRaiserBehind: Int,
)

/**
 * What one seat's [SeatView] says about the hand, worked out once: positions, effective stacks, and
 * the betting rebuilt entry by entry so that every observed action can be judged against the spot it
 * was taken in.
 */
internal class Situation(val view: SeatView) {
    val me = view.seat
    val bb = view.blinds.big
    val inHandSeats: List<Int> = (0 until Contract.SEATS).filter { view.inHand[it] }
    val opponents: IntArray = inHandSeats.filter { it != me && !view.folded[it] }.toIntArray()
    val startStacks = IntArray(Contract.SEATS) { view.stacks[it] + view.contributions[it] }

    /** Seats still to act behind each seat preflop, blinds included: 0 for the big blind, 1 for the small. */
    val behind = IntArray(Contract.SEATS).also { out ->
        fun order(s: Int) = (s - view.bigBlindSeat - 1 + Contract.SEATS) % Contract.SEATS
        for (s in inHandSeats) out[s] = inHandSeats.count { it != s && order(it) > order(s) }
    }

    val steps: List<Step>
    var bets = 0
        private set
    var limpers = 0
        private set
    var lastRaiserBehind = -1
        private set
    val live: Int = inHandSeats.count { !view.folded[it] }

    init {
        val n = Contract.SEATS
        val streetBets = IntArray(n)
        val left = startStacks.copyOf()
        var pot = 0
        var street = Street.PREFLOP
        var curBet = view.blinds.big
        var foldedCount = 0
        for ((seat, blind) in listOf(view.smallBlindSeat to view.blinds.small, view.bigBlindSeat to view.blinds.big)) {
            val put = minOf(blind, left[seat])
            streetBets[seat] = put
            left[seat] -= put
            pot += put
        }
        val out = ArrayList<Step>(view.history.size)
        for (e in view.history) {
            if (e.street != street) {
                street = e.street
                streetBets.fill(0)
                curBet = 0
                bets = 0
                limpers = 0
            }
            out += Step(
                entry = e,
                potBefore = pot,
                toCallBefore = maxOf(0, curBet - streetBets[e.seat]),
                currentBetBefore = curBet,
                streetBetBefore = streetBets[e.seat],
                betsBefore = bets,
                limpersBefore = limpers,
                stackBefore = left[e.seat],
                liveBefore = inHandSeats.size - foldedCount,
                lastRaiserBehind = lastRaiserBehind,
            )
            left[e.seat] -= e.chips
            streetBets[e.seat] = e.streetTotal
            pot += e.chips
            when (e.action) {
                Action.Fold -> foldedCount++
                Action.Call -> if (street == Street.PREFLOP && bets == 0) limpers++
                Action.Check -> Unit
                else -> if (e.streetTotal > curBet) {
                    bets++
                    curBet = e.streetTotal
                    lastRaiserBehind = behind[e.seat]
                }
            }
        }
        steps = out
        if (street != view.street) {
            bets = 0
            limpers = 0
        }
    }

    /** Chips this seat could win or lose against the biggest stack still contesting, in big blinds. */
    fun effectiveBb(seat: Int): Double {
        var other = 0
        for (s in inHandSeats) if (s != seat && !view.folded[s]) other = maxOf(other, startStacks[s])
        return minOf(startStacks[seat], other).toDouble() / bb
    }

    val boardIds: IntArray = IntArray(view.board.size) { view.board[it].id }
}
