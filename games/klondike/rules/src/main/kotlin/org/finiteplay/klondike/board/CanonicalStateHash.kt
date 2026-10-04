package org.finiteplay.klondike.board

import org.finiteplay.cards.Card
import org.finiteplay.cards.Suit

/**
 * A stable 64-bit content hash of [state], for pinning a reference game's exact
 * behavior across engine changes (`EXECUTION_PLAN.md` "RF — Rules Freeze"). Every
 * input is a card ID, ordinal, or plain numeric field — never a JVM identity hash —
 * so the value is reproducible across processes, platforms, and Kotlin/JVM versions,
 * the same guarantee [org.finiteplay.cards.shuffleDeckIndices]'s
 * reference vectors rely on.
 *
 * Column and pile *order* is significant here, unlike the solver's search-only
 * `canonicalHashOf` (`:solver`), which deliberately collapses column-order symmetry
 * to shrink its search space. This hash pins one specific, concrete board — it must
 * distinguish two states that differ only by which column holds which pile, since
 * those are different game states to a player.
 *
 * Never used for gameplay logic, only as a frozen test fixture's expected value: it
 * is not part of the persisted save format (`docs/games/klondike/DESIGN.md` "Persistence and
 * Privacy" — the active game persists as seed + move log, not a state snapshot).
 */
fun canonicalStateHash(state: GameState): Long {
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
        for (tableauCard in column) {
            mixInt(if (tableauCard.faceUp) 1 else 0)
            mixCard(tableauCard.card)
        }
    }

    for (suit in Suit.entries) mixInt(3000 + state.foundations.getValue(suit))

    mixInt(4000 + state.waste.size)
    for (card in state.waste) mixCard(card)

    mixInt(5000 + state.stock.size)
    for (card in state.stock) mixCard(card)

    mixInt(state.status.ordinal)
    mixInt(state.parkedCard?.id ?: -1)
    mixInt(state.moveCount)
    mixInt(state.drawMode.ordinal)

    return h
}
