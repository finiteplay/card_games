package org.finiteplay.blackjack.rules

import org.finiteplay.cards.Card
import org.junit.Assert.assertEquals
import org.junit.Test

/** The deterministic shoe contract (`EXECUTION_PLAN.md`): reference vectors, committed. */
class ShoeContractTest {
    private fun text(card: Card): String {
        val rank = when (card.rank) {
            org.finiteplay.cards.Rank.ACE -> "A"
            org.finiteplay.cards.Rank.TEN -> "T"
            org.finiteplay.cards.Rank.JACK -> "J"
            org.finiteplay.cards.Rank.QUEEN -> "Q"
            org.finiteplay.cards.Rank.KING -> "K"
            else -> card.rank.value.toString()
        }
        return rank + card.suit.name[0]
    }

    /** The first twenty cards of the shoe for representative seeds. A change here is a shuffle-version change. */
    private val vectors = mapOf(
        0L to "4H 3C KH 5S 9C 7S KH KH 6D KS 3C TC AD JS QS 3H KC 7C AS AC",
        1L to "6S AC 7H 3C 6S JH 3H QS QC 6H 6D 7D AS QD 6D AH 8C 8S KC 7H",
        42L to "7S KS 2D QC 7C 7H 3C 7H 6C 7D 7S 4D TC 5C JD 8C KH JH AC 3D",
        20261003L to "TD 6D 5C 3D 9S JC 8H KH 2C TS TD TS JC TH 3H TH TS 6D JC JS",
        -1L to "6C QH KC 6H 2D 5C KD 4C TC QC 4H QC AS 2H 8H 8H TD TC 6H 2S",
        Long.MAX_VALUE to "QC 8D 2H 5C 8S AC 8C KS QH 7D 9D 8D 9S 8S 9H AS QC JS 7C QS",
    )

    @Test
    fun `reference vectors`() {
        for ((seed, expected) in vectors) {
            assertEquals("seed $seed", expected, shoeFor(seed).take(20).joinToString(" ", transform = ::text))
        }
    }

    @Test
    fun `the shoe is six full decks`() {
        val counts = shoeFor(7L).groupingBy { it }.eachCount()
        assertEquals(52, counts.size)
        assertEquals(setOf(6), counts.values.toSet())
        assertEquals(SHOE_SIZE, shoeFor(7L).size)
    }

    @Test
    fun `the same seed always gives the same shoe, and the deal order follows the contract`() {
        assertEquals(shoeFor(99L), shoeFor(99L))
        val shoe = shoeFor(99L)
        val state = deal(99L, 100, 1_000)
        assertEquals(listOf(shoe[0], shoe[2]), state.hands[0].cards)
        assertEquals(listOf(shoe[1], shoe[3]), state.dealer)
        assertEquals(RULES_VERSION, state.rulesVersion)
        assertEquals(SHUFFLE_VERSION, state.shuffleVersion)
    }
}
