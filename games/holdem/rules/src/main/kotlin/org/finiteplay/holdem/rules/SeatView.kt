package org.finiteplay.holdem.rules

import org.finiteplay.cards.Card

/**
 * Everything one seat can know, and the only game type the opponents module receives
 * (`DESIGN.md` "What Hold'em is not", "Architecture"): the seat's own hole cards, the board, every
 * stack, the pot, the blinds, the button and the action history.
 *
 * There is no field from which another seat's hole cards or the undealt deck can be read. Neither
 * the hand's seed nor the tournament's is here either, since either one rebuilds the deck. Cards
 * other seats have turned over at a showdown are public and appear in [revealed], only then.
 *
 * [legal] is set only when it is this seat's turn; [toCall] is always the seat's own cost to match.
 */
data class SeatView(
    val seat: Int,
    val holeCards: List<Card>,
    val board: List<Card>,
    val street: Street,
    val phase: Phase,
    val handNumber: Int,
    val blinds: Blinds,
    val button: Int,
    val smallBlindSeat: Int,
    val bigBlindSeat: Int,
    val stacks: List<Int>,
    val streetBets: List<Int>,
    val contributions: List<Int>,
    val inHand: List<Boolean>,
    val folded: List<Boolean>,
    val allIn: List<Boolean>,
    val inTournament: List<Boolean>,
    val pot: Int,
    val pots: List<Pot>,
    val currentBet: Int,
    val raiseIncrement: Int,
    val toAct: Int,
    val toCall: Int,
    val legal: LegalActions?,
    val history: List<HistoryEntry>,
    val revealed: Map<Int, List<Card>>,
) {
    val isMyTurn: Boolean get() = toAct == seat
}

/** What [seat] sees of this state. */
fun HoldemState.seatView(seat: Int): SeatView {
    require(seat in 0 until Contract.SEATS) { "no seat $seat" }
    return SeatView(
        seat = seat,
        holeCards = holeCards[seat],
        board = board,
        street = street,
        phase = phase,
        handNumber = handNumber,
        blinds = blinds,
        button = button,
        smallBlindSeat = smallBlindSeat,
        bigBlindSeat = bigBlindSeat,
        stacks = stacks,
        streetBets = streetBets,
        contributions = contributions,
        inHand = List(Contract.SEATS, ::inHand),
        folded = folded,
        allIn = allIn,
        inTournament = places.map { it == null },
        pot = pot,
        pots = pots,
        currentBet = currentBet,
        raiseIncrement = raiseIncrement,
        toAct = toAct,
        toCall = toCall(seat),
        legal = if (phase == Phase.BETTING && toAct == seat) legalActions(this) else null,
        history = history,
        revealed = result?.takeIf { it.showdown }?.values?.keys?.associateWith { holeCards[it] } ?: emptyMap(),
    )
}
