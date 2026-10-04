package org.finiteplay.cards

/** Declaration order is the canonical deck order fixed by the deal contract. */
enum class Suit(val symbol: Char, val color: CardColor) {
    CLUBS('♣', CardColor.BLACK),
    DIAMONDS('♦', CardColor.RED),
    HEARTS('♥', CardColor.RED),
    SPADES('♠', CardColor.BLACK),
}

enum class CardColor {
    RED,
    BLACK,
}
