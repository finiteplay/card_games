package org.finiteplay.spider.deal

import android.content.Context
import org.finiteplay.solitaire.catalog.catalog.CatalogLoadResult as ParsedCatalogLoadResult
import org.finiteplay.solitaire.catalog.catalog.loadCatalog
import org.finiteplay.spider.layout.SuitCount

/**
 * The validated ONE-, TWO- and FOUR-suit catalogs bundled with the app (`docs/games/spider/DEALS.md`).
 * FOUR's was certified by an external solver and imported; every suit count now deals from its own
 * catalog, and the uncertified formula (`DealSequence`) remains only for a game built without one
 * (tests).
 *
 * Unlike Klondike's difficulty partitions, Spider's suit-count partitions are not mutually
 * exclusive by construction — the same seed number means a different board at each suit count
 * (`dealGame` takes the suit count as part of the shuffle) — but the certifying build still keeps
 * every seed number unique across the shipped partitions, so a seed never has to be
 * disambiguated by which catalog it came from.
 */
class SpiderCertifiedDealCatalog private constructor(
    val catalogVersion: Int,
    private val seedsBySuitCount: Map<SuitCount, List<Long>>,
) {
    fun seedsFor(suitCount: SuitCount): List<Long>? = seedsBySuitCount[suitCount]

    companion object {
        private val CERTIFIED_SUIT_COUNTS = listOf(SuitCount.ONE, SuitCount.TWO, SuitCount.FOUR)
        private const val ASSET_DIRECTORY = "catalogs"
        private const val EXPECTED_VERSION = 1

        fun load(context: Context): LoadResult = load { name ->
            context.assets.open("$ASSET_DIRECTORY/$name").use { it.readBytes() }
        }

        internal fun load(readAsset: (String) -> ByteArray): LoadResult {
            val catalogs = linkedMapOf<SuitCount, List<Long>>()
            var version: Int? = null
            for (suitCount in CERTIFIED_SUIT_COUNTS) {
                val name = "${suitCount.name.lowercase()}.catalog"
                val loaded = try {
                    loadCatalog(readAsset(name))
                } catch (e: Exception) {
                    return LoadResult.Invalid("could not read $name: ${e.message ?: e.javaClass.simpleName}")
                }
                if (loaded is ParsedCatalogLoadResult.Invalid) return LoadResult.Invalid(loaded.reason)
                loaded as ParsedCatalogLoadResult.Valid
                val header = loaded.header
                if (header.catalogVersion != EXPECTED_VERSION ||
                    header.rulesVersion != EXPECTED_VERSION ||
                    header.shuffleVersion != EXPECTED_VERSION ||
                    header.solverVersion != EXPECTED_VERSION
                ) {
                    return LoadResult.Invalid("unexpected versions in $name")
                }
                if (header.partitionId.toInt() != suitCount.ordinal + 1) {
                    return LoadResult.Invalid("unexpected partition in $name")
                }
                if (version != null && version != header.catalogVersion) {
                    return LoadResult.Invalid("catalog versions disagree")
                }
                version = header.catalogVersion
                catalogs[suitCount] = loaded.seeds.map { it.toLong() }
            }
            return LoadResult.Valid(SpiderCertifiedDealCatalog(requireNotNull(version), catalogs))
        }
    }

    sealed class LoadResult {
        data class Valid(val catalog: SpiderCertifiedDealCatalog) : LoadResult()
        data class Invalid(val reason: String) : LoadResult()
    }
}
