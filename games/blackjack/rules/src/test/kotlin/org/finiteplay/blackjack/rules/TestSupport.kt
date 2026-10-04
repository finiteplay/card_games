package org.finiteplay.blackjack.rules

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit

/** "AS" is the Ace of Spades, "TD" the Ten of Diamonds, "9C" the Nine of Clubs. */
fun c(text: String): Card {
    val rank = when (text[0]) {
        'A' -> Rank.ACE
        'T' -> Rank.TEN
        'J' -> Rank.JACK
        'Q' -> Rank.QUEEN
        'K' -> Rank.KING
        else -> Rank.fromValue(text[0].digitToInt())
    }
    val suit = when (text[1]) {
        'C' -> Suit.CLUBS
        'D' -> Suit.DIAMONDS
        'H' -> Suit.HEARTS
        else -> Suit.SPADES
    }
    return Card(suit, rank)
}

fun cards(vararg texts: String): List<Card> = texts.map(::c)

/**
 * A full-size shoe starting with [first] in dealing order and padded with a filler no fixture
 * should reach (every fixture draws a handful of cards at most).
 */
fun shoeOf(vararg first: String): List<Card> {
    val filler = Card.CANONICAL_DECK
    return first.map(::c) + List(SHOE_SIZE - first.size) { filler[it % filler.size] }
}

/** A round dealt from a hand-built shoe: player, dealer up, player, dealer hole, then [rest] in order. */
fun roundFrom(
    player1: String,
    dealerUp: String,
    player2: String,
    hole: String,
    vararg rest: String,
    bet: Int = 100,
    bankroll: Int = 1_000,
): BlackjackSession = startRound(seed = 1L, bet = bet, bankroll = bankroll, shoe = shoeOf(player1, dealerUp, player2, hole, *rest))

fun BlackjackSession.play(vararg decisions: Decision, bankroll: Int = 1_000): BlackjackSession {
    var session = this
    for (decision in decisions) session = session.decide(decision, bankroll) ?: error("$decision not legal in ${session.state.phase}")
    return session
}

/** A hand-built state for tests that need a position no shoe reaches cheaply. */
fun stateWith(
    hands: List<PlayerHand>,
    dealer: List<Card> = cards("9S", "8H"),
    phase: Phase = Phase.PLAYING,
    activeHand: Int = 0,
    insuranceStake: Int = 0,
    bet: Int = 100,
): BlackjackState = BlackjackState(
    seed = 0L,
    rulesVersion = RULES_VERSION,
    shuffleVersion = SHUFFLE_VERSION,
    bet = bet,
    shoe = shoeOf(),
    shoePosition = 4,
    dealer = dealer,
    holeRevealed = false,
    hands = hands,
    activeHand = activeHand,
    insuranceStake = insuranceStake,
    phase = phase,
    settlement = null,
)
