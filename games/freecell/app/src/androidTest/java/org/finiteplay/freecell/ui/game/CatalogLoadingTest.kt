package org.finiteplay.freecell.ui.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.freecell.deal.FreeCellCertifiedDealCatalog
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * FreeCell has no uncertified fallback tier (`docs/games/freecell/DEALS.md`): a catalog that
 * fails to load must leave the game non-playable rather than dealing from an unverified formula,
 * and a catalog that loads must actually be what deals are drawn from
 * (`docs/games/freecell/EXECUTION_PLAN.md` F5's own gate).
 */
class CatalogLoadingTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aFailedCatalogLoadShowsUnrecoverableAndRetryReAttemptsIt() {
        var attempts = 0
        val viewModel = FreeCellViewModel(
            catalogLoader = {
                attempts++
                FreeCellCertifiedDealCatalog.LoadResult.Invalid("bad magic value")
            },
        )
        composeRule.setContent {
            FinitePlayTheme { GameScreen(viewModel = viewModel) }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("unrecoverable_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("app_root").assertDoesNotExist()
        assertTrue(attempts >= 1)

        val before = attempts
        // Retrying calls the loader again; it still fails here, so the screen stays.
        viewModel.retryCatalogLoad()
        composeRule.waitForIdle()
        assertTrue(attempts > before)
        composeRule.onNodeWithTag("unrecoverable_screen").assertIsDisplayed()
    }

    @Test
    fun aValidCatalogLetsTheGameProceedAndDealsFromIt() {
        val seeds = listOf(11L, 22L, 33L)
        val catalog = validCatalog(seeds)
        val viewModel = FreeCellViewModel(catalogLoader = { FreeCellCertifiedDealCatalog.LoadResult.Valid(catalog) })

        composeRule.setContent {
            FinitePlayTheme { GameScreen(viewModel = viewModel) }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("unrecoverable_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("app_root").assertIsDisplayed()
        assertTrue("the opening deal must come from the catalog", viewModel.session.state.seed in seeds)

        composeRule.onNodeWithTag("action_new").performClick()
        composeRule.waitForIdle()
        assertTrue("a fresh deal must also come from the catalog", viewModel.session.state.seed in seeds)
    }

    private fun validCatalog(seeds: List<Long>): FreeCellCertifiedDealCatalog {
        val loaded = FreeCellCertifiedDealCatalog.load {
            org.finiteplay.solitaire.catalog.catalog.encodeCatalog(
                org.finiteplay.solitaire.catalog.catalog.CatalogMetadata(
                    catalogVersion = 1,
                    rulesVersion = 1,
                    shuffleVersion = 1,
                    solverVersion = 1,
                    modeByte = null,
                ),
                seeds.map { it.toULong() },
                org.finiteplay.solitaire.catalog.catalog.CatalogFormat.magicBytes("FRCL"),
            )
        }
        check(loaded is FreeCellCertifiedDealCatalog.LoadResult.Valid)
        return loaded.catalog
    }
}
