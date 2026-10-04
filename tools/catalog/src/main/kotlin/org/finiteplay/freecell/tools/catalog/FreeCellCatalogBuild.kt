package org.finiteplay.freecell.tools.catalog

import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicLong
import org.finiteplay.freecell.layout.GameVersions
import org.finiteplay.freecell.layout.dealGame
import org.finiteplay.freecell.rules.Move
import org.finiteplay.freecell.rules.applyMove
import org.finiteplay.freecell.rules.isLegal
import org.finiteplay.freecell.solver.SolveOutcome
import org.finiteplay.freecell.solver.SolverLimits
import org.finiteplay.freecell.solver.solveOnLargeStack
import org.finiteplay.solitaire.catalog.catalog.CatalogFormat
import org.finiteplay.solitaire.catalog.catalog.CatalogLoadResult
import org.finiteplay.solitaire.catalog.catalog.CatalogMetadata
import org.finiteplay.solitaire.catalog.catalog.encodeCatalog
import org.finiteplay.solitaire.catalog.catalog.loadCatalog

/**
 * Frozen for every seed this build tool ever certifies (`docs/games/freecell/DEALS.md`
 * "Versioned rules") — the deterministic deal contract means a version bump here is a catalog
 * regeneration, not a patch.
 */
internal val FREECELL_CATALOG_VERSIONS = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1)

/** Bumped with any change to the solver that could change which line it certifies for a seed. */
internal const val FREECELL_SOLVER_VERSION = 1

/**
 * Scans ascending seeds from [firstSeed], solving each and independently replaying the returned
 * line through the real reducer before accepting it: the solver's own verdict is never shipped
 * untrusted (`docs/games/freecell/DEALS.md` "Generation"). A candidate the solver cannot resolve —
 * unsolved or timed out alike — is skipped rather than shipped, exactly as that section states;
 * see `Solver.kt`'s own measured-solve-rate note for why that skip happens as often as it does.
 * Stops once [target] seeds are accepted.
 */
internal fun scanForCertifiedSeeds(
    target: Int,
    firstSeed: Long,
    limits: SolverLimits,
    threads: Int,
    progressEvery: Int = 200,
    onProgress: (accepted: Int, scanned: Long) -> Unit = { _, _ -> },
): Map<Long, List<Move>> {
    val accepted = ConcurrentSkipListMap<Long, List<Move>>()
    val next = AtomicLong(firstSeed)
    val scanned = AtomicLong(0)

    val workers = (0 until threads).map {
        Thread {
            while (accepted.size < target) {
                val seed = next.getAndIncrement()
                val state = dealGame(seed = seed, versions = FREECELL_CATALOG_VERSIONS)
                val outcome = solveOnLargeStack(state, limits)
                if (outcome is SolveOutcome.Solved && replayWinsForReal(seed, outcome.certificate)) {
                    accepted[seed] = outcome.certificate
                }
                val done = scanned.incrementAndGet()
                if (done % progressEvery == 0L) onProgress(accepted.size, done)
            }
        }.apply { isDaemon = true; start() }
    }
    workers.forEach { it.join() }

    // Threads race a few seeds past `target` before every worker's loop condition catches up;
    // trimming to the lowest `target` keys makes the shipped list size exact regardless of thread
    // timing, keeping the first-found (lowest-numbered) seeds.
    return accepted.entries.take(target).associate { it.key to it.value }
}

internal fun replayWinsForReal(seed: Long, line: List<Move>): Boolean {
    var state = dealGame(seed = seed, versions = FREECELL_CATALOG_VERSIONS)
    for (move in line) {
        if (!isLegal(state, move)) return false
        state = applyMove(state, move)
    }
    return state.isWon
}

/**
 * Certifies [target] deals and writes the catalog, a solutions blob, and a manifest — the F5
 * catalog build (`docs/games/freecell/EXECUTION_PLAN.md`). One flat, unpartitioned catalog
 * (`DEALS.md` "What ships"), unlike a game that splits its deals by suit count or difficulty.
 *
 * Re-running this is not guaranteed to pick the exact same seed set — the scan is multi-threaded
 * and stops as soon as `target` is reached, so which seeds near the boundary get fully evaluated
 * depends on thread scheduling — but every shipped seed, once selected, is independently
 * replay-verified before it is written, so what ships is never in question even though which
 * exact set ships from a re-run can vary.
 */
