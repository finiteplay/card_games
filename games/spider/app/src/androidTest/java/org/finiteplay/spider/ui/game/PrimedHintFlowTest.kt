package org.finiteplay.spider.ui.game

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.spider.deal.SpiderCertifiedDealCatalog
import org.finiteplay.spider.deal.SpiderSolutionCatalog
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.rules.isLegal
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The actual user-facing bug this closes: with "Hint shows the winning move" on (the default),
 * a fresh certified deal used to only ever resolve Hint through a live on-device search — fine at
 * [SuitCount.ONE], but [org.finiteplay.spider.solver.HintEngine]'s own doc records that a TWO-suit
 * search essentially never completes within any budget tried, so a real player reported "Hint on
 * spider does not show winning move even with setting on." [SpiderSolutionCatalog] now ships the
 * certified catalog's own winning line for every seed, and [SpiderViewModel] primes the hint
 * engine's cache with it on every fresh deal, so the very first hint request resolves off that
 * cache — a lookup, not a search — for both suit counts.
 *
 * Uses the real, shipped `assets/solutions.bin` and the catalog files under `assets/catalogs`, not a fake: this
 * is the end-to-end confirmation the feature exists for, not a unit test of the codec in isolation
 * ([org.finiteplay.spider.solution.CompactSolutionCodecTest]) or of the asset's own integrity
 * (`SolutionsAssetTest`).
 */
class PrimedHintFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun primedViewModel(suitCount: SuitCount): SpiderViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loaded = SpiderCertifiedDealCatalog.load(context)
        check(loaded is SpiderCertifiedDealCatalog.LoadResult.Valid) { "catalog asset did not load: $loaded" }
        val viewModel = SpiderViewModel(
            certifiedCatalog = loaded.catalog,
            solutionCatalog = SpiderSolutionCatalog(context),
        )
        viewModel.pickNextSuitCount(suitCount)
        viewModel.newGame()
        return viewModel
    }

    private fun assertFirstHintIsInstantlyGuided(suitCount: SuitCount) {
        val viewModel = primedViewModel(suitCount)
        composeRule.setContent {
            FinitePlayTheme { GameScreen(viewModel = viewModel) }
        }

        composeRule.onNodeWithTag("action_hint").performClick()
        // A primed lookup, not a live search: a real TWO-suit search essentially never finishes
        // within any budget tried (`HintEngine.kt`), so a short timeout here is itself part of the
        // assertion — this would time out on the old, unprimed code path.
        composeRule.waitUntil(timeoutMillis = 1_500) { viewModel.hintState !is HintUiState.Loading }

        val shown = viewModel.hintState
        check(shown is HintUiState.Guided) { "expected an instantly Guided hint from the primed cache, got $shown" }
        assertTrue(
            "the guided move must be legal against the board it was computed from",
            isLegal(viewModel.session.state, shown.move),
        )
    }

    @Test
    fun aFreshOneSuitCertifiedDealShowsAGuidedHintInstantly() {
        assertFirstHintIsInstantlyGuided(SuitCount.ONE)
    }

    @Test
    fun aFreshTwoSuitCertifiedDealShowsAGuidedHintInstantly() {
        assertFirstHintIsInstantlyGuided(SuitCount.TWO)
    }
}
