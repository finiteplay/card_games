package org.finiteplay.holdem.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.SentimentVeryDissatisfied
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.finiteplay.cards.Card
import org.finiteplay.core.ui.card.CardFan
import org.finiteplay.core.ui.chips.ChipStack
import org.finiteplay.core.ui.layout.ConfettiBurst
import org.finiteplay.core.ui.layout.LossWash
import org.finiteplay.core.ui.layout.ResultMark
import org.finiteplay.core.ui.layout.signed
import org.finiteplay.holdem.R

private const val RAISE_DP = 6

private val markWin = Color(0xFF7CE08F)
private val markEven = Color(0xFFB0BEC5)
private val markLose = Color(0xFFFF8A80)
private val ring = Color(0xFFFFD54F)

/** A card row whose raised cards (and their outline) may extend above its band into the gap. */
private fun Modifier.liftable(): Modifier = wrapContentHeight(Alignment.Bottom, unbounded = true)

private fun indicesIn(cards: List<Card?>, highlight: Set<Card>?): Set<Int>? =
    highlight?.let { set -> cards.indices.filter { cards[it] in set }.toSet() }

@Composable
private fun nameOf(seat: Int, profiles: List<OpponentProfile>): String =
    if (seat == 0) stringResource(R.string.you) else profiles.getOrNull(seat - 1)?.name ?: ""

/**
 * The felt: six seats round the oval, the board and the pot, per `UI_SPEC.md` "Layout". Card sizes
 * and gaps come from [TableGeometry] against the room the table has.
 */
@Composable
fun TableView(
    table: TableModel,
    profiles: List<OpponentProfile>,
    landscape: Boolean,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (landscape) Landscape(table, profiles, animate, maxWidth.value, maxHeight.value)
            else Portrait(table, profiles, animate, maxWidth.value, maxHeight.value)
        }
        if (animate && table.lossWash) LossWash()
        if (animate && table.confetti > 0) ConfettiBurst(seed = table.handNumber.toLong(), count = table.confetti)
    }
}

@Composable
private fun Portrait(table: TableModel, profiles: List<OpponentProfile>, animate: Boolean, width: Float, height: Float) {
    val g = TableGeometry.portrait(width, height)
    val compact = g.density != TableGeometry.Density.Regular
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = TableGeometry.SIDE_MARGIN.dp),
        verticalArrangement = Arrangement.spacedBy(g.gap.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (seat in listOf(2, 3, 4)) {
                OpponentSeat(seat, table, profiles[seat - 1], g.seatWidth.dp, g.seatHeight.dp, g.opponentCardWidth.dp, compact, animate, PillSide.Below)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            OpponentSeat(1, table, profiles[0], g.seatWidth.dp, g.seatHeight.dp, g.opponentCardWidth.dp, compact, animate, PillSide.End)
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { PotView(table) }
            OpponentSeat(5, table, profiles[4], g.seatWidth.dp, g.seatHeight.dp, g.opponentCardWidth.dp, compact, animate, PillSide.Start)
        }
        BoardBand(table, g.boardCardWidth.dp, g.boardBandHeight.dp, animate)
        YourSeat(table, g.yourCardWidth.dp, g.yourSeatHeight.dp, animate)
    }
}

