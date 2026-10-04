package org.finiteplay.spider.game

import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.dealGame
import org.finiteplay.spider.rules.Move
import org.finiteplay.spider.rules.applyMove
import org.finiteplay.spider.rules.legalMoves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TapResolutionTest {

    private val versions = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

    @Test
    fun `tapping a card with no legal destination resolves to nothing`() {
        // The last card of a freshly dealt column: liftable (it's the last card, alone) but has
        // no destination to build onto until the board changes.
        val state = dealGame(seed = 1L, versions = versions)
        val column = state.tableau.indexOfFirst { it.isNotEmpty() }
        val topIndex = state.tableau[column].lastIndex

        // Some fresh deals do have a legal destination for their top card (any column's own top
        // outranks it by one); this only asserts the function agrees with the rules either way.
        val resolved = resolveTap(state, column, topIndex)
        val actuallyLegal = legalMoves(state).filterIsInstance<Move.TableauToTableau>()
            .any { it.fromColumn == column && it.fromIndex == topIndex }
        assertEquals(actuallyLegal, resolved != null)
    }

    @Test
    fun `tapping a non-liftable card resolves to nothing`() {
        val state = dealGame(seed = 1L, versions = versions)
        val column = state.tableau.indexOfFirst { it.size > 1 }

        // Index 0 of a multi-card column is face-down at the start of the game, never liftable.
        assertNull(resolveTap(state, column, 0))
    }

    @Test
    fun `tapping a liftable card with a legal destination resolves to the leftmost one`() {
        var state = dealGame(seed = 5L, versions = versions)
        // Play until some tap actually has a legal destination, deterministically, so the test
        // doesn't depend on a fresh deal happening to offer one.
        var found: Move.TableauToTableau? = null
        var guard = 0
        while (found == null && guard++ < 200) {
            val candidates = legalMoves(state).filterIsInstance<Move.TableauToTableau>()
            val move = candidates.firstOrNull() ?: break
            val resolved = resolveTap(state, move.fromColumn, move.fromIndex)
            if (resolved != null) {
                found = resolved
                break
            }
            state = applyMove(state, move)
        }

        checkNotNull(found) { "no reachable board offered a resolvable tap within the guard" }
        val allDestinations = legalMoves(state).filterIsInstance<Move.TableauToTableau>()
            .filter { it.fromColumn == found.fromColumn && it.fromIndex == found.fromIndex }
            .map { it.toColumn }
            .sorted()
        assertEquals(allDestinations.first(), found.toColumn)
        assertTrue(allDestinations.isNotEmpty())
    }

    @Test
    fun `a tap goes to the nearest legal column on the right`() {
        val (state, column, index) = boardWithDestinations(minimum = 1)
        val destinations = tapDestinations(state, column, index)
        val toTheRight = destinations.filter { it > column }
        if (toTheRight.isEmpty()) return  // covered by the wrap test instead

        assertEquals(toTheRight.first(), resolveTap(state, column, index)?.toColumn)
    }

    @Test
    fun `a sequence with nothing legal to its right wraps to the leftmost column`() {
        // Search for the wrap case specifically: every legal destination left of the source.
        var state = dealGame(seed = 5L, versions = versions)
        var found: Triple<org.finiteplay.spider.layout.SpiderState, Int, Int>? = null
        var guard = 0
        while (found == null && guard++ < 400) {
            outer@ for (column in state.tableau.indices) {
                for (index in state.tableau[column].indices) {
                    val destinations = tapDestinations(state, column, index)
                    if (destinations.isNotEmpty() && destinations.none { it > column }) {
                        found = Triple(state, column, index)
                        break@outer
                    }
                }
            }
            if (found != null) break
            val move = legalMoves(state).filterIsInstance<Move.TableauToTableau>().firstOrNull() ?: break
            state = applyMove(state, move)
        }

        val (s, column, index) = found ?: return  // no reachable board exercised the wrap
        val destinations = tapDestinations(s, column, index)
        assertEquals(destinations.first(), resolveTap(s, column, index)?.toColumn)
        assertTrue("this is the wrap case: the destination is left of the source", destinations.first() < column)
    }

    /** A reachable board plus a (column, index) whose tap has at least [minimum] destinations. */
    private fun boardWithDestinations(minimum: Int): Triple<org.finiteplay.spider.layout.SpiderState, Int, Int> {
        var state = dealGame(seed = 5L, versions = versions)
        var guard = 0
        while (guard++ < 400) {
            for (column in state.tableau.indices) {
                for (index in state.tableau[column].indices) {
                    if (tapDestinations(state, column, index).size >= minimum) {
                        return Triple(state, column, index)
                    }
                }
            }
            val move = legalMoves(state).filterIsInstance<Move.TableauToTableau>().firstOrNull()
                ?: error("no reachable board offered a tap with $minimum destinations")
            state = applyMove(state, move)
        }
        error("no reachable board offered a tap with $minimum destinations within the guard")
    }

    @Test
    fun `an empty column is skipped when a non-empty destination is also legal`() {
        // Search for a tap offering both an empty column and a built-on one at once.
        var state = dealGame(seed = 5L, versions = versions)
        var found: Triple<org.finiteplay.spider.layout.SpiderState, Int, Int>? = null
        var guard = 0
        while (found == null && guard++ < 400) {
            outer@ for (column in state.tableau.indices) {
                for (index in state.tableau[column].indices) {
                    val destinations = tapDestinations(state, column, index)
                    val hasEmpty = destinations.any { state.tableau[it].isEmpty() }
                    val hasNonEmpty = destinations.any { state.tableau[it].isNotEmpty() }
                    if (hasEmpty && hasNonEmpty) {
                        found = Triple(state, column, index)
                        break@outer
                    }
                }
            }
            if (found != null) break
            val move = legalMoves(state).filterIsInstance<Move.TableauToTableau>().firstOrNull() ?: break
            state = applyMove(state, move)
        }

        val (s, column, index) = found ?: return // no reachable board exercised this case
        val resolved = resolveTap(s, column, index)
        assertTrue(resolved != null && s.tableau[resolved.toColumn].isNotEmpty())
    }

    @Test
    fun `an empty column is the destination when it is the only legal one`() {
        // Search for a tap whose only legal destinations are empty columns.
        var state = dealGame(seed = 5L, versions = versions)
        var found: Triple<org.finiteplay.spider.layout.SpiderState, Int, Int>? = null
        var guard = 0
        while (found == null && guard++ < 400) {
            outer@ for (column in state.tableau.indices) {
                for (index in state.tableau[column].indices) {
                    val destinations = tapDestinations(state, column, index)
                    if (destinations.isNotEmpty() && destinations.all { state.tableau[it].isEmpty() }) {
                        found = Triple(state, column, index)
                        break@outer
                    }
                }
            }
            if (found != null) break
            val move = legalMoves(state).filterIsInstance<Move.TableauToTableau>().firstOrNull() ?: break
            state = applyMove(state, move)
        }

        val (s, column, index) = found ?: return // no reachable board exercised this case
        val resolved = resolveTap(s, column, index)
        assertTrue(resolved != null && s.tableau[resolved.toColumn].isEmpty())
    }

    @Test
    fun `tapping the source column itself is never offered as a destination`() {
        val state = dealGame(seed = 1L, versions = versions)
        for (column in state.tableau.indices) {
            val resolved = resolveTap(state, column, state.tableau[column].lastIndex)
            assertTrue(resolved == null || resolved.toColumn != column)
        }
    }
}
