package org.finiteplay.klondike.deal

/**
 * Canonical pile order for deterministic ties, frozen by the deal contract:
 * waste, tableau left-to-right, foundations Clubs/Diamonds/Hearts/Spades, then stock.
 * Ordinal order is the canonical order; do not reorder these entries.
 */
enum class PileId {
    WASTE,
    TABLEAU_1,
    TABLEAU_2,
    TABLEAU_3,
    TABLEAU_4,
    TABLEAU_5,
    TABLEAU_6,
    TABLEAU_7,
    FOUNDATION_CLUBS,
    FOUNDATION_DIAMONDS,
    FOUNDATION_HEARTS,
    FOUNDATION_SPADES,
    STOCK,
    ;

    companion object {
        val TABLEAU: List<PileId> = listOf(
            TABLEAU_1, TABLEAU_2, TABLEAU_3, TABLEAU_4, TABLEAU_5, TABLEAU_6, TABLEAU_7,
        )
        val FOUNDATIONS: List<PileId> = listOf(
            FOUNDATION_CLUBS, FOUNDATION_DIAMONDS, FOUNDATION_HEARTS, FOUNDATION_SPADES,
        )
    }
}
