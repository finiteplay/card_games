package org.finiteplay.core.ui.layout

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import org.finiteplay.core.ui.R
import org.finiteplay.core.ui.theme.LocalAppColors

/** Watermark opacity: present, but never competing with a card drawn over it. */
const val LOGO_ALPHA = 0.45f

/**
 * The FinitePlay wordmark — publisher branding, not a control, and the same mark in every game
 * here, which is why it lives in the shared layer rather than in whichever game happened to need
 * it first.
 *
 * Drawn in its own colours, untinted. One artwork per theme: the light table takes the
 * navy-on-white mark, the dark table the pale one, because a single mark tinted to fit both would
 * suit neither. The theme decides which, not luminance — both tables are green.
 */
@Composable
fun FinitePlayLogo(modifier: Modifier = Modifier, widthFraction: Float = 0.8f, alpha: Float = LOGO_ALPHA) {
    val darkTable = LocalAppColors.current.isDark
    Image(
        painter = painterResource(if (darkTable) R.drawable.finiteplay_logo_dark else R.drawable.finiteplay_logo),
        contentDescription = null,
        alpha = alpha,
        modifier = modifier.testTag("finiteplay_logo").fillMaxWidth(widthFraction),
    )
}
