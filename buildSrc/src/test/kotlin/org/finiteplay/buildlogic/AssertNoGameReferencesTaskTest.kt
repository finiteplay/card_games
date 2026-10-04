package org.finiteplay.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssertNoGameReferencesTaskTest {

    private val forbidden = listOf("klondike", "spider", "blackjack")

    @Test
    fun `flags a game named in an import`() {
        val content = "package org.finiteplay.core.ui\n\nimport org.finiteplay.klondike.board.GameState\n"

        assertEquals(listOf("klondike"), findGameReferences(content, forbidden))
    }

    @Test
    fun `flags a game named only in a comment`() {
        val content = "// Sized for the Klondike tableau.\nval columnCount = 7\n"

        assertEquals(listOf("klondike"), findGameReferences(content, forbidden))
    }

    @Test
    fun `flags a game named only in a string literal`() {
        val content = """val store = preferencesDataStoreAt(context, "klondike_settings")"""

        assertEquals(listOf("klondike"), findGameReferences(content, forbidden))
    }

    @Test
    fun `reports every game named`() {
        val content = "when (game) { \"klondike\" -> a; \"spider\" -> b }"

        assertEquals(listOf("klondike", "spider"), findGameReferences(content, forbidden))
    }

    @Test
    fun `passes for game-agnostic source`() {
        val content = "package org.finiteplay.cards\n\nenum class Suit { HEARTS, DIAMONDS, CLUBS, SPADES }\n"

        assertTrue(findGameReferences(content, forbidden).isEmpty())
    }
}
