package org.finiteplay.freecell.layout

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit

/** Tableau columns FreeCell deals into (`docs/games/freecell/RULES.md`). */
const val TABLEAU_COLUMNS = 8

/** Free cells, each holding at most one card. */
const val FREE_CELLS = 4

/** One standard deck; there is no second deck the way Spider has. */
const val DECK_CARDS = 52

data class GameVersions(
    val catalogVersion: Int,
    val rulesVersion: Int,
    val shuffleVersion: Int,
)

enum class GameStatus {
    IN_PROGRESS,
    WON,
}

/**
 * The complete, immutable layout of one FreeCell deal. A single reducer
 * (`org.finiteplay.freecell.rules.applyMove`) owns every committed transition; nothing else
 * produces a state.
 *
 * Every card is face up, everywhere, always (`docs/games/freecell/RULES.md` "The layout") — there
 * is no face-down bit anywhere in this type, unlike Klondike's `TableauCard` or Spider's.
 *
 * Pile conventions, matching Klondike's and Spider's so a reader moving between games is not
 * caught out:
 * - [tableau] columns run bottom (index 0) to top (last index).
 * - [freeCells] is a fixed-size list of [FREE_CELLS] slots; `null` means empty.
 * - [foundations] holds the highest rank value placed per suit; 0 means empty.
 */
data class FreeCellState(
    val seed: Long,
    val versions: GameVersions,
    val tableau: List<List<Card>>,
    val freeCells: List<Card?>,
    val foundations: Map<Suit, Int>,
    val status: GameStatus,
    val moveCount: Int = 0,
) {
    init {
        require(tableau.size == TABLEAU_COLUMNS) { "tableau must have $TABLEAU_COLUMNS columns" }
        require(freeCells.size == FREE_CELLS) { "freeCells must have $FREE_CELLS slots" }
        require(foundations.keys == Suit.entries.toSet()) { "foundations must have an entry for every suit" }
    }

    val isWon: Boolean get() = status == GameStatus.WON

    /** Empty free cells right now — one term of the supermove formula (`RULES.md` "Supermove"). */
    val emptyFreeCells: Int get() = freeCells.count { it == null }

    /** Empty tableau columns right now — the other term of the supermove formula. */
    val emptyColumns: Int get() = tableau.count { it.isEmpty() }

    fun foundationTop(suit: Suit): Card? {
        val rankValue = foundations.getValue(suit)
        return if (rankValue == 0) null else Card(suit, Rank.fromValue(rankValue))
    }
}
