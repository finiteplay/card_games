package org.finiteplay.core.ui.card

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import org.finiteplay.core.ui.R
import org.finiteplay.cards.Card
import org.finiteplay.core.ui.theme.CardColors
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit

private fun rankLabel(rank: Rank): String = when (rank) {
    Rank.ACE -> "A"
    Rank.TWO -> "2"
    Rank.THREE -> "3"
    Rank.FOUR -> "4"
    Rank.FIVE -> "5"
    Rank.SIX -> "6"
    Rank.SEVEN -> "7"
    Rank.EIGHT -> "8"
    Rank.NINE -> "9"
    Rank.TEN -> "10"
    Rank.JACK -> "J"
    Rank.QUEEN -> "Q"
    Rank.KING -> "K"
}

/** Spoken rank, localized, for semantics ("Ace", "Two", ... "King"). */
@Composable
fun rankSpokenLabel(rank: Rank): String = stringResource(
    when (rank) {
        Rank.ACE -> R.string.rank_ace
        Rank.TWO -> R.string.rank_two
        Rank.THREE -> R.string.rank_three
        Rank.FOUR -> R.string.rank_four
        Rank.FIVE -> R.string.rank_five
        Rank.SIX -> R.string.rank_six
        Rank.SEVEN -> R.string.rank_seven
        Rank.EIGHT -> R.string.rank_eight
        Rank.NINE -> R.string.rank_nine
        Rank.TEN -> R.string.rank_ten
        Rank.JACK -> R.string.rank_jack
        Rank.QUEEN -> R.string.rank_queen
        Rank.KING -> R.string.rank_king
    },
)

/** Spoken suit, localized, for semantics ("Clubs", "Diamonds", "Hearts", "Spades"). */
@Composable
fun suitSpokenLabel(suit: Suit): String = stringResource(
    when (suit) {
        Suit.CLUBS -> R.string.suit_clubs
        Suit.DIAMONDS -> R.string.suit_diamonds
        Suit.HEARTS -> R.string.suit_hearts
        Suit.SPADES -> R.string.suit_spades
    },
)

private fun cornerRadius(size: Size) = CornerRadius(size.width * 0.08f, size.width * 0.08f)

// A bold serif, not the default sans, for card face text: traditional card-deck
// corner indices and pips are set in a bold serif/slab-serif face, not a
// geometric sans, so this reads closer to a classic deck than Typeface.DEFAULT_BOLD
// did. Typeface.SERIF resolves to the platform's built-in serif family (Noto Serif
// on current Android) - no bundled font asset needed.
private val CARD_FACE_TYPEFACE: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)

// Card face text is drawn on the raw android.graphics.Canvas rather than through
// Compose's Text/TextMeasurer APIs: card face text must scale with card size, never
// the system font scale (`UI_SPEC.md` "Accessibility"), and a Paint sized directly in
// pixels sidesteps that concern entirely instead of fighting the ambient fontScale.
private fun textPaint(color: Color, textSizePx: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color.toArgb()
    this.textSize = textSizePx
    this.typeface = CARD_FACE_TYPEFACE
}

// U+FE0E forces a suit glyph into the text-style font rather than the colour-emoji font a bare
// glyph resolves to (`drawCardFace`'s own comment explains why), but that text-style font draws
// the glyph noticeably smaller within the same nominal Paint.textSize than the colour-emoji font
// did — a font-metric difference between the two, not a bug in the sizing math. This scales the
// center pip's paint size back up to the emoji font's visual footprint (it also sizes the empty-slot
// watermark, the same style of glyph at a different base size), tuned by eye on a physical device.
private const val PIP_GLYPH_SCALE = 1.45f

// The corner suit glyph's own size, as a fraction of the corner rank's — see the comment beside
// its one use site in `drawCardFace` for what the two things this single number folds together.
private const val CORNER_SUIT_TEXT_SIZE_FACTOR = 1.0f

private val reusableBounds = Rect()

/** Draws [text] with its left edge at [x], vertically centered on [centerY]. */
private fun DrawScope.drawTextLeftVCenter(text: String, x: Float, centerY: Float, paint: Paint) {
    paint.getTextBounds(text, 0, text.length, reusableBounds)
    drawContext.canvas.nativeCanvas.drawText(text, x - reusableBounds.left, centerY - reusableBounds.exactCenterY(), paint)
}

