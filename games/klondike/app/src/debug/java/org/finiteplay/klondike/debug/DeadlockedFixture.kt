package org.finiteplay.klondike.debug

import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.GameStatus
import org.finiteplay.klondike.board.GameVersions
import org.finiteplay.klondike.board.TableauCard
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit

/**
 * A hand-built genuinely unsolvable board, mirroring the `:solver` module's own
 * `HintEngineTest` fixture of the same shape: every tableau column holds one
 * face-up card whose rank has no neighbor two ranks away among the others, so no
 * tableau-to-tableau move is ever legal; none is foundation-eligible (foundations
 * start empty and no card here is an Ace); no column is empty, so not even a King
 * could move. The only legal actions are stock draw/recycle, which cycle through
 * finitely many waste/stock arrangements without ever changing the tableau — small
 * enough that the on-device search proves [org.finiteplay.klondike.solver.HintOutcome.NoSolution]
 * near-instantly, for tests that need that outcome without paying for a full search.
 */
fun deadlockedGameState(): GameState {
    val tableau = listOf(
        listOf(TableauCard(Card(Suit.CLUBS, Rank.THREE), faceUp = true)),
        listOf(TableauCard(Card(Suit.DIAMONDS, Rank.FIVE), faceUp = true)),
        listOf(TableauCard(Card(Suit.CLUBS, Rank.SEVEN), faceUp = true)),
        listOf(TableauCard(Card(Suit.DIAMONDS, Rank.NINE), faceUp = true)),
        listOf(TableauCard(Card(Suit.CLUBS, Rank.JACK), faceUp = true)),
        listOf(TableauCard(Card(Suit.DIAMONDS, Rank.KING), faceUp = true)),
        listOf(TableauCard(Card(Suit.HEARTS, Rank.THREE), faceUp = true)),
    )
    return GameState(
        seed = 0L,
        versions = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1),
        tableau = tableau,
        foundations = Suit.entries.associateWith { 0 },
        waste = emptyList(),
        stock = listOf(Card(Suit.SPADES, Rank.TWO), Card(Suit.SPADES, Rank.FOUR)),
        status = GameStatus.IN_PROGRESS,
    )
}
