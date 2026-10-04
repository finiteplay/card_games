package org.finiteplay.spider.layout

import org.finiteplay.cards.Card
import org.finiteplay.cards.Suit

/** Tableau columns Spider deals into (`docs/games/spider/RULES.md`). */
const val TABLEAU_COLUMNS = 10

/** Two decks, so every card in the layout has an identical twin. */
const val DECK_CARDS = 104

/** Complete King-to-Ace sequences to bank before the deal is won: one per set of thirteen. */
const val SEQUENCES_TO_WIN = 8

/** Cards a row deal turns face up, one per column, five times over. */
const val ROW_DEALS = 5

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
 * How many suits the two decks are dealt from, which is Spider's difficulty
 * (`RULES.md` "Suit counts"). Fixed by the deal and never changed during a game — part of
 * the dealt layout rather than session state, the same way Klondike's draw mode is.
 */
enum class SuitCount(val suits: List<Suit>) {
    ONE(listOf(Suit.SPADES)),
    TWO(listOf(Suit.SPADES, Suit.HEARTS)),
    FOUR(Suit.entries.toList()),
    ;

    /** Copies of each suit's thirteen ranks needed to make [DECK_CARDS] cards. */
    val setsPerSuit: Int get() = DECK_CARDS / (suits.size * Card.RANKS_PER_SUIT)
}

/** One card in a tableau column, with the only thing that varies about it. */
data class TableauCard(val card: Card, val faceUp: Boolean)

/**
 * The complete, immutable layout of one Spider deal. A single reducer
 * (`org.finiteplay.spider.rules.applyMove`) owns every committed transition; nothing else
 * produces a state.
 *
 * Pile conventions, matching Klondike's so a reader moving between the two is not caught out:
 * - [tableau] columns run bottom (index 0) to top (last index).
 * - [stock] is ordered next-to-deal first, and is consumed ten at a time.
 * - [banked] counts completed sequences per suit rather than holding their cards: a banked
 *   sequence is always the same thirteen cards and never returns to play, so the cards
 *   themselves carry no information the count does not.
 */
data class SpiderState(
    val seed: Long,
    val versions: GameVersions,
    val suitCount: SuitCount,
    val tableau: List<List<TableauCard>>,
    val stock: List<Card>,
    val banked: Map<Suit, Int>,
    val status: GameStatus,
    val moveCount: Int = 0,
) {
    init {
        require(tableau.size == TABLEAU_COLUMNS) { "tableau must have $TABLEAU_COLUMNS columns" }
        require(banked.keys == Suit.entries.toSet()) { "banked must have an entry for every suit" }
        require(banked.values.all { it >= 0 }) { "a suit cannot have a negative banked count" }
    }

    val sequencesBanked: Int get() = banked.values.sum()

    val isWon: Boolean get() = status == GameStatus.WON

    /** Row deals still to come, which is what the interface shows where Klondike shows a stock pile. */
    val rowDealsRemaining: Int get() = stock.size / TABLEAU_COLUMNS
}
