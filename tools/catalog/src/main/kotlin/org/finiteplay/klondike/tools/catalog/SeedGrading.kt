package org.finiteplay.klondike.tools.catalog

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * The shipped difficulty levels, and what populates each one
 * (`docs/games/klondike/DIFFICULTY_LEVELS.md` "Levels as shipped").
 *
 * The first four share a name with the [Ruleset] that fills them, one to one, and
 * [levelForRuleset] is the identity. That was not always so — full foundation restraint once
 * had a level of its own, worth a measured step of 2.6%, too thin to keep — and the resulting
 * five-to-four mapping made "a Hard deal" mean two different things depending on who was
 * asking. The tiers were merged rather than the mapping kept.
 *
 * The last two have no ruleset by definition: they are the deals no order wins, split by what
 * the two searches say.
 */
enum class CatalogLevel {
    /** [Ruleset.TRIVIAL]'s undeviating order wins it. */
    TRIVIAL,

    /** [Ruleset.EASY]'s order wins it, Trivial's does not. */
    EASY,

    /** [Ruleset.MEDIUM]'s order wins it: full foundation restraint and the setup move. */
    MEDIUM,

    /** [Ruleset.HARD]'s order wins it: foundation withdrawal. */
    HARD,

    /** No ruleset wins it and exactly one of DFS and A* finds a win — the edge of searchability. */
    EXPERT,

    /** No ruleset wins it and neither search resolves it. Ships without a certificate. */
    INSANE,
}

/**
 * Which ruleset grade maps to which shipped level: one to one, by name.
 *
 * It is the identity and is meant to stay so. This was a five-to-four mapping while the tiers
 * and the levels had drifted apart, which is what made "a Hard deal" ambiguous across the whole
 * of `DIFFICULTY_LEVELS.md`. Kept as a function rather than inlined because the two remain
 * different kinds — a ruleset is a measurement, a level is what a player picks — and the next
 * recut goes here rather than into every call site.
 *
 * [CatalogLevel.EXPERT] and [CatalogLevel.INSANE] are absent by construction: no ruleset wins
 * their deals, which is their definition, so they are cut from the two searches instead.
 */
fun levelForRuleset(ruleset: Ruleset): CatalogLevel = when (ruleset) {
    Ruleset.TRIVIAL -> CatalogLevel.TRIVIAL
    Ruleset.EASY -> CatalogLevel.EASY
    Ruleset.MEDIUM -> CatalogLevel.MEDIUM
    Ruleset.HARD -> CatalogLevel.HARD
}

/**
 * One graded deal, as the reusable grading file records it.
 *
 * Every column is a measurement, not a decision: which level a seed ends up in is applied by
 * the catalog build reading this file, so a level can be recut — different quotas, different
 * priorities, a different mapping — without regrading anything.
 */
data class SeedGradeRecord(
    val seed: Long,
    val level: CatalogLevel,
    /** The lowest ruleset whose undeviating order wins, or null when none does. */
    val ruleset: Ruleset?,
    /** Length of the winning line, draws included, as it would be replayed. */
    val moves: Int,
    /** Moves that change the state — draws and recycles excluded. */
    val choices: Int,
    /** Largest number of mistakes the deal is proven to survive, or -1 where not measured. */
    val robustness: Int,
    /** Critical choices along the line (`checkWithdrawalTier`), or -1 where not measured. */
    val criticalChoices: Int,
    /** Critical points along the line, or -1 where not measured. */
    val criticalPoints: Int,
    /** `yes`, `no`, `deal_is_lost`, `unknown`, or `` where not measured. */
    val withdrawalNeeded: String = "",
    val dfsOutcome: String = "",
    val dfsMoves: Int = 0,
    val astarOutcome: String = "",
    val astarMoves: Int = 0,
) {
    fun toCsv(): String = listOf(
        seed, level, ruleset ?: "", moves, choices, robustness, criticalChoices, criticalPoints,
        withdrawalNeeded, dfsOutcome, dfsMoves, astarOutcome, astarMoves,
    ).joinToString(",")
}

