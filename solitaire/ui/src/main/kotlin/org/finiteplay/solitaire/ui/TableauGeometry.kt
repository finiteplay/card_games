package org.finiteplay.solitaire.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The numbers a tableau's width- and overlap-fitting math is fit *to*: `docs/PLATFORM.md`
 * "Accessibility" states the mechanism — a minimum card width and an exposed face-up band stand
 * in for the 48 dp control minimum cards are exempt from — but leaves the numbers themselves to
 * each game's own UI spec, stated under that game's own docs. Every game states its own rather
 * than inheriting these by default, which is why there is no default value anywhere in this file.
 *
 * [cardWidthToHeightRatio] is width-over-height, e.g. `7f / 5f`. Every other field is a floor,
 * a ceiling, or a fraction of the card size the function it feeds is named after.
 */
data class TableauGeometryConfig(
    val cardWidthToHeightRatio: Float,
    val minColumnSpacing: Dp,
    val minCardWidth: Dp,
    val minFaceUpBand: Dp,
    val minFaceDownBand: Dp,
    val minFaceDownBandFloor: Dp,
    val maxFaceUpStepFraction: Float,
    val maxFaceDownStepFraction: Float,
    val columnGapFraction: Float,
    val sideMarginFraction: Float,
)

data class CardSize(val width: Dp, val height: Dp)

/** Gap between two adjacent piles of [cardWidth], never below [TableauGeometryConfig.minColumnSpacing]. */
fun TableauGeometryConfig.columnGapFor(cardWidth: Dp): Dp =
    maxOf(cardWidth * columnGapFraction, minColumnSpacing)

/** Inset from each side edge of the board for a board of [cardWidth] cards. */
fun TableauGeometryConfig.boardSideMarginFor(cardWidth: Dp): Dp =
    maxOf(cardWidth * sideMarginFraction, minColumnSpacing)

/**
 * Card width/height for [columns] equal tableau columns across [availableWidth], scaling
 * to fit width first: the columns share the width with a gap between them and a margin at
 * each side, and [TableauGeometryConfig.cardWidthToHeightRatio] fixes height from width.
 * Below the practical minimum width this still returns [TableauGeometryConfig.minCardWidth];
 * the caller may then overflow.
 *
 * Solved in two steps because gap and margin are themselves shares of the answer: divide the
 * width among the columns and their share of the gutters, then take the floors into account
 * and re-divide.
 */
fun TableauGeometryConfig.computeCardSize(availableWidth: Dp, columns: Int): CardSize {
    require(columns > 0) { "columns must be positive" }
    val shares = columns + (columns - 1) * columnGapFraction + 2 * sideMarginFraction
    val provisional = availableWidth / shares
    val gutters = columnGapFor(provisional) * (columns - 1) + boardSideMarginFor(provisional) * 2
    val width = maxOf((availableWidth - gutters) / columns, minCardWidth)
    return CardSize(width, width * cardWidthToHeightRatio)
}

/**
 * Spacing between adjacent tableau columns for [columns] columns of [cardWidth] across
 * [availableWidth]. Never less than [TableauGeometryConfig.minColumnSpacing].
 */
fun TableauGeometryConfig.computeColumnSpacing(availableWidth: Dp, cardWidth: Dp, columns: Int): Dp {
    if (columns <= 1) return minColumnSpacing
    val leftover = availableWidth - cardWidth * columns
    return maxOf(leftover / (columns - 1), minColumnSpacing)
}

/**
 * Tallest card that still lets a [tallestColumn]-card column, of which at most
 * [maxFaceDownInColumn] are ever face down, fit [availableHeight] whole — using the tightest
 * bands the config will ever apply: the face-down floor for the deal's own face-down cards,
 * the face-up guarantee for the rest.
 *
 * Fitting card size to width alone is what lets a wide screen pick a card too tall for the
 * longest column the same board permits, so the column runs off the bottom. Callers take the
 * smaller of this and their width fit.
 */
fun TableauGeometryConfig.cardHeightFittingTallestColumn(
    availableHeight: Dp,
    tallestColumn: Int,
    maxFaceDownInColumn: Int,
): Dp {
    val minCardHeight = minCardWidth * cardWidthToHeightRatio
    if (tallestColumn <= 1) return maxOf(availableHeight, minCardHeight)
    val faceDownSteps = minOf(tallestColumn - 1, maxFaceDownInColumn)
    val faceUpSteps = tallestColumn - 1 - faceDownSteps
    val bands = minFaceDownBandFloor * faceDownSteps + minFaceUpBand * faceUpSteps
    return maxOf(availableHeight - bands, minCardHeight)
}

