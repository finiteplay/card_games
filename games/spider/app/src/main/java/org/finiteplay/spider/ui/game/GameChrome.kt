package org.finiteplay.spider.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import org.finiteplay.cards.Suit
import org.finiteplay.core.session.formatElapsed
import org.finiteplay.core.ui.R as CoreR
import org.finiteplay.core.ui.layout.BoardAction
import org.finiteplay.core.ui.layout.boardStatusColor
import org.finiteplay.core.ui.layout.boardStatusStyle
import org.finiteplay.core.ui.layout.boardTitleColor
import org.finiteplay.core.ui.layout.boardTitleStyle
import org.finiteplay.core.ui.layout.BoardActionBar
import org.finiteplay.core.ui.layout.BoardOrientation
import org.finiteplay.core.ui.layout.landscapeRailWidth
import org.finiteplay.core.ui.theme.CardColors
import org.finiteplay.core.ui.theme.LocalAppColors
import org.finiteplay.spider.R
import org.finiteplay.spider.layout.SuitCount

/**
 * The status row's accent for a suit count, borrowing three stops off Klondike's own
 * cool-to-warm difficulty ramp (`core:ui`'s `DifficultyAccentColors`) rather than a Spider-only
 * palette: ONE calmest and FOUR warmest is the same "more to manage reads as harder" mapping the
 * ramp already encodes, and Spider has no difficulty accents of its own to spend on a third hue
 * set. Color is never the only cue — the count is always spelled out beside it.
 */
@Composable
private fun SuitCount.accent(): Color {
    val accents = LocalAppColors.current.difficulty
    return when (this) {
        SuitCount.ONE -> accents.trivial
        SuitCount.TWO -> accents.medium
        SuitCount.FOUR -> accents.hard
    }
}

/**
 * The game's name, what it currently reads, and a trailing group that differs by orientation —
 * the banked counters in landscape, Help and Statistics in portrait — matching the shape of
 * Klondike's own status row (`GameScreen.kt`'s `StatusRow`): title on the leading edge, the level
 * (here, suit count and deal number) and the move/time counters centered in whatever space is
 * left, so they read as one line with the title rather than a block under it.
 *
 * [singleLine] is true in landscape: the board needs every bit of height there, so the deal
 * number and the counters share the one line landscape can spare rather than costing a second —
 * the same reasoning Klondike's own `lines` parameter follows. Portrait has the room and keeps
 * them on separate lines. Landscape also has no spare row for the banked counters, so they join
 * this same line instead of costing another one, and Help/Statistics move to the landscape rails
 * instead of showing here; see [GameScreen].
 */
@Composable
internal fun StatusRow(
    suitCount: SuitCount,
    dealNumber: Int,
    moves: Int,
    elapsedSeconds: Int,
    won: Boolean,
    banked: Map<Suit, Int>?,
    suits: List<Suit>,
    colors: CardColors,
    onSelectSuitCount: () -> Unit,
    onSelectDeal: () -> Unit,
    singleLine: Boolean = false,
    onHelp: (() -> Unit)? = null,
    onStatistics: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.game_title),
            style = boardTitleStyle(),
            fontWeight = FontWeight.Bold,
            color = boardTitleColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("game_title"),
        )
        // The height is reserved on the box, not by padding the text out to that many lines, so
        // the row never grows or shrinks between the won state (shorter) and the normal state —
        // the same reasoning as Klondike's own reserved-height box.
        val statusStyle = boardStatusStyle()
        val statusLines = if (singleLine) 1 else 2
        val reservedHeight = with(LocalDensity.current) { (statusStyle.lineHeight * statusLines).toDp() }
        Box(
            modifier = Modifier.weight(1f).heightIn(min = reservedHeight).padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            val counters = if (won) {
                stringResource(CoreR.string.status_won)
            } else {
                stringResource(CoreR.string.status_moves_time, moves, formatElapsed(elapsedSeconds))
            }
            val separator = if (singleLine) "  " else "\n"
            // The suit count is a link, not merely coloured text, mirroring Klondike's own level
            // span (`GameScreen.kt`'s `StatusRow`): it is the shortest route to changing it, and
            // Settings is a long way to go for something named on screen.
            val suitCountLink = LinkAnnotation.Clickable(
                tag = "suit_count",
                styles = TextLinkStyles(SpanStyle(color = suitCount.accent(), fontWeight = FontWeight.Bold)),
            ) { onSelectSuitCount() }
            // The deal number is its own link: the suit count changes which game is played, the number
            // picks a deal within it, and a player reaching for one should not land on the other.
            val dealLink = LinkAnnotation.Clickable(
                tag = "deal",
                styles = TextLinkStyles(SpanStyle(color = suitCount.accent(), fontWeight = FontWeight.Bold)),
            ) { onSelectDeal() }
            val status = buildAnnotatedString {
                withLink(suitCountLink) { append(stringResource(suitCount.labelRes())) }
                append(" ")
                withLink(dealLink) { append(stringResource(CoreR.string.deal_number, dealNumber)) }
                append(separator)
                append(counters)
            }
            Text(
                text = status,
                style = statusStyle,
                color = boardStatusColor(),
                textAlign = TextAlign.Center,
                maxLines = statusLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("status_row"),
            )
        }
        if (banked != null) BankedCounters(banked = banked, suits = suits, colors = colors)
        // Help and Statistics describe the game rather than act on it, so they keep the trailing
        // corner conventional for them instead of taking two slots in the action bar — where,
        // with everything Spider now has, "Statistics" no longer fits on one line.
        if (onHelp != null) StatusIcon(Icons.AutoMirrored.Filled.HelpOutline, R.string.help_title, LocalAppColors.current.action.replay, "help_button", onHelp)
        if (onStatistics != null) StatusIcon(Icons.Filled.BarChart, CoreR.string.statistics_title, LocalAppColors.current.action.statistics, "statistics_button", onStatistics)
    }
}

