package org.finiteplay.klondike.deal

import android.content.Context
import kotlinx.coroutines.CancellationException
import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.solution.CompactSolutionCodec

/**
 * The winning line shipped with every certified catalogued seed (`assets/solutions.bin`,
 * `docs/games/klondike/DEALS.md` "Shipped Solutions"), so a hint can follow a known solution
 * instead of re-deriving one.
 *
 * Loaded once, lazily, on first use rather than at startup: nothing needs it until the player
 * asks for a hint, so paying for it during cold start would spend the app's 1.5 s startup
 * budget (`docs/games/klondike/DESIGN.md` "Performance") on something most sessions never
 * touch.
 *
 * Only the **index** is parsed on load, and one line is decoded per lookup. The catalog holds
 * tens of thousands of solutions; decoding them all to answer for one would allocate millions
 * of `Move` objects for a single hint.
 *
 * A missing or corrupt asset degrades to "no stored solutions" rather than crashing: hints
 * then resolve exactly as they did before solutions shipped — Expert ruleset, then search — so
 * the feature is an accelerator, never a dependency. The Insane level ships **without** lines
 * at all (`DIFFICULTY_LEVELS.md` "Insane ships uncertified"), and takes that same path.
 */
class SolutionCatalog(private val context: Context) {

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
     * Takes the dealt board rather than the seed alone because the stored form is a choice
     * list: the draws that reach each pile card are rebuilt against the board as it plays, not
     * stored (`CompactSolutionCodec`).
     */
    fun solutionFor(deal: GameState): List<Move>? {
        val catalog = loaded ?: return null
        return runCatching { CompactSolutionCodec.decodeLine(catalog.bytes, catalog.index, deal, deal.seed) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val ASSET_NAME = "solutions.bin"
    }
}
