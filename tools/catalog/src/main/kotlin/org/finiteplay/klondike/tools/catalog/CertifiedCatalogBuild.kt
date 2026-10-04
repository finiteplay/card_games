package org.finiteplay.klondike.tools.catalog

import java.io.File
import java.security.MessageDigest
import java.time.Instant
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.klondike.solution.CompactSolutionCodec
import org.finiteplay.solitaire.catalog.catalog.CatalogFormat
import org.finiteplay.solitaire.catalog.catalog.CatalogLoadResult
import org.finiteplay.solitaire.catalog.catalog.CatalogMetadata
import org.finiteplay.solitaire.catalog.catalog.encodeCatalog
import org.finiteplay.solitaire.catalog.catalog.loadCatalog

/**
 * D1b: turns the six graded level lists into six certified binary catalogs, a manifest, and the
 * evidence `verifyDealCatalogs` re-checks in CI (`docs/games/klondike/DEALS.md`).
 *
 * This selects nothing and solves nothing. `build-levels` already decided which ten thousand
 * seeds each level ships and already replay-verified every line it wrote into `solutions.bin`;
 * this packages that work into the versioned format the app's loader and the catalog gate are
 * specified against. Anything that changes *membership* goes through `build-levels` first.
 *
 * One catalog per level rather than one file with a level column: a level is a
 * [partition][CatalogFormat.OFFSET_PARTITION_ID] (`docs/solitaire/CATALOG.md`), traversal keeps a
 * position per partition, and a player switching levels must not disturb the others' positions.
 */
fun buildCertifiedCatalogs(
    dealDir: String = DEFAULT_DEAL_DIR,
    solutionsPath: String = DEFAULT_SOLUTIONS_PATH,
    outDir: String = DEFAULT_CATALOG_DIR,
    catalogVersion: Int = 1,
    log: (String) -> Unit = ::println,
) {
    val solutionsFile = File(solutionsPath)
    check(solutionsFile.exists()) { "no solutions asset at $solutionsPath (from ${File("").absolutePath})" }
    val solutionIndex = CompactSolutionCodec.readIndex(solutionsFile.readBytes())
    check(solutionIndex.size > 0) { "read no solutions from $solutionsPath" }
    log("read ${solutionIndex.size} solutions from $solutionsPath")

    val directory = File(outDir).apply { mkdirs() }
    val seen = HashMap<Long, CatalogLevel>()
    val entries = ArrayList<CatalogManifestEntry>()

    for (level in CatalogLevel.entries) {
        val exported = readExportedSeeds(dealDir, level)
        check(exported.isNotEmpty()) { "no exported seeds for $level under $dealDir" }

        for (seed in exported) {
            val claimedBy = seen.put(seed, level)
            check(claimedBy == null) { "seed $seed appears in both $claimedBy and $level" }
        }

        // Ascending in the file; which deal the player meets first is traversal's job, so the
        // presentation permutation `build-levels` applies to the Kotlin lists is not carried over
        // and is not needed once a catalog is traversed with a random start and coprime step.
        val seeds = exported.sorted()
        check(seeds.first() >= 0) { "$level: a seed must be non-negative to store as an unsigned record" }

        val certified = level != CatalogLevel.INSANE
        if (certified) {
            val missing = seeds.count { it !in solutionIndex }
            check(missing == 0) { "$level: $missing of ${seeds.size} seeds have no certificate in $solutionsPath" }
        }

        val metadata = CatalogMetadata(
            catalogVersion = catalogVersion,
            rulesVersion = D1S_SPIKE_VERSIONS.rulesVersion,
            shuffleVersion = D1S_SPIKE_VERSIONS.shuffleVersion,
            solverVersion = SOLVER_VERSION,
            partitionId = level.partitionId,
        )
        val bytes = encodeCatalog(metadata, seeds.map { it.toULong() })
        val file = File(directory, catalogFileName(level))
        file.writeBytes(bytes)

        entries += CatalogManifestEntry(
            level = level,
            partitionId = level.partitionId,
            recordCount = seeds.size,
            payloadHash = sha256Hex(bytes.copyOfRange(CatalogFormat.HEADER_SIZE_V1, bytes.size)),
            fileHash = sha256Hex(bytes),
            certified = certified,
        )
        log("  wrote ${file.name}: ${seeds.size} seeds, partition ${level.partitionId}${if (certified) "" else ", uncertified"}")
    }

    val manifest = File(directory, MANIFEST_NAME)
    manifest.writeText(manifestTextFor(entries, catalogVersion))
    log("")
    log("wrote ${entries.sumOf { it.recordCount }} seeds across ${entries.size} catalogs and ${manifest.name} to $outDir")
    log("run `verifyDealCatalogs` to check what was written")
}

