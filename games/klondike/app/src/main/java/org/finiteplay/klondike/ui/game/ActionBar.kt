package org.finiteplay.klondike.ui.game

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.finiteplay.core.ui.R as CoreR
import org.finiteplay.core.ui.layout.BoardAction
import org.finiteplay.core.ui.layout.BoardActionBar
import org.finiteplay.core.ui.layout.BoardOrientation
import org.finiteplay.core.ui.theme.LocalAppColors
import org.finiteplay.klondike.storage.Handedness

/**
 * Which landscape column an action belongs to. [PRIMARY] is the in-play pair, Hint and
 * Undo, pinned to the bottom of the rail nearest the holding hand; [SECONDARY] is
 * everything else, centered in a column at the opposite edge.
 */
enum class LandscapeActionGroup { PRIMARY, SECONDARY }

/**
 * The labeled actions from `UI_SPEC.md`/`DESIGN.md` ("Interface"). Statistics is not among
 * them: it sits in the status row's top corner instead, leaving this bar to the five actions
 * that act on the game in play.
 *
 * The bar's shape — touch targets, locale-stable label heights, equal-width portrait buttons,
 * rail width — is `core:ui`'s [BoardActionBar]. This file decides only which five actions exist
 * and how they split across the two landscape rails, which is the part no other game shares.
 *
 * Undo and Hint sit nearest the player's dominant hand — the right edge of the row in
 * portrait, the bottom of the rail at the right edge in landscape, for [Handedness.RIGHT];
 * [mirrored] (`docs/games/klondike/UI_SPEC.md` "Left-Handed Layout") reverses the portrait
 * row and, in landscape, swaps which edge each rail sits at (the caller's choice), without
 * changing which action does what.
 *
 * In landscape the caller renders this twice, once per [landscapeGroup], and places each
 * column itself; the parameter is ignored in portrait, where all five share one row.
 */
@Composable
fun ActionBar(
    orientation: BoardOrientation,
    canUndo: Boolean,
    hintEnabled: Boolean,
    onUndo: () -> Unit,
    onNew: () -> Unit,
    onReplay: () -> Unit,
    onHint: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    mirrored: Boolean = false,
    landscapeGroup: LandscapeActionGroup = LandscapeActionGroup.PRIMARY,
) {
    val accents = LocalAppColors.current.action
    val settings = BoardAction(stringResource(CoreR.string.action_settings), Icons.Filled.Settings, accents.settings, enabled = true, onClick = onSettings)
    val replay = BoardAction(stringResource(CoreR.string.action_replay), Icons.Filled.Replay, accents.replay, enabled = true, onClick = onReplay)
    val new = BoardAction(stringResource(CoreR.string.action_new), Icons.Filled.Add, accents.new, enabled = true, onClick = onNew)
    val hint = BoardAction(stringResource(CoreR.string.action_hint), Icons.Filled.Lightbulb, accents.hint, enabled = hintEnabled, onClick = onHint)
    val undo = BoardAction(stringResource(CoreR.string.action_undo), Icons.AutoMirrored.Filled.Undo, accents.undo, enabled = canUndo, onClick = onUndo)

    val actions = if (orientation == BoardOrientation.PORTRAIT) {
        listOf(settings, replay, new, hint, undo)
    } else {
        // One button wide. Five in a single column would not fit a landscape phone's height,
        // which is why they are split across two rails at opposite edges — the two-button
        // primary rail is short enough to sit at the bottom corner, the three secondary ones
        // fit centered in the other. Undo last, so it lands in the corner nearest the
        // holding hand.
        when (landscapeGroup) {
            LandscapeActionGroup.PRIMARY -> listOf(hint, undo)
            LandscapeActionGroup.SECONDARY -> listOf(settings, replay, new)
        }
    }

    BoardActionBar(orientation = orientation, actions = actions, modifier = modifier, mirrored = mirrored)
}
