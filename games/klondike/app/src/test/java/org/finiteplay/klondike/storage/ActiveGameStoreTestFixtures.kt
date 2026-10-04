package org.finiteplay.klondike.storage

import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.board.GameVersions
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.rules.legalMoves
import org.finiteplay.klondike.session.GameSession
import org.finiteplay.klondike.session.commitMove
import org.finiteplay.klondike.session.undo
import org.finiteplay.klondike.session.withAutomaticMoves
import java.io.File

val TEST_VERSIONS = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1)

// Every store below is constructed against FakeDataStores (see that class for why: a
// confirmed, environment-specific androidx.datastore limitation on at least one Windows
// JVM, not a defect in this module). Each factory here builds a brand-new instance every
// call — never memoized — so "a fresh reader" in these tests always means a genuinely new
// object with no shared in-memory state, reading back only what was durably written to
// FakeDataStores' path-keyed content map, matching what the S1 gate means by "simulate
// restart by creating a fresh reader."
fun activeGameStore(directory: File) = ActiveGameStore(directory, dataStoreFactory = FakeDataStores::create)
fun settingsStore(directory: File) = SettingsStore(directory, dataStoreFactory = FakeDataStores::create)
fun catalogTraversalStore(directory: File) = CatalogTraversalStore(directory, dataStoreFactory = FakeDataStores::create)
fun historyStore(directory: File) = HistoryStore(directory, dataStoreFactory = FakeDataStores::create)
fun gameArchiveStore(directory: File) = GameArchiveStore(directory, dataStoreFactory = FakeDataStores::create)

/**
 * Deals a real game from [seed] and plays it forward through a mix of committed moves,
 * one undo, and an automation toggle, using the real legal-move generator rather than a
 * hand-built board — so persistence tests exercise a realistic, engine-produced log
 * instead of a synthetic one. [initialAutomaticMovesEnabled] must match whatever value a
 * caller then passes to [ActiveGameStore.save]: it seeds the deal itself, so replaying
 * the resulting log against a mismatched initial flag diverges from the recorded moves.
 */
fun playRealisticSession(
    seed: Long,
    versions: GameVersions = TEST_VERSIONS,
    initialAutomaticMovesEnabled: Boolean = true,
    drawMode: DrawMode = DrawMode.ONE,
): GameSession {
    var session = GameSession.start(seed, versions, automaticMovesEnabled = initialAutomaticMovesEnabled, drawMode = drawMode)

    repeat(12) {
        val moves = legalMoves(session.state)
        // Prefer a non-Draw move so the log exercises more than one opcode; fall back to
        // Draw/Recycle when nothing else is legal.
        val move = moves.firstOrNull { it !is Move.Draw } ?: moves.firstOrNull()
        if (move != null) session = session.commitMove(move)
    }

    if (session.canUndo) session = session.undo()

    session = session.withAutomaticMoves(false)
    legalMoves(session.state).firstOrNull()?.let { session = session.commitMove(it) }

    return session
}
