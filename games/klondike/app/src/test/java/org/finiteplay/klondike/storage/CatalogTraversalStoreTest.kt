package org.finiteplay.klondike.storage

import kotlinx.coroutines.test.runTest
import org.finiteplay.solitaire.catalog.catalog.CatalogTraversalState
import org.finiteplay.solitaire.catalog.catalog.DealTraversal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CatalogTraversalStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `a fresh directory starts a new traversal`() = runTest {
        val result = catalogTraversalStore(tempFolder.newFolder()).load(currentCatalogVersion = 1, currentRecordCount = 10)
        assertEquals(TraversalLoadResult.StartNew, result)
    }

    @Test
    fun `a saved traversal round-trips for the same catalog identity, via a fresh reader`() = runTest {
        val state = CatalogTraversalState(catalogVersion = 1, nextPosition = 2)
        val dir = tempFolder.newFolder()
        catalogTraversalStore(dir).save(state, recordCount = 10)

        val restored = catalogTraversalStore(dir).load(currentCatalogVersion = 1, currentRecordCount = 10)

        assertEquals(TraversalLoadResult.Present(state), restored)
    }

    @Test
    fun `a saved traversal round-trips at a realistic 100,000-record catalog size`() = runTest {
        val state = CatalogTraversalState(catalogVersion = 1, nextPosition = 99_998)
        val dir = tempFolder.newFolder()
        catalogTraversalStore(dir).save(state, recordCount = 100_000)

        val restored = catalogTraversalStore(dir).load(currentCatalogVersion = 1, currentRecordCount = 100_000)

        assertEquals(TraversalLoadResult.Present(state), restored)
    }

    @Test
    fun `a catalog version upgrade starts a new traversal`() = runTest {
        val state = CatalogTraversalState(catalogVersion = 1, nextPosition = 2)
        val dir = tempFolder.newFolder()
        catalogTraversalStore(dir).save(state, recordCount = 10)

        // Catalog version bumped from 1 to 2: the stored traversal no longer applies.
        val result = catalogTraversalStore(dir).load(currentCatalogVersion = 2, currentRecordCount = 10)

        assertEquals(TraversalLoadResult.StartNew, result)
    }

    @Test
    fun `a record count change under the same version starts a new traversal`() = runTest {
        val state = CatalogTraversalState(catalogVersion = 1, nextPosition = 2)
        val dir = tempFolder.newFolder()
        catalogTraversalStore(dir).save(state, recordCount = 10)

        val result = catalogTraversalStore(dir).load(currentCatalogVersion = 1, currentRecordCount = 100_000)

        assertEquals(TraversalLoadResult.StartNew, result)
    }

    @Test
    fun `loadOrStartNew persists what it starts, so a fresh reader sees the same traversal`() = runTest {
        val dir = tempFolder.newFolder()
        val first = catalogTraversalStore(dir).loadOrStartNew(catalogVersion = 1, recordCount = 10)

        val second = catalogTraversalStore(dir).loadOrStartNew(catalogVersion = 1, recordCount = 10)

        assertEquals(first, second)
    }

    @Test
    fun `traversal advances sequentially across repeated persist cycles at a realistic record count`() = runTest {
        // Full-cycle "every index exactly once, in order" coverage belongs to :solitaire:catalog's
        // DealTraversalTest, which already proves it for N = 1, 10, and 100,000; this
        // test is about *persistence* surviving repeated save/reload, not re-proving the
        // traversal algorithm itself.
        val recordCount = 100_000
        val store = catalogTraversalStore(tempFolder.newFolder())
        var state = store.loadOrStartNew(catalogVersion = 1, recordCount = recordCount)
        val visited = mutableListOf(DealTraversal.indexAt(state, recordCount))

        repeat(20) {
            state = DealTraversal.advance(state, recordCount)
            store.save(state, recordCount)
            visited += DealTraversal.indexAt(state, recordCount)
        }

        assertEquals((0..20).toList(), visited)
    }

    @Test
    fun `a garbage traversal file starts a new traversal instead of throwing`() = runTest {
        val dir = tempFolder.newFolder()
        FakeDataStores.corrupt(dir, "catalog_traversal")

        val result = catalogTraversalStore(dir).load(currentCatalogVersion = 1, currentRecordCount = 10)

        assertEquals(TraversalLoadResult.StartNew, result)
    }
}
