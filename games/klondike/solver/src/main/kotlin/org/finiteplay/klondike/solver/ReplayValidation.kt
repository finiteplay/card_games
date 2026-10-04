package org.finiteplay.klondike.solver

import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.GameStatus
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.rules.applyMove
import org.finiteplay.klondike.rules.isLegal

/** Thrown by [validateCertificate] when a certificate does not independently replay to a win. */
class CertificateReplayException(message: String) : Exception(message)

/**
 * Replays [certificate] through the canonical [applyMove]/[isLegal] reducer, starting
 * from [start]. This is what actually proves solvability — the solver's own search
 * state (`SNode`) is never trusted as self-certifying (D1s). Never invokes the
 * automatic finish: certificates reach the win purely through ordinary moves, per the
 * frozen engine surface contract.
 *
 * Throws [CertificateReplayException] naming the offending step if any move in the
 * certificate is illegal against the canonical reducer at the point it is applied.
 */
fun replay(start: GameState, certificate: List<Move>): GameState {
    var state = start
    for ((index, move) in certificate.withIndex()) {
        if (!isLegal(state, move)) {
            throw CertificateReplayException("illegal move at step $index ($move) against state with moveCount=${state.moveCount}")
        }
        state = applyMove(state, move)
    }
    return state
}

/**
 * Confirms [certificate] reaches [GameStatus.WON] when replayed from [start]. Throws
 * [CertificateReplayException] on any illegal step or a non-winning final state.
 */
fun validateCertificate(start: GameState, certificate: List<Move>) {
    val end = replay(start, certificate)
    if (end.status != GameStatus.WON) {
        throw CertificateReplayException("certificate replayed fully but ended in ${end.status}, not WON")
    }
}

/**
 * Confirms only the first move of [certificate] is legal against [start] — much
 * cheaper than [validateCertificate], and enough to guarantee a single suggested move
 * is safe to present as a hint (`docs/games/klondike/DESIGN.md` "On-Device Hint Search"). Does not
 * prove the rest of the certificate stays legal; the hint engine re-solves from
 * scratch whenever the board diverges from a previously found certificate, so a later
 * step is always re-validated the same way before it is ever shown.
 */
fun firstMoveIsLegal(start: GameState, certificate: List<Move>): Boolean {
    val first = certificate.firstOrNull() ?: return false
    return isLegal(start, first)
}
