package org.finiteplay.cards

/**
 * A single card. [id] is its canonical deck index (0..51): Clubs, Diamonds, Hearts,
 * Spades; Ace through King, matching the deal contract's shuffle indices.
 */
data class Card(val suit: Suit, val rank: Rank) {
    val id: Int get() = suit.ordinal * RANKS_PER_SUIT + rank.ordinal
    val color: CardColor get() = suit.color

    companion object {
        const val RANKS_PER_SUIT = 13
        val DECK_SIZE = Suit.entries.size * RANKS_PER_SUIT

        /** The full canonical deck, index `i` is the card with `id == i`. */
        val CANONICAL_DECK: List<Card> = Suit.entries.flatMap { suit -> Rank.entries.map { rank -> Card(suit, rank) } }

        fun fromId(id: Int): Card {
            require(id in 0 until DECK_SIZE) { "id must be in 0..${DECK_SIZE - 1}, was $id" }
            return CANONICAL_DECK[id]
        }
    }
}
