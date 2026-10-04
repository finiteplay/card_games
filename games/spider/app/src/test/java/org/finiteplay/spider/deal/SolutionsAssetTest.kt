package org.finiteplay.spider.deal

import java.io.File
import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.dealGame
import org.finiteplay.spider.rules.applyMove
import org.finiteplay.spider.rules.isLegal
import org.finiteplay.spider.solution.CompactSolutionCodec
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The permanent gate for the shipped `assets/solutions.bin`: decodes it directly (not through
 * [SpiderSolutionCatalog], which needs an Android `Context`) and independently replays a sample of
 * committed seeds from each certified suit count through the real reducer, so a future corruption
 * of this asset — a bad re-encode, a bit-packing regression — fails `check` instead of surfacing to
 * a player as a missing or wrong hint.
 *
 * Mirrors `verifySpiderDealCatalogs`'s own replay-sample convention (`tools/catalog`) and
 * FreeCell's own `SolutionsAssetTest`, but runs as an ordinary JVM test here because `:app` must
 * never depend on `:tools:catalog` (`assertAppExcludesSolver`) — this only needs
 * `:games:spider:rules`, which `:app` already depends on.
 */
class SolutionsAssetTest {
    private val versions = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1)

    private fun readAsset(name: String) = File("src/main/assets/$name").readBytes()

    private fun sample(seeds: List<Long>, sampleSize: Int = 200): List<Long> {
        val step = (seeds.size / sampleSize).coerceAtLeast(1)
        return seeds.filterIndexed { i, _ -> i % step == 0 }.take(sampleSize)
    }

    @Test
    fun `every sampled seed's shipped solution replays to a real win`() {
        val solutionsBytes = readAsset("solutions.bin")
        val index = CompactSolutionCodec.readIndex(solutionsBytes)

        val failures = mutableListOf<String>()
        for (suitCount in listOf(SuitCount.ONE, SuitCount.TWO)) {
            val loaded = SpiderCertifiedDealCatalog.load { readAsset("catalogs/$it") }
            check(loaded is SpiderCertifiedDealCatalog.LoadResult.Valid) { "catalog asset did not load: $loaded" }
            val seeds = loaded.catalog.seedsFor(suitCount) ?: error("$suitCount has no certified seeds")

            for (seed in sample(seeds)) {
                var state = dealGame(seed, versions, suitCount)
                val line = CompactSolutionCodec.decodeLine(solutionsBytes, index, state, seed)
                if (line == null) {
                    failures += "$suitCount seed $seed has no stored solution"
                    continue
                }
                var ok = true
                for (move in line) {
                    if (!isLegal(state, move)) {
                        failures += "$suitCount seed $seed: move $move illegal mid-line"
                        ok = false
                        break
                    }
                    state = applyMove(state, move)
                }
                if (ok && !state.isWon) failures += "$suitCount seed $seed's stored line does not win"
            }
        }

        assertTrue("solution replay failures:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun `the index covers every certified ONE and TWO suit seed`() {
        val solutionsBytes = readAsset("solutions.bin")
        val index = CompactSolutionCodec.readIndex(solutionsBytes)

        val loaded = SpiderCertifiedDealCatalog.load { readAsset("catalogs/$it") }
        check(loaded is SpiderCertifiedDealCatalog.LoadResult.Valid) { "catalog asset did not load: $loaded" }

        for (suitCount in listOf(SuitCount.ONE, SuitCount.TWO)) {
            val seeds = loaded.catalog.seedsFor(suitCount) ?: error("$suitCount has no certified seeds")
            val missing = seeds.filter { it !in index }
            assertTrue(
                "$suitCount seeds missing a stored solution: ${missing.take(20)} (${missing.size} total)",
                missing.isEmpty(),
            )
        }
    }
}
