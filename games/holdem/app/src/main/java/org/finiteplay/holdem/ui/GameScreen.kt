package org.finiteplay.holdem.ui

import android.content.res.Configuration
import org.finiteplay.core.storage.AppLocale
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.CircularProgressIndicator
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.finiteplay.core.session.formatElapsed
import org.finiteplay.core.ui.R as CoreR
import org.finiteplay.core.ui.card.LocalAnimateCards
import org.finiteplay.core.ui.layout.BoardOrientation
import org.finiteplay.core.ui.layout.CelebrationOverlay
import org.finiteplay.core.ui.layout.Handedness
import org.finiteplay.core.ui.layout.RestBreakDialog
import org.finiteplay.core.ui.layout.RestReminderDialog
import org.finiteplay.core.ui.layout.landscapeRailWidth
import org.finiteplay.core.ui.sound.AndroidSoundPlayer
import org.finiteplay.core.ui.sound.GatedSoundPlayer
import org.finiteplay.core.ui.table.feltTable
import org.finiteplay.core.ui.theme.LocalAppColors
import org.finiteplay.holdem.R
import kotlinx.coroutines.delay

/**
 * The table, the bar and the dialogs. Everything shown is derived from [HoldemViewModel.table], which
 * is only ever at or behind the session. Help, Statistics and Settings are the callers' (their screens
 * are other packages'); until they are wired the buttons do nothing visible.
 */
@Composable
fun GameScreen(
    viewModel: HoldemViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showStatistics by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val onHelp = { showHelp = true }
    val onStatistics = { showStatistics = true }
    val onSettings = { showSettings = true }
    val androidSoundPlayer = remember { AndroidSoundPlayer(context) }
    DisposableEffect(androidSoundPlayer) { onDispose { androidSoundPlayer.release() } }
    val soundPlayer = remember(androidSoundPlayer) {
        GatedSoundPlayer(androidSoundPlayer, isEnabled = { viewModel.settings.soundEnabled }, isForeground = { viewModel.isForeground })
    }
    DisposableEffect(soundPlayer, viewModel) {
        viewModel.soundSink = soundPlayer::play
        onDispose { viewModel.soundSink = {} }
    }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val mirrored = viewModel.settings.handedness == Handedness.LEFT
    val animate = viewModel.settings.animationsEnabled
    val table = viewModel.table
    val sizing = viewModel.sizing?.takeIf { table.stage == Stage.YOUR_TURN }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = modifier
            .testTag("app_root")
            .feltTable(base = MaterialTheme.colorScheme.background, thread = LocalAppColors.current.card.emptySlot),
    ) { padding ->
        if (viewModel.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding).testTag("loading"), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val bar = BarState(table.stage, viewModel.legal, sizing, viewModel.busy, viewModel.hintShown?.suggested)
        val actionBar: @Composable (BoardOrientation) -> Unit = { orientation ->
            ActionBar(
                orientation = orientation,
                mirrored = mirrored,
                bar = bar,
                onAction = viewModel::act,
                onNextHand = viewModel::nextHand,
                onNewTournament = viewModel::newTournament,
                onSettings = onSettings,
                onHint = viewModel::requestHint,
                landscape = orientation == BoardOrientation.LANDSCAPE,
            )
        }
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                StatusRow(
                    table = table,
                    onHelp = onHelp,
                    onStatistics = onStatistics,
                    onLeave = viewModel::requestLeave,
                    onSettings = if (landscape) onSettings else null,
                    bottomGap = if (landscape) 0.dp else TableGeometry.STATUS_GAP.dp,
                    onHint = if (landscape && table.stage == Stage.YOUR_TURN && viewModel.legal != null && !viewModel.busy) viewModel::requestHint else null,
                )
                if (viewModel.recoveryNoticeVisible) RecoveryNotice(onDismiss = viewModel::dismissRecoveryNotice)
                CompositionLocalProvider(LocalAnimateCards provides animate) {
                    if (landscape) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            if (mirrored) actionBar(BoardOrientation.LANDSCAPE)
                            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxSize()) {
                                TableView(table, viewModel.profiles, landscape = true, animate = animate)
                                // Beside your cards, in the empty corner of the felt, so the hint hides neither them nor a seat.
                                viewModel.hintShown?.let {
                                    HintNotice(it, Modifier.align(Alignment.BottomEnd).widthIn(max = maxWidth * 0.42f).padding(bottom = 4.dp))
                                }
                            }
                            Box(modifier = Modifier.width(TableGeometry.SIZING_COLUMN_WIDTH.dp).fillMaxSize()) {
                                if (sizing != null) {
                                    SizingControls(sizing, horizontal = false, onStep = viewModel::stepSizing, onPreset = viewModel::setSizingPreset, onAmount = viewModel::setSizingAmount)
                                }
                            }
                            if (!mirrored) actionBar(BoardOrientation.LANDSCAPE)
                        }
                    } else {
                        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            TableView(table, viewModel.profiles, landscape = false, animate = animate)
                            // The hint sits in the empty band between the two rows of seats, so it hides neither a card nor a seat.
                            viewModel.hintShown?.let { HintNotice(it, Modifier.align(Alignment.TopCenter).padding(top = maxHeight * 0.165f)) }
                        }
                        Spacer(modifier = Modifier.height(TableGeometry.CONTROLS_GAP.dp))
                        // Always reserved, so the table does not jump when the row appears.
                        Box(modifier = Modifier.fillMaxWidth().height(TableGeometry.SIZING_ROW.dp)) {
                            if (sizing != null) {
                                SizingControls(sizing, horizontal = true, onStep = viewModel::stepSizing, onPreset = viewModel::setSizingPreset, onAmount = viewModel::setSizingAmount)
                            }
                        }
                        actionBar(BoardOrientation.PORTRAIT)
                    }
                }
            }
            viewModel.blindsUp?.let { notice ->
                LaunchedEffect(notice.id) {
                    delay(BLINDS_NOTICE_MS)
                    viewModel.dismissBlindsUp(notice.id)
                }
                // Landscape: the free middle of the status row. Portrait: the empty band between the two rows of seats,
                // about a sixth of the way down the table, so it covers no seat and no card.
                if (landscape) {
                    BlindsUpNotice(notice, Modifier.align(Alignment.TopCenter).padding(top = 4.dp))
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(top = TableGeometry.STATUS_ROW.dp + TableGeometry.STATUS_GAP.dp)) {
                        BlindsUpNotice(notice, Modifier.align(Alignment.TopCenter).padding(top = (maxHeight * 0.16f)))
                    }
                }
            }
            AnnouncementRegion(viewModel)
        }
    }

    if (viewModel.leaveConfirmVisible) LeaveDialog(viewModel)
    viewModel.endDialog?.let { EndDialog(it, viewModel, onStatistics) }

    if (viewModel.showRestReminderDialog) {
        RestReminderDialog(
            playedSeconds = (viewModel.settings.restReminderInterval.minutes ?: 0) * 60,
            onTakeBreak = viewModel::startRestBreak,
            onKeepPlaying = viewModel::dismissRestReminder,
        )
    }
    viewModel.restBreakRemainingSeconds?.let { remaining ->
        RestBreakDialog(remainingSeconds = remaining, onCancel = viewModel::cancelRestBreak)
    }

    BackHandler(enabled = showHelp || showStatistics || showSettings) {
        when {
            showSettings -> showSettings = false
            showHelp -> showHelp = false
            else -> showStatistics = false
        }
    }
    if (showHelp) HelpScreen(onClose = { showHelp = false })
    if (showStatistics) {
        StatisticsScreen(statistics = viewModel.statistics, onReset = viewModel::resetStatistics, onClose = { showStatistics = false })
    }
    if (showSettings) {
        SettingsScreen(
            settings = viewModel.settings.toStore(),
            restReminderElapsedSeconds = viewModel.restReminderElapsedSeconds,
            onHandednessChange = viewModel::setHandedness,
            onAnimationsChange = viewModel::setAnimationsEnabled,
            onSoundChange = viewModel::setSoundEnabled,
            onThemeChange = viewModel::setThemeMode,
            onLanguageChange = { tag ->
                viewModel.setLanguageTag(tag)
                // Read in attachBaseContext, so it only takes hold on a fresh Activity.
                AppLocale.setTag(context, tag)
                (context as? Activity)?.recreate()
            },
            onRestReminderChange = viewModel::setRestReminderInterval,
            onClose = { showSettings = false },
        )
    }
}

