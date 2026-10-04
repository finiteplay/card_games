package org.finiteplay.spider.game

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.finiteplay.solitaire.ui.CardSize
import org.finiteplay.solitaire.ui.TableauGeometryConfig
import org.finiteplay.solitaire.ui.TableauOverlap
import org.finiteplay.solitaire.ui.cardHeightFittingTallestColumn
import org.finiteplay.solitaire.ui.computeCardSize
import org.finiteplay.solitaire.ui.computeColumnSpacing
import org.finiteplay.solitaire.ui.computeTableauOverlap
import org.finiteplay.solitaire.ui.minimumColumnHeight
import org.finiteplay.spider.layout.TABLEAU_COLUMNS

/**
 * The most face-down cards any tableau column ever holds: a deep column deals six, the last of
 * them face up (`docs/games/spider/RULES.md` "The layout"). Cards are only ever turned face up,
 * never back down, and a row deal only ever adds a face-up card, so this is a ceiling for the
 * whole game, not just the opening deal.
 */
private const val MAX_FACE_DOWN_IN_COLUMN = 5

/**
 * Spider's own numbers for `:solitaire:ui`'s fitting math (`docs/games/spider/UI_SPEC.md` "Board
 * Geometry"). Most are still Klondike's inherited floors, not yet the product of a design pass of
 * Spider's own, since a floor asserted in two places without a reason for agreeing is exactly the
 * kind of number `CLAUDE.md` warns goes stale first — [minCardWidth] is the one Spider had to
 * make its own regardless: Klondike's 40 dp assumes seven columns, and ten of them at that floor
 * do not fit a 320-360 dp phone at all (436-449 dp needed). `SpiderGeometryGateTest` is what
 * caught this.
 */
internal val SPIDER_GEOMETRY = TableauGeometryConfig(
    cardWidthToHeightRatio = 7f / 5f,
    minColumnSpacing = 4.dp,
    // Ten columns' own floor, not Klondike's seven-column one: at 320 dp with the gap and margin
    // fractions below, ten columns fit width naturally at about 27.5 dp; 26 dp leaves a small
    // margin below that so the floor never actually overrides the fit at the gate widths.
    minCardWidth = 26.dp,
    // Tightened from Klondike's 24/10 to buy back vertical space in landscape, where ten columns
    // and a full-height lane leave the least of it. A face-up band still has to show rank and
    // suit — that is what it is for — and 19 dp does at the sizes this board reaches; a face-down
    // band only has to prove another card is under there.
    minFaceUpBand = 19.dp,
    minFaceDownBand = 9.dp,
    minFaceDownBandFloor = 4.dp,
    maxFaceUpStepFraction = 0.52f,
    maxFaceDownStepFraction = 0.2f,
    columnGapFraction = 0.10f,
    sideMarginFraction = 0.16f,
)

/** Card size for [TABLEAU_COLUMNS] equal columns across [availableWidth]. */
fun computeSpiderCardSize(availableWidth: Dp, columns: Int = TABLEAU_COLUMNS): CardSize =
    SPIDER_GEOMETRY.computeCardSize(availableWidth, columns)

fun spiderColumnSpacing(availableWidth: Dp, cardWidth: Dp, columns: Int = TABLEAU_COLUMNS): Dp =
    SPIDER_GEOMETRY.computeColumnSpacing(availableWidth, cardWidth, columns)

/** Tallest card that still lets [tallestColumn] cards fit [availableHeight] whole. */
fun spiderCardHeightFittingTallestColumn(availableHeight: Dp, tallestColumn: Int): Dp =
    SPIDER_GEOMETRY.cardHeightFittingTallestColumn(availableHeight, tallestColumn, MAX_FACE_DOWN_IN_COLUMN)

fun spiderMinimumColumnHeight(cardHeight: Dp, faceDownCount: Int, faceUpCount: Int): Dp =
    SPIDER_GEOMETRY.minimumColumnHeight(cardHeight, faceDownCount, faceUpCount)

fun spiderTableauOverlap(availableHeight: Dp, cardHeight: Dp, faceDownCount: Int, faceUpCount: Int): TableauOverlap =
    SPIDER_GEOMETRY.computeTableauOverlap(availableHeight, cardHeight, faceDownCount, faceUpCount)
