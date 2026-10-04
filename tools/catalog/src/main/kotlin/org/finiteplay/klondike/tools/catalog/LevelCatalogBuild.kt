package org.finiteplay.klondike.tools.catalog

import java.io.File
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.solution.CompactSolutionCodec

/**
 * Builds all six shipped levels from the grading files, in one step, so the catalog cannot be
 * inconsistent with the grades it came from.
 *
 * The grading files are the input and the only input: `SeedGrading.kt` measured every deal and
 * `SeedSearchStage.kt` searched the ones no ruleset wins, and this reads what they wrote. A
 * level can therefore be recut — a different quota, a different priority, a different mapping
 * from grade to level — without regrading a single seed.
 *
 * Three of the four invariants `TrivialTierBuild.kt` introduced still hold here, by
 * construction: a seed is graded once so it cannot appear in two levels, every ruleset level's
 * grade *is* the proof its own order wins, and every shipped line is replay-verified before it
 * is encoded. The fourth — every shipped seed has a winning line — now has one deliberate
 * exception: **Insane ships uncertified**, because that level is defined as the deals neither
 * search resolved. See `DIFFICULTY_LEVELS.md` "Insane ships uncertified".
 */
fun buildLevelCatalogs(
    rulesetGradesPath: String = "tools/catalog/data/grading/ruleset_grades.csv",
    searchGradesPath: String = "tools/catalog/data/grading/search_grades.csv",
    quota: Int = 10_000,
    dealDir: String = "games/klondike/app/src/main/java/org/finiteplay/klondike/deal",
    assetPath: String = "games/klondike/app/src/main/assets/solutions.bin",
    perChunk: Int = 2_500,
    log: (String) -> Unit = ::println,
) {
    // Checked before anything is written. Run from the wrong working directory this read no
    // grades, reported "0 of 0 candidates" for all six levels, and wrote exactly that over a
    // working catalog. No grades means a broken run, never an empty catalog.
    val rulesetGradeFile = gradingFile(rulesetGradesPath)
    val searchGradeFile = gradingFile(searchGradesPath)
    check(rulesetGradeFile.exists()) { "no ruleset grading file at $rulesetGradesPath (from ${File("").absolutePath})" }
    check(searchGradeFile.exists()) { "no search grading file at $searchGradesPath (from ${File("").absolutePath})" }

    val rulesetGrades = parseSeedGrades(rulesetGradeFile)
    val searched = readSearchedSeeds(searchGradeFile)
    check(rulesetGrades.isNotEmpty()) { "read no grades from $rulesetGradesPath" }
    check(searched.isNotEmpty()) { "read no searched deals from $searchGradesPath" }
    log("read ${rulesetGrades.size} ruleset grades and ${searched.size} searched deals")

    val candidates = LinkedHashMap<CatalogLevel, List<SeedGradeRecord>>()
    for (level in listOf(CatalogLevel.TRIVIAL, CatalogLevel.EASY, CatalogLevel.MEDIUM, CatalogLevel.HARD)) {
        candidates[level] = rulesetGrades.filter { it.level == level }
            .sortedWith(compareByDescending<SeedGradeRecord> { priorityOf(it) }.thenBy { it.seed })
    }
    // The searched levels rank by the length of the line the search found: a longer win is more
    // of a game, and it is the only measure of the board these two levels have in common.
    candidates[CatalogLevel.EXPERT] = searched
        .filter { it.verdict == SearchVerdict.EXPERT_SPLIT && it.solution.isNotEmpty() }
        .sortedWith(compareByDescending<SearchedSeed> { it.moves }.thenBy { it.seed })
        .map { it.asGrade(CatalogLevel.EXPERT) }
    candidates[CatalogLevel.INSANE] = searched
        .filter { it.verdict == SearchVerdict.INSANE_UNRESOLVED }
        .sortedBy { it.seed }
        .map { it.asGrade(CatalogLevel.INSANE) }

    val storedLines = searched.associate { it.seed to it.solution }
    val shipped = LinkedHashMap<CatalogLevel, List<Long>>()
    val solutions = LinkedHashMap<Long, List<Move>>()
    val claimed = HashSet<Long>()

    for ((level, pool) in candidates) {
        val taken = ArrayList<Long>(quota)
        var rejected = 0
        var feltEasier = 0
        for (candidate in pool) {
            if (taken.size >= quota) break
            if (!claimed.add(candidate.seed)) continue
            if (level == CatalogLevel.INSANE) {
                // Nothing to verify: this level exists precisely because no search resolved
                // these deals, so there is no line to replay and none is shipped.
                taken += candidate.seed
                continue
            }
            // A level has to be beyond the one below it for a *player*, not merely beyond one
            // fixed priority order. `gradeOneSeed` asks whether the lower ruleset's undeviating
            // line wins; a player offered two obvious moves picks one and backs up if it fails,
            // so if any order of the lower tier's moves wins, the deal plays as that tier
            // whatever the label says. Measured on deals rated in play, every Easy deal that
            // failed this check was rated Trivial by the player.
            if (level in searchGatedLevels && lowerTierSearchWins(candidate.seed, level)) {
                feltEasier++
                claimed.remove(candidate.seed)
                continue
            }
            val line = lineFor(candidate, storedLines)
            if (line == null || !replayWinsFromDeal(candidate.seed, line)) {
                rejected++
                claimed.remove(candidate.seed)
                continue
            }
            solutions[candidate.seed] = line
            taken += candidate.seed
        }
        shipped[level] = presentationOrder(level, taken)
        log(
            "$level: ${taken.size} of ${pool.size} candidates" +
                (if (feltEasier > 0) ", $feltEasier dropped as winnable by a lower tier's own moves" else "") +
                (if (rejected > 0) ", $rejected dropped for having no verified line" else "") +
                (if (taken.size < quota) " — SHORT of the $quota quota" else ""),
        )
    }

    for ((level, seeds) in shipped) writeLevelSeeds(level, seeds, dealDir, perChunk, log)

    val blob = CompactSolutionCodec.encodeCatalog(solutions) { seed -> dealGame(seed, D1S_SPIKE_VERSIONS) }
    File(assetPath).also { it.parentFile?.mkdirs() }.writeBytes(blob)
    val index = CompactSolutionCodec.readIndex(blob)
    log("")
    log("wrote ${index.size} solutions (${blob.size / 1024} KiB) to $assetPath")

    // --- audit, on what was actually written -------------------------------------------
    val exported = CatalogLevel.entries.associateWith { readExportedSeeds(dealDir, it) }
    val total = exported.values.sumOf { it.size }
    val duplicated = exported.values.flatten().groupingBy { it }.eachCount().count { it.value > 1 }
    val missingSolution = exported.filterKeys { it != CatalogLevel.INSANE }
        .values.flatten().count { it !in index }
    log("audit: $total seeds exported, $duplicated in more than one level, $missingSolution without a solution")
    check(duplicated == 0) { "no seed may appear in two levels" }
    check(missingSolution == 0) { "every shipped seed outside Insane must have a verified winning line" }
    log("all catalog invariants hold")
}