@Composable
private fun StatusIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescriptionRes: Int,
    tint: Color,
    tag: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp).testTag(tag)) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(contentDescriptionRes),
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Banked sequences per suit.
 *
 * The glyph keeps its suit colour and the count takes the surrounding text colour: the suit says
 * which pile this is, the number is a reading. Colouring the number as a suit is what made these
 * invisible on the dark board, where the card-face black is near-black by design.
 */
@Composable
internal fun BankedCounters(
    banked: Map<Suit, Int>,
    suits: List<Suit>,
    colors: CardColors,
    // Raw layout coordinates of each suit's own counter, reported once it is laid out — the
    // caller converts to whatever frame it needs (`SpiderBoard.kt` converts to board-relative, the
    // same frame `columnRects` already uses). A no-op where that animation can't reach: landscape's
    // own call to this composable lives in the status row, outside the board's coordinate space
    // entirely, so it is left unwired there rather than reporting a position the board could never
    // make sense of.
    onSuitPositioned: (Suit, LayoutCoordinates) -> Unit = { _, _ -> },
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (suit in suits) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    onSuitPositioned(suit, coordinates)
                },
            ) {
                Text(
                    text = suit.symbol.toString(),
                    color = colors.forSuitOnTable(suit.color),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = (banked[suit] ?: 0).toString(),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/**
 * The recovery and uncertified notices, in the order a player needs them.
 *
 * [showUncertified] is false in landscape and false for a certified deal — every suit count ships
 * a certified catalog (`docs/games/spider/DEALS.md`), so in a shipped build this is never true;
 * it remains for a game dealt without one. Where it can be true, a permanent
 * full-width band is still the single most expensive row on a short screen, and the same
 * statement is carried by Help, one tap away in both orientations — so landscape drops it and
 * portrait, which has the room, still shows it on the board.
 */
@Composable
internal fun Notices(viewModel: SpiderViewModel, showUncertified: Boolean = true) {
    if (viewModel.recoveryNoticeVisible) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.recovery_notice),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f, fill = false),
                )
                TextButton(onClick = viewModel::dismissRecoveryNotice) {
                    Text(stringResource(CoreR.string.action_dismiss))
                }
            }
        }
    }
    if (showUncertified) {
        // Text on the cloth, not a banded panel: the notice has to be readable and permanent, not
        // a separate surface interrupting the table.
        Text(
            text = stringResource(R.string.uncertified_notice),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

/** Which landscape rail an action belongs to, mirroring Klondike's own split. */
internal enum class RailGroup { PRIMARY, SECONDARY }

/**
 * One landscape rail.
 *
 * [RailGroup.SECONDARY] carries the actions that set a game up — Settings, Replay, New — centred
 * in its column, because none of them is reached for mid-move and a group floating at the middle
 * reads as "not the ones you are using".
 *
 * [RailGroup.PRIMARY] splits: Help and Statistics sit at the top, and Hint and Undo are pushed to
 * the bottom, where the holding hand already is. The two the player reaches for repeatedly belong
 * under the thumb; the two that open a screen do not. This is the same split Klondike's rails use,
 * so moving between the games does not move the controls.
 */
@Composable
internal fun ActionRail(
    group: RailGroup,
    viewModel: SpiderViewModel,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onStatistics: () -> Unit,
    onHint: () -> Unit,
    onUndo: () -> Unit,
    onReplay: () -> Unit,
    onNewGame: () -> Unit,
) {
    val accents = LocalAppColors.current.action
    when (group) {
        RailGroup.SECONDARY -> Column(
            modifier = Modifier.width(landscapeRailWidth()).fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoardActionBar(
                orientation = BoardOrientation.LANDSCAPE,
                actions = listOf(
                    settingsAction(accents.settings, onSettings),
                    replayAction(accents.replay, onReplay),
                    newAction(accents.new, onNewGame),
                ),
            )
        }

        RailGroup.PRIMARY -> Column(
            // No vertical padding: the two groups belong in the corners, and padding at each end
            // is taken straight off the gap between them.
            modifier = Modifier.width(landscapeRailWidth()).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoardActionBar(
                orientation = BoardOrientation.LANDSCAPE,
                actions = listOf(
                    helpAction(accents.settings, onHelp),
                    statisticsAction(accents.statistics, onStatistics),
                ),
            )
            Spacer(Modifier.weight(1f))
            BoardActionBar(
                orientation = BoardOrientation.LANDSCAPE,
                actions = listOf(
                    hintAction(accents.hint, onHint),
                    undoAction(accents.undo, viewModel.session.canUndo, onUndo),
                ),
            )
        }
    }
}

/**
 * Portrait's single row: the five actions that act on the game in play, in the order the landscape
 * rails read. Help and Statistics are not among them — they sit in the status row, both because
 * they describe the game rather than change it and because seven labels do not fit across a phone
 * without wrapping.
 */
@Composable
internal fun ActionBar(
    canUndo: Boolean,
    mirrored: Boolean,
    onUndo: () -> Unit,
    onHint: () -> Unit,
    onRestart: () -> Unit,
    onNewGame: () -> Unit,
    onSettings: () -> Unit,
) {
    val accents = LocalAppColors.current.action
    BoardActionBar(
        orientation = BoardOrientation.PORTRAIT,
        mirrored = mirrored,
        actions = listOf(
            settingsAction(accents.settings, onSettings),
            replayAction(accents.replay, onRestart),
            newAction(accents.new, onNewGame),
            hintAction(accents.hint, onHint),
            undoAction(accents.undo, canUndo, onUndo),
        ),
    )
}

@Composable
private fun settingsAction(color: Color, onClick: () -> Unit) = BoardAction(
    label = stringResource(CoreR.string.action_settings),
    icon = Icons.Filled.Settings,
    accentColor = color,
    enabled = true,
    onClick = onClick,
    testTag = "action_settings",
)

@Composable
private fun helpAction(color: Color, onClick: () -> Unit) = BoardAction(
    label = stringResource(R.string.action_help),
    icon = Icons.AutoMirrored.Filled.HelpOutline,
    accentColor = color,
    enabled = true,
    onClick = onClick,
    testTag = "action_help",
)

@Composable
private fun statisticsAction(color: Color, onClick: () -> Unit) = BoardAction(
    label = stringResource(CoreR.string.action_statistics),
    icon = Icons.Filled.BarChart,
    accentColor = color,
    enabled = true,
    onClick = onClick,
    testTag = "action_statistics",
)

@Composable
private fun replayAction(color: Color, onClick: () -> Unit) = BoardAction(
    label = stringResource(CoreR.string.action_replay),
    icon = Icons.Filled.Replay,
    accentColor = color,
    enabled = true,
    onClick = onClick,
    testTag = "action_replay",
)

@Composable
private fun newAction(color: Color, onClick: () -> Unit) = BoardAction(
    label = stringResource(CoreR.string.action_new),
    icon = Icons.Filled.Add,
    accentColor = color,
    enabled = true,
    onClick = onClick,
    testTag = "action_new",
)

@Composable
private fun hintAction(color: Color, onClick: () -> Unit) = BoardAction(
    label = stringResource(CoreR.string.action_hint),
    icon = Icons.Filled.Lightbulb,
    accentColor = color,
    enabled = true,
    onClick = onClick,
    testTag = "action_hint",
)

@Composable
private fun undoAction(color: Color, enabled: Boolean, onClick: () -> Unit) = BoardAction(
    label = stringResource(CoreR.string.action_undo),
    icon = Icons.AutoMirrored.Filled.Undo,
    accentColor = color,
    enabled = enabled,
    onClick = onClick,
    testTag = "action_undo",
)
