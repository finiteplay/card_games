package org.finiteplay.klondike.board

import org.finiteplay.cards.Card
import org.finiteplay.cards.Suit
import org.finiteplay.cards.shuffleDeckIndices

/**
 * The dealt board for [seed] under [versions], per the deterministic deal contract:
 * shuffled index 0 first, in tableau rounds left to right, with each column's final
 * card face-up; shuffled indexes 28..51 form the stock with index 28 first. Every game
 * — regardless of the automatic-moves setting — starts here, at zero moves, with no
 * automatic transfer applied yet: automation (when enabled) first runs as part of the
 * player's own first action, the same as every action after it, rather than before the
 * player has done anything (`docs/games/klondike/DESIGN.md` "Automatic Foundation Moves").
 */
fun dealGame(seed: Long, versions: GameVersions, drawMode: DrawMode = DrawMode.ONE): GameState {
    val shuffled = shuffleDeckIndices(seed).map(Card.Companion::fromId)

    val tableau = List(TABLEAU_COLUMNS) { mutableListOf<TableauCard>() }
    var cursor = 0
    for (round in 0 until TABLEAU_COLUMNS) {
        for (column in round until TABLEAU_COLUMNS) {
            val faceUp = round == column
            tableau[column].add(TableauCard(shuffled[cursor], faceUp))
            cursor++
        }
    }

    val stock = shuffled.subList(cursor, shuffled.size).toList()
    check(stock.size == 24) { "expected 24 stock cards, got ${stock.size}" }

    return GameState(
        seed = seed,
        versions = versions,
        tableau = tableau.map { it.toList() },
        foundations = Suit.entries.associateWith { 0 },
        waste = emptyList(),
        stock = stock,
        status = GameStatus.IN_PROGRESS,
        drawMode = drawMode,
    )
}
