package org.finiteplay.klondike.deal

import android.content.Context
import org.finiteplay.solitaire.catalog.catalog.CatalogLoadResult as ParsedCatalogLoadResult
import org.finiteplay.solitaire.catalog.catalog.loadCatalog

/** The validated draw-one catalogs bundled with the app. */
class CertifiedDealCatalog private constructor(
    val catalogVersion: Int,
    private val seedsByDifficulty: Map<DifficultyTier, List<Long>>,
) {
    fun seedsFor(difficulty: DifficultyTier): List<Long> = seedsByDifficulty.getValue(difficulty)

    fun difficultyOf(seed: Long): DifficultyTier? = seedsByDifficulty.entries.firstOrNull { seed in it.value }?.key

    fun recordCounts(): Map<String, Int> =
        DifficultyTier.entries.associate { it.name to seedsFor(it).size }

    companion object {
        private const val ASSET_DIRECTORY = "catalogs"
        private const val EXPECTED_VERSION = 1

        fun load(context: Context): LoadResult = load { name ->
            context.assets.open("$ASSET_DIRECTORY/$name").use { it.readBytes() }
        }

        internal fun load(readAsset: (String) -> ByteArray): LoadResult {
            val catalogs = linkedMapOf<DifficultyTier, List<Long>>()
            var version: Int? = null
            val allSeeds = mutableSetOf<Long>()
            for (tier in DifficultyTier.entries) {
                val loaded = try {
                    loadCatalog(readAsset("${tier.name.lowercase()}.catalog"))
                } catch (e: Exception) {
                    return LoadResult.Invalid("could not read ${tier.name.lowercase()}.catalog: ${e.message ?: e.javaClass.simpleName}")
                }
                if (loaded is ParsedCatalogLoadResult.Invalid) return LoadResult.Invalid(loaded.reason)
                loaded as ParsedCatalogLoadResult.Valid
                val header = loaded.header
                if (header.catalogVersion != EXPECTED_VERSION ||
                    header.rulesVersion != EXPECTED_VERSION ||
                    header.shuffleVersion != EXPECTED_VERSION ||
                    header.solverVersion != EXPECTED_VERSION
                ) {
                    return LoadResult.Invalid("unexpected versions in ${tier.name.lowercase()}.catalog")
                }
                if (header.partitionId.toInt() != tier.ordinal + 1) {
                    return LoadResult.Invalid("unexpected partition in ${tier.name.lowercase()}.catalog")
                }
                if (version != null && version != header.catalogVersion) {
                    return LoadResult.Invalid("catalog versions disagree")
                }
                version = header.catalogVersion
                val seeds = loaded.seeds.map { it.toLong() }
                if (!allSeeds.addAll(seeds)) return LoadResult.Invalid("duplicate seed across catalog partitions")
                catalogs[tier] = seeds
            }
            return LoadResult.Valid(CertifiedDealCatalog(requireNotNull(version), catalogs))
        }
    }

    sealed class LoadResult {
        data class Valid(val catalog: CertifiedDealCatalog) : LoadResult()
        data class Invalid(val reason: String) : LoadResult()
    }
}
