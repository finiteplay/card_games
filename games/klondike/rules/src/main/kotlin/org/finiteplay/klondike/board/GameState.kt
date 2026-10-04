package org.finiteplay.klondike.board

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit

const val TABLEAU_COLUMNS = 7

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
 * How many cards [org.finiteplay.klondike.rules.Move.Draw] moves from stock to
 * waste at once; only the resulting waste top is ever playable either way. Fixed for
 * the life of a game — there is no live in-game toggle, unlike the automatic-moves
 * setting — so it is part of the dealt board itself, not session state layered on
 * top. Tableau rules, foundation rules, and every other mechanic are identical in
 * both modes.
 */
enum class DrawMode { ONE, THREE }

/**
 * The complete, immutable state of one game. A single reducer (see `rules`) owns every
 * committed transition; nothing else mutates a board directly.
 *
 * Pile conventions:
 * - [tableau] columns are ordered bottom (index 0) to top (last index).
 * - [waste] and [stock] are ordered top/next-to-draw (index 0) to bottom (last index).
 * - [foundations] holds the highest rank value placed per suit; 0 means empty.
 * - [parkedCard] is a card withdrawn from a foundation that automation must not
 *   reclaim while it stays uncovered (E2a).
 */
data class GameState(
    val seed: Long,
    val versions: GameVersions,
    val tableau: List<List<TableauCard>>,
    val foundations: Map<Suit, Int>,
    val waste: List<Card>,
    val stock: List<Card>,
    val status: GameStatus,
    val parkedCard: Card? = null,
    val moveCount: Int = 0,
    val drawMode: DrawMode = DrawMode.ONE,
) {
    init {
        require(tableau.size == TABLEAU_COLUMNS) { "tableau must have $TABLEAU_COLUMNS columns" }
        require(foundations.keys == Suit.entries.toSet()) { "foundations must have an entry for every suit" }
    }

    fun foundationTop(suit: Suit): Card? {
        val rankValue = foundations.getValue(suit)
        return if (rankValue == 0) null else Card(suit, Rank.fromValue(rankValue))
    }

    val isWon: Boolean get() = status == GameStatus.WON
}
