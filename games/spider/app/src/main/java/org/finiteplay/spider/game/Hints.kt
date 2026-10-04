package org.finiteplay.spider.game

import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.TABLEAU_COLUMNS

/**
 * The top card of every movable sub-stack that has somewhere legal to go.
 *
 * A column's liftable tail can be picked up from any point along it, and each of those points is a
 * different move: taking `9-8-7` onto a ten and taking just `7` onto an eight go to different
 * places and are worth knowing about separately. So each lift point that has a destination is
 * marked, at the card that would be grabbed to make it — the sub-stack's top card.
 *
 * What is deliberately *not* marked is every card of one sub-stack. Lighting all of `9-8-7`
 * because the run can move says only "this run moves" three times over; lighting `9` says it once,
 * and lighting `7` as well means something different — that the single card has its own home.
 *
 * Destinations themselves are not marked either. A run with two legal columns has no single
 * answer, and tapping already walks through them ([resolveTap]).
 */
fun hintedCards(state: SpiderState): Set<HintedCard> {
    if (state.isWon) return emptySet()
    val hinted = LinkedHashSet<HintedCard>()
    for (column in 0 until TABLEAU_COLUMNS) {
        for (index in state.tableau[column].indices) {
            if (tapDestinations(state, column, index).isNotEmpty()) {
                hinted += HintedCard(column, index)
            }
        }
    }
    return hinted
}

/** The card at [index] of [column], as a position rather than a card: two decks hold duplicates. */
data class HintedCard(val column: Int, val index: Int)
