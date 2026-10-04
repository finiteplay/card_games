package org.finiteplay.solitaire.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the fitting math against two distinct configs and two distinct column counts, rather
 * than one of each — the shape a single-game test suite cannot rule out is a constant quietly
 * hardcoded instead of read from [TableauGeometryConfig], which would pass every test written
 * against the one config that happens to be in scope.
 *
 * [shipped] reproduces the first shipped game's own Board Geometry numbers exactly, so its own
 * geometry test and this suite pin the same numbers from two directions. [wide] is a different
 * aspect ratio and different floors, standing in for a game that has not stated its own numbers
 * yet.
 */
class TableauGeometryTest {

    private val shipped = TableauGeometryConfig(
        cardWidthToHeightRatio = 7f / 5f,
        minColumnSpacing = 4.dp,
        minCardWidth = 40.dp,
        minFaceUpBand = 24.dp,
        minFaceDownBand = 10.dp,
        minFaceDownBandFloor = 4.dp,
        maxFaceUpStepFraction = 0.52f,
        maxFaceDownStepFraction = 0.2f,
        columnGapFraction = 0.10f,
        sideMarginFraction = 0.16f,
    )

    private val wide = TableauGeometryConfig(
        cardWidthToHeightRatio = 3f / 2f,
        minColumnSpacing = 2.dp,
        minCardWidth = 30.dp,
        minFaceUpBand = 18.dp,
        minFaceDownBand = 6.dp,
        minFaceDownBandFloor = 3.dp,
        maxFaceUpStepFraction = 0.4f,
        maxFaceDownStepFraction = 0.15f,
        columnGapFraction = 0.08f,
        sideMarginFraction = 0.12f,
    )

    @Test
    fun `card width never drops below the configured floor`() {
        for (config in listOf(shipped, wide)) {
            for (columns in listOf(7, 10)) {
                for (width in listOf(320.dp, 360.dp, 800.dp)) {
                    val size = config.computeCardSize(width, columns)
                    assertTrue("$config at $columns columns, $width", size.width >= config.minCardWidth)
                }
            }
        }
    }

    @Test
    fun `card height follows the configured ratio exactly`() {
        val size = shipped.computeCardSize(360.dp, columns = 7)
        assertEquals(size.width.value * shipped.cardWidthToHeightRatio, size.height.value, 0.01f)
    }

    @Test
    fun `more columns yields a narrower card at the same width`() {
        val sevenColumns = shipped.computeCardSize(800.dp, columns = 7).width
        val tenColumns = shipped.computeCardSize(800.dp, columns = 10).width

        assertTrue(tenColumns < sevenColumns)
    }

    @Test
    fun `column spacing never drops below the configured minimum`() {
        for (config in listOf(shipped, wide)) {
            val spacing = config.computeColumnSpacing(360.dp, cardWidth = 60.dp, columns = 10)
            assertTrue(spacing >= config.minColumnSpacing)
        }
    }

    @Test
    fun `a single column needs no spacing beyond the floor`() {
        assertEquals(shipped.minColumnSpacing, shipped.computeColumnSpacing(360.dp, cardWidth = 60.dp, columns = 1))
    }

    @Test
    fun `the face-up step never drops below the configured band, however tall the column`() {
        for (config in listOf(shipped, wide)) {
            val overlap = config.computeTableauOverlap(
                availableHeight = 200.dp,
                cardHeight = 140.dp,
                faceDownCount = 6,
                faceUpCount = 13,
            )
            assertTrue("$config: ${overlap.faceUpStep}", overlap.faceUpStep >= config.minFaceUpBand)
        }
    }

    @Test
    fun `the face-down step may compress toward its floor when the column cannot fit`() {
        val overlap = shipped.computeTableauOverlap(
            availableHeight = 200.dp,
            cardHeight = 140.dp,
            faceDownCount = 6,
            faceUpCount = 13,
        )
        assertEquals(shipped.minFaceDownBandFloor, overlap.faceDownStep)
        assertEquals(shipped.minFaceUpBand, overlap.faceUpStep)
    }

    @Test
    fun `spare height grows the face-up step before the face-down step`() {
        val tight = shipped.computeTableauOverlap(500.dp, 140.dp, faceDownCount = 6, faceUpCount = 7)
        val roomy = shipped.computeTableauOverlap(700.dp, 140.dp, faceDownCount = 6, faceUpCount = 7)

        assertTrue(roomy.faceUpStep > tight.faceUpStep)
    }

    @Test
    fun `the face-up step is capped and never separates the cards fully`() {
        val overlap = shipped.computeTableauOverlap(
            availableHeight = 5000.dp,
            cardHeight = 140.dp,
            faceDownCount = 0,
            faceUpCount = 2,
        )
        assertTrue(overlap.faceUpStep <= 140.dp * shipped.maxFaceUpStepFraction)
    }

    @Test
    fun `a column of one card needs only the minimum bands`() {
        assertEquals(
            TableauOverlap(shipped.minFaceDownBand, shipped.minFaceUpBand),
            shipped.computeTableauOverlap(1000.dp, 140.dp, faceDownCount = 1, faceUpCount = 0),
        )
    }

    @Test
    fun `tallest-column card height respects the deal's own face-down cap`() {
        // A deal where every buried card counts as face-down (cap far above the column depth)
        // fits a shorter card than one where the cap is tight and most steps are face-up.
        val looseCap = shipped.cardHeightFittingTallestColumn(400.dp, tallestColumn = 19, maxFaceDownInColumn = 18)
        val tightCap = shipped.cardHeightFittingTallestColumn(400.dp, tallestColumn = 19, maxFaceDownInColumn = 6)

        assertTrue(looseCap > tightCap)
    }

    @Test
    fun `minimum column height accounts for every step, not every card`() {
        val height = shipped.minimumColumnHeight(cardHeight = 100.dp, faceDownCount = 3, faceUpCount = 4)

        // One card's height, plus a band for each of the three face-down steps, plus a band for
        // three of the four face-up steps (the fourth card has no step above it).
        assertEquals(100.dp + shipped.minFaceDownBand * 3 + shipped.minFaceUpBand * 3, height)
    }
}