/**
 * The catalog gate: re-checks committed evidence and never solves anything
 * (`docs/games/klondike/ACCEPTANCE.md` "Catalog Gate").
 *
 * [sampleSize] certificates per level are replayed through the canonical reducer. A full replay of
 * fifty thousand lines is a catalog-build cost, not a per-CI one; the sample is what keeps this a
 * gate that runs on every change. Passing it means the committed bytes are internally consistent
 * and a representative certificate still wins under the *current* rules — which is what catches a
 * rules change landing without a catalog regeneration.
 */
fun verifyCertifiedCatalogs(
    catalogDir: String = DEFAULT_CATALOG_DIR,
    solutionsPath: String = DEFAULT_SOLUTIONS_PATH,
    sampleSize: Int = 25,
    log: (String) -> Unit = ::println,
): Boolean {
    val directory = File(catalogDir)
    val manifestFile = File(directory, MANIFEST_NAME)
    if (!manifestFile.exists()) {
        // Not a failure: D1b has not generated yet, and the plan forbids generating before the
        // rules freeze. The gate exists ahead of the artifact on purpose.
        log("no catalog manifest at ${manifestFile.path} — nothing committed to verify yet")
        return true
    }

    val manifest = parseManifest(manifestFile.readText())
    check(manifest.isNotEmpty()) { "manifest at ${manifestFile.path} declares no catalogs" }
    val solutionsBytes = File(solutionsPath).takeIf { it.exists() }?.readBytes()
    val failures = ArrayList<String>()
    val seen = HashMap<ULong, CatalogLevel>()

    for (entry in manifest) {
        val file = File(directory, catalogFileName(entry.level))
        if (!file.exists()) {
            failures += "${entry.level}: manifest declares ${file.name} but it is not committed"
            continue
        }
        val bytes = file.readBytes()

        if (sha256Hex(bytes) != entry.fileHash) {
            failures += "${entry.level}: file hash does not match the manifest"
            continue
        }

        when (val loaded = loadCatalog(bytes)) {
            is CatalogLoadResult.Invalid -> failures += "${entry.level}: ${loaded.reason}"
            is CatalogLoadResult.Valid -> {
                val header = loaded.header
                if (header.recordCount != entry.recordCount) {
                    failures += "${entry.level}: header declares ${header.recordCount} records, manifest ${entry.recordCount}"
                }
                if (header.partitionId != entry.partitionId) {
                    failures += "${entry.level}: header partition ${header.partitionId}, manifest ${entry.partitionId}"
                }
                if (sha256Hex(bytes.copyOfRange(CatalogFormat.HEADER_SIZE_V1, bytes.size)) != entry.payloadHash) {
                    failures += "${entry.level}: payload hash does not match the manifest"
                }
                for (seed in loaded.seeds) {
                    val claimedBy = seen.put(seed, entry.level)
                    if (claimedBy != null) failures += "seed $seed appears in both $claimedBy and ${entry.level}"
                }
                failures += replayFailures(entry, loaded.seeds, solutionsBytes, sampleSize)
                log("  ${entry.level}: ${loaded.seeds.size} seeds ok${if (entry.certified) "" else " (uncertified level)"}")
            }
        }
    }

    if (failures.isEmpty()) {
        log("catalog gate passed: ${manifest.sumOf { it.recordCount }} seeds across ${manifest.size} catalogs")
        return true
    }
    log("catalog gate FAILED:")
    for (failure in failures) log("  - $failure")
    return false
}

/** Replays [sampleSize] of [seeds]' certificates through the canonical reducer. */
private fun replayFailures(
    entry: CatalogManifestEntry,
    seeds: List<ULong>,
    solutionsBytes: ByteArray?,
    sampleSize: Int,
): List<String> {
    if (!entry.certified) return emptyList()
    if (solutionsBytes == null) return listOf("${entry.level}: certified level but no solutions asset to replay")

    val index = CompactSolutionCodec.readIndex(solutionsBytes)
    // Evenly spaced rather than the first N: the head of a level is its own end of the ranking,
    // and a sample taken from one end is not a sample of the level.
    val step = (seeds.size / sampleSize).coerceAtLeast(1)
    val sample = seeds.filterIndexed { i, _ -> i % step == 0 }.take(sampleSize)

    val failures = ArrayList<String>()
    for (seed in sample) {
        val signed = seed.toLong()
        if (signed !in index) {
            failures += "${entry.level}: seed $signed has no certificate"
            continue
        }
        val deal = dealGame(signed, D1S_SPIKE_VERSIONS)
        val line = runCatching { CompactSolutionCodec.decodeLine(solutionsBytes, index, deal, signed) }.getOrNull()
        if (line == null || !replayWinsFromDeal(signed, line)) {
            failures += "${entry.level}: seed $signed does not replay to a win under the current rules"
        }
    }
    return failures
}