@Composable
private fun Landscape(table: TableModel, profiles: List<OpponentProfile>, animate: Boolean, width: Float, height: Float) {
    val g = TableGeometry.landscape(width + TableGeometry.RAIL_WIDTH + TableGeometry.SIZING_COLUMN_WIDTH, height + TableGeometry.STATUS_ROW)
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = TableGeometry.SIDE_MARGIN.dp),
        verticalArrangement = Arrangement.spacedBy(g.gap.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (seat in listOf(2, 3, 4)) {
                OpponentSeat(seat, table, profiles[seat - 1], g.seatWidth.dp, g.seatHeight.dp, g.opponentCardWidth.dp, false, animate, PillSide.Below)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            OpponentSeat(1, table, profiles[0], g.seatWidth.dp, g.seatHeight.dp, g.opponentCardWidth.dp, false, animate, PillSide.End)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BoardBand(table, g.boardCardWidth.dp, TableGeometry.cardHeight(g.boardCardWidth).let { kotlin.math.ceil(it) }.dp, animate)
                PotView(table, compactLine = true)
            }
            OpponentSeat(5, table, profiles[4], g.seatWidth.dp, g.seatHeight.dp, g.opponentCardWidth.dp, false, animate, PillSide.Start)
        }
        YourSeat(table, g.yourCardWidth.dp, g.yourSeatHeight.dp, animate)
    }
}

// ---- the pot and the board ---------------------------------------------------------------------

@Composable
private fun PotView(table: TableModel, compactLine: Boolean = false) {
    val spoken = if (table.potCount > 1) stringResource(R.string.a11y_pot_pots, table.pot, table.potCount) else stringResource(R.string.a11y_pot, table.pot)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = spoken }.testTag("pot"),
    ) {
        if (!compactLine && table.pot > 0) ChipStack(amount = table.pot, chipWidth = 18.dp, modifier = Modifier.padding(bottom = 2.dp))
        Text(
            text = stringResource(R.string.pot_label, table.pot),
            style = if (compactLine) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.testTag("pot_amount"),
        )
        if (table.potCount > 1) {
            Text(
                text = stringResource(R.string.pot_count, table.potCount),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.testTag("pot_count"),
            )
        }
    }
}

@Composable
private fun BoardBand(table: TableModel, cardWidth: Dp, bandHeight: Dp, animate: Boolean) {
    val spoken = if (table.board.isEmpty()) stringResource(R.string.a11y_board_empty) else stringResource(R.string.a11y_board, cardsSpoken(table.board))
    Box(modifier = Modifier.height(bandHeight), contentAlignment = Alignment.Center) {
        val gap = TableGeometry.BOARD_CARD_GAP.dp
        Box(modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = spoken }) {
            key(table.handNumber) {
                CardFan(
                    cards = table.board,
                    cardWidth = cardWidth,
                    handWidth = cardWidth * 5 + gap * 4,
                    step = cardWidth + gap,
                    slots = 5,
                    raisedIndices = indicesIn(table.board, table.highlight),
                    raise = RAISE_DP.dp,
                    testTag = "board_cards",
                    modifier = Modifier.liftable(),
                )
            }
        }
        table.banner?.let { banner ->
            val text = when (banner.kind) {
                PotKind.SINGLE -> stringResource(R.string.banner_pot, banner.amount)
                PotKind.MAIN -> stringResource(R.string.banner_main_pot, banner.amount)
                PotKind.SIDE -> stringResource(R.string.banner_side_pot, banner.amount)
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.7f),
                border = BorderStroke(1.5.dp, ring),
                modifier = Modifier.align(Alignment.Center).testTag("pot_banner"),
            ) {
                Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), maxLines = 1)
            }
        }
        val mark = table.mark
        if (mark != null) {
            val (icon, tint, word) = when (mark.kind) {
                MarkKind.WIN -> Triple(Icons.Filled.EmojiEvents, markWin, R.string.mark_win)
                MarkKind.EVEN -> Triple(Icons.Filled.DragHandle, markEven, R.string.mark_even)
                MarkKind.LOSE -> Triple(Icons.Filled.SentimentVeryDissatisfied, markLose, R.string.mark_lose)
            }
            ResultMark(
                icon = icon,
                tint = tint,
                net = mark.net,
                spoken = stringResource(word) + " " + signed(mark.net),
                animate = animate,
                modifier = Modifier.align(Alignment.Center).wrapContentSize(unbounded = true),
            )
        }
    }
}

// ---- seats -------------------------------------------------------------------------------------

