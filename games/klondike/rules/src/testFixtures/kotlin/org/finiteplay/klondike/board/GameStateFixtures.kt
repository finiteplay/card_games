package org.finiteplay.klondike.board

import org.finiteplay.cards.Card
import org.finiteplay.cards.Suit

/** Shared test-fixture builders for constructing custom boards without a real deal. */
object GameStateFixtures {

    val TEST_VERSIONS = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1)

    fun emptyBoard(seed: Long = 0L, versions: GameVersions = TEST_VERSIONS): GameState = GameState(
        seed = seed,
        versions = versions,
        tableau = List(TABLEAU_COLUMNS) { emptyList() },
        foundations = Suit.entries.associateWith { 0 },
        waste = emptyList(),
        stock = emptyList(),
        status = GameStatus.IN_PROGRESS,
    )

    fun faceDown(card: Card) = TableauCard(card, faceUp = false)
    fun faceUp(card: Card) = TableauCard(card, faceUp = true)

    fun GameState.withTableauColumn(column: Int, cards: List<TableauCard>): GameState =
        copy(tableau = tableau.toMutableList().apply { this[column] = cards })

    fun GameState.withFoundation(suit: Suit, topRankValue: Int): GameState =
        copy(foundations = foundations + (suit to topRankValue))

    fun GameState.withWaste(cards: List<Card>): GameState = copy(waste = cards)

    fun GameState.withStock(cards: List<Card>): GameState = copy(stock = cards)
}
