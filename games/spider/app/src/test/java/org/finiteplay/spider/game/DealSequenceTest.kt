package org.finiteplay.spider.game

import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.dealGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DealSequenceTest {
    private val versions = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

    @Test
    fun `a deal number always names the same board`() {
        for (suitCount in SuitCount.entries) {
            for (number in 1..50) {
                assertEquals(
                    DealSequence.seedFor(suitCount, number),
                    DealSequence.seedFor(suitCount, number),
                )
            }
        }
    }

    @Test
    fun `the same number at different suit counts is a different deal`() {
        // Deal 7 at one suit and deal 7 at four are different games; sharing a seed would imply
        // they were somehow the same board.
        for (number in 1..30) {
            val seeds = SuitCount.entries.map { DealSequence.seedFor(it, number) }
            assertEquals("suit counts must not share a seed at deal $number", seeds.size, seeds.distinct().size)
        }
    }

    @Test
    fun `consecutive deals are unrelated boards`() {
        for (suitCount in SuitCount.entries) {
            for (number in 1..40) {
                val a = dealGame(DealSequence.seedFor(suitCount, number), versions, suitCount)
                val b = dealGame(DealSequence.seedFor(suitCount, number + 1), versions, suitCount)
                assertNotEquals("deal $number and ${number + 1} at $suitCount are the same board", a.tableau, b.tableau)
            }
        }
    }

    @Test
    fun `numbering is dense enough to have no early collisions`() {
        for (suitCount in SuitCount.entries) {
            val seeds = (1..5000).map { DealSequence.seedFor(suitCount, it) }
            assertEquals("a player will not meet the same seed twice in 5000 deals", seeds.size, seeds.toSet().size)
        }
    }

    @Test
    fun `every numbered deal is a legal opening board`() {
        for (suitCount in SuitCount.entries) {
            for (number in 1..25) {
                val state = dealGame(DealSequence.seedFor(suitCount, number), versions, suitCount)
                assertEquals(104, state.tableau.sumOf { it.size } + state.stock.size)
                assertTrue(state.tableau.all { it.isNotEmpty() })
            }
        }
    }
}