internal data class CatalogManifestEntry(
    val level: CatalogLevel,
    val partitionId: Byte,
    val recordCount: Int,
    val payloadHash: String,
    val fileHash: String,
    val certified: Boolean,
)

/**
 * The partition id a level ships under (`docs/solitaire/CATALOG.md` "Partitions"). Fixed: a saved
 * traversal position names its partition by id, so renumbering moves a player to another level.
 */
internal val CatalogLevel.partitionId: Byte get() = (ordinal + 1).toByte()

internal fun catalogFileName(level: CatalogLevel): String = "${level.name.lowercase()}.catalog"

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/**
 * Hand-rendered rather than serialized: the shape is flat and fixed, and a manifest that a human
 * reads in a review diff is worth more here than a dependency.
 */
internal fun manifestTextFor(entries: List<CatalogManifestEntry>, catalogVersion: Int = 1): String = buildString {
    appendLine("{")
    appendLine("""  "formatVersion": ${CatalogFormat.FORMAT_VERSION_1},""")
    appendLine("""  "catalogVersion": $catalogVersion,""")
    appendLine("""  "rulesVersion": ${D1S_SPIKE_VERSIONS.rulesVersion},""")
    appendLine("""  "shuffleVersion": ${D1S_SPIKE_VERSIONS.shuffleVersion},""")
    appendLine("""  "solverVersion": $SOLVER_VERSION,""")
    appendLine("""  "generatedAt": "${Instant.now()}",""")
    appendLine("""  "totalSeeds": ${entries.sumOf { it.recordCount }},""")
    appendLine("""  "catalogs": [""")
    entries.forEachIndexed { i, entry ->
        appendLine("    {")
        appendLine("""      "level": "${entry.level.name}",""")
        appendLine("""      "file": "${catalogFileName(entry.level)}",""")
        appendLine("""      "partitionId": ${entry.partitionId},""")
        appendLine("""      "recordCount": ${entry.recordCount},""")
        appendLine("""      "certified": ${entry.certified},""")
        appendLine("""      "payloadHash": "${entry.payloadHash}",""")
        appendLine("""      "fileHash": "${entry.fileHash}"""")
        appendLine("    }${if (i == entries.lastIndex) "" else ","}")
    }
    appendLine("  ]")
    appendLine("}")
}

/** Reads back exactly what [renderManifest] writes; deliberately strict about the shape it expects. */
internal fun parseManifest(text: String): List<CatalogManifestEntry> {
    val blocks = Regex("""\{\s*"level".*?"fileHash"\s*:\s*"([0-9a-f]{64})"\s*}""", RegexOption.DOT_MATCHES_ALL)
        .findAll(text).map { it.value }.toList()
    return blocks.map { block ->
        fun field(name: String) = Regex(""""$name"\s*:\s*"?([A-Za-z0-9_]+)"?""").find(block)?.groupValues?.get(1)
            ?: error("manifest entry is missing \"$name\"")
        CatalogManifestEntry(
            level = CatalogLevel.valueOf(field("level")),
            partitionId = field("partitionId").toByte(),
            recordCount = field("recordCount").toInt(),
            payloadHash = field("payloadHash"),
            fileHash = field("fileHash"),
            certified = field("certified").toBooleanStrict(),
        )
    }
}

/** Bumped with any change to the search that produces a certificate, per `DEALS.md` "Versioned Rules". */
private const val SOLVER_VERSION = 1

internal const val DEFAULT_DEAL_DIR = "games/klondike/app/src/main/java/org/finiteplay/klondike/deal"
internal const val DEFAULT_SOLUTIONS_PATH = "games/klondike/app/src/main/assets/solutions.bin"
internal const val DEFAULT_CATALOG_DIR = "games/klondike/app/src/main/assets/catalogs"
internal const val MANIFEST_NAME = "manifest.json"
