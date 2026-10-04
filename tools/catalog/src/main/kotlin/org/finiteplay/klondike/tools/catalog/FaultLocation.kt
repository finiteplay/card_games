package org.finiteplay.klondike.tools.catalog

import java.util.Base64
import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.GameVersions
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.rules.applyMove
import org.finiteplay.klondike.rules.legalMoves
import org.finiteplay.klondike.session.LogEntry
import org.finiteplay.klondike.session.LogEntryCodec
import org.finiteplay.klondike.session.replaySession
import org.finiteplay.klondike.solver.search.LongHashSet
import org.finiteplay.klondike.solver.search.SolveOutcome
import org.finiteplay.klondike.solver.search.SolverLimits
import org.finiteplay.klondike.solver.search.solveOnLargeStackWithCache

/**
 * Finds the **last position in a played log from which the game was still winnable**, and
 * then asks what the player was actually facing there — how many of the legal moves kept
 * the win alive, and how many threw it away.
 *
 * This is the difference between "the board is lost" (which `replay ... solve` already
 * answers) and "here is the move that lost it", which is the only version of the question
 * a player can learn anything from.
 *
 * Scans **backward** from the end of the log. Once a position is lost, playing on cannot
 * recover it, so the transition is a single boundary — but the log may contain undos,
 * which do restore earlier positions, so this deliberately does not assume monotonicity
 * and binary-search it; it walks back one entry at a time until a winnable position turns
 * up. Late positions solve in milliseconds, so the walk is cheap when the mistake is late
 * and only gets expensive when it is early.
 */
private val FAULT_LIMITS = SolverLimits(maxNodes = 3_000_000L, maxDurationMs = 45_000L)

private enum class Winnable { YES, NO, UNKNOWN }

fun reportFaultLocation(
    seed: Long,
    versions: GameVersions,
    drawMode: DrawMode,
    initialAutomaticMovesEnabled: Boolean,
    encodedLog: String,
    log: (String) -> Unit = ::println,
) {
    val entries = LogEntryCodec.decode(Base64.getDecoder().decode(encodedLog))
    fun stateAfter(count: Int): GameState =
        replaySession(seed, versions, initialAutomaticMovesEnabled, entries.take(count), drawMode).state

    log("scanning ${entries.size} log entries backward for the last winnable position...")
    var lastWinnable = -1
    for (count in entries.size downTo 0) {
        when (winnable(stateAfter(count))) {
            Winnable.YES -> { lastWinnable = count; break }
            Winnable.UNKNOWN -> { log("entry $count: UNKNOWN (budget spent) — stopping, cannot attribute a fault past this"); return }
            Winnable.NO -> log("entry $count: lost")
        }
    }

    if (lastWinnable < 0) {
        log("no winnable position anywhere in the log — the deal was already lost at deal time, which would contradict its catalog entry")
        return
    }
    if (lastWinnable == entries.size) {
        log("the current position is still winnable; nothing was thrown away")
        return
    }

    val fatalEntry = entries[lastWinnable]
    log("")
    log("=== last winnable position: after entry $lastWinnable of ${entries.size} ===")
    log("the entry that lost it: #${lastWinnable + 1} = ${describeEntry(fatalEntry)}")
    log("")
    log(renderBoard(stateAfter(lastWinnable)))

    val state = stateAfter(lastWinnable)
    log("=== what was on offer at that position ===")
    val legal = legalMoves(state)
    val verdicts = legal.map { it to winnable(applyMove(state, it)) }
    val winning = verdicts.filter { it.second == Winnable.YES }
    val losing = verdicts.filter { it.second == Winnable.NO }
    val unknown = verdicts.filter { it.second == Winnable.UNKNOWN }
    log("${legal.size} legal moves: ${winning.size} keep the win, ${losing.size} throw it away, ${unknown.size} unresolved")
    for ((move, verdict) in verdicts) log("  ${describeEntry(LogEntry.PlayerMove(move))} -> $verdict")
    log("")
    log(
        when {
            winning.size == 1 -> "CRITICAL CHOICE: exactly one of ${legal.size} legal moves wins. Nothing on the board marks it out."
            winning.size > 1 -> "NOT a single forced move: ${winning.size} of ${legal.size} moves still win, and the one played was not among them."
            else -> "no winning move found here within budget"
        },
    )
}

private fun winnable(state: GameState): Winnable = when (
    solveOnLargeStackWithCache(state, FAULT_LIMITS, LongHashSet(), includeFoundationWithdrawal = true)
) {
    is SolveOutcome.Solved -> Winnable.YES
    is SolveOutcome.Unsolved -> Winnable.NO
    else -> Winnable.UNKNOWN
}

private fun describeEntry(entry: LogEntry): String = when (entry) {
    LogEntry.Undo -> "undo"
    LogEntry.AutoFinish -> "autofinish"
    is LogEntry.SetAutomaticMoves -> if (entry.enabled) "auto=on" else "auto=off"
    is LogEntry.PlayerMove -> when (val m = entry.move) {
        Move.Draw -> "draw"
        Move.Recycle -> "recycle"
        Move.WasteToFoundation -> "waste->foundation"
        is Move.TableauToTableau -> "T${m.fromColumn}[${m.fromIndex}] -> T${m.toColumn}"
        is Move.TableauToFoundation -> "T${m.fromColumn} -> foundation"
        is Move.WasteToTableau -> "waste -> T${m.toColumn}"
        is Move.FoundationToTableau -> "foundation ${m.suit.symbol} -> T${m.toColumn}"
    }
}