/** Draws [text] with its right edge at [rightX], vertically centered on [centerY]. */
private fun DrawScope.drawTextRightVCenter(text: String, rightX: Float, centerY: Float, paint: Paint) {
    paint.getTextBounds(text, 0, text.length, reusableBounds)
    drawContext.canvas.nativeCanvas.drawText(text, rightX - reusableBounds.right, centerY - reusableBounds.exactCenterY(), paint)
}

/** Draws [text] centered on ([cx], [cy]). */
private fun DrawScope.drawTextCentered(text: String, cx: Float, cy: Float, paint: Paint) {
    paint.getTextBounds(text, 0, text.length, reusableBounds)
    val x = cx - reusableBounds.exactCenterX()
    val y = cy - reusableBounds.exactCenterY()
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

/** Draws a face-down card back at [topLeft]: a simple diagonal-hatch pattern, not a flat fill. */
fun DrawScope.drawCardBack(topLeft: Offset, size: Size, colors: CardColors) {
    val radius = cornerRadius(size)
    drawRoundRect(color = colors.faceDownBack, topLeft = topLeft, size = size, cornerRadius = radius)

    val clip = Path().apply { addRoundRect(RoundRect(ComposeRect(topLeft, size), radius)) }
    clipPath(clip) {
        val step = size.width * 0.18f
        val strokeWidth = size.width * 0.05f
        var x = topLeft.x - size.height
        while (x < topLeft.x + size.width) {
            drawLine(
                color = colors.faceDownPattern,
                start = Offset(x, topLeft.y + size.height),
                end = Offset(x + size.height, topLeft.y),
                strokeWidth = strokeWidth,
            )
            x += step
        }
    }

    drawRoundRect(
        color = colors.faceDownBackOutline,
        topLeft = topLeft,
        size = size,
        cornerRadius = radius,
        style = Stroke(width = size.width * 0.03f),
    )
}

/**
 * Draws a face-up [card] at [topLeft] in the classic layout: rank top-left and suit
 * top-right, vertically centered on the same line, and a large centered suit
 * pictogram low on the card for every rank — Jack/Queen/King currently get no special
 * center treatment, same as any other card.
 */
fun DrawScope.drawCardFace(topLeft: Offset, size: Size, card: Card, colors: CardColors) {
    val radius = cornerRadius(size)
    drawRoundRect(color = colors.face, topLeft = topLeft, size = size, cornerRadius = radius)
    drawRoundRect(
        color = colors.faceBorder,
        topLeft = topLeft,
        size = size,
        cornerRadius = radius,
        style = Stroke(width = size.width * 0.015f),
    )

    val suitColor = colors.forSuit(card.color)
    val cornerTextSize = size.width * 0.372f // 0.338 * 1.10: 10% larger
    val rankPaint = textPaint(suitColor, cornerTextSize)
    // The suit glyph shares the rank's vertical center rather than its top edge — otherwise
    // shrinking it alone would leave it looking to float higher than the rank label beside it.
    // CORNER_SUIT_TEXT_SIZE_FACTOR folds two things into one number: the original design (a
    // suit glyph reading at 90% of the rank's size) and a compensation for U+FE0E's text-style
    // font drawing noticeably smaller ink than the colour-emoji font did at the same nominal
    // Paint.textSize (`drawCardFace`'s own comment on `suitText` below has the full story) — the
    // glyph still reads visibly smaller than the rank despite the factor being above 1, because
    // that font carries more internal padding around its ink than the rank's serif digit does.
    val suitPaint = textPaint(suitColor, cornerTextSize * CORNER_SUIT_TEXT_SIZE_FACTOR)
    val marginX = size.width * 0.09f
    val marginY = size.height * 0.06f

    val rankText = rankLabel(card.rank)
    rankPaint.getTextBounds(rankText, 0, rankText.length, reusableBounds)
    val cornerCenterY = topLeft.y + marginY - reusableBounds.top + reusableBounds.exactCenterY()
    val pipPaint = textPaint(suitColor, size.width * 0.46f * PIP_GLYPH_SCALE)

    // U+FE0E (text presentation selector) forces the suit glyph to render in the ambient text
    // font, in the paint's own colour — without it, the glyph resolves to a **colour emoji**
    // font on most devices, which paints its own vivid red hearts and slate-grey clubs and
    // ignores the paint colour entirely (two different reds on one card, rank and pip — every
    // theme in `docs/PLATFORM.md` "Themes" depends on this not happening). This used to be worked
    // around with an offscreen `saveLayer` + `SrcIn` tint per card instead; with the glyph itself
    // in the right colour there is nothing left to tint, and every card measured its own extra
    // GPU-backed layer, several times a typical board's normal graphics footprint at once
    // (`docs/PLATFORM.md` "Performance").
    val suitText = card.suit.symbol.toString() + "︎"
    drawTextLeftVCenter(rankText, topLeft.x + marginX, cornerCenterY, rankPaint)
    drawTextRightVCenter(suitText, topLeft.x + size.width - marginX, cornerCenterY, suitPaint)
    drawTextCentered(suitText, topLeft.x + size.width / 2f, topLeft.y + size.height * 0.62f, pipPaint)
}

/**
 * A centered numeric/text badge sized as a fraction of [size]'s width, drawn the same way as
 * the card face's own corner rank (raw canvas pixels, not a Compose [androidx.compose.material3.Text]) — so
 * a count drawn over a card, like a stock pile's own remaining-deals badge, scales with the
 * card itself instead of sitting at a fixed size that reads mismatched once the card is larger
 * or smaller than the layout that badge was tuned against (`UI_SPEC.md` "Accessibility": card
 * art ignores the system font scale on purpose, same as [drawCardFace]'s corner text does).
 */
fun DrawScope.drawCenteredCardBadge(topLeft: Offset, size: Size, text: String, color: Color, textSizeFactor: Float = 0.372f) {
    val paint = textPaint(color, size.width * textSizeFactor)
    drawTextCentered(text, topLeft.x + size.width / 2f, topLeft.y + size.height / 2f, paint)
}

/** Draws an empty pile slot outline, optionally with a centered suit-symbol watermark. */
fun DrawScope.drawEmptySlot(topLeft: Offset, size: Size, colors: CardColors, watermark: String? = null) {
    val radius = cornerRadius(size)
    val outlineColor = colors.emptySlot.copy(alpha = 0.4f)
    drawRoundRect(color = outlineColor, topLeft = topLeft, size = size, cornerRadius = radius, style = Stroke(width = size.width * 0.03f))
    if (watermark != null) {
        // The pip is the only thing that says which suit a foundation is for, so it is drawn
        // at nearly full strength and larger than the border around it. U+FE0E forces text
        // (monochrome, paint-coloured) rendering instead of the colour-emoji font the bare
        // glyph resolves to on most devices — see `drawCardFace`'s own comment for the full
        // story and why this replaced an offscreen `saveLayer` + `SrcIn` tint here too.
        val pipColor = colors.emptySlot.copy(alpha = 0.85f)
        val paint = textPaint(pipColor, size.width * 0.42f * PIP_GLYPH_SCALE)
        drawTextCentered(watermark + "︎", topLeft.x + size.width / 2f, topLeft.y + size.height / 2f, paint)
    }
}

/** Highlight ring drawn above a slot to indicate a legal drag destination. */
fun DrawScope.drawDestinationHighlight(topLeft: Offset, size: Size, color: Color) {
    drawRoundRect(
        color = color,
        topLeft = topLeft,
        size = size,
        cornerRadius = cornerRadius(size),
        style = Stroke(width = size.width * 0.05f),
    )
}

/**
 * Hint highlight: a translucent fill wash plus a thicker ring than
 * [drawDestinationHighlight], so the suggested move reads clearly even at a glance.
 */
fun DrawScope.drawHintHighlight(topLeft: Offset, size: Size, alpha: Float, colors: CardColors) {
    val radius = cornerRadius(size)
    drawRoundRect(
        color = colors.hint.copy(alpha = alpha * 0.28f),
        topLeft = topLeft,
        size = size,
        cornerRadius = radius,
    )
    drawRoundRect(
        color = colors.hint.copy(alpha = alpha),
        topLeft = topLeft,
        size = size,
        cornerRadius = radius,
        // A little heavier than the destination ring, and left at full strength once the
        // flash settles: the hint is meant to stay findable while the player looks at the
        // board, not to fade into it.
        style = Stroke(width = size.width * 0.12f),
    )
}
