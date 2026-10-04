package org.finiteplay.core.ui.theme

import androidx.compose.ui.graphics.Color
import org.finiteplay.cards.CardColor

/**
 * Card tokens from `UI_SPEC.md` ("Themes"). Not part of the Material `ColorScheme`
 * because nothing in it describes a playing card; carried alongside it on [AppColors]
 * instead, and reached through [LocalAppColors].
 */
data class CardColors(
    val face: Color,
    val faceBorder: Color,
    val blackSuit: Color,
    val redSuit: Color,
    val faceDownBack: Color,
    val faceDownPattern: Color,
    val faceDownBackOutline: Color,
    /**
     * Outline and suit watermark of an empty pile.
     *
     * Separate from [faceDownBackOutline] since the card back went navy: an empty slot is a
     * space on the cloth and belongs to the table's own colour family, while a face-down card
     * is an object lying on it and belongs to the back's.
     */
    val emptySlot: Color,
    val hint: Color,
    /** Ring drawn on a legal drag destination. */
    val destination: Color,
    /** Ring drawn on a rejected drop. */
    val invalid: Color,
    /**
     * Suit glyphs drawn straight onto the table — a banked-sequence counter, a pile label —
     * rather than onto a card face.
     *
     * Separate from [blackSuit]/[redSuit] because those are ink on a light card, and the table is
     * not one: on the dark board a near-black spade drawn on the cloth is invisible, which is
     * exactly what happened to a counter that reused the card ink. On the light board these are the standard
     * black and red a player expects; on the dark board the black one lightens to stay legible,
     * since a suit that cannot be seen is worse than a suit that is not literally black.
     */
    val blackSuitOnTable: Color,
    val redSuitOnTable: Color,
) {
    /** Suit colour for ink on a card face. */
    fun forSuit(color: CardColor): Color = when (color) {
        CardColor.RED -> redSuit
        CardColor.BLACK -> blackSuit
    }

    /** Suit colour for a glyph drawn on the table itself. */
    fun forSuitOnTable(color: CardColor): Color = when (color) {
        CardColor.RED -> redSuitOnTable
        CardColor.BLACK -> blackSuitOnTable
    }
}

/**
 * The dark board.
 *
 * The face is deliberately *not* near-white. WCAG sets no upper bound on contrast, but
 * dark-theme practice — Material's dark theme, Apple's HIG, IBM Carbon — agrees that a
 * large light surface on a near-black background at 12:1 and up causes halation: the
 * shape smears for astigmatic and night-adapted eyes. The face sits at about 8.6:1
 * against the board instead, past AAA's 7:1 and short of the glare.
 *
 * The red then has to be chosen *for that grey face*, which is the part the first attempt got
 * wrong. Darkening a red until it clears the contrast floor takes the colour out of it: on a
 * grey ground #A32830 read as brick — brownish, and visibly weaker than the black suits it
 * sits beside at 8.4:1. The replacement drops the green and blue channels almost to nothing
 * instead of dimming all three, which lifts contrast to 4.2:1 *and* raises chroma, so red
 * reads as red rather than as dark. Card faces are the one surface where a suit colour is
 * information, so the pair has to look like a pair.
 */
internal val DarkCardColors = CardColors(
    face = Color(0xFFB4B7B1),
    faceBorder = Color(0xFF3A3F3B),
    blackSuit = Color(0xFF1A1D1B),
    redSuit = Color(0xFF9E0611),
    faceDownBack = Color(0xFF283542),
    faceDownPattern = Color(0xFF35485A),
    faceDownBackOutline = Color(0xFF9BAAB8),
    emptySlot = Color(0xFFB8D9C5),
    // The light theme's own saturated gold, drawn as a thick full-alpha stroke
    // (`CardArt.kt`'s `drawHintHighlight`), is the same halation the face colour above was
    // chosen to avoid: a bright ring added on top of an already-light face, against a near-black
    // board. Dimmed and desaturated toward the same calm, muted family as the sage destination
    // ring and coral invalid flash beside it, rather than carried over unchanged.
    hint = Color(0xFFD9AE5C),
    destination = Color(0xFFB8D9C5),
    invalid = Color(0xFFFFB4AB),
    // Light enough to read on the dark cloth; the red is lifted to match it in weight so the
    // pair still reads as a pair rather than one suit shouting over the other.
    blackSuitOnTable = Color(0xFFE3E8E4),
    redSuitOnTable = Color(0xFFFF6E6A),
)

/**
 * The light board: a casino card table in deep baize green, with white cards. Here the
 * face *is* white — it sits at about 8:1 on that green, the same comfortable band the dark
 * face targets, and a dimmed face on a green table would only look grubby. Suit colours are
 * correspondingly crisp, having a white ground to sit on rather than a dimmed one.
 *
 * The card back is **slate**, in both themes: a blue held most of the way to grey. A green
 * back on a green table is the same object twice — a face-down stack has to read as cards
 * lying on cloth — and blue on baize is the classic answer because it works. Saturated navy
 * did the job too loudly: on velvet the back is the largest block of colour on the board
 * after the table itself, so it is the one thing that must not shout. Desaturating it keeps
 * the separation and lets the red suits stay the brightest thing on screen.
 */
internal val LightCardColors = CardColors(
    face = Color(0xFFFFFFFF),
    faceBorder = Color(0xFF8A968E),
    blackSuit = Color(0xFF101311),
    redSuit = Color(0xFFC4141F),
    faceDownBack = Color(0xFF3C4E63),
    faceDownPattern = Color(0xFF4E6480),
    faceDownBackOutline = Color(0xFFC9D3DD),
    emptySlot = Color(0xFFCDE8D9),
    hint = Color(0xFFFFC94D),
    destination = Color(0xFFFFF3C4),
    invalid = Color(0xFFFF8A80),
    // The standard pair, on a light table that can carry them.
    blackSuitOnTable = Color(0xFF101311),
    redSuitOnTable = Color(0xFFC4141F),
)
