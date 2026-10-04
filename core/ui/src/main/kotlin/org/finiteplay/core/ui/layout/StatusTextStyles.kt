package org.finiteplay.core.ui.layout

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * How a board's status line is typed, in one place so two games do not drift into naming the same
 * thing in different colours.
 *
 * Both scale with the chrome ([chromeScale]) rather than sitting at a fixed size: the status line
 * is chrome, and a player who has enlarged everything else has enlarged this too.
 */
@Composable
fun boardTitleStyle(): TextStyle =
    MaterialTheme.typography.headlineSmall.let { it.copy(fontSize = it.fontSize * chromeScale()) }

/**
 * The title's colour. Deliberately not `onBackground`: the game's own name is the one piece of the
 * status line that identifies rather than reports, and the theme's primary is what marks it as
 * such — near-white on the dark table, white on the light one.
 */
@Composable
fun boardTitleColor(): Color = MaterialTheme.colorScheme.primary

/** Moves, time, and anything else the line reports about the game in play. */
@Composable
fun boardStatusStyle(): TextStyle = MaterialTheme.typography.bodyMedium.let {
    it.copy(fontSize = it.fontSize * chromeScale(), lineHeight = it.lineHeight * chromeScale())
}

/** The reading's colour: plain text on the table, at full contrast in both themes. */
@Composable
fun boardStatusColor(): Color = MaterialTheme.colorScheme.onBackground
