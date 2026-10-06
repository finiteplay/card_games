package org.finiteplay.klondike.deal

import kotlin.random.Random

/**
 * Supplies the seed for a new deal. Production draw-one games use the validated D1b
 * catalog; the interim sources below remain for unit fixtures.
 */
fun interface DealSeedSource {
    fun nextSeed(): Long
}

/** Fully random 64-bit seed, not guaranteed solvable. Unused by default; kept for tests. */
val RandomDealSeedSource: DealSeedSource = DealSeedSource { Random.nextLong() }

/**
 * Legacy seed source for tests: steps through [INTERIM_SOLVABLE_SEEDS] in order —
 * deal 1, 2, 3, ... — wrapping back to the first seed once the last has been dealt,
 * instead of a fully random value, so the MVP deals only games verified solvable by
 * the D1s solver under both automation settings, in a stable, repeatable sequence.
 * See that list's doc comment for why this is not the D1b certified catalog.
 *
 * [startIndex] resumes a previously reached position instead of always starting at
 * deal 1 — `GameViewModel` loads it from `CatalogTraversalStore` so a killed-and-
 * restarted app (not just backgrounded) continues the sequence where it left off.
 * A fresh instance per `GameViewModel`, not a shared singleton: sharing one cursor
 * across instances would let unrelated `GameViewModel`s (or, in tests, unrelated
 * test cases) silently advance each other's position.
 */
class InterimSolvableDealSeedSource(startIndex: Int = 0) : DealSeedSource {
    private var nextIndex = startIndex.mod(INTERIM_SOLVABLE_SEEDS.size)

    /** The index [nextSeed] will hand out next, for persisting position across restarts. */
    val currentIndex: Int get() = nextIndex

    override fun nextSeed(): Long {
        val seed = INTERIM_SOLVABLE_SEEDS[nextIndex]
        nextIndex = (nextIndex + 1) % INTERIM_SOLVABLE_SEEDS.size
        return seed
    }
}

/**
 * Steps through one difficulty's seeds in order, wrapping at the end — the source
 * behind Settings' difficulty selection (`docs/games/klondike/UI_SPEC.md`). [difficulty] is fixed for
 * the life of this instance; changing the setting builds a new one, so each level keeps
 * its own place in its own list rather than sharing a single cursor.
 *
 * A separate persisted position per difficulty is deliberate: switching to Hard for a
 * few games and back should resume the Easy sequence where it was left, not restart it
 * or skip ahead by however many Hard games were played in between.
 */
class DifficultyDealSeedSource(
    val difficulty: DifficultyTier,
    private val seeds: List<Long> = seedsFor(difficulty),
    startIndex: Int = 0,
) : DealSeedSource {
    private var nextIndex = startIndex.mod(seeds.size)

    /** The index [nextSeed] will hand out next, for persisting position across restarts. */
    val currentIndex: Int get() = nextIndex

    override fun nextSeed(): Long {
        val seed = seeds[nextIndex]
        nextIndex = (nextIndex + 1) % seeds.size
        return seed
    }

    /**
     * Takes back the deal just handed out, if [seed] is it: the cursor steps back so the next
     * [nextSeed] returns [seed] again. For a deal that was dealt and abandoned without a single
     * move, so leaving it does not use it up. A no-op when [seed] is not the last one handed out
     * (a deal picked by hand, or one that other deals have followed).
     */
    fun giveBack(seed: Long) {
        val last = (nextIndex - 1).mod(seeds.size)
        if (seeds[last] == seed) nextIndex = last
    }
}

/**
 * Picks a difficulty uniformly at random for each deal, then takes that level's next
 * seed — the "Random" difficulty setting. Every level keeps advancing its own cursor,
 * so a player who later selects a specific difficulty resumes it rather than finding it
 * untouched or arbitrarily far along.
 */
class RandomDifficultyDealSeedSource(
    private val sourceFor: (DifficultyTier) -> DealSeedSource,
    private val random: Random = Random.Default,
) : DealSeedSource {
    override fun nextSeed(): Long = sourceFor(DifficultyTier.entries.random(random)).nextSeed()
}