/**
 * The header carries a **ladder version** because the column names alone cannot say which
 * ruleset ladder wrote the file, and the names are not self-describing across a change to it.
 *
 * Version 1 was five tiers where `HARD` meant the setup move and `EXPERT` meant withdrawal.
 * Version 2 merged restraint and setup into `MEDIUM` and renamed withdrawal to `HARD`, so a
 * version-1 row saying `HARD` parses cleanly and means something else entirely — the one
 * failure mode worth spending a header field to make impossible. `EXPERT` at least fails loudly
 * on its own; `HARD` would not.
 */
internal const val GRADE_LADDER_VERSION = 2

internal const val GRADE_CSV_HEADER =
    "seed,level,ruleset_v$GRADE_LADDER_VERSION,moves,choices,robustness,critical_choices,critical_points," +
        "withdrawal_needed,dfs_outcome,dfs_moves,astar_outcome,astar_moves"

/** Every header this tool has ever written, so a stale file is named rather than merely rejected. */
private val KNOWN_STALE_HEADERS = mapOf(
    "seed,level,ruleset,moves,choices,robustness,critical_choices,critical_points," +
        "withdrawal_needed,dfs_outcome,dfs_moves,astar_outcome,astar_moves" to 1,
)

/**
 * Reads a grading file, gzipped or not.
 *
 * The committed copies are gzipped: the two of them are 36 MB of CSV and about 5 MB
 * compressed, they are regenerated wholesale rather than edited, and a diff of four hundred
 * thousand seed rows is of no use to anyone. Reading both forms means a rebuild works against
 * either without a decompression step in between.
 */
internal fun readGradingLines(file: File): List<String> =
    if (file.name.endsWith(".gz")) {
        java.util.zip.GZIPInputStream(file.inputStream().buffered()).bufferedReader().readLines()
    } else {
        file.readLines()
    }

/** The path as it exists on disk: the plain file if it is there, otherwise its `.gz` twin. */
internal fun gradingFile(path: String): File =
    File(path).takeIf { it.exists() } ?: File("$path.gz")

fun parseSeedGrades(file: File): List<SeedGradeRecord> {
    if (!file.exists()) return emptyList()
    val lines = readGradingLines(file)
    val header = lines.firstOrNull()
    val staleVersion = KNOWN_STALE_HEADERS[header]
    check(staleVersion == null) {
        "${file.path} was graded by ladder version $staleVersion, and this tool is version " +
            "$GRADE_LADDER_VERSION. The tiers were renumbered, so its `HARD` rows mean the setup " +
            "move where `HARD` now means foundation withdrawal — reading them would silently " +
            "regrade every deal. Rerun `grade-seeds` and `grade-search`; the file cannot be migrated, " +
            "because the merged Medium tier was never measured as one."
    }
    require(header == GRADE_CSV_HEADER) { "not a seed grading file: ${file.path}" }
    return lines.drop(1).mapNotNull { line ->
        val c = line.split(',')
        if (c.size != GRADE_CSV_HEADER.count { it == ',' } + 1) return@mapNotNull null
        val seed = c[0].toLongOrNull() ?: return@mapNotNull null
        SeedGradeRecord(
            seed = seed,
            level = CatalogLevel.valueOf(c[1]),
            ruleset = c[2].takeIf { it.isNotEmpty() }?.let { Ruleset.valueOf(it) },
            moves = c[3].toInt(),
            choices = c[4].toInt(),
            robustness = c[5].toInt(),
            criticalChoices = c[6].toInt(),
            criticalPoints = c[7].toInt(),
            withdrawalNeeded = c[8],
            dfsOutcome = c[9],
            dfsMoves = c[10].toInt(),
            astarOutcome = c[11],
            astarMoves = c[12].toInt(),
        )
    }
}

