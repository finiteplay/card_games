package org.finiteplay.freecell.game

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.finiteplay.freecell.layout.TABLEAU_COLUMNS
import org.finiteplay.solitaire.ui.CardSize
import org.finiteplay.solitaire.ui.TableauGeometryConfig
import org.finiteplay.solitaire.ui.cardHeightFittingTallestColumn
import org.finiteplay.solitaire.ui.computeCardSize
import org.finiteplay.solitaire.ui.computeColumnSpacing
import org.finiteplay.solitaire.ui.computeTableauOverlap

/**
 * FreeCell's own numbers for `:solitaire:ui`'s fitting math (`docs/games/freecell/UI_SPEC.md`
 * "Board Geometry"). [minCardWidth] and [minFaceUpBand] are provisional, set the way Spider's own
 * were: below what eight columns naturally fit to at 320 dp with the fractions below, so the
 * floor should not actually override the fit at gate widths. `FreeCellGeometryGateTest` either
 * confirms them or corrects them once it exists.
 *
 * Every card in this game is face up, always (`docs/games/freecell/RULES.md` "The layout"), so
 * the face-down fields are never exercised — they are set equal to the face-up ones rather than
 * left as a second, meaningless number to keep in sync.
 */
internal val FREECELL_GEOMETRY = TableauGeometryConfig(
    cardWidthToHeightRatio = 7f / 5f,
    minColumnSpacing = 4.dp,
    minCardWidth = 32.dp,
    minFaceUpBand = 22.dp,
    minFaceDownBand = 22.dp,
    minFaceDownBandFloor = 22.dp,
    // Capped below where the center pip's own ink starts (0.4575 of card height, measured on a
    // physical device with the pip scaled up for legibility, `core:ui`'s `CardArt.kt`
    // `PIP_GLYPH_SCALE`) — otherwise a covered card's exposed band reaches far enough down to
    // show the top of its pip peeking out from under the next card stacked on it.
    maxFaceUpStepFraction = 0.42f,
    maxFaceDownStepFraction = 0.42f,
    columnGapFraction = 0.10f,
    sideMarginFraction = 0.16f,
)

/**
 * A column can grow no deeper than this: seven dealt cards plus every other rank of the same suit
 * built on top of a King (`docs/games/freecell/UI_SPEC.md` "Board Geometry"). Sizing for it
 * permanently would pin every game to the smallest cards a depth most games never reach, so the
 * board instead tracks the tallest column *currently on the board*, the same approach Klondike's
 * own geometry uses.
 */
internal const val MAX_COLUMN_DEPTH = 7 + 12

/** Card size for [TABLEAU_COLUMNS] equal columns across [availableWidth]. */
internal fun computeFreeCellCardSize(availableWidth: Dp, columns: Int = TABLEAU_COLUMNS): CardSize =
    FREECELL_GEOMETRY.computeCardSize(availableWidth, columns)

internal fun freeCellColumnSpacing(availableWidth: Dp, cardWidth: Dp, columns: Int = TABLEAU_COLUMNS): Dp =
    FREECELL_GEOMETRY.computeColumnSpacing(availableWidth, cardWidth, columns)

/** Tallest card that still lets [tallestColumn] cards fit [availableHeight] whole. */
internal fun freeCellCardHeightFittingTallestColumn(availableHeight: Dp, tallestColumn: Int): Dp =
    FREECELL_GEOMETRY.cardHeightFittingTallestColumn(availableHeight, tallestColumn, maxFaceDownInColumn = 0)

/**
 * Cumulative vertical offset of each card's top edge in a column of [cardCount] cards — every
 * step is the face-up band, since there is only one kind of step in this game
 * (`docs/games/freecell/UI_SPEC.md` "Board Geometry").
 */
internal fun freeCellCardTopOffsets(cardCount: Int, availableHeight: Dp, cardHeight: Dp): List<Dp> {
    if (cardCount <= 0) return emptyList()
    val overlap = FREECELL_GEOMETRY.computeTableauOverlapAllFaceUp(availableHeight, cardHeight, cardCount)
    return (0 until cardCount).map { overlap * it }
}

/** [TableauGeometryConfig.computeTableauOverlap] specialised to "every card is face up." */
private fun TableauGeometryConfig.computeTableauOverlapAllFaceUp(availableHeight: Dp, cardHeight: Dp, cardCount: Int): Dp =
    computeTableauOverlap(availableHeight, cardHeight, faceDownCount = 0, faceUpCount = cardCount).faceUpStep