private enum class PillSide { Below, End, Start }

/**
 * The seat's small second line (its style word, its last action) shrinks to fit rather than being cut
 * mid-word: a seat is 96 dp wide, a translation or a large system font is longer than "aggressive",
 * and a clipped word reads as a bug. It never goes below 7 sp.
 */
private val FitSubtitle = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = 10.sp, stepSize = 0.5.sp)

@Composable
private fun Avatar(profile: OpponentProfile) {
    val background = Color(profile.colorArgb)
    val figure = Color.Black.copy(alpha = 0.34f)
    androidx.compose.foundation.Canvas(modifier = Modifier.size(24.dp).clip(CircleShape).background(background)) {
        val w = size.width
        // Shoulders below, head above: a neutral silhouette that is drawn, so the app ships no faces.
        drawOval(figure, topLeft = androidx.compose.ui.geometry.Offset(w * 0.12f, w * 0.62f), size = androidx.compose.ui.geometry.Size(w * 0.76f, w * 0.7f))
        drawCircle(figure, radius = w * 0.2f, center = androidx.compose.ui.geometry.Offset(w * 0.5f, w * 0.38f))
    }
}

@Composable
private fun Marker(text: String, color: Color, tag: String) {
    Box(
        modifier = Modifier.size(20.dp).clip(CircleShape).background(color).testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black, maxLines = 1)
    }
}

@Composable
private fun Markers(table: TableModel, seat: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (table.button == seat) Marker(stringResource(R.string.marker_dealer), Color.White, "marker_dealer_$seat")
        if (table.smallBlind == seat && table.seats[seat].inHand) Marker(stringResource(R.string.marker_small_blind), Color(0xFF90CAF9), "marker_sb_$seat")
        if (table.bigBlind == seat && table.seats[seat].inHand) Marker(stringResource(R.string.marker_big_blind), Color(0xFFFFCC80), "marker_bb_$seat")
    }
}

/** A chip and the figure: a seat's bet on this street. */
@Composable
private fun BetPill(amount: Int, tag: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, Color(0xFFFFC83D).copy(alpha = 0.8f)),
        modifier = modifier.wrapContentSize(unbounded = true).clearAndSetSemantics { },
    ) {
        Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ChipStack(amount = amount, chipWidth = 15.dp)
            Text("%,d".format(amount), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, modifier = Modifier.testTag(tag))
        }
    }
}

