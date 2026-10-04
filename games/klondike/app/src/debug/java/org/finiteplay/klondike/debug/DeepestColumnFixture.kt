package org.finiteplay.klondike.debug

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.GameStatus
import org.finiteplay.klondike.board.GameVersions
import org.finiteplay.klondike.board.TableauCard

/**
 * The tallest board the rules permit: the last column holding its six dealt face-down cards
 * under a full King-to-Ace sequence, nineteen cards in one column
 * (`MAX_TABLEAU_COLUMN_CARDS`).
 *
 * A layout fixture rather than a game — it is not reachable by play from any deal, and nothing
 * about it is solvable. It exists so the worst case for column height can be *looked at* on
 * every screen size, since that is the shape that overruns a board area or collides with
 * whatever the layout puts below the tableau, and no ordinary deal will produce it on demand.
 */
fun deepestColumnGameState(): GameState {
    // Alternating colours down the run, as the rules require, starting from a black King.
    val run = Rank.entries.sortedByDescending { it.value }.mapIndexed { index, rank ->
        val suit = if (index % 2 == 0) Suit.SPADES else Suit.HEARTS
        TableauCard(Card(suit, rank), faceUp = true)
    }
    val buried = List(MAX_FACE_DOWN) { TableauCard(Card(Suit.CLUBS, Rank.entries[it]), faceUp = false) }

    val tableau = List(TABLEAU_COLUMNS) { column ->
        when (column) {
            TABLEAU_COLUMNS - 1 -> buried + run
            // A short column beside it, so the screenshot shows the deep one against a normal
            // one rather than against empty table.
            0 -> listOf(TableauCard(Card(Suit.DIAMONDS, Rank.KING), faceUp = true))
            else -> emptyList()
        }
    }

    return GameState(
        seed = 0L,
        versions = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1),
        tableau = tableau,
        foundations = Suit.entries.associateWith { 0 },
        waste = listOf(Card(Suit.DIAMONDS, Rank.SEVEN)),
        stock = listOf(Card(Suit.CLUBS, Rank.NINE), Card(Suit.DIAMONDS, Rank.TWO)),
        status = GameStatus.IN_PROGRESS,
    )
}

private const val MAX_FACE_DOWN = 6
private const val TABLEAU_COLUMNS = 7
