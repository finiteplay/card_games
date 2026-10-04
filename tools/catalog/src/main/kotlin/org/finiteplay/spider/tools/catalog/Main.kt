package org.finiteplay.spider.tools.catalog

import java.io.File
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.rules.Move
import org.finiteplay.spider.solver.SolverLimits

/**
 * Desktop-only entry point for Spider's catalog build and gate. Separate from Klondike's
 * `org.finiteplay.klondike.tools.catalog.MainKt` on purpose — `:app` must never depend on this
 * module, and a shared entry point would mean either game's build failing could look like the
 * other's.
 *
 * `build [target] [firstSeed] [maxNodes] [maxMillis] [playouts] [threads]`: certifies [target]
 * deals each for ONE and TWO suits, writing catalogs, a solutions blob, and a manifest under
 * `games/spider/app/src/main/assets`.
 *
 * `import-external suitCount solutionsDir [outDir] [solutionsPath]`: packages an external solver's
 * already-verified certificates (`docs/games/spider/EXECUTION_PLAN.md` "S6" — plspider's
 * `verified/solution-N.txt` files, `ReplayPlspiderSolutions.kt`'s own output format) into the same
 * catalog/manifest/solutions-blob format [buildSpiderCatalogs] writes.
 *
 * `verify`: the gate `verifySpiderDealCatalogs` runs — re-checks committed evidence, never solves.
 *
 * `compact [solutionsPath] [catalogDir] [assetPath]`: the one-off re-encode
 * (`SpiderSolutionCompaction.kt`) — reads the committed verification artifact, bit-packs every
 * seed's line with `CompactSolutionCodec`, and writes the shipped `assets/solutions.bin` the app
 * actually reads to power a hint that follows a known winning line. Not a gate; run by hand.
 */
fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "build" -> buildSpiderCatalogs(
            target = args.getOrNull(1)?.toIntOrNull() ?: 10_000,
            firstSeed = args.getOrNull(2)?.toLongOrNull() ?: 1L,
            limits = SolverLimits(
                maxNodes = args.getOrNull(3)?.toLongOrNull() ?: 10_000L,
                maxMillis = args.getOrNull(4)?.toLongOrNull() ?: 1_500L,
                playouts = args.getOrNull(5)?.toIntOrNull() ?: 150,
            ),
            threads = args.getOrNull(6)?.toIntOrNull() ?: 12,
        )
        "import-external" -> writeCatalogFromExternalSolutions(
            suitCount = SuitCount.valueOf(args[1].uppercase()),
            solutions = parsePlspiderSolutions(File(args[2])),
            outDir = args.getOrNull(3) ?: DEFAULT_SPIDER_CATALOG_DIR,
            solutionsPath = args.getOrNull(4) ?: DEFAULT_SPIDER_SOLUTIONS_PATH,
        )
        "verify" -> {
            val passed = verifySpiderCatalogs()
            if (!passed) kotlin.system.exitProcess(1)
        }
        "compact" -> compactSpiderSolutions(
            solutionsPath = args.getOrNull(1) ?: DEFAULT_SPIDER_SOLUTIONS_PATH,
            catalogDir = args.getOrNull(2) ?: DEFAULT_SPIDER_CATALOG_DIR,
            assetPath = args.getOrNull(3) ?: DEFAULT_SPIDER_SOLUTIONS_ASSET_PATH,
        )
        else -> {
            println(
                "usage: build [target] [firstSeed] [maxNodes] [maxMillis] [playouts] [threads] | " +
                    "import-external suitCount solutionsDir [outDir] [solutionsPath] | verify | " +
                    "compact [solutionsPath] [catalogDir] [assetPath]",
            )
            kotlin.system.exitProcess(1)
        }
    }
}

/**
 * Parses `ReplayPlspiderSolutions.kt`'s own `solution-N.txt` output format: a header line naming
 * the seed, a metadata line, then one `DEAL` or `MOVE from fromIndex to` per certificate move.
 * Trusts the file's own `verified=true` claim no further than any other input here — every parsed
 * line still goes through [writeCatalogFromExternalSolutions]'s own `replayWinsForReal` check.
 */
internal fun parsePlspiderSolutions(directory: File): Map<Long, List<Move>> {
    val seedLine = Regex("""seed=(\d+) .*""")
    val result = LinkedHashMap<Long, List<Move>>()
    for (file in directory.listFiles { f -> f.name.startsWith("solution-") && f.name.endsWith(".txt") }.orEmpty()) {
        val lines = file.readLines()
        val seed = seedLine.matchEntire(lines[0])?.groupValues?.get(1)?.toLong()
            ?: error("${file.name}: first line does not match 'seed=N ...': ${lines[0]}")
        val moves = lines.drop(2).map { line ->
            if (line == "DEAL") {
                Move.DealRow
            } else {
                val (from, fromIndex, to) = line.removePrefix("MOVE ").split(" ").map { it.toInt() }
                Move.TableauToTableau(from, fromIndex, to)
            }
        }
        result[seed] = moves
    }
    return result
}
