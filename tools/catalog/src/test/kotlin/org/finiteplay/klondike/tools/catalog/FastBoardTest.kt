package org.finiteplay.klondike.tools.catalog

import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.rules.applyMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gates [FastBoard] against the `GameState` generator it replaces.
 *
 * Equivalence has to be checked on **moves, their order, and the board each one leaves**.
 * Three earlier attempts at speeding up pile handling each produced a different wrong
 * answer, and two of them emitted identical move *sets* — a set comparison would have
 * passed all three. Order matters because the reference line is the first tap of the
 * highest-priority kind; the resulting board matters because the stock/waste boundary
 * decides what is playable next.
 */
class FastBoardTest {

    private fun assertDestinationMasks(message: String, board: FastBoard) {
        for (card in 0 until FastBoard.DECK) {
            var expected = 0
            for (column in 0 until 7) {
                val size = board.columnSize[column]
                val accepts = if (size == 0) {
                    FastBoard.rankOf(card) == FastBoard.RANKS
                } else {
                    val under = board.columnCards[column][size - 1]
                    FastBoard.rankOf(card) == FastBoard.rankOf(under) - 1 &&
                        FastBoard.isRed(card) != FastBoard.isRed(under)
                }
                if (accepts) expected = expected or (1 shl column)
            }
            if (board.foundations[FastBoard.suitOf(card)] == FastBoard.rankOf(card) - 1) {
                expected = expected or (1 shl 7)
            }
            assertEquals("$message card=$card", expected, board.destinationMask(card))
        }
    }

    private fun describe(move: Move): String = when (move) {
        is Move.TableauToFoundation -> "T${move.fromColumn}>F"
        is Move.TableauToTableau -> "T${move.fromColumn}.${move.fromIndex}>T${move.toColumn}"
        Move.WasteToFoundation -> "W>F"
        is Move.WasteToTableau -> "W>T${move.toColumn}"
        else -> "other($move)"
    }

    private fun describe(board: FastBoard, packed: Int): String = when (FastBoard.kindOf(packed)) {
        FastBoard.KIND_TABLEAU_FOUNDATION -> "T${FastBoard.fieldA(packed)}>F"
        FastBoard.KIND_TABLEAU_TABLEAU ->
            "T${FastBoard.fieldA(packed)}.${FastBoard.fieldB(packed)}>T${FastBoard.fieldC(packed)}"
        FastBoard.KIND_WASTE_FOUNDATION -> "W>F"
        else -> "W>T${FastBoard.fieldC(packed)}"
    }

    private fun fastTapDescriptions(state: GameState): List<String> {
        val board = FastBoard().apply { loadFrom(state) }
        val buffer = IntArray(64)
        val count = board.generateTaps(buffer)
        return (0 until count).map { describe(board, buffer[it]) }
    }

    @Test
    fun `generates the same taps, in the same order, as the GameState generator`() {
        var compared = 0
        var withWaste = 0
        for (seed in listOf(1L, 2L, 530L, 835L, 2067L, 2441L, 1386L, 2782L, 9999L)) {
            var state = dealGame(seed, D1S_SPIKE_VERSIONS)
            repeat(140) { step ->
                repeat(step % 5) { state = advancePile(state) }
                if (state.waste.isNotEmpty()) withWaste++
                assertEquals(
                    "seed $seed step $step",
                    obviousTapDescriptionsForTest(state),
                    fastTapDescriptions(state),
                )
                compared++
                state = obviousSuccessorForTest(state) ?: return@repeat
            }
        }
        assertTrue("expected many comparisons, made $compared", compared > 400)
        assertTrue("never saw a non-empty waste, so the pile split was untested", withWaste > 100)
    }

    /**
     * Applying a tap must leave [FastBoard] describing the same board the reducer produces —
     * including the pile split, which is what decides the next playable card and is exactly
     * what a closed-form rotation got wrong.
     */
    @Test
    fun `make reproduces the reducer's board, and unmake restores it exactly`() {
        var checked = 0
        for (seed in listOf(1L, 2L, 530L, 835L, 2441L)) {
            var state = dealGame(seed, D1S_SPIKE_VERSIONS)
            repeat(90) { step ->
                repeat(step % 4) { state = advancePile(state) }
                val board = FastBoard().apply { loadFrom(state) }
                val before = board.fingerprint()
                val buffer = IntArray(64)
                val count = board.generateTaps(buffer)
                val expected = obviousSuccessorFingerprintsForTest(state)
                assertEquals("seed $seed step $step tap count", expected.size, count)

                for (index in 0 until count) {
                    board.make(buffer[index])
                    assertEquals("seed $seed step $step tap $index", expected[index], board.fingerprint())
                    board.unmake()
                    assertEquals("unmake must restore the board exactly", before, board.fingerprint())
                    checked++
                }
                state = obviousSuccessorForTest(state) ?: return@repeat
            }
        }
        assertTrue("expected many make/unmake checks, made $checked", checked > 400)
    }

