package org.finiteplay.klondike.tools.catalog

import java.util.Base64
import org.finiteplay.cards.Rank
import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.GameVersions
import org.finiteplay.klondike.rules.isStuck
import org.finiteplay.klondike.session.LogEntry
import org.finiteplay.klondike.session.LogEntryCodec
import org.finiteplay.klondike.session.replaySession
import org.finiteplay.klondike.solver.search.LongHashSet
import org.finiteplay.klondike.solver.search.SolveOutcome
import org.finiteplay.klondike.solver.search.SolverLimits
import org.finiteplay.klondike.solver.search.solveOnLargeStackWithCache

/**
 * Renders a save pulled off a device — the active game, or one entry from the game
 * archive — back into a readable board and the move list that produced it.
 *
 * Replays through `replaySession`, the same reducer the live game uses, so what this
 * prints is the board the player is actually looking at rather than a reconstruction that
 * might drift from it. Undo entries in the log are applied as undos, exactly as on device.
 */
fun reportSave(
    seed: Long,
    versions: GameVersions,
    drawMode: DrawMode,
    initialAutomaticMovesEnabled: Boolean,
    encodedLog: String,
    solve: Boolean = false,
    log: (String) -> Unit = ::println,
) {
    val entries = LogEntryCodec.decode(Base64.getDecoder().decode(encodedLog))
    val session = replaySession(seed, versions, initialAutomaticMovesEnabled, entries, drawMode)
    val state = session.state

    log("seed=$seed drawMode=$drawMode automaticMoves=$initialAutomaticMovesEnabled")
    log("logEntries=${entries.size} moveCount=${state.moveCount} won=${state.isWon} stuck=${isStuck(state)}")
    log("")
    log(renderBoard(state))
    log("")
    log("=== moves, in order ===")
    log(entries.mapIndexed { i, e -> "${i + 1}.${describe(e)}" }.joinToString(" "))
    if (solve) {
        log("")
        reportWinnability(state, log)
    }
}

/**
 * Whether the position is still winnable, searched over the **full** move set —
 * foundation withdrawal included. That inclusion is what makes a negative answer mean
 * anything: `SolveOutcome.Unsolved` is an exhaustion proof only over the move space
 * actually searched, so a withdrawal-free exhaustion would prove merely "not winnable
 * without withdrawing" (the narrower claim `DifficultyDiagnostics` deliberately settles
 * for, because it runs hundreds of probes per deal rather than one).
 *
 * A timeout proves nothing either way and is reported as such rather than rounded to a no.
 */
private fun reportWinnability(state: GameState, log: (String) -> Unit) {
    log("=== winnability (full move set, incl. foundation withdrawal) ===")
    val started = System.nanoTime()
    val outcome = solveOnLargeStackWithCache(
        state,
        SolverLimits(maxNodes = 8_000_000L, maxDurationMs = 180_000L),
        LongHashSet(),
        includeFoundationWithdrawal = true,
    )
    val elapsedMs = (System.nanoTime() - started) / 1_000_000L
    when (outcome) {
        is SolveOutcome.Solved -> {
            log("WINNABLE — a winning line exists (${outcome.certificate.size} moves, ${outcome.nodes} nodes, ${elapsedMs}ms)")
            log("line: " + outcome.certificate.joinToString(" ") { describe(LogEntry.PlayerMove(it)) })
        }
        is SolveOutcome.Unsolved ->
            log("LOST — the reachable space is exhausted with no win (${outcome.nodes} nodes, ${elapsedMs}ms). This is a proof, not a guess.")
        is SolveOutcome.Timeout ->
            log("UNKNOWN — budget spent before proving either way (${outcome.nodes} nodes, ${elapsedMs}ms). Not evidence of a loss.")
        is SolveOutcome.Error -> log("ERROR — ${outcome.message}")
    }
}

/** A plain-text board: foundations, stock/waste, then one line per tableau column. */
fun renderBoard(state: GameState): String = buildString {
    val foundations = state.foundations.entries
        .sortedBy { it.key.ordinal }
        .joinToString(" ") { (suit, rank) -> "${suit.symbol}${rankLabel(rank)}" }
    appendLine("foundations: $foundations")
    appendLine("stock: ${state.stock.size}  waste: ${state.waste.size}${state.waste.firstOrNull()?.let { " (top ${card(it)})" } ?: ""}")
    state.tableau.forEachIndexed { index, column ->
        val cards = column.joinToString(" ") { if (it.faceUp) card(it.card) else "##" }
        appendLine("T$index [${column.count { !it.faceUp }} down]: $cards")
    }
}

private fun card(c: org.finiteplay.cards.Card) = "${rankLabel(c.rank.value)}${c.suit.symbol}"

private fun rankLabel(value: Int): String = when (value) {
    0 -> "-"
    Rank.ACE.value -> "A"
    Rank.JACK.value -> "J"
    Rank.QUEEN.value -> "Q"
    Rank.KING.value -> "K"
    else -> value.toString()
}

private fun describe(entry: LogEntry): String = when (entry) {
    LogEntry.Undo -> "undo"
    LogEntry.AutoFinish -> "autofinish"
    is LogEntry.SetAutomaticMoves -> if (entry.enabled) "auto=on" else "auto=off"
    is LogEntry.PlayerMove -> when (val m = entry.move) {
        org.finiteplay.klondike.rules.Move.Draw -> "draw"
        org.finiteplay.klondike.rules.Move.Recycle -> "recycle"
        org.finiteplay.klondike.rules.Move.WasteToFoundation -> "W>F"
        is org.finiteplay.klondike.rules.Move.TableauToTableau -> "T${m.fromColumn}.${m.fromIndex}>T${m.toColumn}"
        is org.finiteplay.klondike.rules.Move.TableauToFoundation -> "T${m.fromColumn}>F"
        is org.finiteplay.klondike.rules.Move.WasteToTableau -> "W>T${m.toColumn}"
        is org.finiteplay.klondike.rules.Move.FoundationToTableau -> "F${m.suit.symbol}>T${m.toColumn}"
    }
}
