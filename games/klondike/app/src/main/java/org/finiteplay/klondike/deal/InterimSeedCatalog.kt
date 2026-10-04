package org.finiteplay.klondike.deal

import org.finiteplay.klondike.storage.DifficultyPreference

/**
 * Human-facing difficulty, shown in the status row as e.g. "Medium #5", draw-one only.
 *
 * [EXPERT] is the level no strategy ruleset (`docs/games/klondike/DIFFICULTY_LEVELS.md`) can
 * play out and exactly one of the two searches wins, so its deals ship with that search's
 * certificate rather than a ruleset's line.
 *
 * [INSANE] is not "unsolvable" and not "solvable" either: it is the level neither search
 * resolved inside its budget, so those deals ship **uncertified**, with no line at all
 * (`DIFFICULTY_LEVELS.md` "Insane ships uncertified"). A deal a search *proved* unwinnable is
 * never catalogued anywhere.
 */
enum class DifficultyTier { TRIVIAL, EASY, MEDIUM, HARD, EXPERT, INSANE }

/**
 * Legacy Kotlin seed lists, up to 10,000 seeds per difficulty.
 * (`docs/games/klondike/DEALS.md`, "Interim Pre-RF Seed Source"), cut from a ten-million-seed
 * grading pass. Outside [DifficultyTier.INSANE], membership of one of these lists is
 * simultaneously a seed's grade and the proof it is solvable.
 *
 * This is not the D1b certified catalog: production validates and reads the binary assets.
 * The lists stay available only to keep unit fixtures independent of Android assets.
 */
internal val INTERIM_SEEDS_BY_DIFFICULTY: Map<DifficultyTier, List<Long>> = mapOf(
    DifficultyTier.TRIVIAL to INTERIM_SEEDS_TRIVIAL,
    DifficultyTier.EASY to INTERIM_SEEDS_EASY,
    DifficultyTier.MEDIUM to INTERIM_SEEDS_MEDIUM,
    DifficultyTier.HARD to INTERIM_SEEDS_HARD,
    DifficultyTier.EXPERT to INTERIM_SEEDS_EXPERT,
    DifficultyTier.INSANE to INTERIM_SEEDS_INSANE,
)

/**
 * Seed to the difficulty it was catalogued under, built at runtime rather than written
 * out as a 4,000-entry literal: a map that large in source would blow the JVM's 64 KB
 * static-initializer limit, and it costs one pass over four lists to assemble.
 */
internal val INTERIM_SEED_GRADES: Map<Long, DifficultyTier> by lazy {
    buildMap(INTERIM_SEEDS_BY_DIFFICULTY.values.sumOf { it.size }) {
        for ((difficulty, seeds) in INTERIM_SEEDS_BY_DIFFICULTY) for (seed in seeds) put(seed, difficulty)
    }
}

/** Every catalogued seed, easiest difficulty first — the order [InterimSolvableDealSeedSource] walks when no single difficulty is selected. */
internal val INTERIM_SOLVABLE_SEEDS: List<Long> = DifficultyTier.entries.flatMap { INTERIM_SEEDS_BY_DIFFICULTY.getValue(it) }

/** The seed list a given preference draws from; [DifficultyPreference.RANDOM] has none of its own and picks a level per deal instead. */
internal fun seedsFor(difficulty: DifficultyTier): List<Long> = INTERIM_SEEDS_BY_DIFFICULTY.getValue(difficulty)

/** Maps the persisted setting onto a catalogued difficulty, or null for [DifficultyPreference.RANDOM]. */
internal fun DifficultyPreference.toTier(): DifficultyTier? = when (this) {
    DifficultyPreference.TRIVIAL -> DifficultyTier.TRIVIAL
    DifficultyPreference.EASY -> DifficultyTier.EASY
    DifficultyPreference.MEDIUM -> DifficultyTier.MEDIUM
    DifficultyPreference.HARD -> DifficultyTier.HARD
    DifficultyPreference.EXPERT -> DifficultyTier.EXPERT
    DifficultyPreference.INSANE -> DifficultyTier.INSANE
    DifficultyPreference.RANDOM -> null
}
