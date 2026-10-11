package org.finiteplay.holdem.rules

import org.finiteplay.cards.Card

/**
 * The five board cards the hand would have had, from the deck positions the contract fixes (a burn,
 * then the flop, a burn and the turn, a burn and the river), however early the hand ended. Only for
 * looking back at a finished hand: the cards are hidden while it is played.
 */
fun HoldemState.runOutBoard(): List<Card> {
    val dealt = 2 * seatsInHand.size
    return listOf(deck[dealt + 1], deck[dealt + 2], deck[dealt + 3], deck[dealt + 5], deck[dealt + 7])
}

/**
 * In hindsight, would [seat]'s hand, run out to the river, have beaten every hand still contesting
 * at the end of the hand? Null when the seat did not fold or nobody else was left to compare with.
 * It judges the cards only: had the seat stayed in, the others might have bet differently.
 */
fun HoldemState.wouldHaveWon(seat: Int): Boolean? {
    if (!inHand(seat) || !folded[seat]) return null
    val rivals = seatsInHand.filter { it != seat && !folded[it] }
    if (rivals.isEmpty()) return null
    val board = runOutBoard()
    val mine = evaluate(holeCards[seat] + board)
    return rivals.all { mine > evaluate(holeCards[it] + board) }
}
