package org.finiteplay.holdem.ui

import org.finiteplay.holdem.rules.HandCategory
import org.finiteplay.holdem.rules.categoryOf
import org.finiteplay.holdem.rules.evaluate
import org.junit.Assert.assertEquals
import org.junit.Test

class HelpExamplesTest {
    @Test
    fun `each drawn example is five different cards making exactly its ranking, strongest first`() {
        assertEquals(HandCategory.entries.reversed(), HAND_EXAMPLES.map { it.category })
        for (example in HAND_EXAMPLES) {
            assertEquals(5, example.cards.distinct().size)
            assertEquals(example.category.name, example.category, categoryOf(evaluate(example.cards)))
        }
    }
}
