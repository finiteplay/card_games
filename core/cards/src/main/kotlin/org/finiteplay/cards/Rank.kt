package org.finiteplay.cards

/** Declaration order is Ace through King, matching the canonical deck order. */
enum class Rank(val value: Int) {
    ACE(1),
    TWO(2),
    THREE(3),
    FOUR(4),
    FIVE(5),
    SIX(6),
    SEVEN(7),
    EIGHT(8),
    NINE(9),
    TEN(10),
    JACK(11),
    QUEEN(12),
    KING(13),
    ;

    companion object {
        private val byValue = entries.associateBy(Rank::value)
        fun fromValue(value: Int): Rank =
            byValue[value] ?: throw IllegalArgumentException("No rank with value $value")
    }
}