@Composable
private fun OpponentSeat(
    seat: Int,
    table: TableModel,
    profile: OpponentProfile,
    width: Dp,
    height: Dp,
    cardWidth: Dp,
    compact: Boolean,
    animate: Boolean,
    pill: PillSide,
) {
    val model = table.seats[seat]
    val out = model.place != null
    val acting = table.actor == seat
    val pulse = if (acting && table.thinking && animate) {
        rememberInfiniteTransition(label = "think").animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
            label = "pulse",
        ).value
    } else {
        1f
    }
    val stackText = when {
        out -> stringResource(R.string.seat_out, placeText(model.place!!))
        model.allIn && model.stack == 0 -> stringResource(R.string.seat_all_in)
        else -> "%,d".format(model.stack)
    }
    val spoken = seatSpoken(table, seat, profile, stackText)
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .alpha(if (out) 0.4f else if (model.folded) 0.5f else 1f)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .testTag("seat_$seat"),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(10.dp),
            color = Color.Black.copy(alpha = 0.34f),
            border = if (acting) BorderStroke(2.5.dp, ring.copy(alpha = pulse)) else BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        ) {}
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp).clearAndSetSemantics { }) {
            Row(modifier = Modifier.height(28.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Avatar(profile)
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("seat_${seat}_name"))
                    val last = model.lastAction
                    val won = model.net?.takeIf { it > 0 && !out }
                    if (won != null) {
                        // The hand is over: what this seat won, small, where its style word was.
                        Text(stringResource(R.string.seat_won, signed(won)), fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.ExtraBold, color = markWin, maxLines = 1, autoSize = FitSubtitle, modifier = Modifier.testTag("seat_${seat}_won"))
                    } else if (last != null && !out) {
                        Text(lastActionText(last), fontSize = 10.sp, lineHeight = 11.sp, color = ring, maxLines = 1, autoSize = FitSubtitle, modifier = Modifier.testTag("seat_${seat}_action"))
                    } else {
                        Text(styleText(profile.style), fontSize = 10.sp, lineHeight = 11.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 1, autoSize = FitSubtitle, modifier = Modifier.testTag("seat_${seat}_style"))
                    }
                }
            }
            Text(
                text = stackText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.height(16.dp).testTag("seat_${seat}_stack"),
            )
        }
        // The cards: two backs while in the hand, face up from showdown; taller than the compact row, they cover the stack line.
        if (model.contesting && !out) {
            val cards: List<Card?> = model.cards ?: listOf(null, null)
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                key(table.handNumber) {
                    val shownWidth = if (model.cards != null) maxOf(cardWidth, TableGeometry.MIN_CARD_WIDTH.dp) else cardWidth
                    CardFan(
                        cards = cards,
                        cardWidth = shownWidth,
                        handWidth = width,
                        step = shownWidth * TableGeometry.STEP_FRACTION,
                        raisedIndices = indicesIn(cards, table.highlight),
                        raise = RAISE_DP.dp,
                        testTag = "seat_${seat}_cards",
                        modifier = Modifier.liftable(),
                    )
                }
            }
            val value = model.handValue
            if (value != null && model.cards != null) {
                val name = handName(model.cards + table.board, value)
                // As wide as the seat and no wider, shrunk to fit: a long name ("Two pair, queens and jacks")
                // must not run off the screen's edge from a seat that sits against it.
                Text(
                    text = handNameText(name),
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    autoSize = FitSubtitle,
                    modifier = Modifier.align(Alignment.BottomCenter).offset(y = 22.dp).width(width)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp)
                        .testTag("seat_${seat}_hand"),
                )
            }
        }
        val net = model.net
        if (model.award > 0 && net == null) {
            Text(
                text = stringResource(R.string.seat_award, model.award),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = markWin,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = (-10).dp).wrapContentSize(unbounded = true)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp)
                    .testTag("seat_${seat}_award"),
            )
        }
        Box(modifier = Modifier.align(if (seat <= 3) Alignment.BottomEnd else Alignment.BottomStart).offset(y = 8.dp).wrapContentSize(unbounded = true)) {
            Markers(table, seat)
        }
        if (model.bet > 0) {
            val m = when (pill) {
                PillSide.Below -> Modifier.align(Alignment.BottomCenter).offset(y = 11.dp)
                PillSide.End -> Modifier.align(Alignment.CenterEnd).offset(x = 26.dp)
                PillSide.Start -> Modifier.align(Alignment.CenterStart).offset(x = (-26).dp)
            }
            BetPill(model.bet, "seat_${seat}_bet", m)
        }
    }
}

@Composable
private fun seatSpoken(table: TableModel, seat: Int, profile: OpponentProfile, stackText: String): String {
    val model = table.seats[seat]
    val parts = ArrayList<String>()
    if (model.place != null) parts += stringResource(R.string.a11y_out, placeText(model.place))
    else if (model.folded) parts += stringResource(R.string.a11y_folded)
    parts += stringResource(R.string.a11y_seat, profile.name, styleText(profile.style), model.stack)
    if (model.allIn && model.stack == 0) parts += stringResource(R.string.a11y_all_in)
    if (table.button == seat) parts += stringResource(R.string.a11y_dealer)
    if (table.smallBlind == seat && model.inHand) parts += stringResource(R.string.a11y_small_blind)
    if (table.bigBlind == seat && model.inHand) parts += stringResource(R.string.a11y_big_blind)
    if (model.bet > 0) parts += stringResource(R.string.a11y_bet, model.bet)
    model.lastAction?.let { parts += lastActionText(it) }
    model.net?.let { if (it > 0) parts += stringResource(R.string.seat_won, signed(it)) }
    if (table.actor == seat) parts += stringResource(R.string.a11y_thinking)
    return parts.joinToString(", ")
}

