package org.finiteplay.spider.storage

import kotlinx.coroutines.runBlocking
import org.finiteplay.core.storage.FakeDataStores
import org.finiteplay.spider.game.DealSequence
import org.finiteplay.spider.layout.SuitCount
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SpiderTraversalStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun storeIn(dir: File) = SpiderTraversalStore(dir, dataStoreFactory = FakeDataStores::create)

    @Test
    fun `with no wrap, position grows past the certified range the way the uncertified formula needs`() = runBlocking {
        val store = storeIn(folder.newFolder())
        assertEquals(DealSequence.FIRST, store.next(SuitCount.FOUR))
        store.advancePast(SuitCount.FOUR, dealNumber = 10)
        assertEquals(11, store.next(SuitCount.FOUR))
    }

    @Test
    fun `with a wrap, position cycles back to 1 instead of walking off the certified list`() = runBlocking {
        val store = storeIn(folder.newFolder())
        val wrapAt = 5
        store.advancePast(SuitCount.ONE, dealNumber = wrapAt, wrapAt = wrapAt)
        assertEquals(1, store.next(SuitCount.ONE, wrapAt))
    }

    @Test
    fun `each suit count keeps its own position`() = runBlocking {
        val store = storeIn(folder.newFolder())
        store.advancePast(SuitCount.ONE, dealNumber = 4)
        assertEquals(5, store.next(SuitCount.ONE))
        assertEquals(DealSequence.FIRST, store.next(SuitCount.TWO))
    }
}
