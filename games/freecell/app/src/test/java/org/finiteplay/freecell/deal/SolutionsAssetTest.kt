package org.finiteplay.freecell.deal

import java.io.File
import org.finiteplay.freecell.layout.GameVersions
import org.finiteplay.freecell.layout.dealGame
import org.finiteplay.freecell.rules.applyMove
import org.finiteplay.freecell.rules.isLegal
import org.finiteplay.freecell.solution.CompactSolutionCodec
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The permanent gate for the shipped `assets/solutions.bin`: decodes it directly (not through
 * [FreeCellSolutionCatalog], which needs an Android `Context`) and independently replays a sample
 * of committed seeds through the real reducer, so a future corruption of this asset — a bad
 * re-encode, a bit-packing regression — fails `check` instead of surfacing to a player as a
 * missing or wrong hint.
 *
 * Mirrors `verifyFreeCellDealCatalogs`'s own replay-sample convention (`tools/catalog`), but runs
 * as an ordinary JVM test here because `:app` must never depend on `:tools:catalog`
 * (`assertAppExcludesSolver`'s sibling rule) — this only needs `:games:freecell:rules`, which
 * `:app` already depends on.
 */
class SolutionsAssetTest {
    private val versions = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1)

    private fun readAsset(name: String) = File("src/main/assets/$name").readBytes()

    @Test
    fun `every sampled seed's shipped solution replays to a real win`() {
        val catalogBytes = readAsset("catalogs/freecell.catalog")
        val loaded = FreeCellCertifiedDealCatalog.load { catalogBytes }
        check(loaded is FreeCellCertifiedDealCatalog.LoadResult.Valid) { "catalog asset did not load: $loaded" }
        val seeds = loaded.catalog.seeds

        val solutionsBytes = readAsset("solutions.bin")
        val index = CompactSolutionCodec.readIndex(solutionsBytes)

        val sampleSize = 200
        val step = (seeds.size / sampleSize).coerceAtLeast(1)
        val sample = seeds.filterIndexed { i, _ -> i % step == 0 }.take(sampleSize)
        assertTrue("expected a sample to check", sample.isNotEmpty())

        val failures = mutableListOf<String>()
        for (seed in sample) {
            val line = CompactSolutionCodec.decodeLine(solutionsBytes, index, seed)
            if (line == null) {
                failures += "seed $seed has no stored solution"
                continue
            }
            var state = dealGame(seed, versions)
            for (move in line) {
                if (!isLegal(state, move)) {
                    failures += "seed $seed: move $move illegal mid-line"
                    break
                }
                state = applyMove(state, move)
            }
            if (!state.isWon) failures += "seed $seed's stored line does not win"
        }

        assertTrue("solution replay failures:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun `the index covers every certified seed`() {
        val catalogBytes = readAsset("catalogs/freecell.catalog")
        val loaded = FreeCellCertifiedDealCatalog.load { catalogBytes }
        check(loaded is FreeCellCertifiedDealCatalog.LoadResult.Valid) { "catalog asset did not load: $loaded" }

        val solutionsBytes = readAsset("solutions.bin")
        val index = CompactSolutionCodec.readIndex(solutionsBytes)

        val missing = loaded.catalog.seeds.filter { it !in index }
        assertTrue("seeds missing a stored solution: ${missing.take(20)} (${missing.size} total)", missing.isEmpty())
    }
}
