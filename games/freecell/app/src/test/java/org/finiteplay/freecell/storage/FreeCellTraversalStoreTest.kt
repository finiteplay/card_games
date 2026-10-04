package org.finiteplay.freecell.storage

import kotlinx.coroutines.test.runTest
import org.finiteplay.core.storage.FakeDataStores
import org.finiteplay.solitaire.catalog.catalog.CatalogTraversalState
import org.finiteplay.solitaire.catalog.catalog.DealTraversal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FreeCellTraversalStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun storeIn(dir: File) = FreeCellTraversalStore(dir, dataStoreFactory = FakeDataStores::create)

    @Test
    fun `a fresh directory starts a new traversal`() = runTest {
        val result = storeIn(folder.newFolder()).load(currentCatalogVersion = 1, currentRecordCount = 10)
        assertEquals(FreeCellTraversalLoadResult.StartNew, result)
    }

    @Test
    fun `a saved traversal round-trips for the same catalog identity, via a fresh reader`() = runTest {
        val state = CatalogTraversalState(catalogVersion = 1, nextPosition = 2)
        val dir = folder.newFolder()
        storeIn(dir).save(state, recordCount = 10)

        val restored = storeIn(dir).load(currentCatalogVersion = 1, currentRecordCount = 10)

        assertEquals(FreeCellTraversalLoadResult.Present(state), restored)
    }

    @Test
    fun `a catalog version upgrade starts a new traversal`() = runTest {
        val state = CatalogTraversalState(catalogVersion = 1, nextPosition = 2)
        val dir = folder.newFolder()
        storeIn(dir).save(state, recordCount = 10)

        val result = storeIn(dir).load(currentCatalogVersion = 2, currentRecordCount = 10)

        assertEquals(FreeCellTraversalLoadResult.StartNew, result)
    }

    @Test
    fun `a record count change under the same version starts a new traversal`() = runTest {
        val state = CatalogTraversalState(catalogVersion = 1, nextPosition = 2)
        val dir = folder.newFolder()
        storeIn(dir).save(state, recordCount = 10)

        val result = storeIn(dir).load(currentCatalogVersion = 1, currentRecordCount = 11)

        assertEquals(FreeCellTraversalLoadResult.StartNew, result)
    }

    @Test
    fun `loadOrStartNew persists what it starts, so a fresh reader sees the same traversal`() = runTest {
        val dir = folder.newFolder()
        val started = storeIn(dir).loadOrStartNew(catalogVersion = 1, recordCount = 10)

        val reread = storeIn(dir).load(currentCatalogVersion = 1, currentRecordCount = 10)

        assertEquals(FreeCellTraversalLoadResult.Present(started), reread)
    }

    @Test
    fun `traversal advances sequentially across repeated persist cycles`() = runTest {
        val dir = folder.newFolder()
        var state = storeIn(dir).loadOrStartNew(catalogVersion = 1, recordCount = 5)
        val visited = mutableListOf(state.nextPosition)

        repeat(4) {
            state = DealTraversal.advance(state, recordCount = 5)
            storeIn(dir).save(state, recordCount = 5)
            visited += state.nextPosition
        }

        assertEquals(listOf(0, 1, 2, 3, 4), visited)
    }
}
