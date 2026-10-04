package org.finiteplay.spider.storage

import kotlinx.coroutines.runBlocking
import org.finiteplay.core.storage.FakeDataStores
import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.rules.Move
import org.finiteplay.spider.rules.canDealRow
import org.finiteplay.spider.session.SpiderSession
import org.finiteplay.spider.session.commitMove
import org.finiteplay.spider.session.undo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.time.Duration.Companion.seconds

private val VERSIONS = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

class SpiderActiveGameStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun storeIn(dir: File) = SpiderActiveGameStore(dir, dataStoreFactory = FakeDataStores::create)

    @Test
    fun `nothing saved reports Missing, not Recovered`() = runBlocking {
        val result = storeIn(folder.newFolder()).load()
        assertEquals(SpiderActiveGameLoadResult.Missing, result)
    }

    @Test
    fun `a played game restores to the exact same board`() = runBlocking {
        val dir = folder.newFolder()
        var session = SpiderSession.start(seed = 7L, versions = VERSIONS, suitCount = SuitCount.TWO)
        if (canDealRow(session.state)) session = session.commitMove(Move.DealRow)

        storeIn(dir).save(session, elapsed = 93.seconds, gameId = "game-1", dealNumber = 1)
        val loaded = storeIn(dir).load()

        assertTrue("expected a restore, got $loaded", loaded is SpiderActiveGameLoadResult.Restored)
        loaded as SpiderActiveGameLoadResult.Restored
        assertEquals(session.state.tableau, loaded.session.state.tableau)
        assertEquals(session.state.stock, loaded.session.state.stock)
        assertEquals(session.state.banked, loaded.session.state.banked)
        assertEquals(session.state.moveCount, loaded.session.state.moveCount)
        assertEquals(93L, loaded.elapsed.inWholeSeconds)
        assertEquals("game-1", loaded.gameId)
    }

    @Test
    fun `the suit count survives, so the same seed does not restore a different game`() = runBlocking {
        // The whole reason DealParameters exists: seed 7 at one suit and seed 7 at four suits are
        // different boards, and a restore that forgets which was played hands back the wrong one.
        val dir = folder.newFolder()
        val one = SpiderSession.start(seed = 7L, versions = VERSIONS, suitCount = SuitCount.ONE)
        val four = SpiderSession.start(seed = 7L, versions = VERSIONS, suitCount = SuitCount.FOUR)
        assertTrue("fixture is pointless if these boards match", one.state.tableau != four.state.tableau)

        storeIn(dir).save(one, elapsed = 0.seconds, gameId = "g", dealNumber = 1)
        val loaded = storeIn(dir).load() as SpiderActiveGameLoadResult.Restored

        assertEquals(SuitCount.ONE, loaded.session.state.suitCount)
        assertEquals(one.state.tableau, loaded.session.state.tableau)
    }

    @Test
    fun `the undo stack survives, so undo still works after a restart`() = runBlocking {
        val dir = folder.newFolder()
        var session = SpiderSession.start(seed = 3L, versions = VERSIONS, suitCount = SuitCount.ONE)
        val dealt = session.state
        session = session.commitMove(Move.DealRow)

        storeIn(dir).save(session, elapsed = 0.seconds, gameId = "g", dealNumber = 1)
        val restored = (storeIn(dir).load() as SpiderActiveGameLoadResult.Restored).session

        assertTrue("a restored game with a move behind it must be undoable", restored.canUndo)
        assertEquals(dealt.tableau, restored.undo().state.tableau)
    }

    @Test
    fun `an undo already in the log replays as an undo`() = runBlocking {
        val dir = folder.newFolder()
        var session = SpiderSession.start(seed = 11L, versions = VERSIONS, suitCount = SuitCount.ONE)
        session = session.commitMove(Move.DealRow).undo()

        storeIn(dir).save(session, elapsed = 0.seconds, gameId = "g", dealNumber = 1)
        val restored = (storeIn(dir).load() as SpiderActiveGameLoadResult.Restored).session

        assertEquals(session.state.tableau, restored.state.tableau)
        // Undo keeps the moves already counted and adds one (`docs/PLATFORM.md` "Persistence"),
        // so this is the number that must survive, not the pre-undo one.
        assertEquals(session.state.moveCount, restored.state.moveCount)
    }

    @Test
    fun `a save from a build with an unknown suit count is discarded, not guessed at`() = runBlocking {
        val dir = folder.newFolder()
        val session = SpiderSession.start(seed = 1L, versions = VERSIONS, suitCount = SuitCount.FOUR)
        storeIn(dir).save(session, elapsed = 0.seconds, gameId = "g", dealNumber = 1)

        FakeDataStores.setRawStringPreference(dir, STORE_NAME, "suit_count", "EIGHT")

        assertEquals(SpiderActiveGameLoadResult.Recovered, storeIn(dir).load())
    }

    @Test
    fun `a corrupt log is discarded rather than partially replayed`() = runBlocking {
        val dir = folder.newFolder()
        val session = SpiderSession.start(seed = 1L, versions = VERSIONS, suitCount = SuitCount.FOUR)
        storeIn(dir).save(session, elapsed = 0.seconds, gameId = "g", dealNumber = 1)

        // A valid Base64 blob whose bytes are not a valid log: this has to fail at decode, not
        // yield whatever prefix happened to parse.
        FakeDataStores.setRawStringPreference(dir, STORE_NAME, "log", "//8=")

        assertEquals(SpiderActiveGameLoadResult.Recovered, storeIn(dir).load())
    }

    @Test
    fun `the deal number survives a restart`() = runBlocking {
        val dir = folder.newFolder()
        val session = SpiderSession.start(seed = 9L, versions = VERSIONS, suitCount = SuitCount.TWO)
        storeIn(dir).save(session, elapsed = 0.seconds, gameId = "g", dealNumber = 42)

        val loaded = storeIn(dir).load() as SpiderActiveGameLoadResult.Restored
        assertEquals(42, loaded.dealNumber)
    }

    @Test
    fun `clearing leaves no save behind`() = runBlocking {
        val dir = folder.newFolder()
        val session = SpiderSession.start(seed = 1L, versions = VERSIONS, suitCount = SuitCount.FOUR)
        val store = storeIn(dir)
        store.save(session, elapsed = 0.seconds, gameId = "g", dealNumber = 1)
        store.clear()

        assertEquals(SpiderActiveGameLoadResult.Missing, storeIn(dir).load())
    }

    private companion object {
        const val STORE_NAME = "active_game"
    }
}
