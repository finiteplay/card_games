package org.finiteplay.solitaire.catalog.catalog

/**
 * Non-repeating local deal-selection state for the active draw-one catalog
 * (`docs/solitaire/CATALOG.md`, "Traversal"). Plain and serializable so S1 can persist it
 * verbatim; D1a only implements the algorithm over it.
 */
data class CatalogTraversalState(
    val catalogVersion: Int,
    val nextPosition: Int,
)

/**
 * Local, non-repeating, sequential traversal over a catalog's `N` record indexes: deal 1,
 * deal 2, deal 3, ... in catalog order, wrapping back to index 0 after all `N` indexes have
 * been visited.
 */
object DealTraversal {

    /** Starts a new traversal cycle at position 0. */
    fun startTraversal(catalogVersion: Int, recordCount: Int): CatalogTraversalState {
        require(recordCount > 0) { "recordCount must be positive" }
        return CatalogTraversalState(catalogVersion = catalogVersion, nextPosition = 0)
    }

    /** The catalog index selected by [state]'s current, not-yet-consumed position. */
    fun indexAt(state: CatalogTraversalState, recordCount: Int): Int {
        require(recordCount > 0) { "recordCount must be positive" }
        return state.nextPosition
    }

    /**
     * Advances past the current position. After all `recordCount` indexes have been
     * visited, wraps back to the start instead of repeating the last index.
     */
    fun advance(state: CatalogTraversalState, recordCount: Int): CatalogTraversalState {
        require(recordCount > 0) { "recordCount must be positive" }
        val nextPosition = state.nextPosition + 1
        return if (nextPosition >= recordCount) {
            state.copy(nextPosition = 0)
        } else {
            state.copy(nextPosition = nextPosition)
        }
    }
}
