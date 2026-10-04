package org.finiteplay.klondike.tools.catalog

import java.io.File
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.rules.applyMove

/**
 * Builds one robustness-graded tier in a single step, so the catalog cannot be inconsistent.
 *
 * Three invariants are enforced here by **construction** — a candidate failing any is dropped
 * before export, so what ships is exactly the set that passed. The first two were violated
 * silently, and both reached the device before anyone noticed; a player found each by losing
 * a hand that was supposed to be safe.
 *
 * - **Every seed must have a verified winning line.** A graded deal is by definition one the
 *   tier's reference line wins, so a seed with no line is proof the grade is wrong, not a
 *   missing asset. It was being logged as a warning and shipped anyway; 164 of 180 failed and
 *   the build carried on.
 * - **No seed may appear in two tiers.** `INTERIM_SEED_GRADES` resolves duplicates by "last
 *   tier wins", so a seed in both Trivial and Insane displays as Insane. Fifteen collided,
 *   because the tier was selected on robustness alone with no reference to the other lists.
 * - **No tier below may already win it** ([mustNotBeWonBy]). Every tier offers the same
 *   obvious moves, so what separates them is only which order finds the win. Without this a
 *   deal Trivial plays out unaided would grade Easy the moment Easy's order also won it,
 *   which is no difficulty step at all. Every lower tier is checked, not just the one
 *   immediately below: the orders are different rulesets, not one rule tightened by degrees,
 *   so nothing guarantees that defeating Easy's order implies defeating Trivial's.
 *
 * The build reports what it discarded rather than failing outright — a low yield is
 * information about the grading, not a reason to leave the previous tier in place.
 */
