package org.finiteplay.solitaire.catalog

import org.finiteplay.cards.DECK_SHUFFLE_REFERENCE_VECTORS
import org.finiteplay.cards.shuffleDeckIndices
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Re-asserts [DECK_SHUFFLE_REFERENCE_VECTORS] here, redundant with `:core:cards`' own
 * test, to prove this module sees identical shuffle output through its dependency on
 * that module (D1a gate: reference vectors verified in the catalog tool, JVM tests, and
 * Android tests). The point is the seam between modules, not the shuffle itself — a
 * catalog generated against a different shuffle than the app deals would be silently,
 * catastrophically wrong.
 */
class DeckShuffleReferenceVectorTest {
    @Test
    fun shuffleMatchesReferenceVectorsThroughTheCardsModule() {
        DECK_SHUFFLE_REFERENCE_VECTORS.forEach { (seed, expected) ->
            assertEquals(expected, shuffleDeckIndices(seed).toList())
        }
    }
}
