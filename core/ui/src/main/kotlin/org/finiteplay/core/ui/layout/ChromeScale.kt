package org.finiteplay.core.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import kotlin.math.pow

/**
 * How much larger the interface chrome should be drawn on this screen.
 *
 * The board scales with the window by construction — seven columns across whatever width there
 * is — but everything around it does not. Icons, labels and status text are fixed in `dp`, so
 * on an 800 dp tablet the cards grow while the controls stay phone-sized and the screen reads
 * as a phone layout stretched over a tablet. Multiplying the chrome by these factors keeps the
 * two in proportion.
 *
 * The curve is continuous rather than stepped through Material's size classes, because the
 * thing it has to track is continuous: a card is a seventh of the width, so going from a 400 dp
 * phone to an 800 dp tablet doubles the cards. Chrome that jumped 1.25x at 600 dp and stayed
 * there left an 800 dp tablet with phone-sized buttons beside doubled cards, which is the
 * complaint this exists to answer.
 *
 * It is deliberately *sub*-proportional - the 0.6 exponent gives 1.5x where the board gets 2x -
 * and capped at double. A tablet is held further away than a phone, so chrome that grew with
 * the width would end up larger in the eye than on a phone rather than merely proportionate,
 * and a fully proportional action bar on a 10-inch screen would be a 125 dp band of buttons.
 */
@Composable
@ReadOnlyComposable
fun chromeScale(): Float {
    // The *smallest* width, not the current one: `screenWidthDp` is the long edge in landscape,
    // so a phone turned sideways looked 731 dp wide and got tablet-sized chrome - oversized type
    // and a rail too tall for its own buttons. `smallestScreenWidthDp` is the device's shorter
    // edge whatever the rotation, which is what "how big is this screen" actually means, and it
    // keeps the chrome identical between a phone's two orientations.
    val widthDp = LocalConfiguration.current.smallestScreenWidthDp.toFloat()
    return (widthDp / BASELINE_WIDTH_DP).pow(0.6f).coerceIn(1f, 2f)
}

/** A typical phone in portrait, where the chrome sizes are already right and the scale is 1. */
private const val BASELINE_WIDTH_DP = 400f

/** [this] scaled by [chromeScale], for a dimension that should grow with the window. */
@Composable
@ReadOnlyComposable
fun Dp.scaledForChrome(): Dp = this * chromeScale()

/** [this] scaled by [chromeScale], for type that should grow with the window. */
@Composable
@ReadOnlyComposable
fun TextUnit.scaledForChrome(): TextUnit = this * chromeScale()
