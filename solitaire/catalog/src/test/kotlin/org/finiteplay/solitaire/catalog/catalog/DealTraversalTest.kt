package org.finiteplay.solitaire.catalog.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class DealTraversalTest {

    @Test
    fun visitsEveryIndexExactlyOnceInOrderThenWrapsIntoANewCycle() {
        for (recordCount in listOf(1, 10, 100_000)) {
            var state = DealTraversal.startTraversal(catalogVersion = 7, recordCount = recordCount)

            val firstCycle = (0 until recordCount).map {
                val index = DealTraversal.indexAt(state, recordCount)
                state = DealTraversal.advance(state, recordCount)
                index
            }
            assertEquals(
                "first cycle must visit every index exactly once, in order, for N=$recordCount",
                (0 until recordCount).toList(),
                firstCycle,
            )

            val secondCycle = (0 until recordCount).map {
                val index = DealTraversal.indexAt(state, recordCount)
                state = DealTraversal.advance(state, recordCount)
                index
            }
            assertEquals(
                "second cycle must also visit every index exactly once, in order, for N=$recordCount",
                (0 until recordCount).toList(),
                secondCycle,
            )
        }
    }

    @Test
    fun indexAtReturnsTheStoredPositionDirectly() {
        val state = CatalogTraversalState(catalogVersion = 1, nextPosition = 5)
        assertEquals(5, DealTraversal.indexAt(state, recordCount = 11))
    }

    @Test
    fun singleRecordCatalogAlwaysSelectsIndexZero() {
        var state = DealTraversal.startTraversal(catalogVersion = 1, recordCount = 1)
        repeat(5) {
            assertEquals(0, DealTraversal.indexAt(state, 1))
            state = DealTraversal.advance(state, 1)
        }
    }
}
