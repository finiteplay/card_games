package org.finiteplay.klondike.debug

import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.GameStatus
import org.finiteplay.klondike.board.GameVersions
import org.finiteplay.klondike.board.TableauCard
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit

/**
 * A hand-built near-win board for the `ACCEPTANCE.md` manual smoke test: every suit but
 * Hearts is fully banked, and Hearts is one card (Jack) short of the same, with the
 * Queen face-down under the King in the one occupied tableau column. Moving the King to
 * an empty column exposes the Queen — "the last face-down card" — and the automatic
 * finish should immediately sweep both to the foundation for a single recorded win.
 * `buildConfig` is disabled, so this lives in `src/debug` rather than behind a flag.
 */
fun nearWinGameState(): GameState {
    val foundations = mapOf(
        Suit.CLUBS to Rank.KING.value,
        Suit.DIAMONDS to Rank.KING.value,
        Suit.SPADES to Rank.KING.value,
        Suit.HEARTS to Rank.JACK.value,
    )
    val tableau = listOf(
        listOf(
            TableauCard(Card(Suit.HEARTS, Rank.QUEEN), faceUp = false),
            TableauCard(Card(Suit.HEARTS, Rank.KING), faceUp = true),
        ),
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
    )
    return GameState(
        seed = 0L,
        versions = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1),
        tableau = tableau,
        foundations = foundations,
        waste = emptyList(),
        stock = emptyList(),
        status = GameStatus.IN_PROGRESS,
    )
}