/**
 * Rewrites the shipped seed lists in presentation order, selecting nothing.
 *
 * [buildLevelCatalogs] produces the same result, but it re-runs the gate — one strategy search
 * per candidate — to arrive at a membership that a reorder does not change. This applies the
 * same permutation to the lists already exported, which is identical by construction because
 * `presentationOrder` is a pure function of the level and the selection order, and the exported
 * list *is* the selection order. `solutions.bin` is keyed by seed, so it needs nothing.
 *
 * Only valid while membership is unchanged. Anything that re-cuts a level goes through
 * [buildLevelCatalogs].
 */
fun reorderLevelCatalogs(
    dealDir: String = "games/klondike/app/src/main/java/org/finiteplay/klondike/deal",
    perChunk: Int = 2_500,
    log: (String) -> Unit = ::println,
) {
    for (level in CatalogLevel.entries) {
        // Applying the permutation to an already-permuted list silently produces a third order,
        // so the marker the writer leaves in the header is the interlock: this runs on lists in
        // selection order, once.
        val name = levelFileNames.getValue(level)
        val header = File(dealDir, "InterimSeeds$name.kt").takeIf { it.exists() }?.readText().orEmpty()
        check(!header.contains(PRESENTATION_ORDER_MARKER)) {
            "$name is already in presentation order; rebuild it with build-levels before reordering"
        }
        val selected = readExportedSeeds(dealDir, level)
        check(selected.isNotEmpty()) { "no exported seeds for $level under $dealDir" }
        val ordered = presentationOrder(level, selected)
        check(ordered.toSet() == selected.toSet()) { "$level changed membership, which a reorder must not" }
        writeLevelSeeds(level, ordered, dealDir, perChunk, log)
    }
}

