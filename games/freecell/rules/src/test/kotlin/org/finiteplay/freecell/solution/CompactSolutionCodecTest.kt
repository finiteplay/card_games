package org.finiteplay.freecell.solution

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.freecell.layout.GameStatus
import org.finiteplay.freecell.layout.GameVersions
import org.finiteplay.freecell.rules.Move
import org.finiteplay.freecell.rules.applyMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hand-built fixture coverage for every opcode this format packs. The real correctness gate is
 * `SolutionsAssetTest` (`games/freecell/app`), which round-trips the shipped, re-solved catalog —
 * these fixtures exist so a single opcode's bit width can be checked in isolation.
 */
class CompactSolutionCodecTest {

    private val versions = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1)

    /**
     * Every foundation at Jack (44 cards banked) with the remaining eight cards — the four Queens
     * and four Kings — placed so a short line finishing the game exercises all five opcodes.
     */
    private fun endgame(seed: Long = 1L): FreeCellState = FreeCellState(
        seed = seed,
        versions = versions,
        tableau = listOf(
            listOf(Card(Suit.SPADES, Rank.KING)),
            listOf(Card(Suit.HEARTS, Rank.QUEEN)),
            listOf(Card(Suit.CLUBS, Rank.QUEEN)),
            listOf(Card(Suit.DIAMONDS, Rank.QUEEN)),
            listOf(Card(Suit.DIAMONDS, Rank.KING)),
            listOf(Card(Suit.HEARTS, Rank.KING)),
            listOf(Card(Suit.CLUBS, Rank.KING)),
            emptyList(),
        ),
        freeCells = listOf(Card(Suit.SPADES, Rank.QUEEN), null, null, null),
        foundations = Suit.entries.associateWith { Rank.JACK.value },
        status = GameStatus.IN_PROGRESS,
    )

    /** One of every opcode, in an order that reaches a full win. */
    private val endgameLine = listOf(
        Move.TableauToFreeCell(fromColumn = 2, cell = 1),
        Move.TableauToTableau(fromColumn = 1, fromIndex = 0, toColumn = 0),
        Move.TableauToFoundation(fromColumn = 3),
        Move.FreeCellToTableau(cell = 1, toColumn = 4),
        Move.TableauToFoundation(fromColumn = 4),
        Move.TableauToFoundation(fromColumn = 4),
        Move.TableauToFoundation(fromColumn = 0),
        Move.TableauToFoundation(fromColumn = 5),
        Move.FreeCellToFoundation(cell = 0),
        Move.TableauToFoundation(fromColumn = 0),
        Move.TableauToFoundation(fromColumn = 6),
    )

    private fun assertWins(deal: FreeCellState, line: List<Move>) {
        var state = deal
        for (move in line) state = applyMove(state, move)
        assertTrue("fixture line must win", state.isWon)
    }

    @Test
    fun `a won line survives encode and decode`() {
        val deal = endgame()
        assertWins(deal, endgameLine)

        val blob = CompactSolutionCodec.encodeCatalog(mapOf(1L to endgameLine)) { deal }
        val index = CompactSolutionCodec.readIndex(blob)

        assertEquals(1, index.size)
        assertTrue(1L in index)
        val decoded = CompactSolutionCodec.decodeLine(blob, index, 1L)
        assertEquals(endgameLine, decoded)
        assertWins(deal, decoded!!)
    }

    @Test
    fun `an uncatalogued seed decodes to null rather than throwing`() {
        val deal = endgame()
        val blob = CompactSolutionCodec.encodeCatalog(mapOf(1L to endgameLine)) { deal }
        val index = CompactSolutionCodec.readIndex(blob)

        assertNull(CompactSolutionCodec.decodeLine(blob, index, 99L))
    }

    @Test
    fun `every entry decodes from its own block in a multi-seed catalog`() {
        val seeds = listOf(1L, 7L, 900L, 900_001L)
        val deals = seeds.associateWith { endgame(it) }
        val lines = seeds.associateWith { endgameLine }
        for (seed in seeds) assertWins(deals.getValue(seed), lines.getValue(seed))

        val blob = CompactSolutionCodec.encodeCatalog(lines) { deals.getValue(it) }
        val index = CompactSolutionCodec.readIndex(blob)

        assertEquals(seeds.size, index.size)
        for (seed in seeds) {
            val decoded = CompactSolutionCodec.decodeLine(blob, index, seed)
            assertEquals("seed $seed", lines.getValue(seed), decoded)
            assertWins(deals.getValue(seed), decoded!!)
        }
    }

    @Test
    fun `a line that does not win is refused rather than stored`() {
        val deal = endgame()

        val blob = CompactSolutionCodec.encodeCatalog(mapOf(1L to endgameLine.dropLast(1))) { deal }

        assertEquals(0, CompactSolutionCodec.readIndex(blob).size)
    }

    @Test
    fun `a truncated block decodes to null instead of throwing`() {
        val deal = endgame()
        val blob = CompactSolutionCodec.encodeCatalog(mapOf(1L to endgameLine)) { deal }
        val truncated = blob.copyOf(blob.size - 2)
        val index = CompactSolutionCodec.readIndex(blob)

        assertNull(CompactSolutionCodec.decodeLine(truncated, index, 1L))
    }

    @Test
    fun `the compact form is well under two bytes per move`() {
        val deal = endgame()

        val compact = CompactSolutionCodec.encodeCatalog(mapOf(1L to endgameLine)) { deal }

        assertTrue("compact ${compact.size} for ${endgameLine.size} moves", compact.size < endgameLine.size * 2)
    }
}
