package org.finiteplay.spider.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.core.ui.card.drawCardFace
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.core.ui.theme.LocalAppColors
import org.junit.Assert.assertArrayEquals
import org.junit.Rule
import org.junit.Test

/**
 * Card face text is drawn by `core:ui`'s shared `CardArt.kt`, which sizes a raw
 * `android.graphics.Paint` directly from the card's own pixel size rather than through
 * Compose's `sp`-based text APIs, precisely so it ignores the system font scale
 * (`docs/PLATFORM.md` "Accessibility"). That guarantee is shared by every game; this is
 * Spider's own instrumented proof of it, mirroring Klondike's and FreeCell's own
 * `CardFaceFontScaleTest`.
 */
class CardFaceFontScaleTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cardFaceIsPixelIdenticalAcrossFontScales() {
        lateinit var setFontScale: (Float) -> Unit

        composeRule.setContent {
            var fontScale by remember { mutableFloatStateOf(1f) }
            setFontScale = { fontScale = it }
            val baseDensity = LocalDensity.current
            FinitePlayTheme {
                CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale)) {
                    val colors = LocalAppColors.current.card
                    Box(Modifier.size(100.dp, 140.dp).testTag("card")) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCardFace(topLeft = Offset.Zero, size = this.size, card = Card(Suit.HEARTS, Rank.KING), colors = colors)
                        }
                    }
                }
            }
        }

        val atDefaultScale = composeRule.onNodeWithTag("card").captureToImage().asAndroidBitmap()
        val defaultPixels = IntArray(atDefaultScale.width * atDefaultScale.height)
        atDefaultScale.getPixels(defaultPixels, 0, atDefaultScale.width, 0, 0, atDefaultScale.width, atDefaultScale.height)

        composeRule.runOnIdle { setFontScale(3f) }
        composeRule.waitForIdle()

        val atLargeScale = composeRule.onNodeWithTag("card").captureToImage().asAndroidBitmap()
        val largePixels = IntArray(atLargeScale.width * atLargeScale.height)
        atLargeScale.getPixels(largePixels, 0, atLargeScale.width, 0, 0, atLargeScale.width, atLargeScale.height)

        assertArrayEquals(
            "card face pixels must not change when only the system font scale changes",
            defaultPixels,
            largePixels,
        )
    }
}