/**
 * The order the player meets a level's deals in, which is deliberately not the order they were
 * chosen in.
 *
 * Selection ranks by how forgiving a deal is - that is how ten thousand are picked out of a
 * million and a half - but shipping them in that order hands the player the ten softest deals
 * in the level first. Reported in play: Easy #5 and #8 both felt like Trivial, and both survive
 * two mistakes where the ten-thousandth Easy deal survives none.
 *
 * A fixed permutation rather than a re-sort, so any run of consecutive deals is a fair sample
 * of the whole level instead of a walk from one end of it to the other. Seeded per level from a
 * constant, and shuffled with `java.util.Random`, whose algorithm the JDK specifies exactly:
 * deal #5 has to be the same deal on every device and every rebuild, which is the deterministic
 * deal contract.
 */
private fun presentationOrder(level: CatalogLevel, seeds: List<Long>): List<Long> {
    val ordered = ArrayList(seeds)
    java.util.Collections.shuffle(ordered, java.util.Random(PRESENTATION_SHUFFLE_SEED + level.ordinal))
    return ordered
}

/** Visible for test: the permutation has to be shown stable, and it is not otherwise reachable. */
internal fun presentationOrderForTest(level: CatalogLevel, seeds: List<Long>): List<Long> = presentationOrder(level, seeds)

/** Written into every level's header, and the interlock that stops a second reorder from running. */
private const val PRESENTATION_ORDER_MARKER = "In presentation order"

/** Fixed so the shuffle is reproducible; changing it renumbers every level and needs a catalog version bump. */
private const val PRESENTATION_SHUFFLE_SEED = 0x5C0FFEEL

/**
 * The levels whose population is defined by a ruleset, and which therefore have to be checked
 * against the ruleset below. Expert and Insane are cut from the searches and have no lower
 * ruleset to be confused with.
 */
private val searchGatedLevels = setOf(CatalogLevel.EASY, CatalogLevel.MEDIUM, CatalogLevel.HARD)

/**
 * Whether some order of the moves available *below* this level wins the deal.
 *
 * `searchStrategyLine` explores every equally ranked choice inside a tier's move set, which is
 * the closest cheap model of a player who picks one of two obvious moves and backs up if it
 * does not work out. Where it wins, the deal belongs to that lower tier in everything but name.
 */
private fun lowerTierSearchWins(seed: Long, level: CatalogLevel): Boolean {
    // One ruleset per ruleset-backed level, so "the tiers below this level" is just the tiers
    // below its own ruleset. This was a hand-written table while the two had drifted apart, and
    // the table is what would go stale next time; `tiersBelow` cannot.
    val below = Ruleset.entries.firstOrNull { levelForRuleset(it) == level }?.tiersBelow.orEmpty()
    return below.any { ruleset ->
        runCatching { searchStrategyLine(seed, ruleset).outcome == StrategySearchOutcome.WON }.getOrDefault(false)
    }
}

/** The line a level ships: a ruleset level replays its own order, a searched level its certificate. */
private fun lineFor(candidate: SeedGradeRecord, storedLines: Map<Long, String>): List<Move>? =
    if (candidate.ruleset != null) {
        tierSolution(candidate.seed, candidate.ruleset)
    } else {
        storedLines[candidate.seed]?.takeIf { it.isNotEmpty() }?.let { decodeLine(it) }
    }

