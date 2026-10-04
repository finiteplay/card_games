package org.finiteplay.spider.tools.catalog

import java.io.File
import java.util.zip.GZIPInputStream
import org.finiteplay.solitaire.catalog.catalog.CatalogLoadResult
import org.finiteplay.solitaire.catalog.catalog.loadCatalog
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.dealGame
import org.finiteplay.spider.solution.CompactSolutionCodec

/**
 * Converts the committed verification artifact (`tools/catalog/data/spider_solutions.bin.gz`,
 * `SpiderSolutionCodec` — full moves, not bit-packed, per `docs/games/spider/DEALS.md` "Solutions")
 * into the bit-packed, seekable `CompactSolutionCodec` format and writes it to
 * `games/spider/app/src/main/assets/solutions.bin`, the asset the app actually reads.
 *
 * No re-solving here: existing certificate lengths (111-473 moves) are already realistic, and the
 * size problem this closes is purely encoding inefficiency (`DEALS.md`'s own account of the raw
 * and gzipped sizes), unlike FreeCell's shrink pass, which re-solved because its DFS-derived
 * certificates ran to thousands of moves. `CompactSolutionCodec.encodeCatalog` never trusts a
 * decoded line on the gzip blob's word alone — it independently replays every seed's line through
 * the real reducer before packing it, dropping (not failing on) any that does not actually win.
 */
fun compactSpiderSolutions(
    solutionsPath: String = DEFAULT_SPIDER_SOLUTIONS_PATH,
    catalogDir: String = DEFAULT_SPIDER_CATALOG_DIR,
    assetPath: String = DEFAULT_SPIDER_SOLUTIONS_ASSET_PATH,
    log: (String) -> Unit = ::println,
) {
    val gzipped = File(solutionsPath).readBytes()
    val raw = GZIPInputStream(gzipped.inputStream()).use { it.readBytes() }
    val solutions = SpiderSolutionCodec.decode(raw)
    log("read ${solutions.size} solutions from $solutionsPath (${gzipped.size} bytes gzipped, ${raw.size} bytes raw)")

    val suitCountOf = loadSuitCountBySeed(catalogDir)
    val compact = CompactSolutionCodec.encodeCatalog(solutions) { seed ->
        val suitCount = suitCountOf[seed]
            ?: error("seed $seed is not in either committed catalog — cannot tell which suit count it was certified at")
        dealGame(seed = seed, versions = SPIDER_CATALOG_VERSIONS, suitCount = suitCount)
    }
    val dropped = solutions.size - CompactSolutionCodec.readIndex(compact).size
    if (dropped > 0) log("WARNING: $dropped seed(s) failed independent replay and were dropped from the compact asset")

    val assetFile = File(assetPath).apply { parentFile?.mkdirs() }
    assetFile.writeBytes(compact)
    log("wrote ${compact.size} bytes (compact, bit-packed) to $assetPath")
}

/**
 * Every certified seed's suit count, read once from the committed ONE/TWO-suit catalogs rather
 * than re-parsed per lookup — a bare seed number does not name a board on its own
 * (`docs/games/spider/DEALS.md` "Unlike Klondike's difficulty partitions...").
 */
private fun loadSuitCountBySeed(catalogDir: String): Map<Long, SuitCount> {
    val result = HashMap<Long, SuitCount>()
    // Every suit count with a committed catalog file, not just the ONE/TWO the app currently
    // loads for certified traversal (`SpiderCertifiedDealCatalog`) — the solutions blob this
    // reads may hold more than what is wired into live play (e.g. an externally-solved FOUR
    // partition, `writeCatalogFromExternalSolutions`), and every seed it has still needs its
    // own board rebuilt correctly to be packed.
    for (suitCount in SuitCount.entries) {
        val file = File(catalogDir, catalogFileName(suitCount))
        if (!file.exists()) continue
        when (val loaded = loadCatalog(file.readBytes())) {
            is CatalogLoadResult.Valid -> for (seed in loaded.seeds) result[seed.toLong()] = suitCount
            is CatalogLoadResult.Invalid -> error("${file.path}: ${loaded.reason}")
        }
    }
    return result
}

internal const val DEFAULT_SPIDER_SOLUTIONS_ASSET_PATH = "games/spider/app/src/main/assets/solutions.bin"
