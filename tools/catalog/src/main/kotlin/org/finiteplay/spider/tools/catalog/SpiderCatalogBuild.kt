package org.finiteplay.spider.tools.catalog

import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.finiteplay.solitaire.catalog.catalog.CatalogFormat
import org.finiteplay.solitaire.catalog.catalog.CatalogLoadResult
import org.finiteplay.solitaire.catalog.catalog.CatalogMetadata
import org.finiteplay.solitaire.catalog.catalog.encodeCatalog
import org.finiteplay.solitaire.catalog.catalog.loadCatalog
import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.dealGame
import org.finiteplay.spider.rules.Move
import org.finiteplay.spider.rules.applyMove
import org.finiteplay.spider.rules.isLegal
import org.finiteplay.spider.solver.SolverLimits
import org.finiteplay.spider.solver.SpiderSolver

/**
 * Frozen for every seed this build tool ever certifies (`docs/games/spider/DEALS.md`
 * "Versioned Rules") — the deterministic deal contract means a version bump here is a catalog
 * regeneration, not a patch.
 */
internal val SPIDER_CATALOG_VERSIONS = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1)

/** Bumped with any change to [SpiderSolver] that could change which line it certifies for a seed. */
internal const val SPIDER_SOLVER_VERSION = 1

/**
 * Scans ascending seeds at [suitCount] — the same ones the game deals from (`Survey.kt`) — solving
 * each and independently replaying the returned line through the real reducer before accepting it:
 * [SpiderSolver.certify]'s own verdict is never shipped untrusted. Stops once [target] seeds are
 * accepted. Seeds already in [claimed] (certified for an earlier suit count in this build) are
 * skipped, keeping every shipped seed number unambiguous across partitions.
 */
