package org.finiteplay.spider.deal

import android.content.Context
import kotlinx.coroutines.CancellationException
import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.rules.Move
import org.finiteplay.spider.solution.CompactSolutionCodec

/**
 * The winning line shipped with every certified catalogued seed (`assets/solutions.bin`,
 * `docs/games/spider/DEALS.md` "Shipped Solutions"), so a hint can follow a known solution instead
 * of re-deriving one, mirroring Klondike's own `SolutionCatalog` and FreeCell's own
 * `FreeCellSolutionCatalog` exactly.
 *
 * Loaded once, lazily, on first use rather than at startup — nothing needs it until the player
 * asks for a hint, so paying for it during cold start would spend startup budget on something
 * most sessions never touch.
 *
 * Only the **index** is parsed on load, and one line is decoded per lookup. The catalog holds
 * twenty thousand solutions across both suit counts; decoding them all to answer for one would
 * allocate a large number of `Move` objects for a single hint.
 *
 * A missing or corrupt asset degrades to "no stored solutions" rather than crashing: hints then
 * resolve exactly as they did before solutions shipped — a live [org.finiteplay.spider.solver.SpiderSolver]
 * search — so the feature is an accelerator, never a dependency. FOUR-suit deals have no stored
 * solutions at all (no catalog exists for them, `DEALS.md`), and take that same path.
 */
class SpiderSolutionCatalog(private val context: Context) {

    private class Loaded(val bytes: ByteArray, val index: CompactSolutionCodec.Index)

    private val loaded: Loaded? by lazy {
        try {
            val bytes = context.assets.open(ASSET_NAME).use { it.readBytes() }
            Loaded(bytes, CompactSolutionCodec.readIndex(bytes))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The stored winning line for [deal], or null for an uncatalogued or uncertified seed.
     *
     * Takes the dealt board rather than the seed alone because the stored form is a choice list:
     * a transfer's pile position is rebuilt against the board as it plays, not stored
     * (`CompactSolutionCodec`).
     */
    fun solutionFor(deal: SpiderState): List<Move>? {
        val catalog = loaded ?: return null
        return runCatching { CompactSolutionCodec.decodeLine(catalog.bytes, catalog.index, deal, deal.seed) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val ASSET_NAME = "solutions.bin"
    }
}
