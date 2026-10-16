package org.finiteplay.core.ui.layout

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import org.finiteplay.core.ui.R

private const val MOVES_ICON = "status_moves_icon"
private const val TIME_ICON = "status_time_icon"

/**
 * The moves and the time of the game in play, as an icon and a number each ("⇄ 12  ◷ 1:23") instead
 * of the words "Moves:" and "Time:". The words were the widest part of the status row in most
 * languages and pushed the time, the part a player reads it for, off the end of the line; icons and
 * digits are the same width everywhere. The words stay as each icon's alternate text, so the text
 * a screen reader (and a test) gets is still "Moves: 12  Time: 1:23" in the player's language.
 *
 * Pair with [counterIcons] as the `inlineContent` of the `Text` that shows it.
 */
@Composable
fun AnnotatedString.Builder.appendCounters(moves: Int, elapsed: String) {
    appendInlineContent(MOVES_ICON, stringResource(R.string.status_moves_label))
    append(" $moves  ")
    appendInlineContent(TIME_ICON, stringResource(R.string.status_time_label))
    append(" $elapsed")
}

/** The icons [appendCounters] places, sized to the line they sit in and tinted like the text. */
@Composable
fun counterIcons(style: TextStyle, tint: Color): Map<String, InlineTextContent> {
    val size = (if (style.fontSize.isSpecified) style.fontSize else 14.sp) * 1.15f
    val placeholder = Placeholder(size, size, PlaceholderVerticalAlign.TextCenter)
    return mapOf(
        MOVES_ICON to InlineTextContent(placeholder) { Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = tint, modifier = Modifier.fillMaxSize()) },
        TIME_ICON to InlineTextContent(placeholder) { Icon(Icons.Filled.Schedule, contentDescription = null, tint = tint, modifier = Modifier.fillMaxSize()) },
    )
}
