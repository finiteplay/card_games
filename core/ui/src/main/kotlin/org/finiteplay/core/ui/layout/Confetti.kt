package org.finiteplay.core.ui.layout

import android.content.res.Configuration
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** One piece of confetti: how it is thrown, and how it spins and looks. */
private class Piece(
    val angle: Float,
    val speed: Float,
    val spin: Float,
    val spin0: Float,
    val width: Float,
    val height: Float,
    val color: Color,
)

private val CONFETTI_COLORS = listOf(
    Color(0xFFFFC107), Color(0xFF4CAF50), Color(0xFFF44336), Color(0xFF2196F3), Color(0xFFFFFFFF), Color(0xFFE040FB),
)

/** How long a burst lasts, from the throw until the last piece has faded. */
const val CONFETTI_MS = 2_000

/**
 * Confetti thrown up and out of one point and pulled down by gravity, fading out over the last
 * third. Deterministic for a [seed], so the same win throws the same confetti. The piece positions
 * are read in the draw step only, so a burst redraws without recomposing anything, and it stops
 * entirely when it ends: nothing keeps running afterwards.
 *
 * Callers decide whether to show it at all; under Skip Animations and system reduced motion they
 * do not (`docs/PLATFORM.md` "Accessibility").
 *
 * [originY] is where the pieces leave from, as a fraction of the height.
 */
@Composable
fun ConfettiBurst(seed: Long, count: Int, modifier: Modifier = Modifier, originY: Float = 0.46f) {
    val pieces = remember(seed, count) {
        val random = Random(seed xor 0x5DEECE66DL)
        List(count) {
            Piece(
                angle = (-160f + random.nextFloat() * 140f) * (Math.PI.toFloat() / 180f),
                speed = 420f + random.nextFloat() * 620f,
                spin = (random.nextFloat() - 0.5f) * 900f,
                spin0 = random.nextFloat() * 360f,
                width = 6f + random.nextFloat() * 6f,
                height = 9f + random.nextFloat() * 8f,
                color = CONFETTI_COLORS[random.nextInt(CONFETTI_COLORS.size)],
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(pieces) { progress.animateTo(1f, tween(durationMillis = CONFETTI_MS, easing = LinearEasing)) }
    Canvas(modifier = modifier.fillMaxSize()) {
        val seconds = progress.value * (CONFETTI_MS / 1_000f)
        val origin = Offset(size.width / 2f, size.height * originY)
        val gravity = 1_500f * density
        val fade = ((1f - progress.value) / 0.3f).coerceIn(0f, 1f)
        for (piece in pieces) {
            val x = origin.x + cos(piece.angle) * piece.speed * density * seconds
            val y = origin.y + sin(piece.angle) * piece.speed * density * seconds + 0.5f * gravity * seconds * seconds
            val w = piece.width * density
            val h = piece.height * density
            rotate(piece.spin0 + piece.spin * seconds, pivot = Offset(x, y)) {
                drawRect(piece.color.copy(alpha = fade), Offset(x - w / 2f, y - h / 2f), Size(w, h))
            }
        }
    }
}

/**
 * A celebration over the whole screen, above a dialog: [banner] near the top, where a centred
 * dialog does not reach, and confetti falling from behind it when [confetti] is set. A dialog is a
 * window of its own, so anything drawn in the screen beneath it would sit under the dialog's
 * scrim; this is a second, transparent window over it, set not to take a touch or the focus, so the
 * dialog's buttons work exactly as before and nothing is dimmed. It lasts as long as it is composed,
 * which is as long as the dialog it accompanies.
 */
@Composable
fun CelebrationOverlay(seed: Long, confetti: Boolean, count: Int = 70, banner: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.apply {
                addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
        }
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        Box(modifier = Modifier.fillMaxSize()) {
            if (confetti) ConfettiBurst(seed = seed, count = count, originY = 0.28f)
            // Portrait leaves clear space above a centred dialog; a short landscape screen has little, so the banner takes the very top.
            Box(modifier = Modifier.align(BiasAlignment(0f, if (landscape) -0.97f else -0.62f))) { banner() }
        }
    }
}
