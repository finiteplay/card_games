package org.finiteplay.core.ui.layout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Board orientation, which decides whether the actions form a row or an edge rail. */
enum class BoardOrientation { PORTRAIT, LANDSCAPE }

private const val ACTION_TOUCH_TARGET_DP = 60

/**
 * Every label occupies exactly two lines, whatever it says. A one-word label and a wrapped
 * translation of it then take identical space, so the bar's height — and each button's position
 * within it — is the same in every locale instead of shifting per translation
 * (`docs/PLATFORM.md` "Localization").
 */
private const val ACTION_LABEL_LINES = 2

/**
 * Width of a landscape rail: one action button plus its padding. Callers size their own rails
 * to it so the board gets exactly the width that remains.
 */
val LANDSCAPE_RAIL_WIDTH = ACTION_TOUCH_TARGET_DP.dp + 16.dp

/** The rail widens with the chrome, or the buttons inside it overflow it on a tablet. */
@Composable
fun landscapeRailWidth(): Dp = ACTION_TOUCH_TARGET_DP.dp * chromeScale() + 4.dp

/**
 * One labeled action: an icon above a label, in its own accent colour.
 *
 * [accentColor] applies only while [enabled]; a disabled action always falls back to flat grey,
 * so colour never implies usability.
 */
data class BoardAction(
    val label: String,
    val icon: ImageVector,
    val accentColor: Color,
    val enabled: Boolean,
    val onClick: () -> Unit,
    /** Optional tag for this action's button, so a caller's tests can address it by name. */
    val testTag: String? = null,
)

/**
 * The in-play action bar: [actions] as a single row in portrait, or as one edge rail in
 * landscape.
 *
 * Which actions exist, what they mean, and how they split across rails is the caller's — a game
 * with no hint has no hint button, and nothing here assumes a particular set or count. This owns
 * only the shape they are drawn in, which is where the platform-wide requirements live: touch
 * targets that hold at every chrome scale, labels that do not reflow the bar when translated,
 * and equal-width portrait buttons.
 *
 * [mirrored] reverses the portrait order so the actions a player uses mid-game sit nearest the
 * holding hand ([Handedness]); it changes position only, never which action does what. In
 * landscape the caller renders this once per rail and places each itself, so mirroring there is
 * a matter of which edge it passes each group to.
 */
@Composable
fun BoardActionBar(
    orientation: BoardOrientation,
    actions: List<BoardAction>,
    modifier: Modifier = Modifier,
    mirrored: Boolean = false,
) {
    if (orientation == BoardOrientation.PORTRAIT) {
        val ordered = if (mirrored) actions.reversed() else actions
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(8.dp)
                .semantics { traversalIndex = 100f },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Equal shares of the row rather than content-sized buttons: a long translation
            // otherwise widens its own button and pushes every later one along, eventually off
            // the screen edge. Each button owns a fixed share and its label wraps inside it.
            ordered.forEach {
                ActionButton(it, Modifier.weight(1f))
            }
        }
    } else {
        Column(
            modifier = modifier
                .width(landscapeRailWidth())
                .padding(horizontal = 2.dp)
                .semantics { traversalIndex = 100f },
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            actions.forEach {
                ActionButton(it, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ActionButton(
    action: BoardAction,
    modifier: Modifier = Modifier,
) {
    val (label, icon, accentColor, enabled, onClick, tag) = action
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val iconColor = if (enabled) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = ACTION_TOUCH_TARGET_DP.dp * chromeScale())
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .padding(PaddingValues(horizontal = 2.dp, vertical = 4.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(30.dp * chromeScale()),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.let { it.copy(fontSize = it.fontSize * chromeScale()) },
            textAlign = TextAlign.Center,
            minLines = ACTION_LABEL_LINES,
            maxLines = ACTION_LABEL_LINES,
            overflow = TextOverflow.Ellipsis,
            color = textColor,
        )
    }
}
