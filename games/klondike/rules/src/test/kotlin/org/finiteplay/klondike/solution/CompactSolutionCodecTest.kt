package org.finiteplay.klondike.solution

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.GameStateFixtures
import org.finiteplay.klondike.board.GameStateFixtures.faceUp
import org.finiteplay.klondike.board.GameStateFixtures.withFoundation
import org.finiteplay.klondike.board.GameStateFixtures.withStock
import org.finiteplay.klondike.board.GameStateFixtures.withTableauColumn
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.rules.applyMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The compact format stores choices and rebuilds the draws from the board, so what has to be
 * checked is the *expansion*: that decoding replays to the same literal line and the same win.
 * Bit-level round-tripping alone would pass while the draws came back wrong.
 */
class CompactSolutionCodecTest {

    /**
     * A board one move short of every ending this format has to encode: the four Kings up,
     * their foundations at Queen, one King left in the stock, and an empty column to move
     * through. Every opcode appears in the line below.
     */
    private fun endgame(seed: Long = 1L): GameState =
        GameStateFixtures.emptyBoard(seed = seed)
            .withTableauColumn(0, listOf(faceUp(Card(Suit.SPADES, Rank.KING))))
            .withTableauColumn(1, listOf(faceUp(Card(Suit.HEARTS, Rank.KING))))
            .withTableauColumn(2, listOf(faceUp(Card(Suit.DIAMONDS, Rank.KING))))
            .withStock(listOf(Card(Suit.CLUBS, Rank.KING)))
            .withFoundation(Suit.SPADES, Rank.QUEEN.value)
            .withFoundation(Suit.HEARTS, Rank.QUEEN.value)
            .withFoundation(Suit.DIAMONDS, Rank.QUEEN.value)
            .withFoundation(Suit.CLUBS, Rank.QUEEN.value)

    /**
     * Every opcode: a withdrawal back onto a King, the run moved to an empty column, the
     * banking that follows, and the King reached by drawing — which is the one that proves
     * the dropped draw comes back.
     */
    private val endgameLine = listOf(
        // Withdraw the queen onto a king and bank it again: the withdrawal has to be undone
        // before its own king can go up, which is what makes this a legal line rather than a
        // sequence of moves that merely looks like one.
        Move.FoundationToTableau(Suit.HEARTS, toColumn = 0),
        Move.TableauToFoundation(fromColumn = 0),
        Move.TableauToTableau(fromColumn = 1, toColumn = 3, fromIndex = 0),
        Move.TableauToFoundation(fromColumn = 3),
        Move.TableauToFoundation(fromColumn = 0),
        Move.TableauToFoundation(fromColumn = 2),
        Move.Draw,
        Move.WasteToFoundation,
    )

    private fun assertWins(deal: GameState, line: List<Move>) {
        var state = deal
        for (move in line) state = applyMove(state, move)
        assertTrue("fixture line must win", state.isWon)
    }

    private fun choicesOf(line: List<Move>) = line.filter { it != Move.Draw && it != Move.Recycle }

    /**
     * What the format promises: the same choices in the same order, replaying to the same win.
     * Not the same literal line — draws are re-inserted at the last moment that still reaches
     * the card, so one that drew early decodes with the draw moved down to its play.
     */
    private fun assertSameSolution(deal: GameState, expected: List<Move>, actual: List<Move>?, label: String = "") {
        assertNotNull("$label decoded to nothing", actual)
        assertEquals(label, choicesOf(expected), choicesOf(actual!!))
        assertWins(deal, actual)
    }

    @Test
    fun `a won line survives encode and decode, draws included`() {
        val deal = endgame()
        assertWins(deal, endgameLine)

        val blob = CompactSolutionCodec.encodeCatalog(mapOf(1L to endgameLine)) { deal }
        val index = CompactSolutionCodec.readIndex(blob)

        assertEquals(1, index.size)
        assertTrue(1L in index)
        assertSameSolution(deal, endgameLine, CompactSolutionCodec.decodeLine(blob, index, deal, 1L))
    }

    @Test
    fun `an uncatalogued seed decodes to null rather than throwing`() {
        val deal = endgame()
        val blob = CompactSolutionCodec.encodeCatalog(mapOf(1L to endgameLine)) { deal }
        val index = CompactSolutionCodec.readIndex(blob)

        assertNull(CompactSolutionCodec.decodeLine(blob, index, deal, 99L))
    }

    /**
     * The seek path is the point of the index: a catalog of tens of thousands must not decode
     * every line to answer for one, and blocks are variable-length, so an offset that drifts
     * by a byte returns a neighbour's line as this seed's.
     */
    @Test
    fun `every entry decodes from its own block in a multi-seed catalog`() {
        val seeds = listOf(1L, 7L, 900L, 900_001L)
        val deals = seeds.associateWith { endgame(it) }
        // Distinct lengths per entry, so a wrong offset cannot accidentally still parse.
        val lines = seeds.associateWith { seed ->
            if (seed % 2 == 0L) endgameLine else listOf(Move.Draw) + endgameLine.dropLast(2) + listOf(Move.WasteToFoundation)
        }
        for (seed in seeds) assertWins(deals.getValue(seed), lines.getValue(seed))

        val blob = CompactSolutionCodec.encodeCatalog(lines) { deals.getValue(it) }
        val index = CompactSolutionCodec.readIndex(blob)

        assertEquals(seeds.size, index.size)
        for (seed in seeds) {
            val decoded = CompactSolutionCodec.decodeLine(blob, index, deals.getValue(seed), seed)
            assertSameSolution(deals.getValue(seed), lines.getValue(seed), decoded, "seed $seed")
        }
    }

    @Test
    fun `a line that does not win is refused rather than stored`() {
        val deal = endgame()

        val blob = CompactSolutionCodec.encodeCatalog(mapOf(1L to endgameLine.dropLast(1))) { deal }

        assertEquals(0, CompactSolutionCodec.readIndex(blob).size)
    }

    @Test
    fun `the compact form is smaller than two bytes per move`() {
        val deal = endgame()

        val compact = CompactSolutionCodec.encodeCatalog(mapOf(1L to endgameLine)) { deal }
        val original = SolutionCodec.encodeCatalog(mapOf(1L to endgameLine))

        assertTrue("compact ${compact.size} vs original ${original.size}", compact.size < original.size)
    }
}
