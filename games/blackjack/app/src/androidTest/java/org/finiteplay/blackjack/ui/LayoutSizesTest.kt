package org.finiteplay.blackjack.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.Alignment
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.finiteplay.blackjack.rules.SHOE_SIZE
import org.finiteplay.blackjack.storage.BlackjackLedgerStore
import org.finiteplay.blackjack.storage.BlackjackRoundStore
import org.finiteplay.blackjack.storage.BlackjackSettingsStore
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * The table at the screen sizes it has to hold — every button the phase offers, and the most cards
 * a round can put down — checked against the window it is drawn in, not the device's.
 */
class LayoutSizesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class Window(val w: Int, val h: Int) {
        val landscape get() = w > h
        override fun toString() = "${w}x$h" + if (landscape) " landscape" else " portrait"
    }

    // Small phone, common phone, tall phone; then the same turned on its side.
    private val windows = listOf(
        Window(320, 480), Window(360, 640), Window(412, 800),
        Window(440, 300), Window(480, 320), Window(640, 360), Window(800, 360), Window(892, 412),
    )

    private var window by mutableStateOf(windows[1])

    private fun card(text: String): Card {
        val rank = if (text[0] == 'T') Rank.TEN else Rank.fromValue(text[0].digitToInt())
        val suit = when (text[1]) {
            'C' -> Suit.CLUBS
            'D' -> Suit.DIAMONDS
            'H' -> Suit.HEARTS
            else -> Suit.SPADES
        }
        return Card(suit, rank)
    }

    /** Dealt P 2, D 2, P 2, D 2, then twos for as long as anything draws: pairs to split, a dealer who draws and draws. */
    private val shoe: List<Card> = List(SHOE_SIZE) { card("2" + "CDHS"[it % 4]) }

    private fun show() {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "sizes-${System.nanoTime()}").also { it.mkdirs() }
        runBlocking { BlackjackSettingsStore(dir).setAnimationsEnabled(false) }
        val viewModel = BlackjackViewModel(
            roundStore = BlackjackRoundStore(dir),
            ledgerStore = BlackjackLedgerStore(dir),
            settingsStore = BlackjackSettingsStore(dir),
            seedSource = { 1L },
            shoeOverride = { shoe },
        )
        composeRule.setContent {
            val base = LocalConfiguration.current
            val configuration = Configuration(base).apply {
                orientation = if (window.landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                FinitePlayTheme { Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).size(window.w.dp, window.h.dp)) { GameScreen(viewModel) } }
            }
        }
        composeRule.waitUntil(5_000) { !viewModel.isLoading }
    }

    /** Where the node really is: `getBoundsInRoot` is clipped to its parents, so a node pushed off screen still reads as on it. */
    private fun rect(tag: String): Rect {
        val node = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val density = composeRule.density
        return with(density) {
            Rect(node.positionInRoot.x.toDp().value, node.positionInRoot.y.toDp().value, (node.positionInRoot.x + node.size.width).toDp().value, (node.positionInRoot.y + node.size.height).toDp().value)
        }
    }

    private class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        override fun toString() = "%.0f,%.0f–%.0f,%.0f".format(left, top, right, bottom)
    }

    private fun present(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun tap(tag: String) {
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.waitForIdle()
    }

    private val buttons = listOf(
        "help_button", "statistics_button", "action_settings", "action_bet_down", "action_bet_up", "action_deal",
        "action_hit", "action_stand", "action_double", "action_split", "action_hint",
    )
    private val cards = listOf("dealer_cards", "dealer_total") + (0..3).flatMap { listOf("hand_${it}_cards", "hand_${it}_total") }

    /** What does not fit, at every window, for the state the table is in. */
    private fun misfits(state: String): List<String> = windows.flatMap { win ->
        window = win
        composeRule.waitForIdle()
        val root = composeRule.onNodeWithTag("app_root", useUnmergedTree = true).getBoundsInRoot()
        val sized = if (root.width == win.w.dp && root.height == win.h.dp) emptyList() else listOf("$state @ $win: drawn at ${root.width} x ${root.height}")
        val tags = (buttons + cards).filter(::present)
        android.util.Log.i("LAYOUT", "$state @ $win root=${rect("app_root")} " + tags.joinToString { "$it=${rect(it)}" })
        sized + tags.mapNotNull { tag ->
            val b = rect(tag)
            val inside = b.left >= -0.5f && b.top >= -0.5f && b.right <= win.w + 0.5f && b.bottom <= win.h + 0.5f
            if (inside) null else "$state @ $win: $tag at $b"
        } + run {
            // Cards must not run into each other, the dealer's, or the rail buttons.
            val areas = (listOf("dealer_cards") + (0..3).map { "hand_${it}_cards" } + listOf("action_hit", "action_stand", "action_hint", "action_deal", "action_double", "action_split")).filter(::present)
            val boxes = areas.map { it to rect(it) }
            boxes.flatMap { (a, ba) ->
                boxes.filter { (b, _) -> a < b }.mapNotNull { (b, bb) ->
                    val overlap = ba.left < bb.right - 1 && bb.left < ba.right - 1 && ba.top < bb.bottom - 1 && bb.top < ba.bottom - 1
                    if (overlap) "$state @ $win: $a overlaps $b" else null
                }
            }
        } + listOf("action_hit", "action_stand", "action_double", "action_split", "action_hint", "action_deal").filter(::present).let { shown ->
            // Buttons must not sit on top of one another.
            val boxes = shown.map { it to rect(it) }
            boxes.flatMap { (a, ba) ->
                boxes.filter { (b, _) -> a < b }.mapNotNull { (b, bb) ->
                    val overlap = ba.left < bb.right - 1 && bb.left < ba.right - 1 && ba.top < bb.bottom - 1 && bb.top < ba.bottom - 1
                    if (overlap) "$state @ $win: $a overlaps $b" else null
                }
            }
        }
    }

    /** Pictures of the state at the tightest windows, pulled off the device to be looked at. */
    private fun shoot(state: String) {
        val out = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        if (System.getProperty("noshots") != null) return
        listOf(Window(320, 480), Window(360, 640), Window(440, 300), Window(440, 360)).forEach { win ->
            window = win
            composeRule.waitForIdle()
            val image = composeRule.onNodeWithTag("app_root", useUnmergedTree = true).captureToImage().asAndroidBitmap()
            File(out, "shot-${state.replace(' ', '_')}-${win.w}x${win.h}.png").outputStream().use {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test
    fun everyButtonAndEveryCardFitsAtEverySize() {
        show()
        val problems = mutableListOf<String>()
        shoot("idle")
        problems += misfits("idle")
        tap("action_deal")
        problems += misfits("dealt, pair: split, double, stand, hit, hint")
        shoot("pair")
        repeat(3) { tap("action_split") }
        // Four hands of two cards, then every hand to six, standing on each but the last.
        problems += misfits("four hands")
        repeat(4) { hand ->
            repeat(4) { tap("action_hit") }
            if (hand < 3) tap("action_stand")
        }
        problems += misfits("four hands of six cards")
        shoot("six")
        tap("action_stand")
        composeRule.waitUntil(15_000) { present("result_mark") }
        problems += misfits("settled, dealer drawn out")
        shoot("settled")
        assertTrue(problems.joinToString("\n", prefix = "\n"), problems.isEmpty())
    }
}
