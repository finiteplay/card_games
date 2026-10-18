package org.finiteplay.holdem.ui

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.unit.dp
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.holdem.opponents.HintReason
import org.finiteplay.holdem.rules.Action
import org.finiteplay.holdem.rules.HindsightResult
import org.finiteplay.holdem.rules.evaluate
import org.finiteplay.holdem.rules.startTournament
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/** Check actual pixels: a semantics string alone cannot detect an empty, opaque rendered bar. */
class OpponentHandRenderingTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rightSeatStraightWinnerRendersCompleteHandName() {
        val board = listOf(
            Card(Suit.SPADES, Rank.ACE), Card(Suit.SPADES, Rank.SEVEN),
            Card(Suit.CLUBS, Rank.EIGHT), Card(Suit.CLUBS, Rank.NINE), Card(Suit.CLUBS, Rank.JACK),
        )
        val cards = listOf(Card(Suit.DIAMONDS, Rank.TEN), Card(Suit.CLUBS, Rank.ACE))
        val dealt = TablePresenter.dealt(startTournament(21L).state)
        val review = HandReview(
            kind = ReviewKind.FOLLOWED_FOLD,
            hint = HintShown(Action.Fold, equity = 0.31, potOdds = 0.2, nothingToCall = false, reason = HintReason.FOLD_TO_RAISE),
            hindsightResult = HindsightResult.LOST,
        )
        val table = dealt.copy(
            board = board, actor = null, stage = Stage.HAND_OVER, pot = 0,
            seats = dealt.seats.mapIndexed { seat, model ->
                model.copy(
                    bet = 0,
                    cards = when (seat) {
                        0 -> listOf(Card(Suit.HEARTS, Rank.JACK), Card(Suit.SPADES, Rank.QUEEN))
                        5 -> cards
                        else -> null
                    },
                    handValue = if (seat == 5) evaluate(cards + board) else null,
                    net = when (seat) { 0 -> -80; 5 -> 1_758; else -> 0 },
                    stack = when (seat) { 0 -> 1_536; 5 -> 4_950; else -> model.stack },
                    place = if (seat == 1) 5 else null,
                    folded = seat == 0 || seat == 1,
                )
            },
        )
        composeRule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.forLanguageTag("uk")) }
            val context = LocalContext.current.createConfigurationContext(configuration)
            CompositionLocalProvider(LocalContext provides context, LocalResources provides context.resources, LocalConfiguration provides configuration) {
                FinitePlayTheme(darkTheme = true) {
                    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF306050)).padding(top = 90.dp)) {
                        TableView(table, DrawnRoster.profiles(21L), landscape = false, animate = false, modifier = Modifier.height(700.dp))
                        HandReviewNotice(review, Modifier.align(Alignment.TopCenter).padding(top = maxHeight * 0.165f + 4.dp))
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("seat_5_hand", useUnmergedTree = true).assertTextEquals("Стріт, старша карта: валет")
        run {
            val pixels = composeRule.onNodeWithTag("seat_5_hand", useUnmergedTree = true).captureToImage().toPixelMap()
            var textPixels = 0
            var translucentPixels = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val color = pixels[x, y]
                if (color.red > 0.7f && color.green > 0.7f && color.blue > 0.7f) textPixels++
                // The label is 36% black over green felt, even when the seat is active.
                if (color.green in 0.22f..0.26f && color.red in 0.10f..0.14f) translucentPixels++
            }
            assertTrue("right winner must paint white hand-name text ($textPixels pixels)", textPixels > 10)
            assertTrue("right winner label must retain visible felt through its background ($translucentPixels pixels)", translucentPixels > 10)
        }
    }
}