private fun SearchedSeed.asGrade(level: CatalogLevel) = SeedGradeRecord(
    seed = seed,
    level = level,
    ruleset = null,
    moves = moves,
    choices = 0,
    robustness = -1,
    criticalChoices = -1,
    criticalPoints = -1,
    withdrawalNeeded = withdrawalNeeded,
    dfsOutcome = dfsOutcome,
    dfsMoves = dfsMoves,
    astarOutcome = astarOutcome,
    astarMoves = astarMoves,
)

private val levelFileNames = mapOf(
    CatalogLevel.TRIVIAL to "Trivial",
    CatalogLevel.EASY to "Easy",
    CatalogLevel.MEDIUM to "Medium",
    CatalogLevel.HARD to "Hard",
    CatalogLevel.EXPERT to "Expert",
    CatalogLevel.INSANE to "Insane",
)

internal fun readExportedSeeds(dealDir: String, level: CatalogLevel): List<Long> {
    val name = levelFileNames.getValue(level)
    return File(dealDir).listFiles { f -> f.name.matches(Regex("""InterimSeeds$name[A-Z]\.kt""")) }
        ?.sortedBy { it.name }
        ?.flatMap { file ->
            Regex("""^\s+(\d+)L,""", RegexOption.MULTILINE).findAll(file.readText())
                .map { it.groupValues[1].toLong() }.toList()
        }
        ?: emptyList()
}

/**
 * Writes one level's seed list, in the order the level was ranked in.
 *
 * Chunked across files because each Kotlin file compiles its top-level properties into that
 * file's own static initializer, and ten thousand entries in one would risk the JVM's 64 KB
 * method limit.
 */
private fun writeLevelSeeds(
    level: CatalogLevel,
    seeds: List<Long>,
    dealDir: String,
    perChunk: Int,
    log: (String) -> Unit,
) {
    val name = levelFileNames.getValue(level)
    val upper = name.uppercase()
    val directory = File(dealDir).apply { mkdirs() }
    directory.listFiles { f -> f.name.matches(Regex("""InterimSeeds$name[A-Z]\.kt""")) }
        ?.forEach { it.delete() }

    val chunks = seeds.chunked(perChunk)
    val chunkNames = chunks.indices.map { "INTERIM_SEEDS_${upper}_${'A' + it}" }
    chunks.forEachIndexed { index, chunk ->
        File(directory, "InterimSeeds$name${'A' + index}.kt").printWriter().use { out ->
            out.println("package org.finiteplay.klondike.deal")
            out.println()
            out.println("/**")
            out.println(" * $name seeds, part ${index + 1} of ${chunks.size}, in presentation order")
            out.println(" * (`docs/games/klondike/DIFFICULTY_LEVELS.md`, \"$name\"). Generated by")
            out.println(" * `tools/catalog`'s `build-levels`; do not edit by hand.")
            out.println(" */")
            out.println("internal val ${chunkNames[index]}: List<Long> = listOf(")
            for (seed in chunk) out.println("    ${seed}L,")
            out.println(")")
        }
    }

    File(directory, "InterimSeeds$name.kt").printWriter().use { out ->
        out.println("package org.finiteplay.klondike.deal")
        out.println()
        out.println("/**")
        out.println(" * Draw-one seeds graded **$name** (`docs/games/klondike/DIFFICULTY_LEVELS.md`).")
        out.println(" *")
        out.println(" * $PRESENTATION_ORDER_MARKER, which is a fixed permutation of the order the level was")
        out.println(" * ranked and cut in: ranking decides *which* ten thousand deals ship, and shipping")
        out.println(" * them in that order would open every level on its softest deals. The parts exist")
        out.println(" * only to keep each static initializer under the JVM's 64 KB limit.")
        out.println(" *")
        out.println(" * Regenerate with `tools/catalog`'s `grade-seeds`, `grade-search`, then `build-levels`.")
        out.println(" */")
        out.println("internal val INTERIM_SEEDS_$upper: List<Long> = ${chunkNames.joinToString(" + ").ifEmpty { "emptyList()" }}")
    }
    log("  wrote $name: ${seeds.size} seeds across ${chunks.size} parts")
}
