package org.finiteplay.core.ui.table

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max

/**
 * Paints the table as **velvet**: a dense pile with a soft sheen, rather than a flat fill or a
 * woven cloth.
 *
 * A material is a *spectrum*, not a set of features. Anything stamped on — dots, blobs, threads —
 * puts all of the contrast in one band, and a single band is what the eye names: fine ones read
 * as dust or sensor noise, coarse ones as lumps or stains. So the grain here is value noise
 * summed over octaves with the amplitude falling as the frequency rises ([OCTAVES]), which is
 * how cloth, skin, paper and every other real surface distribute theirs. Nothing in it is a
 * shape, so there is nothing to recognise or to count.
 *
 * The layers over it:
 *
 * - **Pile grain.** Velvet has no visible weave — its surface is a forest of upright fibres, so
 *   the grain must be fine and directionless. The spectrum is weighted hard to the finest
 *   octaves and stops at 8 px, because anything coarser is a property of the *cloth*, not of the
 *   pile, and belongs in the layers below that do not repeat.
 * - **Nap clouding.** The pile lies in slightly different directions across a bolt of cloth, and
 *   each patch catches the light differently. Four soft, screen-wide gradients at 2–4% give that
 *   mottling; without them the surface looks sprayed rather than laid.
 * - **Sheen.** A broad, off-centre highlight — velvet's defining property is that it glows where
 *   the light hits and swallows light elsewhere, a far wider tonal range than baize. This, more
 *   than the grain, is what reads as velvet rather than as felt.
 * - **Vignette.** Deeper than a woven cloth would need, because that swallowing at the edges is
 *   exactly what makes the middle read as lustrous.
 *
 * Every layer that carries texture stays weak — the strongest is 11%, and only the vignette's
 * outermost stop goes past it — because this sits behind playing cards, and a texture a player
 * consciously notices is one competing with the game.
 *
 * The grain is one tile built per colour and repeated by a shader, so a frame costs a rect per
 * layer and no per-pixel work. The tile is drawn unrotated and unscaled, one texel to one pixel:
 * the noise is periodic by construction, so the repeat is seamless, and with no feature in it
 * wider than 8 px there is no landmark for the eye to find the period by.
 */
fun Modifier.feltTable(base: Color, thread: Color): Modifier = drawWithCache {
    val pile = ShaderBrush(ImageShader(pileTile(base, thread), TileMode.Repeated, TileMode.Repeated))
    val longest = max(size.width, size.height)
    // Every shadow on this table is the cloth's own colour taken down, never black. Dye keeps
    // its hue in shadow; mixing towards neutral instead drains the green, and a green that has
    // lost its green in patches is the definition of a dirty cloth.
    val shade = base.darkened(SHADE_DEPTH)

    // Nap: broad patches catching the light differently, placed off any axis of symmetry so
    // the eye does not find a centre to them. Screen-sized, and so free of any repeat — this
    // is where all the coarse variation lives, and why the tile's spectrum stops where it does.
    val nap = listOf(
        Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.035f), Color.Transparent),
            center = Offset(size.width * 0.22f, size.height * 0.18f),
            radius = longest * 0.55f,
        ),
        Brush.radialGradient(
            colors = listOf(shade.copy(alpha = 0.05f), Color.Transparent),
            center = Offset(size.width * 0.82f, size.height * 0.36f),
            radius = longest * 0.48f,
        ),
        Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.025f), Color.Transparent),
            center = Offset(size.width * 0.68f, size.height * 0.72f),
            radius = longest * 0.5f,
        ),
        Brush.radialGradient(
            colors = listOf(shade.copy(alpha = 0.045f), Color.Transparent),
            center = Offset(size.width * 0.18f, size.height * 0.86f),
            radius = longest * 0.52f,
        ),
    )
    val sheen = Brush.radialGradient(
        colors = listOf(Color.White.copy(alpha = 0.11f), Color.White.copy(alpha = 0.03f), Color.Transparent),
        center = Offset(size.width * 0.42f, size.height * 0.28f),
        radius = longest * 0.78f,
    )
    val vignette = Brush.radialGradient(
        colors = listOf(Color.Transparent, shade.copy(alpha = 0.12f), shade.copy(alpha = 0.4f)),
        center = Offset(size.width * 0.46f, size.height * 0.4f),
        radius = longest * 0.86f,
    )
    onDrawBehind {
        // The tile is opaque, so it is the base coat as well as the grain.
        drawRect(pile)
        for (patch in nap) drawRect(patch)
        drawRect(sheen)
        drawRect(vignette)
    }
}

/** The cloth's own colour taken down in luminance, hue and saturation intact. */
private fun Color.darkened(factor: Float): Color = Color(red * factor, green * factor, blue * factor)

/**
 * A multiple of every octave's cell size, so each octave's lattice closes exactly on the tile
 * edge and the noise wraps with no seam. At 384 px even the coarsest octave repeats no more than
 * three times across a phone, and the bitmap is still under a megabyte.
 */
private const val TILE_SIZE = 384

/**
 * The grain's spectrum: cell size in pixels against the share of the contrast it carries.
 *
 * Not the 1/f falloff of the physical surface, but that falloff **as the eye receives it**. At
 * 400+ dpi and arm's length, a 1 px fibre is well past what the eye resolves — a spectrum
 * weighted to the finest octaves measures as texture and looks like a flat fill, which is what
 * the first attempt at this did. The visible band is about 4–40 px, so that is where the
 * contrast goes, tapering off either side. It stays a taper, not a peak: energy at one scale
 * alone is what reads as lumps.
 */