fun buildFreeCellCatalog(
    target: Int = 10_000,
    firstSeed: Long = 1L,
    limits: SolverLimits = SolverLimits(),
    threads: Int = 12,
    outDir: String = DEFAULT_FREECELL_CATALOG_DIR,
    solutionsPath: String = DEFAULT_FREECELL_SOLUTIONS_PATH,
    catalogVersion: Int = 1,
    log: (String) -> Unit = ::println,
) {
    val directory = File(outDir).apply { mkdirs() }
    val started = System.nanoTime()

    val result = scanForCertifiedSeeds(
        target = target,
        firstSeed = firstSeed,
        limits = limits,
        threads = threads,
        onProgress = { accepted, scanned -> log("  $accepted/$target certified, $scanned scanned") },
    )
    check(result.size == target) { "only found ${result.size} of $target certifiable seeds" }

    val seeds = result.keys.sorted()
    val metadata = CatalogMetadata(
        catalogVersion = catalogVersion,
        rulesVersion = FREECELL_CATALOG_VERSIONS.rulesVersion,
        shuffleVersion = FREECELL_CATALOG_VERSIONS.shuffleVersion,
        solverVersion = FREECELL_SOLVER_VERSION,
        // FreeCell has no draw-mode axis (`docs/games/freecell/EXECUTION_PLAN.md` "Deterministic
        // Deal Contract"): the byte that would have carried one is folded into the reserved run.
        modeByte = null,
        partitionId = CatalogFormat.PARTITION_NONE,
    )
    val bytes = encodeCatalog(metadata, seeds.map { it.toULong() }, FREECELL_MAGIC_BYTES)
    val file = File(directory, FREECELL_CATALOG_FILE_NAME)
    file.writeBytes(bytes)

    val entry = FreeCellCatalogManifestEntry(
        recordCount = seeds.size,
        payloadHash = sha256Hex(bytes.copyOfRange(CatalogFormat.HEADER_SIZE_V1, bytes.size)),
        fileHash = sha256Hex(bytes),
    )
    val seconds = (System.nanoTime() - started) / 1_000_000_000.0
    log("  wrote ${file.name}: ${seeds.size} seeds (${String.format("%.1fs", seconds)})")

    val solutionsBytes = FreeCellSolutionCodec.encode(result)
    val solutionsFile = File(solutionsPath).apply { parentFile?.mkdirs() }
    solutionsFile.writeBytes(solutionsBytes)
    log("wrote ${result.size} solutions (${solutionsBytes.size} bytes) to $solutionsPath")

    val manifest = File(directory, FREECELL_MANIFEST_NAME)
    manifest.writeText(manifestTextFor(entry, catalogVersion))
    log("")
    log("wrote ${entry.recordCount} seeds and ${manifest.name} to $outDir")
    log("run verifyFreeCellDealCatalogs to check what was written")
}

/**
 * The catalog gate: re-checks committed evidence and never solves anything
 * (`docs/games/freecell/DEALS.md` "Catalog gate").
 */
fun verifyFreeCellCatalog(
    catalogDir: String = DEFAULT_FREECELL_CATALOG_DIR,
    solutionsPath: String = DEFAULT_FREECELL_SOLUTIONS_PATH,
    sampleSize: Int = 200,
    log: (String) -> Unit = ::println,
): Boolean {
    val directory = File(catalogDir)
    val manifestFile = File(directory, FREECELL_MANIFEST_NAME)
    if (!manifestFile.exists()) {
        log("no FreeCell catalog manifest at ${manifestFile.path} — nothing committed to verify yet")
        return true
    }

    val entry = parseManifest(manifestFile.readText())
    val solutionsBytes = File(solutionsPath).takeIf { it.exists() }?.readBytes()
    val solutions = solutionsBytes?.let { FreeCellSolutionCodec.decode(it) } ?: emptyMap()
    val failures = ArrayList<String>()

    val file = File(directory, FREECELL_CATALOG_FILE_NAME)
    if (!file.exists()) {
        failures += "manifest declares ${file.name} but it is not committed"
    } else {
        val bytes = file.readBytes()
        if (sha256Hex(bytes) != entry.fileHash) {
            failures += "file hash does not match the manifest"
        }
        when (val loaded = loadCatalog(bytes, FREECELL_MAGIC_BYTES, hasModeByte = false)) {
            is CatalogLoadResult.Invalid -> failures += loaded.reason
            is CatalogLoadResult.Valid -> {
                val header = loaded.header
                if (header.recordCount != entry.recordCount) {
                    failures += "header declares ${header.recordCount} records, manifest ${entry.recordCount}"
                }
                if (header.partitionId != CatalogFormat.PARTITION_NONE) {
                    failures += "header partition ${header.partitionId}, expected unpartitioned"
                }
                if (sha256Hex(bytes.copyOfRange(CatalogFormat.HEADER_SIZE_V1, bytes.size)) != entry.payloadHash) {
                    failures += "payload hash does not match the manifest"
                }
                failures += replayFailures(loaded.seeds, solutions, sampleSize)
                log("  ${loaded.seeds.size} seeds ok")
            }
        }
    }

    if (failures.isEmpty()) {
        log("FreeCell catalog gate passed: ${entry.recordCount} seeds")
        return true
    }
    log("FreeCell catalog gate FAILED:")
    for (failure in failures) log("  - $failure")
    return false
}

