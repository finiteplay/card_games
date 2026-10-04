package org.finiteplay.klondike.ui.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.klondike.deal.DifficultyTier
import org.finiteplay.klondike.debug.nearWinGameState
import org.junit.Rule
import org.junit.Test

/**
 * The debug rating capture is the only source of the subjective-difficulty data
 * `docs/games/klondike/DIFFICULTY_LEVELS.md` grades itself against, so a tier the player
 * cannot reach is a silently truncated dataset — and it was truncated at exactly the top
 * end the grading is least sure about. Six tiers on one `Row` overflow an `AlertDialog`'s
 * content width, and a `Row` clips rather than wraps, so Expert and Insane were laid out
 * past the right edge: present in the tree, and `assertIsDisplayed` fails on them.
 */
class WinDialogRatingTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun everyDifficultyOptionIsOnScreen() {
        composeRule.setContent {
            FinitePlayTheme {
                WinDialog(
                    state = nearWinGameState(),
                    elapsedSeconds = 42,
                    skipAnimations = true,
                    dealDifficulty = DifficultyTier.EXPERT,
                    solutionMoveCount = 0,
                    onNewGame = {},
                    onReplay = {},
                )
            }
        }

        DifficultyTier.entries.forEach { tier ->
            composeRule.onNodeWithTag("debug_rate_${tier.name.lowercase()}").assertIsDisplayed()
        }
    }
}