/** Every action, street and pot said aloud as it happens, through a polite live region (`UI_SPEC.md` "Accessibility"). */
/** How long the blinds-up pill stays: long enough to read, short enough to need no tap. */
private const val BLINDS_NOTICE_MS = 3_000L

@Composable
private fun AnnouncementRegion(viewModel: HoldemViewModel) {
    val announced = viewModel.announcement ?: return
    val text = announcementText(announced.what) { seat -> if (seat == 0) stringResource(R.string.you) else viewModel.profiles.getOrNull(seat - 1)?.name ?: "" }
    Box(
        modifier = Modifier
            .size(1.dp)
            .semantics {
                contentDescription = text
                liveRegion = LiveRegionMode.Polite
            }
            .testTag("announcement"),
    )
}

@Composable
private fun LeaveDialog(viewModel: HoldemViewModel) {
    val place = viewModel.session.state.places.count { it == null }
    AlertDialog(
        modifier = Modifier.testTag("leave_dialog"),
        onDismissRequest = viewModel::dismissLeave,
        title = { Text(stringResource(R.string.leave_title)) },
        text = { Text(stringResource(R.string.leave_body, placeText(place))) },
        confirmButton = { TextButton(onClick = viewModel::confirmLeave, modifier = Modifier.testTag("leave_confirm")) { Text(stringResource(R.string.leave_confirm)) } },
        dismissButton = { TextButton(onClick = viewModel::dismissLeave, modifier = Modifier.testTag("leave_stay")) { Text(stringResource(R.string.leave_stay)) } },
    )
}

@Composable
private fun EndDialog(end: TournamentEnd, viewModel: HoldemViewModel, onStatistics: () -> Unit) {
    val won = end.place == 1
    if (won) {
        CelebrationOverlay(seed = viewModel.session.state.tournament.seed, confetti = viewModel.settings.animationsEnabled) {
            Text(stringResource(R.string.end_title_won), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
    }
    AlertDialog(
        modifier = Modifier.testTag("end_dialog"),
        onDismissRequest = viewModel::dismissEndDialog,
        title = {
            Text(
                if (won) stringResource(R.string.end_title_won) else stringResource(R.string.end_title_place, placeText(end.place)),
                modifier = Modifier.testTag("end_title"),
            )
        },
        text = {
            Text(
                if (won) stringResource(R.string.end_body_won, end.hands, end.largestPot) else stringResource(R.string.end_body_lost, end.hands, end.largestPot),
                modifier = Modifier.testTag("end_body"),
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onStatistics, modifier = Modifier.testTag("end_statistics")) { Text(stringResource(CoreR.string.statistics_title)) }
                TextButton(onClick = viewModel::newTournament, modifier = Modifier.testTag("end_new_tournament")) { Text(stringResource(R.string.action_new_tournament)) }
            }
        },
        dismissButton = { TextButton(onClick = viewModel::dismissEndDialog, modifier = Modifier.testTag("end_close")) { Text(stringResource(CoreR.string.action_close)) } },
    )
}
