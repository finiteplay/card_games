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
        assertEquals(emptyMap<Long, DealStatus>(), store(folder.newFolder()).current())
    }

    @Test
    fun `progress survives a simulated restart`() = runTest {
        val dir = folder.newFolder()
        store(dir).mark(7L, DealStatus.PLAYED)
        store(dir).mark(-9L, DealStatus.WON)

        assertEquals(mapOf(7L to DealStatus.PLAYED, -9L to DealStatus.WON), store(dir).current())
    }

    @Test
    fun `a deal moves from played to won`() = runTest {
        val s = store(folder.newFolder())
        s.mark(1L, DealStatus.PLAYED)
        s.mark(1L, DealStatus.WON)

        assertEquals(DealStatus.WON, s.current().getValue(1L))
    }

    @Test
    fun `a won deal is never turned back into played`() = runTest {
        val s = store(folder.newFolder())
        s.mark(1L, DealStatus.WON)
        s.mark(1L, DealStatus.PLAYED)

        assertEquals(DealStatus.WON, s.current().getValue(1L))
    }

    @Test
    fun `clearing leaves no progress behind`() = runTest {
        val dir = folder.newFolder()
        val s = store(dir)
        s.mark(1L, DealStatus.WON)
        s.clear()

        assertEquals(emptyMap<Long, DealStatus>(), store(dir).current())
    }

    @Test
    fun `a garbage blob reads back as no progress instead of throwing`() = runTest {
        val dir = folder.newFolder()
        FakeDataStores.corrupt(dir, DealProgressStore.DEFAULT_NAME)

        assertEquals(emptyMap<Long, DealStatus>(), store(dir).current())
    }
}