@Composable
private fun YourSeat(table: TableModel, cardWidth: Dp, seatHeight: Dp, animate: Boolean) {
    val model = table.seats[0]
    val cards: List<Card?> = model.cards.orEmpty()
    val step = cardWidth * TableGeometry.STEP_FRACTION
    val cardsSpoken = if (cards.isEmpty()) "" else stringResource(R.string.a11y_your_cards, cardsSpoken(cards.filterNotNull()))
    Box(modifier = Modifier.fillMaxWidth().height(seatHeight).testTag("seat_0"), contentAlignment = Alignment.TopCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .height(TableGeometry.cardHeight(cardWidth.value).let { kotlin.math.ceil(it) }.dp)
                    .alpha(if (model.folded) 0.5f else 1f)
                    .semantics(mergeDescendants = true) { contentDescription = cardsSpoken },
                contentAlignment = Alignment.TopCenter,
            ) {
                key(table.handNumber) {
                    CardFan(
                        cards = cards,
                        cardWidth = cardWidth,
                        handWidth = cardWidth * 2,
                        step = step,
                        raisedIndices = indicesIn(cards, table.highlight),
                        raise = RAISE_DP.dp,
                        testTag = "your_cards",
                        modifier = Modifier.liftable(),
                    )
                }
            }
            val ringed = table.actor == 0
            Row(
                modifier = Modifier.height(TableGeometry.YOUR_LINE.dp).then(if (ringed) Modifier.background(ring.copy(alpha = 0.25f), RoundedCornerShape(10.dp)).padding(horizontal = 6.dp) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val line = if (model.allIn && model.stack == 0) stringResource(R.string.you) + " · " + stringResource(R.string.seat_all_in) else stringResource(R.string.your_line, model.stack)
                Text(line, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, modifier = Modifier.testTag("seat_0_stack"))
                Markers(table, 0)
                // The hand held right now, as the poker apps show it: "Pair of kings" from the flop on, and a
                // pocket pair before it. Once showdown names it, it is the same name.
                val name = if (model.folded) null else model.cards?.let { liveHandName(it, table.board) }
                if (name != null) {
                    Text(handNameText(name), style = MaterialTheme.typography.labelMedium, color = ring, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("seat_0_hand"))
                }
            }
        }
        if (model.bet > 0) {
            BetPill(model.bet, "seat_0_bet", Modifier.align(Alignment.TopStart).offset(x = 16.dp, y = 8.dp))
        }
        model.lastAction?.let {
            Text(lastActionText(it), style = MaterialTheme.typography.labelMedium, color = ring, modifier = Modifier.align(Alignment.TopEnd).offset(x = (-16).dp, y = 8.dp).testTag("seat_0_action"))
        }
        val net = model.net
        if (net != null && net != 0) {
            // The hand is over and chips moved: what the player won, or in red what they lost, beside their cards.
            // A seat that folded and lost its blind or its bets says so too: the chips are gone either way.
            val won = net > 0
            Text(
                text = stringResource(if (won) R.string.seat_won else R.string.seat_lost, signed(net)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (won) markWin else markLose,
                maxLines = 1,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = (-12).dp, y = 30.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
                    .testTag(if (won) "seat_0_won" else "seat_0_lost"),
            )
        } else if (model.award > 0 && net == null) {
            Text(
                text = stringResource(R.string.seat_award, model.award),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = markWin,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = (-16).dp, y = 30.dp).testTag("seat_0_award"),
            )
        }
    }
}
