package org.finiteplay.freecell.storage

import kotlinx.coroutines.runBlocking
import org.finiteplay.freecell.layout.GameVersions
import org.finiteplay.freecell.rules.Move
import org.finiteplay.freecell.session.FreeCellSession
import org.finiteplay.freecell.session.commitMove
import org.finiteplay.freecell.session.undo
import org.finiteplay.core.storage.FakeDataStores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.time.Duration.Companion.seconds

private val VERSIONS = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

class FreeCellActiveGameStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun storeIn(dir: File) = FreeCellActiveGameStore(dir, dataStoreFactory = FakeDataStores::create)

    @Test
    fun `nothing saved reports Missing, not Recovered`() = runBlocking {
        assertEquals(FreeCellActiveGameLoadResult.Missing, storeIn(folder.newFolder()).load())
    }

    @Test
    fun `a played game restores to the exact same board, free cells, and foundations`() = runBlocking {
        val dir = folder.newFolder()
        var session = FreeCellSession.start(seed = 7L, versions = VERSIONS, automaticMovesEnabled = true)
        val column = session.state.tableau.indexOfFirst { it.isNotEmpty() }
        session = session.commitMove(Move.TableauToFreeCell(column, 0))

        storeIn(dir).save(session, initialAutomaticMovesEnabled = true, elapsed = 93.seconds, gameId = "game-1")
        val loaded = storeIn(dir).load()

        assertTrue("expected a restore, got $loaded", loaded is FreeCellActiveGameLoadResult.Restored)
        loaded as FreeCellActiveGameLoadResult.Restored
        assertEquals(session.state.tableau, loaded.session.state.tableau)
        assertEquals(session.state.freeCells, loaded.session.state.freeCells)
        assertEquals(session.state.foundations, loaded.session.state.foundations)
        assertEquals(session.state.moveCount, loaded.session.state.moveCount)
        assertEquals(93L, loaded.elapsed.inWholeSeconds)
        assertEquals("game-1", loaded.gameId)
    }

    @Test
    fun `the undo stack survives, so undo still works after a restart`() = runBlocking {
        val dir = folder.newFolder()
        var session = FreeCellSession.start(seed = 3L, versions = VERSIONS, automaticMovesEnabled = true)
        val dealt = session.state
        val column = session.state.tableau.indexOfFirst { it.isNotEmpty() }
        session = session.commitMove(Move.TableauToFreeCell(column, 0))

        storeIn(dir).save(session, initialAutomaticMovesEnabled = true, elapsed = 0.seconds, gameId = "g")
        val restored = (storeIn(dir).load() as FreeCellActiveGameLoadResult.Restored).session

        assertTrue("a restored game with a move behind it must be undoable", restored.canUndo)
        assertEquals(dealt.tableau, restored.undo().state.tableau)
    }

    @Test
    fun `an undo already in the log replays as an undo, preserving the counted moves`() = runBlocking {
        val dir = folder.newFolder()
        var session = FreeCellSession.start(seed = 11L, versions = VERSIONS, automaticMovesEnabled = true)
        val column = session.state.tableau.indexOfFirst { it.isNotEmpty() }
        session = session.commitMove(Move.TableauToFreeCell(column, 0)).undo()

        storeIn(dir).save(session, initialAutomaticMovesEnabled = true, elapsed = 0.seconds, gameId = "g")
        val restored = (storeIn(dir).load() as FreeCellActiveGameLoadResult.Restored).session

        assertEquals(session.state.tableau, restored.state.tableau)
        // Undo keeps the moves already counted and adds one (`docs/PLATFORM.md` "Persistence"),
        // so this is the number that must survive, not the pre-undo one.
        assertEquals(session.state.moveCount, restored.state.moveCount)
    }

    @Test
    fun `a corrupt log is discarded rather than partially replayed`() = runBlocking {
        val dir = folder.newFolder()
        val session = FreeCellSession.start(seed = 1L, versions = VERSIONS, automaticMovesEnabled = true)
        storeIn(dir).save(session, initialAutomaticMovesEnabled = true, elapsed = 0.seconds, gameId = "g")

        // A valid Base64 blob whose bytes are not a valid log: this has to fail at decode, not
        // yield whatever prefix happened to parse.
        FakeDataStores.setRawStringPreference(dir, STORE_NAME, "log", "//8=")

        assertEquals(FreeCellActiveGameLoadResult.Recovered, storeIn(dir).load())
    }

    @Test
    fun `a save from a newer format version is discarded, not guessed at`() = runBlocking {
        val dir = folder.newFolder()
        val session = FreeCellSession.start(seed = 1L, versions = VERSIONS, automaticMovesEnabled = true)
        storeIn(dir).save(session, initialAutomaticMovesEnabled = true, elapsed = 0.seconds, gameId = "g")

        FakeDataStores.setRawStringPreference(dir, STORE_NAME, "format_version", "not-an-int")

        assertEquals(FreeCellActiveGameLoadResult.Recovered, storeIn(dir).load())
    }

    @Test
    fun `a restart with automatic moves off at deal time never bags a safe ace during replay`() = runBlocking {
        // If restore ignored the saved initial value and assumed automation was on, replaying
        // this log would auto-bank the ace the moment it is exposed — a different game from the
        // one that was actually played and saved.
        val dir = folder.newFolder()
        val column = FreeCellSession.start(seed = 1L, versions = VERSIONS, automaticMovesEnabled = false)
            .state.tableau.indexOfFirst { it.size >= 2 }
        var session = FreeCellSession.start(seed = 1L, versions = VERSIONS, automaticMovesEnabled = false)
        session = session.commitMove(Move.TableauToFreeCell(column, 0))

        storeIn(dir).save(session, initialAutomaticMovesEnabled = false, elapsed = 0.seconds, gameId = "g")
        val restored = (storeIn(dir).load() as FreeCellActiveGameLoadResult.Restored).session

        assertEquals(session.state, restored.state)
        assertEquals(false, restored.automaticMovesEnabled)
    }

    @Test
    fun `clearing leaves no save behind`() = runBlocking {
        val dir = folder.newFolder()
        val session = FreeCellSession.start(seed = 1L, versions = VERSIONS, automaticMovesEnabled = true)
        val store = storeIn(dir)
        store.save(session, initialAutomaticMovesEnabled = true, elapsed = 0.seconds, gameId = "g")
        store.clear()

        assertEquals(FreeCellActiveGameLoadResult.Missing, storeIn(dir).load())
    }

    private companion object {
        const val STORE_NAME = "active_game"
    }
}