/**
 * How a level ranks its candidates: the best come first, and the catalog takes its quota off
 * the front. Trivial through Medium rank by how many mistakes the deal survives, so a level
 * opens on its most forgiving hands; Hard ranks by how much the board can punish, and Expert
 * by how long the search's own line runs.
 */
internal fun priorityOf(grade: SeedGradeRecord): Long = when (grade.level) {
    CatalogLevel.TRIVIAL, CatalogLevel.EASY, CatalogLevel.MEDIUM ->
        grade.robustness * 100_000L + grade.moves
    CatalogLevel.HARD -> grade.criticalChoices * 100_000L + grade.criticalPoints * 1_000L + grade.moves
    CatalogLevel.EXPERT -> grade.moves.toLong()
    CatalogLevel.INSANE -> 0L
}

/**
 * Keeps the best [capacity] candidates seen for one level, by [priorityOf].
 *
 * A bounded pool rather than the whole population: ten million seeds grade about seven
 * million wins, far past what any catalog needs or any file should hold, while the quota is
 * ten thousand. Keeping ten times the quota leaves room to recut a level later without
 * rescanning, and bounds the grading file at a few tens of megabytes.
 */
internal class GradePool(val capacity: Int) {
    private val entries = ArrayList<SeedGradeRecord>(capacity + 1)
    private var sorted = true

    @Synchronized
    fun offer(grade: SeedGradeRecord) {
        entries += grade
        sorted = false
        if (entries.size > capacity * 2) trim()
    }

    @Synchronized
    fun best(): List<SeedGradeRecord> {
        trim()
        return ArrayList(entries)
    }

    private fun trim() {
        if (!sorted) {
            entries.sortWith(compareByDescending<SeedGradeRecord> { priorityOf(it) }.thenBy { it.seed })
            sorted = true
        }
        while (entries.size > capacity) entries.removeAt(entries.size - 1)
    }
}

/**
 * Stage A: grades [seedCount] deals by the lowest ruleset whose undeviating order wins them,
 * measuring the mistake budget each survives, and keeps the best [poolPerLevel] per level.
 *
 * This is the cheap stage and it runs over everything: a ruleset playout costs microseconds
 * and the robustness walk rejects most deals in under a hundred nodes, so ten million seeds
 * is hours rather than the weeks a search over the same population would take. The deals no
 * ruleset wins are counted here and left for `gradeSearchStage`, which searches only as many
 * as the top two levels actually need.
 */
