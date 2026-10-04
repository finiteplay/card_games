package org.finiteplay.klondike.tools.catalog

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Stage C: measures what the Hard level is ranked by.
 *
 * Trivial through Medium rank on the mistake budget stage A already measured, and that budget
 * is nearly always zero this far up the ladder — tolerance thins by an order of magnitude per
 * tier — so it cannot order this level. What can is how much the board punishes: the
 * **critical choices** along the line, moves after which no winning path remains
 * (`DIFFICULTY_LEVELS.md` Glossary), and whether the win needs a card taken back off a
 * foundation.
 *
 * It runs on the top [limit] candidates by line length rather than the whole pool, because the
 * walk costs a bounded search per seed while stage A costs microseconds. Everything it
 * measures is written back into the grading file, so a later rebuild re-ranks for free.
 */
fun enrichHardLevel(
    gradesPath: String = "tools/catalog/data/grading/ruleset_grades.csv",
    limit: Int = 30_000,
    parallelism: Int = 12,
    maxStates: Int = 300_000,
    progressPath: String = "tools/catalog/data/grading/hard_enrichment_progress.txt",
    log: (String) -> Unit = ::println,
) {
    val file = File(gradesPath)
    val grades = parseSeedGrades(file)
    val hard = grades.filter { it.level == CatalogLevel.HARD }
        .sortedWith(compareByDescending<SeedGradeRecord> { it.moves }.thenBy { it.seed })
        .take(limit)
    log("measuring critical choices on ${hard.size} Hard candidates on $parallelism threads")

    val measured = java.util.concurrent.ConcurrentHashMap<Long, SeedGradeRecord>()
    val next = AtomicInteger(0)
    val done = AtomicInteger(0)
    val startedAt = System.nanoTime()

    val executor = Executors.newFixedThreadPool(parallelism)
    try {
        List(parallelism) {
            executor.submit {
                while (true) {
                    val offset = next.getAndIncrement()
                    if (offset >= hard.size) break
                    val row = hard[offset]
                    // The ablation is what proves a withdrawal is *needed*, and it is the
                    // expensive half; the count of critical choices comes from the same walk.
                    val verdict = runCatching { checkWithdrawalTier(row.seed, maxStates, ablate = true) }.getOrNull()
                    if (verdict != null) {
                        measured[row.seed] = row.copy(
                            criticalChoices = verdict.criticalChoices,
                            criticalPoints = verdict.criticalPoints,
                            withdrawalNeeded = when (verdict.reason) {
                                WithdrawalTierReason.WINNABLE_WITHOUT_WITHDRAWAL -> "no"
                                WithdrawalTierReason.INCONCLUSIVE -> "unknown"
                                else -> "yes"
                            },
                        )
                    }
                    val completed = done.incrementAndGet()
                    if (completed % 500 == 0) {
                        val elapsed = (System.nanoTime() - startedAt) / 1_000_000_000L
                        File(progressPath).also { it.parentFile?.mkdirs() }.writeText(
                            "measured: $completed / ${hard.size}\nelapsed: ${elapsed}s\n" +
                                if (elapsed > 0) "rate: %.1f seeds/s\n".format(completed.toDouble() / elapsed) else "",
                        )
                    }
                }
            }
        }.forEach { it.get() }
    } finally {
        executor.shutdown()
        executor.awaitTermination(24, TimeUnit.HOURS)
    }

    file.printWriter().use { out ->
        out.println(GRADE_CSV_HEADER)
        for (row in grades) out.println((measured[row.seed] ?: row).toCsv())
    }
    val elapsed = (System.nanoTime() - startedAt) / 1_000_000_000L
    log("wrote ${measured.size} enriched rows back into $gradesPath after ${elapsed}s")
    log("critical choices: " + measured.values.groupingBy { it.criticalChoices }.eachCount().toSortedMap())
    log("withdrawal needed: " + measured.values.groupingBy { it.withdrawalNeeded }.eachCount())
}
