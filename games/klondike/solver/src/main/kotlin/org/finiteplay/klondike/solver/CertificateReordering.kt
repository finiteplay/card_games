package org.finiteplay.klondike.solver

import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.rules.applyMove
import org.finiteplay.klondike.rules.isLegal

/**
 * How early a human following the line one move at a time would expect [move] to
 * come: progress on the tableau first, then playing an available card down from the
 * waste (or a foundation withdrawal), and rotating the stock only when the line
 * genuinely needs the next card. Lower is earlier.
 */
private fun eagerness(move: Move): Int = when (move) {
    is Move.TableauToTableau, is Move.TableauToFoundation -> 0
    is Move.WasteToTableau, is Move.WasteToFoundation, is Move.FoundationToTableau -> 1
    is Move.Draw, is Move.Recycle -> 2
}

/**
 * Reorders a proven winning line into the order a human following it move by move
 * would expect (`docs/games/klondike/DESIGN.md` "On-Device Hint Search"), without changing what the
 * line *is*: only adjacent moves that provably commute are ever swapped, checked
 * semantically — both orders legal and producing the identical board through the
 * canonical reducer — never assumed from move types. A draw-one waste play, for
 * example, can never cross the draw that exposes its card, because playing earlier
 * would play a different card and the states diverge; an unrelated tableau move
 * crosses any number of draws freely. The reordered line therefore has exactly the
 * same moves, length, and final board as the found one — this is presentation for
 * the player who follows the hint step by step, not search.
 *
 * A bubble pass over [eagerness] classes, repeated to fixpoint: each executed swap
 * strictly reduces the number of class inversions, so termination is bounded by the
 * (small) inversion count of a real line. Runs once per solve, before the line is
 * cached; the search itself is untouched, so search performance and the fitted
 * portfolio budget (`HintSearchBudgetTest`) cannot regress from this.
 */
fun reorderCertificateForFollowing(start: GameState, certificate: List<Move>): List<Move> {
    if (certificate.size < 2) return certificate
    val moves = certificate.toMutableList()
    var swappedAny = true
    while (swappedAny) {
        swappedAny = false
        var state = start
        for (i in 0 until moves.size - 1) {
            val first = moves[i]
            val second = moves[i + 1]
            if (eagerness(second) < eagerness(first) && commutes(state, first, second)) {
                moves[i] = second
                moves[i + 1] = first
                swappedAny = true
            }
            state = applyMove(state, moves[i])
        }
    }
    return moves
}

/** True when swapping the adjacent pair keeps both moves legal and lands on the identical board. */
private fun commutes(state: GameState, first: Move, second: Move): Boolean {
    if (!isLegal(state, second)) return false
    val afterSecond = applyMove(state, second)
    if (!isLegal(afterSecond, first)) return false
    return applyMove(afterSecond, first) == applyMove(applyMove(state, first), second)
}
