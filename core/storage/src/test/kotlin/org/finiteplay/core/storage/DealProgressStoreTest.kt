package org.finiteplay.core.storage

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DealProgressStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun store(dir: java.io.File) = DealProgressStore(dir, dataStoreFactory = FakeDataStores::create)

    @Test
    fun `a fresh directory has no progress`() = runTest {
        assertEquals(emptyMap<Long, DealProgress>(), store(folder.newFolder()).current())
    }

    @Test
    fun `progress survives a simulated restart`() = runTest {
        val dir = folder.newFolder()
        store(dir).mark(7L, DealStatus.PLAYED, 42)
        store(dir).mark(-9L, DealStatus.WON, 130)

        assertEquals(
            mapOf(7L to DealProgress(DealStatus.PLAYED, 42), -9L to DealProgress(DealStatus.WON, 130)),
            store(dir).current(),
        )
    }

    @Test
    fun `a deal moves from played to won, taking the winning moves`() = runTest {
        val s = store(folder.newFolder())
        s.mark(1L, DealStatus.PLAYED, 30)
        s.mark(1L, DealStatus.WON, 95)

        assertEquals(DealProgress(DealStatus.WON, 95), s.current().getValue(1L))
    }

    @Test
    fun `a played deal keeps the moves of the game last played`() = runTest {
        val s = store(folder.newFolder())
        s.mark(1L, DealStatus.PLAYED, 30)
        s.mark(1L, DealStatus.PLAYED, 12)

        assertEquals(DealProgress(DealStatus.PLAYED, 12), s.current().getValue(1L))
    }

    @Test
    fun `a won deal keeps its fewest moves, and is never turned back into played`() = runTest {
        val s = store(folder.newFolder())
        s.mark(1L, DealStatus.WON, 120)
        s.mark(1L, DealStatus.WON, 150)
        s.mark(1L, DealStatus.PLAYED, 5)

        assertEquals(DealProgress(DealStatus.WON, 120), s.current().getValue(1L))

        s.mark(1L, DealStatus.WON, 100)
        assertEquals(DealProgress(DealStatus.WON, 100), s.current().getValue(1L))
    }

    @Test
    fun `a win with unknown moves does not erase a known best, and a known best replaces an unknown one`() {
        assertEquals(DealProgress(DealStatus.WON, 90), DealProgress(DealStatus.WON, 90).advancedBy(DealStatus.WON, 0))
        assertEquals(DealProgress(DealStatus.WON, 90), DealProgress(DealStatus.WON, 0).advancedBy(DealStatus.WON, 90))
    }

    @Test
    fun `clearing leaves no progress behind`() = runTest {
        val dir = folder.newFolder()
        val s = store(dir)
        s.mark(1L, DealStatus.WON, 100)
        s.clear()

        assertEquals(emptyMap<Long, DealProgress>(), store(dir).current())
    }

    @Test
    fun `a garbage blob reads back as no progress instead of throwing`() = runTest {
        val dir = folder.newFolder()
        FakeDataStores.corrupt(dir, DealProgressStore.DEFAULT_NAME)

        assertEquals(emptyMap<Long, DealProgress>(), store(dir).current())
    }

    @Test
    fun `progress written before moves were kept still reads, with the moves unknown`() = runTest {
        val dir = folder.newFolder()
        FakeDataStores.setRawStringPreference(dir, DealProgressStore.DEFAULT_NAME, "records", legacyBlob(mapOf(3L to DealStatus.WON, 4L to DealStatus.PLAYED)))

        assertEquals(
            mapOf(3L to DealProgress(DealStatus.WON, 0), 4L to DealProgress(DealStatus.PLAYED, 0)),
            store(dir).current(),
        )
    }

    /** The first format: a version, a count, then a seed and a status byte per deal, with no moves. */
    private fun legacyBlob(entries: Map<Long, DealStatus>): String {
        val bytes = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(bytes).use { out ->
            out.writeInt(1)
            out.writeInt(entries.size)
            for ((seed, status) in entries) {
                out.writeLong(seed)
                out.writeByte(status.ordinal)
            }
        }
        return java.util.Base64.getEncoder().encodeToString(bytes.toByteArray())
    }
}