private fun replayFailures(seeds: List<ULong>, solutions: Map<Long, List<Move>>, sampleSize: Int): List<String> {
    if (solutions.isEmpty()) return listOf("no solutions asset to replay")
    val step = (seeds.size / sampleSize).coerceAtLeast(1)
    val sample = seeds.filterIndexed { i, _ -> i % step == 0 }.take(sampleSize)

    val failures = ArrayList<String>()
    for (seed in sample) {
        val signed = seed.toLong()
        val line = solutions[signed]
        if (line == null) {
            failures += "seed $signed has no certificate"
            continue
        }
        if (!replayWinsForReal(signed, line)) {
            failures += "seed $signed does not replay to a win under the current rules"
        }
    }
    return failures
}

internal data class FreeCellCatalogManifestEntry(
    val recordCount: Int,
    val payloadHash: String,
    val fileHash: String,
)

internal val FREECELL_MAGIC_BYTES: ByteArray = CatalogFormat.magicBytes("FRCL")

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

internal fun manifestTextFor(entry: FreeCellCatalogManifestEntry, catalogVersion: Int): String = buildString {
    appendLine("{")
    appendLine("""  "formatVersion": ${CatalogFormat.FORMAT_VERSION_1},""")
    appendLine("""  "catalogVersion": $catalogVersion,""")
    appendLine("""  "rulesVersion": ${FREECELL_CATALOG_VERSIONS.rulesVersion},""")
    appendLine("""  "shuffleVersion": ${FREECELL_CATALOG_VERSIONS.shuffleVersion},""")
    appendLine("""  "solverVersion": $FREECELL_SOLVER_VERSION,""")
    appendLine("""  "generatedAt": "${Instant.now()}",""")
    appendLine("""  "file": "$FREECELL_CATALOG_FILE_NAME",""")
    appendLine("""  "partitionId": ${CatalogFormat.PARTITION_NONE},""")
    appendLine("""  "recordCount": ${entry.recordCount},""")
    appendLine("""  "payloadHash": "${entry.payloadHash}",""")
    appendLine("""  "fileHash": "${entry.fileHash}"""")
    appendLine("}")
}

internal fun parseManifest(text: String): FreeCellCatalogManifestEntry {
    fun field(name: String) = Regex(""""$name"\s*:\s*"?([A-Za-z0-9_]+)"?""").find(text)?.groupValues?.get(1)
        ?: error("manifest is missing \"$name\"")
    return FreeCellCatalogManifestEntry(
        recordCount = field("recordCount").toInt(),
        payloadHash = field("payloadHash"),
        fileHash = field("fileHash"),
    )
}

internal const val DEFAULT_FREECELL_CATALOG_DIR = "games/freecell/app/src/main/assets/catalogs"

// Kept as a committed verification artifact outside assets/ so verifyFreeCellDealCatalogs can
// replay-check without re-solving, not as a shipped asset — the app does not read certificates,
// only seeds (mirroring the other games' own documented fallback for this exact asset).
internal const val DEFAULT_FREECELL_SOLUTIONS_PATH = "tools/catalog/data/freecell_solutions.bin"
internal const val FREECELL_MANIFEST_NAME = "manifest.json"
internal const val FREECELL_CATALOG_FILE_NAME = "freecell.catalog"
