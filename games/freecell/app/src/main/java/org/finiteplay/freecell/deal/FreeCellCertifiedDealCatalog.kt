package org.finiteplay.freecell.deal

import android.content.Context
import org.finiteplay.solitaire.catalog.catalog.CatalogFormat
import org.finiteplay.solitaire.catalog.catalog.CatalogLoadResult as ParsedCatalogLoadResult
import org.finiteplay.solitaire.catalog.catalog.loadCatalog

/**
 * The validated certified-deal catalog bundled with the app (`docs/games/freecell/DEALS.md`). One
 * flat, unpartitioned list — unlike Klondike's per-difficulty or Spider's per-suit-count catalogs,
 * FreeCell has no axis to split this by.
 */
class FreeCellCertifiedDealCatalog private constructor(
    val catalogVersion: Int,
    val seeds: List<Long>,
) {
    companion object {
        private const val ASSET_DIRECTORY = "catalogs"
        private const val CATALOG_FILE_NAME = "freecell.catalog"
        private const val EXPECTED_VERSION = 1
        private val MAGIC_BYTES = CatalogFormat.magicBytes("FRCL")

        fun load(context: Context): LoadResult = load { context.assets.open("$ASSET_DIRECTORY/$CATALOG_FILE_NAME").use { it.readBytes() } }

        internal fun load(readAsset: () -> ByteArray): LoadResult {
            val loaded = try {
                loadCatalog(readAsset(), MAGIC_BYTES, hasModeByte = false)
            } catch (e: Exception) {
                return LoadResult.Invalid("could not read $CATALOG_FILE_NAME: ${e.message ?: e.javaClass.simpleName}")
            }
            if (loaded is ParsedCatalogLoadResult.Invalid) return LoadResult.Invalid(loaded.reason)
            loaded as ParsedCatalogLoadResult.Valid
            val header = loaded.header
            if (header.catalogVersion != EXPECTED_VERSION ||
                header.rulesVersion != EXPECTED_VERSION ||
                header.shuffleVersion != EXPECTED_VERSION ||
                header.solverVersion != EXPECTED_VERSION
            ) {
                return LoadResult.Invalid("unexpected versions in $CATALOG_FILE_NAME")
            }
            if (header.partitionId != CatalogFormat.PARTITION_NONE) {
                return LoadResult.Invalid("unexpected partition in $CATALOG_FILE_NAME")
            }
            return LoadResult.Valid(FreeCellCertifiedDealCatalog(header.catalogVersion, loaded.seeds.map { it.toLong() }))
        }
    }

    sealed class LoadResult {
        data class Valid(val catalog: FreeCellCertifiedDealCatalog) : LoadResult()
        data class Invalid(val reason: String) : LoadResult()
    }
}