fun buildTrivialTier(
    csvPath: String,
    minBudget: Int,
    dealDir: String,
    assetPath: String,
    supersededPath: String?,
    tier: String = "Trivial",
    ruleset: Ruleset = Ruleset.TRIVIAL,
    mustNotBeWonBy: List<Ruleset> = emptyList(),
    maxBudget: Int = Int.MAX_VALUE,
    ascending: Boolean = false,
    bandLabel: String = "survives",
    log: (String) -> Unit = ::println,
) {
    val candidates = File(csvPath).readLines().drop(1).mapNotNull { line ->
        val parts = line.split(',')
        val seed = parts.getOrNull(0)?.trim()?.toLongOrNull() ?: return@mapNotNull null
        val budget = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return@mapNotNull null
        if (budget in minBudget..maxBudget) seed to budget else null
    }
    log("candidates in $minBudget..$maxBudget: ${candidates.size}")

    // --- invariant 2: a seed already claimed by another tier cannot also belong to this one ---
    // Read from whatever the other tiers' files actually contain, parts included: a chunked
    // tier keeps its seeds in InterimSeeds<Tier>A.kt, not in the index file that concatenates
    // them, so matching only the index would silently see an empty tier.
    val ownFiles = Regex("""InterimSeeds$tier([A-Z])?\.kt""")
    val claimed = HashSet<Long>()
    File(dealDir).listFiles { f -> f.name.startsWith("InterimSeeds") && f.name.endsWith(".kt") }
        ?.filterNot { ownFiles.matches(it.name) }
        ?.forEach { file ->
            Regex("""^\s+(\d+)L,""", RegexOption.MULTILINE).findAll(file.readText())
                .forEach { claimed += it.groupValues[1].toLong() }
        }
    val (clashing, unclaimed) = candidates.partition { it.first in claimed }
    if (clashing.isNotEmpty()) {
        log("dropped ${clashing.size} already claimed by another tier: ${clashing.take(10).map { it.first }}")
    }

    // --- invariant 3: the tier below must not already play this deal out ---
    val (tooEasy, strictlyRequired) =
        unclaimed.partition { row -> mustNotBeWonBy.any { undeviatingLineWins(row.first, it) } }
    if (tooEasy.isNotEmpty()) {
        log("dropped ${tooEasy.size} that $mustNotBeWonBy already wins undeviating — no difficulty step")
    }

    // --- Expert's own gate: a withdrawal-free win must not be findable ---
    // The disjointness gate above says no lower *order* wins the deal; it says nothing about
    // whether some other line does, without ever touching a foundation. A tier shipped without
    // this had 28 of 37 seeds winnable that way, most in under ten milliseconds, and a player
    // won one of them on the fourth hand.
    val (avoidable, requiresWithdrawal) = if (ruleset != Ruleset.HARD) {
        emptyList<Pair<Long, Int>>() to strictlyRequired
    } else {
        strictlyRequired.partition { winnableWithoutWithdrawal(it.first) == true }
    }
    if (avoidable.isNotEmpty()) {
        log("dropped ${avoidable.size} with a findable withdrawal-free win — the tier's promise fails on them")
    }

    // --- invariant 1: every shipped seed must have a line that provably wins ---
    val solutions = LinkedHashMap<Long, List<Move>>()
    val unsolved = ArrayList<Long>()
    for ((seed, _) in requiresWithdrawal) {
        val line = tierSolution(seed, ruleset)
        if (line == null || !replayVerifies(seed, line)) unsolved += seed else solutions[seed] = line
    }
    if (unsolved.isNotEmpty()) {
        log("dropped ${unsolved.size} with no verified winning line — their grade was wrong, not their asset")
    }

    val shipped = requiresWithdrawal.filter { it.first in solutions }
    log("shipping ${shipped.size} seeds")
    if (shipped.isEmpty()) {
        log("nothing qualifies; leaving the existing tier untouched")
        return
    }

    // --- export, from exactly the set that passed ---
    val verifiedCsv = File(csvPath).resolveSibling("${tier.lowercase()}_verified.csv")
    verifiedCsv.printWriter().use { out ->
        out.println("seed,budget")
        for ((seed, budget) in shipped) out.println("$seed,$budget")
    }
    exportTrivialCatalog(
        verifiedCsv.path,
        dealDir,
        minBudget,
        tier = tier,
        maxBudget = maxBudget,
        ascending = ascending,
        bandLabel = bandLabel,
        log = log,
    )

    val superseded = supersededPath?.let { File(it) }
        ?.takeIf { it.exists() }
        ?.readLines()?.mapNotNull { it.trim().toLongOrNull() }
        ?: emptyList()
    writeTrivialSolutions(shipped.map { it.first }, superseded, assetPath, ruleset, log)

    // --- final audit, on what was actually written ---
    val exported = File(dealDir).listFiles { f -> f.name.matches(Regex("""InterimSeeds$tier[A-Z]\.kt""")) }
        ?.flatMap { file -> Regex("""^\s+(\d+)L,""", RegexOption.MULTILINE).findAll(file.readText()).map { it.groupValues[1].toLong() }.toList() }
        ?: emptyList()
    val missingSolution = exported.count { it !in solutions }
    val stillClashing = exported.count { it in claimed }
    val stillTooEasy = exported.count { seed -> mustNotBeWonBy.any { undeviatingLineWins(seed, it) } }
    val stillAvoidable = if (ruleset != Ruleset.HARD) 0 else exported.count { winnableWithoutWithdrawal(it) == true }
    log("")
    log("audit: ${exported.size} exported, $missingSolution without a solution, $stillClashing claimed by another tier, $stillTooEasy winnable by $mustNotBeWonBy, $stillAvoidable winnable without withdrawing")
    check(missingSolution == 0) { "every shipped $tier seed must have a verified winning line" }
    check(stillClashing == 0) { "no seed may appear in two tiers" }
    check(stillTooEasy == 0) { "no shipped $tier seed may be won by $mustNotBeWonBy's own order" }
    check(stillAvoidable == 0) { "no shipped $tier seed may have a findable withdrawal-free win" }
    log("all catalog invariants hold")
}

private fun replayVerifies(seed: Long, line: List<Move>): Boolean {
    var state = dealGame(seed, D1S_SPIKE_VERSIONS)
    for (move in line) {
        state = runCatching { applyMove(state, move) }.getOrElse { return false }
    }
    return state.isWon
}
