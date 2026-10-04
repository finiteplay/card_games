package org.finiteplay.klondike.storage

import java.io.File
import kotlinx.coroutines.test.runTest
import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.session.LogEntry
import org.finiteplay.klondike.session.replaySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GameArchiveStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun archived(
        gameId: String,
        outcome: Outcome = Outcome.WIN,
        drawMode: DrawMode = DrawMode.ONE,
        countedForStatistics: Boolean = true,
        moves: List<LogEntry> = listOf(LogEntry.PlayerMove(Move.Draw)),
    ) = ArchivedGame(
        gameId = gameId,
        seed = 42L,
        versions = TEST_VERSIONS,
        drawMode = drawMode,
        initialAutomaticMovesEnabled = true,
        outcome = outcome,
        elapsedMillis = 12_345L,
        moveCount = 87,
        timestampMillis = 1_700_000_000_000L,
        countedForStatistics = countedForStatistics,
        moves = moves,
    )

    private suspend fun recordGames(directory: File, count: Int, from: Int = 0, moves: List<LogEntry>? = null) {
        val store = gameArchiveStore(directory)
        (from until from + count).forEach { i ->
            store.record(moves?.let { archived("game-$i", moves = it) } ?: archived("game-$i"))
        }
    }

    @Test
    fun `a fresh directory has no archived games`() = runTest {
        assertEquals(emptyList<ArchivedGame>(), gameArchiveStore(tempFolder.newFolder()).all())
    }

    @Test
    fun `an archived game survives a simulated restart`() = runTest {
        val dir = tempFolder.newFolder()
        gameArchiveStore(dir).record(archived("g1"))

        assertEquals(listOf(archived("g1")), gameArchiveStore(dir).all())
    }

    @Test
    fun `recording the same gameId twice does not consume two slots`() = runTest {
        val store = gameArchiveStore(tempFolder.newFolder())
        store.record(archived("g1"))
        store.record(archived("g1"))
        store.record(archived("g1"))

        assertEquals(1, store.all().size)
    }

    @Test
    fun `recording the same gameId again replaces its content`() = runTest {
        val store = gameArchiveStore(tempFolder.newFolder())
        store.record(archived("g1", outcome = Outcome.LOSS))
        store.record(archived("g1", outcome = Outcome.WIN))

        assertEquals(Outcome.WIN, store.all().single().outcome)
    }

    @Test
    fun `the archive never holds more than 100 games`() = runTest {
        val dir = tempFolder.newFolder()
        recordGames(dir, count = 250)

        assertEquals(GameArchiveStore.MAX_ARCHIVED_GAMES, gameArchiveStore(dir).all().size)
    }

    @Test
    fun `the oldest game is evicted first, and the newest 100 are kept in order`() = runTest {
        val dir = tempFolder.newFolder()
        recordGames(dir, count = 120)

        val kept = gameArchiveStore(dir).all()

        assertEquals((20 until 120).map { "game-$it" }, kept.map { it.gameId })
    }

    @Test
    fun `eviction still applies across a simulated restart`() = runTest {
        val dir = tempFolder.newFolder()
        recordGames(dir, count = 60)
        recordGames(dir, count = 60, from = 60)

        val kept = gameArchiveStore(dir).all()

        assertEquals(GameArchiveStore.MAX_ARCHIVED_GAMES, kept.size)
        assertEquals("game-20", kept.first().gameId)
        assertEquals("game-119", kept.last().gameId)
    }

    /**
     * The cap counts games, never moves. A single long game keeps every entry it recorded,
     * however far past 100 that goes.
     */
    @Test
    fun `a game with far more than 100 moves keeps all of them`() = runTest {
        val dir = tempFolder.newFolder()
        val longLog = List(437) { LogEntry.PlayerMove(Move.Draw) }
        gameArchiveStore(dir).record(archived("g1", moves = longLog))

        val stored = gameArchiveStore(dir).all().single()

        assertEquals(437, stored.moves.size)
        assertEquals(longLog, stored.moves)
    }

    /** The two limits must not interact: a full archive of long games keeps 100 games *and* every move of each. */
    @Test
    fun `a full archive of long games truncates neither the games nor their moves`() = runTest {
        val dir = tempFolder.newFolder()
        val longLog = List(300) { LogEntry.PlayerMove(Move.Draw) }
        recordGames(dir, count = 120, moves = longLog)

        val kept = gameArchiveStore(dir).all()

        assertEquals(GameArchiveStore.MAX_ARCHIVED_GAMES, kept.size)
        assertTrue(kept.all { it.moves.size == 300 })
    }

    @Test
    fun `a garbage archive file reads back as empty instead of throwing`() = runTest {
        val dir = tempFolder.newFolder()
        FakeDataStores.corrupt(dir, GameArchiveStore.STORE_NAME)

        assertEquals(emptyList<ArchivedGame>(), gameArchiveStore(dir).all())
    }

    @Test
    fun `clear discards every archived game`() = runTest {
        val dir = tempFolder.newFolder()
        recordGames(dir, count = 5)

        gameArchiveStore(dir).clear()

        assertEquals(emptyList<ArchivedGame>(), gameArchiveStore(dir).all())
    }

    @Test
    fun `a replayed game is archived and flagged as not counting for statistics`() = runTest {
        val dir = tempFolder.newFolder()
        gameArchiveStore(dir).record(archived("g1", countedForStatistics = false))

        assertEquals(false, gameArchiveStore(dir).all().single().countedForStatistics)
    }

    @Test
    fun `draw mode round-trips`() = runTest {
        val dir = tempFolder.newFolder()
        gameArchiveStore(dir).record(archived("g1", drawMode = DrawMode.THREE))

        assertEquals(DrawMode.THREE, gameArchiveStore(dir).all().single().drawMode)
    }

    /**
     * The point of the archive: what comes back has to be enough to rebuild the game, not
     * merely to describe it. Replays the stored log through the same reducer the live game
     * uses and checks the board it produces is the one that was actually played.
     */
    @Test
    fun `an archived game replays to the identical board`() = runTest {
        val dir = tempFolder.newFolder()
        val session = playRealisticSession(seed = 7L)
        gameArchiveStore(dir).record(
            archived("g1", moves = session.log).copy(seed = session.state.seed, moveCount = session.state.moveCount),
        )

        val stored = gameArchiveStore(dir).all().single()
        val replayed = replaySession(
            stored.seed,
            stored.versions,
            stored.initialAutomaticMovesEnabled,
            stored.moves,
            stored.drawMode,
        )

        assertTrue(stored.moves.isNotEmpty())
        assertEquals(session.state, replayed.state)
        assertEquals(session.log, stored.moves)
    }

    @Test
    fun `two different games' logs stay distinct through encoding`() = runTest {
        val dir = tempFolder.newFolder()
        val store = gameArchiveStore(dir)
        val first = playRealisticSession(seed = 7L)
        val second = playRealisticSession(seed = 11L)
        store.record(archived("g1", moves = first.log))
        store.record(archived("g2", moves = second.log))

        val all = gameArchiveStore(dir).all()

        assertNotEquals(first.log, second.log)
        assertEquals(first.log, all.first { it.gameId == "g1" }.moves)
        assertEquals(second.log, all.first { it.gameId == "g2" }.moves)
    }
}