/**
 * Height a column of [faceDownCount] face-down cards under [faceUpCount] face-up ones needs at
 * the minimum bands — the tightest it can be drawn without compressing the face-down step below
 * its ordinary floor.
 */
fun TableauGeometryConfig.minimumColumnHeight(cardHeight: Dp, faceDownCount: Int, faceUpCount: Int): Dp =
    cardHeight + minFaceDownBand * faceDownCount + minFaceUpBand * (faceUpCount - 1).coerceAtLeast(0)

data class TableauOverlap(val faceDownStep: Dp, val faceUpStep: Dp)

/**
 * Vertical step between successive card tops in a tableau column holding [faceDownCount]
 * face-down cards under [faceUpCount] face-up cards, fit to [availableHeight]. The face-up
 * step never drops below [TableauGeometryConfig.minFaceUpBand] (a hard guarantee, not
 * best-effort); the face-down step may shrink further, down to
 * [TableauGeometryConfig.minFaceDownBandFloor]. Extra height beyond the minimum layout can grow
 * the face-up step for readability, up to [TableauGeometryConfig.maxFaceUpStepFraction] of the
 * card height — cards always stay stacked, never fully separated.
 */
fun TableauGeometryConfig.computeTableauOverlap(
    availableHeight: Dp,
    cardHeight: Dp,
    faceDownCount: Int,
    faceUpCount: Int,
): TableauOverlap {
    val totalCards = faceDownCount + faceUpCount
    if (totalCards <= 1) return TableauOverlap(minFaceDownBand, minFaceUpBand)

    // Steps are the gaps *between* cards: (totalCards - 1) of them. A step lands on the
    // face-down band whenever the card *above* it is face-down — including the step onto the
    // newly revealed first face-up card, which does not get the face-up readability guarantee
    // since the card above it isn't face-up. Only steps strictly between two face-up cards use
    // the face-up band.
    val faceDownSteps = if (faceUpCount == 0) (faceDownCount - 1).coerceAtLeast(0) else faceDownCount
    val faceUpSteps = (totalCards - 1 - faceDownSteps).coerceAtLeast(0)

    val minHeight = cardHeight + minFaceDownBand * faceDownSteps + minFaceUpBand * faceUpSteps
    if (minHeight <= availableHeight) {
        var leftover = availableHeight - minHeight

        // Face-up first: those are the cards being read and played, so spare height buys
        // legibility there before anywhere else.
        val faceUpStepCap = maxOf(cardHeight * maxFaceUpStepFraction, minFaceUpBand)
        val extraPerFaceUpStep = if (faceUpSteps > 0) {
            minOf(leftover / faceUpSteps, faceUpStepCap - minFaceUpBand)
        } else {
            0.dp
        }
        leftover -= extraPerFaceUpStep * faceUpSteps

        // Then the face-down band: face-down cards carry no information, so their step is
        // capped well below the face-up one — enough to spread the board over the room it has,
        // not enough to suggest there is anything to read down there.
        val faceDownStepCap = maxOf(cardHeight * maxFaceDownStepFraction, minFaceDownBand)
        val extraPerFaceDownStep = if (faceDownSteps > 0) {
            minOf(leftover / faceDownSteps, faceDownStepCap - minFaceDownBand)
        } else {
            0.dp
        }
        return TableauOverlap(minFaceDownBand + extraPerFaceDownStep, minFaceUpBand + extraPerFaceUpStep)
    }

    // Not enough height even at minimum bands: compress the face-down step toward its floor
    // before ever touching the face-up guarantee. The column may still overflow the available
    // height in extreme cases; that is an accepted trade-off, not a silent violation of the
    // face-up-band guarantee.
    val heightWithoutFaceDown = cardHeight + minFaceUpBand * faceUpSteps
    val remainingForFaceDown = availableHeight - heightWithoutFaceDown
    val compressedFaceDownStep = if (faceDownSteps > 0) {
        maxOf(remainingForFaceDown / faceDownSteps, minFaceDownBandFloor)
    } else {
        minFaceDownBandFloor
    }
    return TableauOverlap(compressedFaceDownStep, minFaceUpBand)
}