private val OCTAVES = listOf(
    1 to 0.20f,
    2 to 0.30f,
    4 to 0.38f,
    8 to 0.30f,
    16 to 0.15f,
    32 to 0.06f,
)

/**
 * How far a fully lit pixel goes towards the fibre colour, and a fully shadowed one towards the
 * shade.
 *
 * The light side is given the longer reach on purpose. Broad light variation reads as a surface
 * catching the light; broad *dark* variation reads as something on the surface — which is the
 * difference between sheen and stains, and the reason an earlier pass of this looked soiled.
 * Together these measure about 3 of 255 on the board, the figure `UI_SPEC.md` records.
 */
private const val LIT_REACH = 0.11f
private const val SHADOW_REACH = 0.16f

/**
 * How far the cloth's own colour is taken down to make its shadow.
 *
 * Not to black. A dyed pile in shadow is a darker version of the same dye, so at full strength
 * this still leaves a green; the grain, the dark nap patches and the vignette all shade with it.
 */
private const val SHADE_DEPTH = 0.28f

/**
 * Cached per colour pair, and deliberately global.
 *
 * `drawWithCache` re-runs whenever the layout size changes — rotation, the keyboard, a window
 * resize — and the tile is built pixel by pixel. There are two tables in the app's lifetime, so
 * the cache is two bitmaps that live as long as the process.
 */
private val pileTiles = HashMap<Long, ImageBitmap>()

@Synchronized
private fun pileTile(base: Color, fibre: Color): ImageBitmap =
    pileTiles.getOrPut(base.value.toLong() * 31 + fibre.value.toLong()) { velvetTile(base, fibre) }

/**
 * One tile of pile, opaque: it carries the cloth's colour as well as its grain.
 *
 * Every pixel is the table colour moved towards a lit fibre or towards the cloth's own shadow,
 * so the whole surface stays one dye at one saturation — the alternative, alpha over a flat
 * fill, mixes towards whatever the two tints are, and towards black it greys the green.
 *
 * Both sides are drawn, because a pile is read from the contrast *within* it: only-light fibres
 * look like dust, only-dark like dirt.
 *
 * Seeded rather than random, so the same tile comes back on every recomposition and the table
 * never shimmers.
 */
private fun velvetTile(base: Color, fibre: Color): ImageBitmap {
    val field = FloatArray(TILE_SIZE * TILE_SIZE)
    for ((index, octave) in OCTAVES.withIndex()) {
        val (cell, amplitude) = octave
        addOctave(field, cell, amplitude, seed = 0x5EED + index * 7919)
    }

    val shade = base.darkened(SHADE_DEPTH)
    val pixels = IntArray(field.size) { index ->
        val value = field[index]
        val towards = if (value >= 0f) fibre else shade
        val mix = if (value >= 0f) value * LIT_REACH else -value * SHADOW_REACH
        android.graphics.Color.rgb(
            channel(base.red, towards.red, mix),
            channel(base.green, towards.green, mix),
            channel(base.blue, towards.blue, mix),
        )
    }
    return android.graphics.Bitmap
        .createBitmap(pixels, TILE_SIZE, TILE_SIZE, android.graphics.Bitmap.Config.ARGB_8888)
        .asImageBitmap()
}

private fun channel(from: Float, to: Float, mix: Float): Int =
    (lerp(from, to, mix) * 255f + 0.5f).toInt().coerceIn(0, 255)

/**
 * Adds one octave of value noise: a lattice of random values [cell] pixels apart, interpolated
 * smoothly between.
 *
 * The lattice indices are taken modulo its period, so the octave meets itself exactly at the
 * tile edge — the tile wraps by construction rather than by patching the seam afterwards.
 * A cell of 1 needs no interpolation and is the pixel-level grain.
 */
private fun addOctave(field: FloatArray, cell: Int, amplitude: Float, seed: Int) {
    if (cell == 1) {
        for (index in field.indices) {
            field[index] += amplitude * hash(index % TILE_SIZE, index / TILE_SIZE, seed)
        }
        return
    }
    val period = TILE_SIZE / cell
    // The lattice is drawn once rather than hashed per pixel: four hashes a pixel across seven
    // octaves is millions of them, and this is built on the frame that first shows the board.
    val lattice = FloatArray(period * period) { hash(it % period, it / period, seed) }
    for (y in 0 until TILE_SIZE) {
        val fractionY = smoothstep(y % cell / cell.toFloat())
        val rowA = (y / cell % period) * period
        val rowB = ((y / cell + 1) % period) * period
        for (x in 0 until TILE_SIZE) {
            val fractionX = smoothstep(x % cell / cell.toFloat())
            val columnA = x / cell % period
            val columnB = (x / cell + 1) % period
            val top = lerp(lattice[rowA + columnA], lattice[rowA + columnB], fractionX)
            val bottom = lerp(lattice[rowB + columnA], lattice[rowB + columnB], fractionX)
            field[y * TILE_SIZE + x] += amplitude * lerp(top, bottom, fractionY)
        }
    }
}

/** A repeatable random value in `[-1, 1)` for a lattice point. */
private fun hash(x: Int, y: Int, seed: Int): Float {
    var h = x * 374761393 + y * 668265263 + seed
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFF) / 32768f - 1f
}

private fun smoothstep(t: Float): Float = t * t * (3f - 2f * t)

private fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t
