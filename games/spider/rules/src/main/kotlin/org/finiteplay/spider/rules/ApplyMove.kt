package org.finiteplay.spider.rules

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.spider.layout.GameStatus
import org.finiteplay.spider.layout.SEQUENCES_TO_WIN
import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.TABLEAU_COLUMNS
import org.finiteplay.spider.layout.TableauCard

/**
 * One completed King-to-Ace sequence a move just banked.
 *
 * [cards] are in the order the tableau held them — king first, ace last, the order a completed
 * run is always assembled and stored in (`completedSequenceSuit`). A caller animating the bank
 * (`SpiderBoard.kt`) reads them in reverse to send the ace first.
 */
data class BankedRun(val suit: Suit, val column: Int, val cards: List<Card>)

/** [applyMove]'s result, plus every sequence that move banked — usually none, sometimes one. */
data class MoveOutcome(val state: SpiderState, val bankedRuns: List<BankedRun>)

/**
 * The one reducer. Every committed transition goes through here, and it is the only thing that
 * produces a [SpiderState] after the deal.
 *
 * It applies three consequences the player never asks for, in this order: the exposed card of a
 * source column turns face up, a completed King-to-Ace sequence is banked, and the game ends
 * when the eighth is banked. Banking is unconditional — there is no setting for it and no way
 * to decline, because a complete sequence has no further use in the tableau
 * (`docs/games/spider/RULES.md` "Banking").
 */
fun applyMove(state: SpiderState, move: Move): SpiderState = applyMoveDetailed(state, move).state

/**
 * [applyMove], plus which sequence(s) it banked and where — the detail an animation needs and a
 * caller that only wants the resulting board does not, which is why [applyMove] stays the
 * function almost everything calls.
 */
fun applyMoveDetailed(state: SpiderState, move: Move): MoveOutcome {
    require(isLegal(state, move)) { "illegal move: $move" }
    val outcome = when (move) {
        is Move.DealRow -> dealRow(state)
        is Move.TableauToTableau -> transfer(state, move)
    }
    return outcome.copy(state = outcome.state.copy(moveCount = state.moveCount + 1))
}

private fun dealRow(state: SpiderState): MoveOutcome {
    val dealt = state.stock.take(TABLEAU_COLUMNS)
    val tableau = state.tableau.mapIndexed { column, cards -> cards + TableauCard(dealt[column], faceUp = true) }
    // A row deal can complete a sequence in more than one column at once, which is the single
    // place Spider banks more than one thing in a move.
    return bankCompletedSequences(state.copy(tableau = tableau, stock = state.stock.drop(TABLEAU_COLUMNS)))
}

private fun transfer(state: SpiderState, move: Move.TableauToTableau): MoveOutcome {
    val source = state.tableau[move.fromColumn]
    val lifted = source.subList(move.fromIndex, source.size).toList()
    val remaining = source.subList(0, move.fromIndex).toList()

    val tableau = state.tableau.toMutableList()
    // Turning the newly exposed card face up is part of this move, not a move of its own: the
    // invariant is that a face-down card is never the last card of a non-empty column.
    tableau[move.fromColumn] = remaining.turnLastFaceUp()
    tableau[move.toColumn] = state.tableau[move.toColumn] + lifted

    return bankCompletedSequences(state.copy(tableau = tableau))
}

private fun List<TableauCard>.turnLastFaceUp(): List<TableauCard> =
    if (isEmpty() || last().faceUp) this else dropLast(1) + last().copy(faceUp = true)

/**
 * Removes every complete King-to-Ace single-suited sequence now sitting at the top of a column,
 * and ends the game once [SEQUENCES_TO_WIN] have gone.
 */
private fun bankCompletedSequences(state: SpiderState): MoveOutcome {
    var tableau = state.tableau
    val banked = state.banked.toMutableMap()
    val bankedRuns = ArrayList<BankedRun>()

    for (column in 0 until TABLEAU_COLUMNS) {
        val suit = completedSequenceSuit(tableau[column]) ?: continue
        val bankedCards = tableau[column].takeLast(Card.RANKS_PER_SUIT).map { it.card }
        tableau = tableau.toMutableList().also {
            it[column] = it[column].dropLast(Card.RANKS_PER_SUIT).turnLastFaceUp()
        }
        banked[suit] = banked.getValue(suit) + 1
        bankedRuns += BankedRun(suit, column, bankedCards)
    }
    if (bankedRuns.isEmpty()) return MoveOutcome(state, emptyList())

    val total = banked.values.sum()
    val result = state.copy(
        tableau = tableau,
        banked = banked,
        status = if (total == SEQUENCES_TO_WIN) GameStatus.WON else state.status,
    )
    return MoveOutcome(result, bankedRuns)
}

/** The suit of the complete King-to-Ace sequence ending [column], or null if there is not one. */
internal fun completedSequenceSuit(column: List<TableauCard>): org.finiteplay.cards.Suit? {
    if (column.size < Card.RANKS_PER_SUIT) return null
    val tail = column.subList(column.size - Card.RANKS_PER_SUIT, column.size)
    if (tail.any { !it.faceUp }) return null
    if (tail.first().card.rank != Rank.KING || tail.last().card.rank != Rank.ACE) return null

    val suit = tail.first().card.suit
    for (index in 1 until tail.size) {
        val above = tail[index].card
        val below = tail[index - 1].card
        if (above.suit != suit) return null
        if (above.rank.value != below.rank.value - 1) return null
    }
    return suit
}