internal fun scanForCertifiedSeeds(
    suitCount: SuitCount,
    target: Int,
    firstSeed: Long,
    limits: SolverLimits,
    threads: Int,
    claimed: ConcurrentSkipListSet<Long>,
    progressEvery: Int = 2_000,
    onProgress: (accepted: Int, scanned: Long) -> Unit = { _, _ -> },
): Map<Long, List<Move>> {
    val accepted = ConcurrentSkipListMap<Long, List<Move>>()
    val next = AtomicLong(firstSeed)
    val scanned = AtomicLong(0)

    val workers = (0 until threads).map {
        Thread {
            val solver = SpiderSolver(limits)
            while (accepted.size < target) {
                val seed = next.getAndIncrement()
                if (seed !in claimed) {
                    val state = dealGame(seed = seed, versions = SPIDER_CATALOG_VERSIONS, suitCount = suitCount)
                    val line = solver.certify(state)
                    if (line != null && replayWinsForReal(seed, suitCount, line)) {
                        accepted[seed] = line
                    }
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

private fun replayWinsForReal(seed: Long, suitCount: SuitCount, line: List<Move>): Boolean {
    var state = dealGame(seed = seed, versions = SPIDER_CATALOG_VERSIONS, suitCount = suitCount)
    for (move in line) {
        if (!isLegal(state, move)) return false
        state = applyMove(state, move)
    }
    return state.isWon
}

/**
 * Certifies [target] deals each for [suitCounts] and writes catalogs, a combined solutions blob,
 * and a manifest — the S5 catalog build (`docs/games/spider/EXECUTION_PLAN.md`).
 *
 * Selects and solves; nothing here is precomputed elsewhere the way Klondike's grading stages feed
 * `build-levels`. Re-running this is not guaranteed to pick the exact same seed set — the scan is
 * multi-threaded and stops as soon as `target` is reached, so which seeds near the boundary get
 * fully evaluated depends on thread scheduling — but every shipped seed, once selected, is
 * independently replay-verified before it is written, so what ships is never in question even
 * though which exact set ships from a re-run can vary.
 */
fun buildSpiderCatalogs(
    suitCounts: List<SuitCount> = listOf(SuitCount.ONE, SuitCount.TWO),
    target: Int = 10_000,
    firstSeed: Long = 1L,
    limits: SolverLimits = SolverLimits(maxNodes = 10_000, maxMillis = 1_500, playouts = 150),
    threads: Int = 12,
    outDir: String = DEFAULT_SPIDER_CATALOG_DIR,
    solutionsPath: String = DEFAULT_SPIDER_SOLUTIONS_PATH,
    catalogVersion: Int = 1,
    log: (String) -> Unit = ::println,
) {
    val directory = File(outDir).apply { mkdirs() }
    val claimed = ConcurrentSkipListSet<Long>()
    val bySuitCount = linkedMapOf<SuitCount, Map<Long, List<Move>>>()
    val entries = ArrayList<SpiderCatalogManifestEntry>()

    for (suitCount in suitCounts) {
        val started = System.nanoTime()
        val result = scanForCertifiedSeeds(
            suitCount = suitCount,
            target = target,
            firstSeed = firstSeed,
            limits = limits,
            threads = threads,
            claimed = claimed,
            onProgress = { accepted, scanned -> log("  $suitCount: $accepted/$target certified, $scanned scanned") },
        )
        check(result.size == target) { "$suitCount: only found ${result.size} of $target certifiable seeds" }
        claimed.addAll(result.keys)
        bySuitCount[suitCount] = result

        val seeds = result.keys.sorted()
        val metadata = CatalogMetadata(
            catalogVersion = catalogVersion,
            rulesVersion = SPIDER_CATALOG_VERSIONS.rulesVersion,
            shuffleVersion = SPIDER_CATALOG_VERSIONS.shuffleVersion,
            solverVersion = SPIDER_SOLVER_VERSION,
            partitionId = suitCount.partitionId,
        )
        val bytes = encodeCatalog(metadata, seeds.map { it.toULong() })
        val file = File(directory, catalogFileName(suitCount))
        file.writeBytes(bytes)

        entries += SpiderCatalogManifestEntry(
            suitCount = suitCount,
            partitionId = suitCount.partitionId,
            recordCount = seeds.size,
            payloadHash = sha256Hex(bytes.copyOfRange(CatalogFormat.HEADER_SIZE_V1, bytes.size)),
            fileHash = sha256Hex(bytes),
        )
        val seconds = (System.nanoTime() - started) / 1_000_000_000.0
        log("  wrote ${file.name}: ${seeds.size} seeds, partition ${suitCount.partitionId} (${String.format("%.1fs", seconds)})")
    }

    val allSolutions = bySuitCount.values.fold(sortedMapOf<Long, List<Move>>()) { acc, m -> acc.putAll(m); acc }
    val solutionsBytes = SpiderSolutionCodec.encode(allSolutions)
    val compressed = gzip(solutionsBytes)
    val solutionsFile = File(solutionsPath).apply { parentFile?.mkdirs() }
    solutionsFile.writeBytes(compressed)
    log(
        "wrote ${allSolutions.size} solutions (${solutionsBytes.size} bytes, ${compressed.size} gzipped) " +
            "to $solutionsPath",
    )

    val manifest = File(directory, SPIDER_MANIFEST_NAME)
    manifest.writeText(manifestTextFor(entries, catalogVersion))
    log("")
    log("wrote ${entries.sumOf { it.recordCount }} seeds across ${entries.size} catalogs and ${manifest.name} to $outDir")
    log("run verifySpiderDealCatalogs to check what was written")
}

/**
 * Packages already-certified seed→line solutions from an external solver (`docs/games/spider/
 * EXECUTION_PLAN.md` "S6" — plspider, not [SpiderSolver]) into the same catalog/solutions/manifest
 * format [buildSpiderCatalogs] itself writes, so a suit count solved a different way still ships
 * through the one format every other suit count uses. Adds [suitCount]'s catalog to whatever
 * manifest and solutions blob already exist at [outDir]/[solutionsPath] — replacing only an
 * existing entry for the *same* [suitCount], never another one — since suit counts are expected to
 * accumulate here across separate build runs, not all arrive from one [buildSpiderCatalogs] call.
 *
 * **[solutions]' seeds must already be known disjoint from every other shipped suit count.**
 * [scanForCertifiedSeeds] enforces this itself (a shared `claimed` set across suit counts in one
 * call); an externally-solved suit count has no such call to share, so the caller must guarantee it
 * — a real S6 attempt scanned FOUR-suit seeds starting at 1, the same range ONE-suit's already-
 * shipped catalog claims, and 849 of 854 certified seeds turned out to collide with it. This
 * function refuses to silently ship a collision: it drops (not fails on) any seed already claimed
 * by another partition in the existing manifest, exactly mirroring [verifySpiderCatalogs]'s own
 * "seed appears in both X and Y" check, so a caller reusing a contaminated range gets a small
 * shipped catalog and a loud warning instead of a gate failure discovered only later.
 *
 * Every solution is independently replay-verified again here via [replayWinsForReal], redundant
 * with whatever the caller already did — cheap, and this file's whole point is that no catalog seed
 * is ever trusted past regardless of how many earlier checks it already passed.
 */
fun writeCatalogFromExternalSolutions(
    suitCount: SuitCount,
    solutions: Map<Long, List<Move>>,
    outDir: String = DEFAULT_SPIDER_CATALOG_DIR,
    solutionsPath: String = DEFAULT_SPIDER_SOLUTIONS_PATH,
    catalogVersion: Int = 1,
    log: (String) -> Unit = ::println,
) {
    val directory = File(outDir).apply { mkdirs() }
    val manifestFile = File(directory, SPIDER_MANIFEST_NAME)
    val existingEntries = if (manifestFile.exists()) parseManifest(manifestFile.readText()) else emptyList()
    val otherEntries = existingEntries.filter { it.suitCount != suitCount }

    val claimedElsewhere = otherEntries.flatMap { entry ->
        val bytes = File(directory, catalogFileName(entry.suitCount)).readBytes()
        when (val loaded = loadCatalog(bytes)) {
            is CatalogLoadResult.Valid -> loaded.seeds.map { it.toLong() }
            is CatalogLoadResult.Invalid -> error("${entry.suitCount}: ${loaded.reason} — refusing to write $suitCount alongside a catalog that does not even load")
        }
    }.toHashSet()

    val collisions = solutions.keys.filter { it in claimedElsewhere }
    if (collisions.isNotEmpty()) {
        log("  WARNING: dropping ${collisions.size} seed(s) already claimed by another partition: ${collisions.sorted().take(10)}${if (collisions.size > 10) "…" else ""}")
    }

    val verified = solutions.filterKeys { it !in claimedElsewhere }.filter { (seed, line) -> replayWinsForReal(seed, suitCount, line) }
    val rejected = solutions.size - collisions.size - verified.size
    if (rejected > 0) log("  WARNING: $rejected seed(s) failed replay re-verification and were dropped")

    val seeds = verified.keys.sorted()
    val metadata = CatalogMetadata(
        catalogVersion = catalogVersion,
        rulesVersion = SPIDER_CATALOG_VERSIONS.rulesVersion,
        shuffleVersion = SPIDER_CATALOG_VERSIONS.shuffleVersion,
        solverVersion = SPIDER_SOLVER_VERSION,
        partitionId = suitCount.partitionId,
    )
    val bytes = encodeCatalog(metadata, seeds.map { it.toULong() })
    File(directory, catalogFileName(suitCount)).writeBytes(bytes)

    val newEntry = SpiderCatalogManifestEntry(
        suitCount = suitCount,
        partitionId = suitCount.partitionId,
        recordCount = seeds.size,
        payloadHash = sha256Hex(bytes.copyOfRange(CatalogFormat.HEADER_SIZE_V1, bytes.size)),
        fileHash = sha256Hex(bytes),
    )
    manifestFile.writeText(manifestTextFor(otherEntries + newEntry, catalogVersion))

    val existingSolutions = File(solutionsPath).takeIf { it.exists() }?.readBytes()?.let { gunzip(it) }?.let { SpiderSolutionCodec.decode(it) } ?: emptyMap()
    // A seed can leave this suit count's catalog on a re-run (a smaller re-selection, not just
    // growth) without ever being reclaimed by another partition. Keeping its stale solutions-blob
    // entry around would leave an orphan compactSpiderSolutions can't attribute to any catalog, so
    // the merge is filtered down to exactly what the *current* catalogs (this write plus every
    // other partition) actually claim.
    val validSeeds = claimedElsewhere + seeds
    val mergedSolutions = (existingSolutions + verified.mapKeys { it.key }).filterKeys { it in validSeeds }
    val solutionsBytes = SpiderSolutionCodec.encode(mergedSolutions)
    File(solutionsPath).apply { parentFile?.mkdirs() }.writeBytes(gzip(solutionsBytes))

    log("wrote ${seeds.size} $suitCount seeds to ${catalogFileName(suitCount)} (${collisions.size} dropped for collision, $rejected dropped for failed re-verification)")
}

/**
 * The catalog gate: re-checks committed evidence and never solves anything, mirroring Klondike's
 * `verifyDealCatalogs` (`docs/games/spider/DEALS.md` "Catalog Gate").
 */
fun verifySpiderCatalogs(
    catalogDir: String = DEFAULT_SPIDER_CATALOG_DIR,
    solutionsPath: String = DEFAULT_SPIDER_SOLUTIONS_PATH,
    sampleSize: Int = 200,
    log: (String) -> Unit = ::println,
): Boolean {
    val directory = File(catalogDir)
    val manifestFile = File(directory, SPIDER_MANIFEST_NAME)
    if (!manifestFile.exists()) {
        log("no Spider catalog manifest at ${manifestFile.path} — nothing committed to verify yet")
        return true
    }

    val manifest = parseManifest(manifestFile.readText())
    check(manifest.isNotEmpty()) { "manifest at ${manifestFile.path} declares no catalogs" }
    val solutionsBytes = File(solutionsPath).takeIf { it.exists() }?.readBytes()?.let { gunzip(it) }
    val solutions = solutionsBytes?.let { SpiderSolutionCodec.decode(it) } ?: emptyMap()
    val failures = ArrayList<String>()
    val seen = HashMap<ULong, SuitCount>()

    for (entry in manifest) {
        val file = File(directory, catalogFileName(entry.suitCount))
        if (!file.exists()) {
            failures += "${entry.suitCount}: manifest declares ${file.name} but it is not committed"
            continue
        }
        val bytes = file.readBytes()
        if (sha256Hex(bytes) != entry.fileHash) {
            failures += "${entry.suitCount}: file hash does not match the manifest"
            continue
        }

        when (val loaded = loadCatalog(bytes)) {
            is CatalogLoadResult.Invalid -> failures += "${entry.suitCount}: ${loaded.reason}"
            is CatalogLoadResult.Valid -> {
                val header = loaded.header
                if (header.recordCount != entry.recordCount) {
                    failures += "${entry.suitCount}: header declares ${header.recordCount} records, manifest ${entry.recordCount}"
                }
                if (header.partitionId != entry.partitionId) {
                    failures += "${entry.suitCount}: header partition ${header.partitionId}, manifest ${entry.partitionId}"
                }
                if (sha256Hex(bytes.copyOfRange(CatalogFormat.HEADER_SIZE_V1, bytes.size)) != entry.payloadHash) {
                    failures += "${entry.suitCount}: payload hash does not match the manifest"
                }
                for (seed in loaded.seeds) {
                    val claimedBy = seen.put(seed, entry.suitCount)
                    if (claimedBy != null) failures += "seed $seed appears in both $claimedBy and ${entry.suitCount}"
                }
                failures += replayFailures(entry, loaded.seeds, solutions, sampleSize)
                log("  ${entry.suitCount}: ${loaded.seeds.size} seeds ok")
            }
        }
    }

    if (failures.isEmpty()) {
        log("Spider catalog gate passed: ${manifest.sumOf { it.recordCount }} seeds across ${manifest.size} catalogs")
        return true
    }
    log("Spider catalog gate FAILED:")
    for (failure in failures) log("  - $failure")
    return false
}

private fun replayFailures(
    entry: SpiderCatalogManifestEntry,
    seeds: List<ULong>,
    solutions: Map<Long, List<Move>>,
    sampleSize: Int,
): List<String> {
    if (solutions.isEmpty()) return listOf("${entry.suitCount}: no solutions asset to replay")
    val step = (seeds.size / sampleSize).coerceAtLeast(1)
    val sample = seeds.filterIndexed { i, _ -> i % step == 0 }.take(sampleSize)

    val failures = ArrayList<String>()
    for (seed in sample) {
        val signed = seed.toLong()
        val line = solutions[signed]
        if (line == null) {
            failures += "${entry.suitCount}: seed $signed has no certificate"
            continue
        }
        if (!replayWinsForReal(signed, entry.suitCount, line)) {
            failures += "${entry.suitCount}: seed $signed does not replay to a win under the current rules"
        }
    }
    return failures
}

internal data class SpiderCatalogManifestEntry(
    val suitCount: SuitCount,
    val partitionId: Byte,
    val recordCount: Int,
    val payloadHash: String,
    val fileHash: String,
)

/**
 * The partition id a suit count ships under (`docs/solitaire/CATALOG.md` "Partitions"). Fixed: a
 * saved traversal position names its partition by id, so renumbering moves a player to a different
 * suit count's position.
 */
internal val SuitCount.partitionId: Byte get() = (ordinal + 1).toByte()

internal fun catalogFileName(suitCount: SuitCount): String = "${suitCount.name.lowercase()}.catalog"

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/**
 * Spider's own move log is far more verbose per solved deal than Klondike's bit-packed,
 * draws-omitted choice list — twenty thousand certificates run to about 20 MB raw, which alone
 * would exceed the platform's entire 15 MB release-download budget (`docs/PLATFORM.md`
 * "Performance") for an asset the app does not even read yet (`docs/games/spider/DEALS.md`
 * "Shipped solutions"). Gzip gets it under 10 MB; kept as a build/verification artifact outside
 * `assets/` rather than shipped, mirroring Klondike's own documented fallback for oversized
 * solution sets (`docs/games/klondike/DEALS.md` "Build Artifacts").
 */
private fun gzip(bytes: ByteArray): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    GZIPOutputStream(out).use { it.write(bytes) }
    return out.toByteArray()
}

private fun gunzip(bytes: ByteArray): ByteArray = GZIPInputStream(bytes.inputStream()).use { it.readBytes() }

internal fun manifestTextFor(entries: List<SpiderCatalogManifestEntry>, catalogVersion: Int): String = buildString {
    appendLine("{")
    appendLine("""  "formatVersion": ${CatalogFormat.FORMAT_VERSION_1},""")
    appendLine("""  "catalogVersion": $catalogVersion,""")
    appendLine("""  "rulesVersion": ${SPIDER_CATALOG_VERSIONS.rulesVersion},""")
    appendLine("""  "shuffleVersion": ${SPIDER_CATALOG_VERSIONS.shuffleVersion},""")
    appendLine("""  "solverVersion": $SPIDER_SOLVER_VERSION,""")
    appendLine("""  "generatedAt": "${Instant.now()}",""")
    appendLine("""  "totalSeeds": ${entries.sumOf { it.recordCount }},""")
    appendLine("""  "catalogs": [""")
    entries.forEachIndexed { i, entry ->
        appendLine("    {")
        appendLine("""      "suitCount": "${entry.suitCount.name}",""")
        appendLine("""      "file": "${catalogFileName(entry.suitCount)}",""")
        appendLine("""      "partitionId": ${entry.partitionId},""")
        appendLine("""      "recordCount": ${entry.recordCount},""")
        appendLine("""      "payloadHash": "${entry.payloadHash}",""")
        appendLine("""      "fileHash": "${entry.fileHash}"""")
        appendLine("    }${if (i == entries.lastIndex) "" else ","}")
    }
    appendLine("  ]")
    appendLine("}")
}

internal fun parseManifest(text: String): List<SpiderCatalogManifestEntry> {
    val blocks = Regex("""\{\s*"suitCount".*?"fileHash"\s*:\s*"([0-9a-f]{64})"\s*}""", RegexOption.DOT_MATCHES_ALL)
        .findAll(text).map { it.value }.toList()
    return blocks.map { block ->
        fun field(name: String) = Regex(""""$name"\s*:\s*"?([A-Za-z0-9_]+)"?""").find(block)?.groupValues?.get(1)
            ?: error("manifest entry is missing \"$name\"")
        SpiderCatalogManifestEntry(
            suitCount = SuitCount.valueOf(field("suitCount")),
            partitionId = field("partitionId").toByte(),
            recordCount = field("recordCount").toInt(),
            payloadHash = field("payloadHash"),
            fileHash = field("fileHash"),
        )
    }
}

internal const val DEFAULT_SPIDER_CATALOG_DIR = "games/spider/app/src/main/assets/catalogs"

// Not under assets/: gzipped, this is still ~8 MB, and nothing in the app reads it yet. Kept as a
// committed verification artifact so `verifySpiderDealCatalogs` can replay-check without
// re-solving, not as a shipped asset (`docs/games/spider/DEALS.md` "Shipped solutions").
internal const val DEFAULT_SPIDER_SOLUTIONS_PATH = "tools/catalog/data/spider_solutions.bin.gz"
internal const val SPIDER_MANIFEST_NAME = "manifest.json"
