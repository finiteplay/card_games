package org.finiteplay.klondike.solution

import org.finiteplay.klondike.board.TABLEAU_COLUMNS
import org.finiteplay.cards.Suit
import org.finiteplay.klondike.rules.Move
import org.junit.Assert.assertEquals
import org.junit.Test

class SolutionCodecTest {

    /**
     * Every move shape at every operand extreme, not a hand-picked sample: a packing bug
     * that only bites at column 6 or run index 18 would otherwise ship silently inside
     * four thousand solutions and surface as a hint suggesting an illegal move.
     */
    @Test
    fun `every move round-trips through the packed encoding`() {
        val moves = buildList {
            add(Move.Draw)
            add(Move.Recycle)
            add(Move.WasteToFoundation)
            for (column in 0 until TABLEAU_COLUMNS) {
                add(Move.TableauToFoundation(column))
                add(Move.WasteToTableau(column))
                for (suit in Suit.entries) add(Move.FoundationToTableau(suit, column))
                for (other in 0 until TABLEAU_COLUMNS) {
                    if (other == column) continue
                    // 0 and 18 are the extremes of a run start index: a tableau column
                    // holds at most 19 cards (6 face-down dealt plus a full K-to-A run).
                    for (fromIndex in listOf(0, 1, 18)) {
                        add(Move.TableauToTableau(fromColumn = column, fromIndex = fromIndex, toColumn = other))
                    }
                }
            }
        }

        for (move in moves) {
            assertEquals(move, SolutionCodec.decodeMove(SolutionCodec.encodeMove(move)))
        }
    }

    @Test
    fun `a packed move always fits the two bytes it is written into`() {
        val widest = Move.TableauToTableau(fromColumn = 6, fromIndex = 18, toColumn = 6)

        assertEquals(true, SolutionCodec.encodeMove(widest) <= 0xFFFF)
    }

    @Test
    fun `a catalog round-trips, including an empty solution and the longest allowed one`() {
        val solutions = mapOf(
            7L to listOf(Move.Draw, Move.WasteToFoundation, Move.TableauToTableau(0, 3, 5)),
            2L to emptyList(),
            99L to List(SolutionCodec.MAX_MOVES) { Move.Draw },
        )

        assertEquals(solutions, SolutionCodec.decodeCatalog(SolutionCodec.encodeCatalog(solutions)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a blob without the magic header is rejected rather than silently mis-parsed`() {
        SolutionCodec.decodeCatalog(ByteArray(32))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a truncated catalog is rejected rather than returning the entries it managed to read`() {
        val encoded = SolutionCodec.encodeCatalog(mapOf(1L to List(20) { Move.Draw }))

        SolutionCodec.decodeCatalog(encoded.copyOf(encoded.size - 10))
    }
}
