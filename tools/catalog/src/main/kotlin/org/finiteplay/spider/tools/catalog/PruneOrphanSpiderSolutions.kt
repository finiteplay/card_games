package org.finiteplay.spider.tools.catalog

import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.finiteplay.solitaire.catalog.catalog.CatalogLoadResult
import org.finiteplay.solitaire.catalog.catalog.loadCatalog
import org.finiteplay.spider.layout.SuitCount

/**
 * One-off cleanup: the solutions blob at [DEFAULT_SPIDER_SOLUTIONS_PATH] can accumulate entries for
 * seeds no longer claimed by any committed catalog (a suit count re-selected to a smaller seed set
 * without the old blob being pruned — the bug [writeCatalogFromExternalSolutions] itself now guards
 * against on its own writes, but this repairs a blob already written before that fix existed).
 * Filters the blob down to exactly the seeds the current manifest's catalogs claim.
 */
fun main() {
    val catalogDir = File(DEFAULT_SPIDER_CATALOG_DIR)
    val validSeeds = HashSet<Long>()
    for (suitCount in SuitCount.entries) {
        val file = File(catalogDir, catalogFileName(suitCount))
        if (!file.exists()) continue
        when (val loaded = loadCatalog(file.readBytes())) {
            is CatalogLoadResult.Valid -> loaded.seeds.forEach { validSeeds.add(it.toLong()) }
            is CatalogLoadResult.Invalid -> error("${file.path}: ${loaded.reason}")
        }
    }
    println("current catalogs claim ${validSeeds.size} seeds")

    val solutionsFile = File(DEFAULT_SPIDER_SOLUTIONS_PATH)
    val raw = GZIPInputStream(solutionsFile.readBytes().inputStream()).use { it.readBytes() }
    val solutions = SpiderSolutionCodec.decode(raw)
    println("read ${solutions.size} solutions from $DEFAULT_SPIDER_SOLUTIONS_PATH")

    val pruned = solutions.filterKeys { it in validSeeds }
    val orphans = solutions.size - pruned.size
    println("dropping $orphans orphaned solution(s) not claimed by any current catalog")

    val prunedBytes = SpiderSolutionCodec.encode(pruned)
    val out = java.io.ByteArrayOutputStream()
    GZIPOutputStream(out).use { it.write(prunedBytes) }
    solutionsFile.writeBytes(out.toByteArray())
    println("wrote ${pruned.size} solutions back to $DEFAULT_SPIDER_SOLUTIONS_PATH")
}
