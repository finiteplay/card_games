package org.finiteplay.freecell.layout

import org.finiteplay.cards.Card
import org.finiteplay.cards.Suit

/**
 * A stable 64-bit content hash of [state], for pinning a reference game's exact behavior across
 * engine changes (`EXECUTION_PLAN.md` "RF — Rules Freeze"). Every input is a card ID, ordinal, or
 * plain numeric field — never a JVM identity hash — so the value is reproducible across
 * processes, platforms, and Kotlin/JVM versions, the same guarantee
 * [org.finiteplay.cards.shuffleDeckIndices]'s reference vectors rely on.
 *
 * Column and free-cell *order* is significant here: it pins one specific, concrete board, and two
 * states that differ only by which column or free cell holds which card are different game states
 * to a player.
 *
 * Never used for gameplay logic, only as a frozen test fixture's expected value: it is not part
 * of the persisted save format (the active game persists as seed + versions + elapsed + move log,
 * not a state snapshot).
 */
fun canonicalStateHash(state: FreeCellState): Long {
    var h = -3750763034362895579L // FNV-1a 64-bit offset basis
    val prime = 1099511628211L

    fun mixLong(v: Long) {
        h = h xor v
        h *= prime
    }
    fun mixInt(v: Int) = mixLong(v.toLong())
    fun mixCard(card: Card) = mixInt(card.id)

    mixLong(state.seed)
    mixInt(state.versions.catalogVersion)
    mixInt(state.versions.rulesVersion)
    mixInt(state.versions.shuffleVersion)

    for (column in state.tableau) {
        mixInt(1000 + column.size)
        for (card in column) mixCard(card)
    }

    for (cell in state.freeCells) mixInt(2000 + (cell?.id ?: -1))

    for (suit in Suit.entries) mixInt(3000 + state.foundations.getValue(suit))

    mixInt(state.status.ordinal)
    mixInt(state.moveCount)

    return h
}
