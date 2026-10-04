package org.finiteplay.freecell.solver

import org.finiteplay.cards.Card
import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.cards.Suit

private const val FNV_OFFSET_BASIS = -3750763034362895579L
private const val FNV_PRIME = 1099511628211L

/** Order-preserving hash of one tableau column's card sequence. */
private fun columnSignature(column: List<Card>): Long {
    var h = FNV_OFFSET_BASIS
    h = (h xor (1000L + column.size)) * FNV_PRIME
    for (card in column) h = (h xor card.id.toLong()) * FNV_PRIME
    return h
}

/**
 * Search-only fingerprint of [state], invariant under which physical tableau column or free
 * cell holds which pile or card — the same symmetry-collapsing role Klondike's own
 * `canonicalStateHashOf` plays for its solver (`:games:klondike:solver`'s `SearchState.kt`).
 *
 * Sound because no rule or move generator in `:games:freecell:rules` ever depends on a column's
 * or a free cell's own index, only on its content (`LegalMoves.kt` treats every column and every
 * free cell uniformly) — permuting either can never change what is reachable from a state, so two
 * permutations of the same columns and free cells are, for search purposes, the same state.
 * FreeCell's own board spends a meaningful share of its nominal branching on exactly this
 * symmetry (which of several interchangeable empty columns or free cells a card lands in), so
 * collapsing it is where most of the practical node-count reduction comes from.
 *
 * Never used for gameplay logic or to certify a win — only replaying the certificate through the
 * real reducer does that (`docs/games/freecell/DEALS.md` "Generation").
 */
fun canonicalSearchHash(state: FreeCellState): Long {
    val columnSignatures = state.tableau.map(::columnSignature).sorted()
    val freeCellIds = state.freeCells.map { it?.id ?: -1 }.sorted()

    var h = FNV_OFFSET_BASIS
    for (signature in columnSignatures) h = (h xor signature) * FNV_PRIME
    for (id in freeCellIds) h = (h xor (2000L + id)) * FNV_PRIME
    for (suit in Suit.entries) h = (h xor (3000L + state.foundations.getValue(suit))) * FNV_PRIME

    return h
}

/**
 * Exact fingerprint of [state] — unlike [canonicalSearchHash], sensitive to which physical
 * tableau column or free cell holds which pile or card. [HintEngine]'s certificate cache needs
 * this distinction, not the search's own symmetry-collapsed one: a cached line's moves name
 * specific column and free-cell indices, so matching the live board against it by an identity
 * that treats two different column arrangements as "the same state" would risk replaying a move
 * against a column it was never actually found for.
 */
fun exactStateHash(state: FreeCellState): Long {
    var h = FNV_OFFSET_BASIS
    for (column in state.tableau) h = (h xor columnSignature(column)) * FNV_PRIME
    for (card in state.freeCells) h = (h xor (2000L + (card?.id ?: -1))) * FNV_PRIME
    for (suit in Suit.entries) h = (h xor (3000L + state.foundations.getValue(suit))) * FNV_PRIME
    return h
}
