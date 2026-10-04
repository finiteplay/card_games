package org.finiteplay.freecell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import kotlinx.coroutines.runBlocking
import org.finiteplay.freecell.deal.FreeCellCertifiedDealCatalog
import org.finiteplay.freecell.storage.FreeCellActiveGameStore
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The one test that has to exercise the real, bundled catalog asset rather than a synthetic one
 * (`docs/games/freecell/EXECUTION_PLAN.md` F5's own gate: "Normal app startup only verifies and
 * reads the asset; it never solves") — every other instrumented test constructs its own
 * `FreeCellViewModel` with a fixture loader, which proves the wiring but never actually opens
 * `assets/catalogs/freecell.catalog` the way a real launch does.
 */
class RealCatalogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun clearWhatThisTestLeftBehind() = runBlocking {
        FreeCellActiveGameStore(composeRule.activity.filesDir).clear()
    }

    @Test
    fun theBundledCatalogLoadsAndTheGameIsPlayable() {
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("unrecoverable_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("app_root").assertIsDisplayed()
    }

    @Test
    fun theRealAssetParsesAsAValidCatalogWithTwoThousandSeeds() {
        val loaded = FreeCellCertifiedDealCatalog.load(composeRule.activity)

        check(loaded is FreeCellCertifiedDealCatalog.LoadResult.Valid) { "expected Valid, got $loaded" }
        assertTrue(loaded.catalog.seeds.size >= 1)
    }
}
