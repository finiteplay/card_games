package org.finiteplay.spider.solution

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.spider.layout.GameStatus
import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.TableauCard
import org.finiteplay.spider.rules.Move
import org.finiteplay.spider.rules.applyMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hand-built fixture coverage for both opcodes and both the zero and non-zero
 * [CompactSolutionCodec.Choice.Transfer.delta] paths this format packs. The real correctness gate
 * is `SolutionsAssetTest` (`games/spider/app`), which round-trips the shipped, re-solved catalog —
 * these fixtures exist so a single opcode or delta width can be checked in isolation.
 */
class CompactSolutionCodecTest {
    private val versions = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1)

    private fun up(card: Card) = TableauCard(card, faceUp = true)

    /**
     * Seven suits already banked, a King-to-3 spade run one link short of complete, an ace
     * buried under a filler in another column, and a ten-card row still in the stock — the
     * winning line below exercises both opcodes: [Move.DealRow] completes the run's last link,
     * and the [Move.TableauToTableau] that follows lifts the exposed ace (a zero-delta transfer,
     * the whole movable sequence) to finish it.
     */
    private fun endgame(seed: Long = 1L): SpiderState {
        val runRanks = listOf(
            Rank.KING, Rank.QUEEN, Rank.JACK, Rank.TEN, Rank.NINE,
            Rank.EIGHT, Rank.SEVEN, Rank.SIX, Rank.FIVE, Rank.FOUR, Rank.THREE,
        )
        val column0 = runRanks.map { up(Card(Suit.SPADES, it)) }
        val column1 = listOf(up(Card(Suit.HEARTS, Rank.KING)))
        val fillerColumns = (2 until 10).map { listOf(up(Card(Suit.CLUBS, Rank.KING))) }
        val tableau = listOf(column0, column1) + fillerColumns

        val stock = listOf(Card(Suit.SPADES, Rank.TWO), Card(Suit.SPADES, Rank.ACE)) +
            List(8) { Card(Suit.CLUBS, Rank.QUEEN) }

        return SpiderState(
            seed = seed,
            versions = versions,
            suitCount = SuitCount.FOUR,
            tableau = tableau,
            stock = stock,
            banked = mapOf(Suit.SPADES to 2, Suit.HEARTS to 2, Suit.CLUBS to 2, Suit.DIAMONDS to 1),
            status = GameStatus.IN_PROGRESS,
        )
    }

    private val endgameLine = listOf(
        Move.DealRow,
        Move.TableauToTableau(fromColumn = 1, fromIndex = 1, toColumn = 0),
    )

    /**
     * A board where the first move of the winning line lifts fewer cards than the whole movable
     * sequence, so its stored choice's delta is non-zero and the escape path in
     * [CompactSolutionCodec] actually runs. Column 0's top two cards are a spades run (`8,7`)
     * sitting on an unrelated diamond filler; the line lifts only the `7` onto column 1's `8`
     * filler (a delta-1 choice, not the whole `8,7` run), then finishes the game the same way
     * [endgame] does — an ace onto an eleven-deep run one card short of complete.
     */
    private fun partialLiftEndgame(seed: Long = 2L): SpiderState {
        val column0 = listOf(
            up(Card(Suit.DIAMONDS, Rank.TEN)),
            up(Card(Suit.SPADES, Rank.EIGHT)),
            up(Card(Suit.SPADES, Rank.SEVEN)),
        )
        val column1 = listOf(up(Card(Suit.CLUBS, Rank.EIGHT)))
        val runRanks = listOf(
            Rank.KING, Rank.QUEEN, Rank.JACK, Rank.TEN, Rank.NINE,
            Rank.EIGHT, Rank.SEVEN, Rank.SIX, Rank.FIVE, Rank.FOUR, Rank.THREE, Rank.TWO,
        )
        val column2 = runRanks.map { up(Card(Suit.HEARTS, it)) }
        val column3 = listOf(up(Card(Suit.HEARTS, Rank.ACE)))
        val fillerColumns = (4 until 10).map { listOf(up(Card(Suit.CLUBS, Rank.KING))) }
        val tableau = listOf(column0, column1, column2, column3) + fillerColumns

        return SpiderState(
            seed = seed,
            versions = versions,
            suitCount = SuitCount.FOUR,
            tableau = tableau,
            stock = emptyList(),
            banked = mapOf(Suit.SPADES to 2, Suit.HEARTS to 2, Suit.CLUBS to 2, Suit.DIAMONDS to 1),
            status = GameStatus.IN_PROGRESS,
        )
    }

    private val partialLiftLine = listOf(
        // fromIndex 2 is one short of this column's own sequenceStart (1), so this stores a
        // delta of 1 rather than the 0 every other fixture's moves store.
        Move.TableauToTableau(fromColumn = 0, fromIndex = 2, toColumn = 1),
        Move.TableauToTableau(fromColumn = 3, fromIndex = 0, toColumn = 2),
    )

    private fun assertWins(deal: SpiderState, line: List<Move>) {
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
        val decoded = CompactSolutionCodec.decodeLine(blob, index, deal, 1L)
        assertEquals(endgameLine, decoded)
        assertWins(deal, decoded!!)
    }

    @Test
    fun `a partial lift round-trips its true fromIndex`() {
        val deal = partialLiftEndgame()
        assertWins(deal, partialLiftLine)

        val blob = CompactSolutionCodec.encodeCatalog(mapOf(2L to partialLiftLine)) { deal }
        val index = CompactSolutionCodec.readIndex(blob)
        val decoded = CompactSolutionCodec.decodeLine(blob, index, deal, 2L)

        assertEquals(partialLiftLine, decoded)
        assertWins(deal, decoded!!)
    }

    @Test
    fun `an uncatalogued seed decodes to null rather than throwing`() {
        val deal = endgame()
        val blob = CompactSolutionCodec.encodeCatalog(mapOf(1L to endgameLine)) { deal }
        val index = CompactSolutionCodec.readIndex(blob)

        assertNull(CompactSolutionCodec.decodeLine(blob, index, deal, 99L))
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
            val decoded = CompactSolutionCodec.decodeLine(blob, index, deals.getValue(seed), seed)
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
        val truncated = blob.copyOf(blob.size - 1)
        val index = CompactSolutionCodec.readIndex(blob)

        assertNull(CompactSolutionCodec.decodeLine(truncated, index, deal, 1L))
    }

    /**
     * A realistic-length line needs more moves than a two- or three-move fixture can win in, so
     * this pads [partialLiftEndgame] with harmless moves that shuttle a spare King back and forth
     * between two otherwise-unused empty columns — legal, and irrelevant to the win, the same way
     * a real solved line often revisits an empty column before its final approach.
     */
    private fun paddedPartialLiftEndgame(seed: Long): SpiderState {
        val base = partialLiftEndgame(seed)
        val tableau = base.tableau.toMutableList()
        tableau[8] = emptyList()
        tableau[9] = listOf(up(Card(Suit.CLUBS, Rank.KING)))
        return base.copy(tableau = tableau)
    }

    private fun oscillation(count: Int): List<Move> = List(count) { i ->
        if (i % 2 == 0) {
            Move.TableauToTableau(fromColumn = 9, fromIndex = 0, toColumn = 8)
        } else {
            Move.TableauToTableau(fromColumn = 8, fromIndex = 0, toColumn = 9)
        }
    }

    @Test
    fun `the compact form is well under two bytes per move`() {
        val deal = paddedPartialLiftEndgame(3L)
        val line = oscillation(40) + partialLiftLine
        assertWins(deal, line)

        val compact = CompactSolutionCodec.encodeCatalog(mapOf(3L to line)) { deal }

        assertTrue("compact ${compact.size} for ${line.size} moves", compact.size < line.size * 2)
    }
}
