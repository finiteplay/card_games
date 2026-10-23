package org.finiteplay.core.storage

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.finiteplay.core.session.ENJOY_MIN_DAYS
import org.finiteplay.core.session.ENJOY_MIN_SESSIONS
import org.finiteplay.core.session.Engagement
import org.finiteplay.core.session.SESSION_GAP_MILLIS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EngagementStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val day = TimeUnit.DAYS.toMillis(1)

    private fun store(dir: java.io.File) = EngagementStore(dir, dataStoreFactory = FakeDataStores::create)

    @Test
    fun `a fresh directory is a new player`() = runTest {
        assertEquals(Engagement(), store(folder.newFolder()).current())
    }

    @Test
    fun `starts, days, results and the question survive a simulated restart`() = runTest {
        val dir = folder.newFolder()
        val first = store(dir)
        first.recordStart(100 * day)
        first.recordStart(100 * day + SESSION_GAP_MILLIS)
        first.recordStart(101 * day)
        first.recordGoodResult()
        first.recordGoodResult()
        first.markAsked(5_000_000L)

        val reread = store(dir).current()
        assertEquals(3, reread.sessions)
        assertEquals(2, reread.activeDays)
        assertEquals(2, reread.goodResults)
        assertEquals(5_000_000L, reread.lastAskedMillis)
        assertFalse(reread.answered)
    }

    @Test
    fun `answering is remembered`() = runTest {
        val dir = folder.newFolder()
        store(dir).markAnswered()
        assertTrue(store(dir).current().answered)
    }

    @Test
    fun `a start in the same sitting adds neither a session nor a day`() = runTest {
        val s = store(folder.newFolder())
        s.recordStart(0L)
        s.recordStart(60_000L)
        assertEquals(1, s.current().sessions)
        assertEquals(1, s.current().activeDays)
    }

    @Test
    fun `each change returns the record it stored`() = runTest {
        val s = store(folder.newFolder())
        assertEquals(1, s.recordStart(10L).sessions)
        assertEquals(1, s.recordGoodResult().goodResults)
        assertTrue(s.markAnswered().answered)
    }

    @Test
    fun `a seeded veteran has the sessions and the separate days the question needs`() = runTest {
        val s = store(folder.newFolder())

        s.seedVeteran(1_000 * day, goodResults = 2)

        val e = s.current()
        assertEquals(ENJOY_MIN_SESSIONS, e.sessions)
        assertTrue("got ${e.activeDays} days", e.activeDays >= ENJOY_MIN_DAYS)
        assertEquals(2, e.goodResults)
    }
}
