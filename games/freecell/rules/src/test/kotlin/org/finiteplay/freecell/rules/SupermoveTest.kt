package org.finiteplay.freecell.rules

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.freecell.layout.FREE_CELLS
import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.freecell.layout.GameStatus
import org.finiteplay.freecell.layout.GameVersions
import org.finiteplay.freecell.layout.TABLEAU_COLUMNS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Generated coverage for `RULES.md` "Supermove": every combination of free cells, empty
 * "waypoint" columns, and whether the destination itself is empty, crossed with a lifted length
 * at, below, and past the legal maximum. `CASE_COUNT` is committed so silently shrinking this
 * coverage fails, the same discipline `docs/games/klondike/EXECUTION_PLAN.md` E1 states for its
 * own transition matrix.
 *
 * A regression test for the reviewed error this formula once had: with no free cells, one empty
 * waypoint column, and a separate empty destination, two cards must be movable — the destination
 * does not double-count as a second waypoint.
 */
class SupermoveTest {

    private val versions = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

    /** A descending, alternating-colour, eight-card sequence: King of spades down to six of hearts. */
    private val fullSequence = listOf(
        Card(Suit.SPADES, Rank.KING),
        Card(Suit.DIAMONDS, Rank.QUEEN),
        Card(Suit.CLUBS, Rank.JACK),
        Card(Suit.HEARTS, Rank.TEN),
        Card(Suit.SPADES, Rank.NINE),
        Card(Suit.DIAMONDS, Rank.EIGHT),
        Card(Suit.CLUBS, Rank.SEVEN),
        Card(Suit.HEARTS, Rank.SIX),
    )

    private val sourceColumn = 0
    private val destinationColumn = 1

    /**
     * Builds a synthetic board for one matrix cell. Not a reachable real deal — filler cards
     * repeat across free cells and "other" columns — this only exercises [maxSupermoveLength]
     * and [isLegal]'s agreement with it, neither of which checks card uniqueness.
     */
    private fun fixture(freeCellsUsed: Int, emptyOtherColumns: Int, destinationLanding: Card?): FreeCellState {
        val otherColumns = TABLEAU_COLUMNS - 2
        require(emptyOtherColumns in 0..otherColumns)

        val tableau = MutableList(TABLEAU_COLUMNS) { emptyList<Card>() }
        tableau[sourceColumn] = fullSequence
        tableau[destinationColumn] = if (destinationLanding == null) emptyList() else listOf(destinationLanding)

        var toFill = otherColumns - emptyOtherColumns
        for (index in tableau.indices) {
            if (index == sourceColumn || index == destinationColumn) continue
            if (toFill <= 0) break
            tableau[index] = listOf(Card(Suit.SPADES, Rank.TWO))
            toFill--
        }

        val freeCells = List(FREE_CELLS) { index -> if (index < freeCellsUsed) Card(Suit.HEARTS, Rank.TWO) else null }

        return FreeCellState(
            seed = 0,
            versions = versions,
            tableau = tableau,
            freeCells = freeCells,
            foundations = Suit.entries.associateWith { 0 },
            status = GameStatus.IN_PROGRESS,
        )
    }

    @Test
    fun `the formula matches its definition and isLegal agrees with it at and past the maximum`() {
        var cases = 0
        for (freeCellsUsed in 0..FREE_CELLS) {
            val free = FREE_CELLS - freeCellsUsed
            for (emptyOtherColumns in 0..(TABLEAU_COLUMNS - 2)) {
                for (destinationEmpty in listOf(true, false)) {
                    // A non-empty landing needs a card above fromIndex in the sequence to copy,
                    // so it only applies once the lift leaves at least one card behind.
                    val maxLiftedLength = if (destinationEmpty) fullSequence.size else fullSequence.size - 1
                    for (liftedLength in 1..maxLiftedLength) {
                        cases++
                        val fromIndex = fullSequence.size - liftedLength
                        val landing = if (destinationEmpty) null else fullSequence[fromIndex - 1]
                        val state = fixture(freeCellsUsed, emptyOtherColumns, landing)

                        val expectedMax = (free + 1) * (1 shl emptyOtherColumns)
                        assertEquals(
                            "free=$free empty=$emptyOtherColumns destinationEmpty=$destinationEmpty",
                            expectedMax,
                            maxSupermoveLength(state, destinationColumn),
                        )

                        val move = Move.TableauToTableau(sourceColumn, fromIndex, destinationColumn)
                        assertEquals(
                            "length=$liftedLength max=$expectedMax free=$free empty=$emptyOtherColumns " +
                                "destinationEmpty=$destinationEmpty",
                            liftedLength <= expectedMax,
                            isLegal(state, move),
                        )
                    }
                }
            }
        }
        assertEquals(CASE_COUNT, cases)
    }

    @Test
    fun `no free cells, one empty waypoint, and a separate empty destination move two cards`() {
        // The regression case: empty already excludes the destination, so nothing more is
        // subtracted for it. free=0, one waypoint column empty (not the destination) => max 2.
        val state = fixture(freeCellsUsed = FREE_CELLS, emptyOtherColumns = 1, destinationLanding = null)

        assertEquals(2, maxSupermoveLength(state, destinationColumn))
        assertTrue(isLegal(state, Move.TableauToTableau(sourceColumn, fullSequence.size - 2, destinationColumn)))
        assertFalse(isLegal(state, Move.TableauToTableau(sourceColumn, fullSequence.size - 3, destinationColumn)))
    }

    companion object {
        /** 5 free-cell counts × 7 empty-column counts × (8 lengths empty + 7 lengths non-empty). */
        private const val CASE_COUNT = 5 * 7 * (8 + 7)
    }
}