fun gradeRulesetStage(
    seedCount: Int,
    firstSeed: Long = 1L,
    parallelism: Int = 12,
    poolPerLevel: Int = 100_000,
    maxRobustness: Int = 6,
    maxStates: Int = 400_000,
    outputPath: String = "tools/catalog/data/grading/ruleset_grades.csv",
    censusPath: String = "tools/catalog/data/grading/ruleset_census.txt",
    log: (String) -> Unit = ::println,
) {
    log("grading $seedCount seeds from $firstSeed on $parallelism threads")
    log("lowest winning ruleset, then the largest mistake budget it survives (0..$maxRobustness)")

    val pools = CatalogLevel.entries.associateWith { GradePool(poolPerLevel) }
    val census = ConcurrentHashMap<String, AtomicLong>()
    fun bump(key: String) = census.computeIfAbsent(key) { AtomicLong(0) }.incrementAndGet()

    val next = AtomicLong(0)
    val done = AtomicInteger(0)
    val startedAt = System.nanoTime()

    fun writeCensus(finished: Boolean) {
        val elapsed = (System.nanoTime() - startedAt) / 1_000_000_000L
        val completed = done.get()
        File(censusPath).also { it.parentFile?.mkdirs() }.writeText(
            buildString {
                appendLine(if (finished) "status: complete" else "status: running")
                appendLine("seeds: $completed / $seedCount from $firstSeed")
                appendLine("elapsed: ${elapsed}s")
                if (elapsed > 0) appendLine("rate: %.0f seeds/s".format(completed.toDouble() / elapsed))
                appendLine()
                for (entry in census.entries.sortedBy { it.key }) appendLine("${entry.key}: ${entry.value.get()}")
            },
        )
    }

    val executor = Executors.newFixedThreadPool(parallelism)
    try {
        List(parallelism) {
            executor.submit {
                while (true) {
                    val offset = next.getAndIncrement()
                    if (offset >= seedCount) break
                    val seed = firstSeed + offset
                    runCatching { gradeOneSeed(seed, maxRobustness, maxStates) }
                        .onSuccess { grade ->
                            if (grade == null) {
                                bump("no ruleset wins")
                            } else {
                                bump("level ${grade.level}")
                                bump("ruleset ${grade.ruleset}")
                                bump("robustness ${grade.robustness}")
                                pools.getValue(grade.level).offer(grade)
                            }
                        }
                        .onFailure { bump("failed: ${it::class.simpleName}") }
                    if (done.incrementAndGet() % 100_000 == 0) writeCensus(finished = false)
                }
            }
        }.forEach { it.get() }
    } finally {
        executor.shutdown()
        executor.awaitTermination(24, TimeUnit.HOURS)
    }

    val output = File(outputPath).also { it.parentFile?.mkdirs() }
    output.printWriter().use { out ->
        out.println(GRADE_CSV_HEADER)
        for (level in CatalogLevel.entries) for (grade in pools.getValue(level).best()) out.println(grade.toCsv())
    }
    writeCensus(finished = true)

    val elapsed = (System.nanoTime() - startedAt) / 1_000_000_000L
    log("")
    for (entry in census.entries.sortedBy { it.key }) log("${entry.key}: ${entry.value.get()}")
    log("")
    log("kept ${CatalogLevel.entries.sumOf { pools.getValue(it).best().size }} rows in $outputPath after ${elapsed}s")
}

/**
 * Grades one deal: the lowest ruleset whose order wins it, that line's length, and how many
 * mistakes the deal survives under that same ruleset.
 *
 * Returns null when no ruleset wins — those deals are the search stage's population, and
 * saying so costs five playouts rather than a search.
 */
internal fun gradeOneSeed(seed: Long, maxRobustness: Int, maxStates: Int): SeedGradeRecord? {
    for (ruleset in Ruleset.entries) {
        // The grade is the undeviating line's verdict, not a search's: "each tier's rules are
        // played against the raw deal with no search assistance of any kind"
        // (`DIFFICULTY_LEVELS.md`). Budget zero *is* that question, so the ladder step and the
        // first robustness measurement are one call. Grading through the tier search instead
        // costs about a hundred times as much and answers a different question — it wins deals
        // the rules alone do not, which is exactly what the tier is defined to exclude.
        var robustness = -1
        for (budget in 0..maxRobustness) {
            val outcome = runCatching { checkRobustness(seed, ruleset, budget, maxStates).outcome }.getOrNull()
            if (outcome == TrivialOutcome.ROBUST) robustness = budget else break
        }
        if (robustness < 0) continue
        val reference = runCatching { referenceStrategyLine(seed, ruleset) }.getOrNull() ?: continue
        if (reference.outcome != StrategySearchOutcome.WON) continue
        val line = replayableStrategyLine(seed, reference.packedLine) ?: continue
        return SeedGradeRecord(
            seed = seed,
            level = levelForRuleset(ruleset),
            ruleset = ruleset,
            moves = line.size,
            choices = line.count { it != org.finiteplay.klondike.rules.Move.Draw && it != org.finiteplay.klondike.rules.Move.Recycle },
            robustness = robustness,
            criticalChoices = -1,
            criticalPoints = -1,
        )
    }
    return null
}
