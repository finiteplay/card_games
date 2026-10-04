package org.finiteplay.klondike.tools.catalog

import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.dealGame

/**
 * Walks each seed down the Trivial reference line, handing every board it passes through to
 * [check]. A cheap way to get a few thousand *realistic* mid-game positions for the ranking
 * tests, rather than judging a priority order from the deal alone — where the foundations are
 * empty and half of every tier's rules cannot fire.
 */
internal fun forEachBoard(seeds: LongRange, steps: Int = 80, check: (GameState, FastBoard) -> Unit) {
    for (seed in seeds) {
        var state: GameState? = dealGame(seed, D1S_SPIKE_VERSIONS)
        var taken = 0
        while (state != null && taken++ < steps) {
            val board = FastBoard().apply { loadFrom(state!!) }
            check(state!!, board)
            state = obviousSuccessorForTest(state!!)
        }
    }
}
