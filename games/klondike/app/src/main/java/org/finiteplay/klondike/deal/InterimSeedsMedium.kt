package org.finiteplay.klondike.deal

/**
 * Draw-one seeds graded **Medium** (`docs/games/klondike/DIFFICULTY_LEVELS.md`).
 *
 * In presentation order, which is a fixed permutation of the order the level was
 * ranked and cut in: ranking decides *which* ten thousand deals ship, and shipping
 * them in that order would open every level on its softest deals. The parts exist
 * only to keep each static initializer under the JVM's 64 KB limit.
 *
 * Regenerate with `tools/catalog`'s `grade-seeds`, `grade-search`, then `build-levels`.
 */
internal val INTERIM_SEEDS_MEDIUM: List<Long> = INTERIM_SEEDS_MEDIUM_A + INTERIM_SEEDS_MEDIUM_B + INTERIM_SEEDS_MEDIUM_C + INTERIM_SEEDS_MEDIUM_D
