package org.finiteplay.holdem.rules

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit

/** Test fixtures: hand-built decks and tournaments. Namespaced so it cannot collide with other test helpers. */
object Fx {
    /** "As" or "AS" is the Ace of Spades, "Td" the Ten of Diamonds, "9c" the Nine of Clubs. */
    fun card(text: String): Card {
        val rank = when (text[0]) {
            'A' -> Rank.ACE
            'T' -> Rank.TEN
            'J' -> Rank.JACK
            'Q' -> Rank.QUEEN
            'K' -> Rank.KING
            else -> Rank.fromValue(text[0].digitToInt())
        }
        val suit = when (text[1].uppercaseChar()) {
            'C' -> Suit.CLUBS
            'D' -> Suit.DIAMONDS
            'H' -> Suit.HEARTS
            else -> Suit.SPADES
        }
        return Card(suit, rank)
    }

    fun cards(text: String): List<Card> = text.split(' ').filter { it.isNotEmpty() }.map(::card)

    /** The inverse of [card], upper case: "AS", "TD". */
    fun text(card: Card): String {
        val rank = when (card.rank) {
            Rank.ACE -> "A"
            Rank.TEN -> "T"
            Rank.JACK -> "J"
            Rank.QUEEN -> "Q"
            Rank.KING -> "K"
            else -> card.rank.value.toString()
        }
        return rank + card.suit.name[0]
    }

    fun text(cards: List<Card>): String = cards.joinToString(" ", transform = ::text)

    /**
     * A tournament with these [stacks]; a seat with none is already out, at a place counting down
     * from sixth. [stacks] need not sum to the table's chips unless a test says so.
     */
    fun tournament(stacks: List<Int>, button: Int, handNumber: Int = 1, seed: Long = 1L): Tournament {
        var place = Contract.SEATS
        val places = stacks.map { if (it == 0) place-- else null }
        return Tournament(seed, handNumber, button, stacks, places)
    }

    /**
     * A full deck in the contract's dealing order with [holes] (a seat's two cards, "Ah Kd") and the
     * [board] (five cards: flop, turn, river) stacked where they will be dealt, burns and the rest
     * filled from the unused cards.
     */
    fun deck(t: Tournament, holes: Map<Int, String>, board: String): List<Card> {
        val alive = t.alive
        val order = (1..Contract.SEATS).map { (t.button + it) % Contract.SEATS }.filter { it in alive }
        val n = order.size
        val slots = arrayOfNulls<Card>(52)
        for ((seat, text) in holes) {
            val two = cards(text)
            val i = order.indexOf(seat)
            slots[i] = two[0]
            slots[n + i] = two[1]
        }
        val b = cards(board)
        require(b.size == 5)
        val p = 2 * n
        slots[p + 1] = b[0]; slots[p + 2] = b[1]; slots[p + 3] = b[2]; slots[p + 5] = b[3]; slots[p + 7] = b[4]
        val used = slots.filterNotNull().toSet()
        require(used.size == slots.count { it != null }) { "a card is stacked twice" }
        val spare = Card.CANONICAL_DECK.filter { it !in used }.iterator()
        return slots.map { it ?: spare.next() }
    }

    fun hand(t: Tournament, holes: Map<Int, String>, board: String): HoldemState = dealHand(t, deck(t, holes, board))

    fun HoldemState.step(seat: Int, action: Action): HoldemState =
        apply(this, seat, action) ?: error("seat $seat: $action is not legal; offered ${legalActions(this)}")

    fun HoldemState.run(vararg steps: Pair<Int, Action>): HoldemState {
        var state = this
        for ((seat, action) in steps) state = state.step(seat, action)
        return state
    }

    /** Everyone checks, or calls what is asked, until the hand is over. */
    fun HoldemState.checkDown(): HoldemState {
        var state = this
        while (state.phase == Phase.BETTING) {
            val legal = legalActions(state)!!
            state = state.step(legal.seat, if (legal.canCheck) Action.Check else Action.Call)
        }
        return state
    }
}