    /**
     * Distinct boards must not share a fingerprint. Written after XOR-combining per-column
     * signatures proved wrong: identical signatures cancel under XOR, and every **empty**
     * column shares one, so a board with two empty columns collided with one having none —
     * the single most consequential difference a Klondike position can have. The differential
     * tests could not see it, because both sides of the comparison used the same hash.
     */
    @Test
    fun `fingerprints distinguish boards differing only in empty columns`() {
        val seen = HashMap<Long, String>()
        var collisions = 0
        var checked = 0
        for (seed in listOf(1L, 2L, 530L, 835L, 2067L, 2441L, 467151L)) {
            var state = dealGame(seed, D1S_SPIKE_VERSIONS)
            repeat(120) {
                val board = FastBoard().apply { loadFrom(state) }
                // Describe the board exactly, so any two descriptions that differ are
                // genuinely different positions.
                val description = (0 until 7).joinToString("|") { column ->
                    (0 until board.columnSize[column]).joinToString(",") { index ->
                        "${board.columnCards[column][index]}${if (index < board.columnDown[column]) "d" else "u"}"
                    }
                }.let { columns -> columns + "#" + board.foundations.joinToString(",") }
                val fingerprint = board.fingerprint()
                val previous = seen.put(fingerprint, description)
                if (previous != null && previous != description) {
                    // Column order is deliberately irrelevant, so only a genuine difference counts.
                    val a = previous.substringBefore("#").split("|").sorted()
                    val b = description.substringBefore("#").split("|").sorted()
                    if (a != b || previous.substringAfter("#") != description.substringAfter("#")) collisions++
                }
                checked++
                state = obviousSuccessorForTest(state) ?: return@repeat
            }
        }
        assertTrue("expected many boards, saw $checked", checked > 300)
        assertEquals("distinct boards sharing a fingerprint", 0, collisions)
    }

    @Test
    fun `an empty column is not cancelled out by another empty column`() {
        // Two boards identical but for how many columns are empty must hash differently.
        val board = FastBoard().apply { loadFrom(dealGame(835L, D1S_SPIKE_VERSIONS)) }
        // resync() because these write the arrays directly rather than playing a move, and
        // the fingerprint is maintained incrementally by make/unmake alone.
        board.columnSize[0] = 0
        board.columnDown[0] = 0
        board.resync()
        val oneEmpty = board.fingerprint()
        board.columnSize[1] = 0
        board.columnDown[1] = 0
        board.resync()
        val twoEmpty = board.fingerprint()
        assertTrue("two empty columns must not hash like one", oneEmpty != twoEmpty)
    }

    /**
     * Emptying a column is an obvious move. A player freeing a column for a King is doing
     * something as natural as turning a card over, and grading that as off-model made the
     * Trivial guarantee stop applying at the first such move — which is exactly what
     * happened on seed 106095, at step 9, in real play.
     */
    @Test
    fun `moving a whole face-up column is offered as an obvious tap`() {
        // Seed 106095 step 9: T0 leaves entirely for T3, emptying T0.
        var state = dealGame(106095L, D1S_SPIKE_VERSIONS)
        val prefix = listOf(
            Move.TableauToTableau(1, 1, 3),
            Move.TableauToTableau(2, 2, 0),
            Move.TableauToTableau(2, 1, 4),
            Move.Draw, Move.Draw,
            Move.WasteToTableau(1),
            Move.Draw,
            Move.TableauToTableau(3, 3, 1),
        )
        for (move in prefix) state = applyMove(state, move)

        val offered = fastTapDescriptions(state)
        assertTrue(
            "a column-emptying move must be offered; taps were $offered",
            offered.any { it.startsWith("T0.0>") },
        )
        assertEquals("both generators must agree", obviousTapDescriptionsForTest(state), offered)
    }

    @Test
    fun `pile index mapping walks every card exactly once`() {
        for (seed in listOf(1L, 835L)) {
            var state = dealGame(seed, D1S_SPIKE_VERSIONS)
            repeat(30) {
                val board = FastBoard().apply { loadFrom(state) }
                val visited = (0 until board.pileSize).map { board.pileIndexAt(it) }
                assertEquals("each pile slot visited once", (0 until board.pileSize).toSet(), visited.toSet())
                assertEquals("no duplicates", board.pileSize, visited.size)
                state = advancePile(state)
            }
        }
    }

    /**
     * The fingerprint is maintained incrementally — a move refreshes only the columns it
     * touched — so every mutation path has to keep the running sum current. A missed refresh
     * does not crash and does not fail any move-generation test: it silently merges two
     * different boards into one memo entry, which makes every search report a state space
     * smaller than the real one and every "no win exists" answer unsound.
     *
     * So this asserts the incremental value against a from-scratch recomputation after every
     * single make **and** every unmake, across every move kind the rulesets can produce —
     * including Expert's withdrawal, which is the only move that puts a card back.
     */
    @Test
    fun `the incremental fingerprint matches a full recomputation after every make and unmake`() {
        val taps = IntArray(160)
        var makes = 0
        var withdrawals = 0
        for (seed in 1L..40L) {
            var state = dealGame(seed, D1S_SPIKE_VERSIONS)
            repeat(60) {
                val board = FastBoard().apply { loadFrom(state) }
                assertEquals("seed $seed after load", board.fingerprintFromScratch(), board.fingerprint())
                assertDestinationMasks("seed $seed after load", board)
                val count = board.generateTaps(taps, Ruleset.HARD)
                for (index in 0 until count) {
                    board.make(taps[index])
                    assertEquals(
                        "seed $seed after make of kind ${FastBoard.kindOf(taps[index])}",
                        board.fingerprintFromScratch(),
                        board.fingerprint(),
                    )
                    assertDestinationMasks("seed $seed after make of kind ${FastBoard.kindOf(taps[index])}", board)
                    if (FastBoard.tapOf(taps[index]) == FastBoard.TAP_WITHDRAW) withdrawals++
                    makes++
                    board.unmake()
                    assertEquals(
                        "seed $seed after unmake of kind ${FastBoard.kindOf(taps[index])}",
                        board.fingerprintFromScratch(),
                        board.fingerprint(),
                    )
                    assertDestinationMasks("seed $seed after unmake of kind ${FastBoard.kindOf(taps[index])}", board)
                }
                state = obviousSuccessorForTest(state) ?: return@repeat
            }
        }
        assertTrue("no moves were made, so this proves nothing", makes > 2000)
        assertTrue("no withdrawal was exercised, so the one cycling move is untested", withdrawals > 0)
    }
}
