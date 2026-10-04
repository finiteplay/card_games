package org.finiteplay.klondike

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.finiteplay.cards.DECK_SHUFFLE_REFERENCE_VECTORS
import org.finiteplay.cards.shuffleDeckIndices
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Re-asserts [DECK_SHUFFLE_REFERENCE_VECTORS] on-device, proving the JVM and Android
 * runtimes produce identical shuffle output for the same seeds (D1a gate, deal contract
 * "Produce identical decks on Android and JVM test tools").
 */
@RunWith(AndroidJUnit4::class)
class DeckShuffleReferenceVectorTest {
    @Test
    fun shuffleMatchesReferenceVectorsOnAndroid() {
        DECK_SHUFFLE_REFERENCE_VECTORS.forEach { (seed, expected) ->
            assertEquals(expected, shuffleDeckIndices(seed).toList())
        }
    }
}
