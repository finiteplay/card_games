package org.finiteplay.spider.ui.game

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import org.finiteplay.core.storage.AppLocale
import org.finiteplay.core.ui.R as CoreR
import org.finiteplay.core.ui.layout.AppLanguages
import org.finiteplay.core.ui.layout.BoardAction
import org.finiteplay.core.ui.layout.BoardActionBar
import org.finiteplay.core.ui.layout.BoardOrientation
import org.finiteplay.core.ui.layout.DiscardGameDialog
import org.finiteplay.core.ui.layout.DealPickerDialog
import org.finiteplay.core.ui.layout.SolutionComparison
import org.finiteplay.core.ui.layout.WinDialog
import org.finiteplay.core.ui.layout.Handedness
import org.finiteplay.core.ui.layout.HintProgressDialog
import org.finiteplay.core.ui.layout.LevelUpDialog
import org.finiteplay.core.ui.layout.RestReminderDialog
import org.finiteplay.core.ui.layout.RestBreakDialog
import androidx.compose.ui.graphics.Color
import org.finiteplay.core.ui.sound.AndroidSoundPlayer
import org.finiteplay.core.ui.sound.GatedSoundPlayer
import org.finiteplay.core.ui.sound.SoundEffect
import org.finiteplay.core.ui.table.feltTable
import org.finiteplay.core.ui.theme.LocalAppColors
import org.finiteplay.spider.R
import org.finiteplay.spider.layout.GameStatus

/**
 * Status row, board, and the two actions this vertical slice has (Undo, New Game). Settings,
 * Statistics, and Replay arrive with S3c (`docs/games/spider/EXECUTION_PLAN.md`).
 */
@Composable
fun GameScreen(viewModel: SpiderViewModel, modifier: Modifier = Modifier) {
    val session = viewModel.session
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showSettings by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showStatistics by remember { mutableStateOf(false) }
    var suitCountPickerVisible by remember { mutableStateOf(false) }
    var dealPickerVisible by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.dealRowRefused) {
        if (viewModel.dealRowRefused) {
            snackbarHostState.showSnackbar(message = "")
            viewModel.dismissDealRowRefused()
        }
    }

    // A real SoundPool-backed player, gated so backgrounding or the Settings toggle silence it
    // independent of anything else — the same shared core:ui player Klondike uses
    // (`docs/PLATFORM.md` "Sound").
    val androidSoundPlayer = remember { AndroidSoundPlayer(context) }
    DisposableEffect(androidSoundPlayer) { onDispose { androidSoundPlayer.release() } }
    val soundPlayer = remember(androidSoundPlayer) {
        GatedSoundPlayer(androidSoundPlayer, isEnabled = { viewModel.settings.soundEnabled }, isForeground = { viewModel.isForeground })
    }
    val requestHintWithSound = {
        soundPlayer.play(SoundEffect.HINT)
        viewModel.showHint()
    }
    val requestUndoWithSound = {
        soundPlayer.play(SoundEffect.UNDO)
        viewModel.undo()
    }
    val requestNewWithSound = {
        if (session.state.moveCount == 0 || session.state.status == GameStatus.WON) soundPlayer.play(SoundEffect.SHUFFLE)
        viewModel.requestNewGame()
    }
    val requestReplayWithSound = {
        if (session.state.moveCount == 0 || session.state.status == GameStatus.WON) soundPlayer.play(SoundEffect.SHUFFLE)
        viewModel.requestRestart()
    }

    // Brief flash plus sound for a tap or drag that found no legal destination — Klondike's own
    // `markInvalid`, adapted here since the resolution itself happens in the ViewModel rather
    // than the board composable.
    LaunchedEffect(viewModel.invalidFeedbackColumn) {
        if (viewModel.invalidFeedbackColumn != null) {
            soundPlayer.play(SoundEffect.INVALID)
            delay(220)
            viewModel.dismissInvalidFeedback()
        }
    }
    // Fires once per committed move. Automatic-finish steps use the quieter automatic cue; the
    // three fields below are
    // reset together on every reassignment of `session` that is not a real move (undo, restore,
    // restart, a fresh deal), so their being set is itself the signal a move actually happened.
    LaunchedEffect(session.state) {
        val moved = viewModel.lastMovedSequence != null || viewModel.lastBankedRuns.isNotEmpty() || viewModel.lastDealtRow != null
        if (moved) {
            val effect = when {
                viewModel.lastMoveWasAutomatic -> SoundEffect.AUTOMATIC_MOVE
                viewModel.lastDealtRow != null -> SoundEffect.DEAL
                viewModel.lastBankedRuns.isNotEmpty() -> SoundEffect.SEQUENCE_COMPLETE
                else -> SoundEffect.MOVE
            }
            soundPlayer.play(effect)
        }
    }

    Scaffold(
        // Transparent, so the cloth below is the only background the screen has.
        containerColor = Color.Transparent,
        modifier = modifier
            .testTag("app_root")
            .feltTable(
                base = MaterialTheme.colorScheme.background,
                thread = LocalAppColors.current.card.emptySlot,
            ),
        snackbarHost = {
            SnackbarHost(snackbarHostState) {
                Snackbar { Text(stringResource(R.string.deal_row_refused)) }
            }
        },
    ) { padding ->
        if (viewModel.isLoading) {
            // Nothing interactive until the persisted game has been checked: a fresh deal shown
            // here would be one the player could touch and then have replaced under them.
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val mirrored = viewModel.settings.handedness == Handedness.LEFT

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                StatusRow(
                    suitCount = session.state.suitCount,
                    dealNumber = viewModel.dealNumber,
                    moves = session.state.moveCount,
                    elapsedSeconds = viewModel.elapsedSeconds,
                    won = session.state.status == GameStatus.WON,
                    // Landscape has no spare row for the banked counters, and the board needs its
                    // full height, so they join the one line that is already there.
                    banked = if (landscape) session.state.banked else null,
                    suits = session.state.suitCount.suits,
                    colors = LocalAppColors.current.card,
                    onSelectSuitCount = { suitCountPickerVisible = true },
                    onSelectDeal = { dealPickerVisible = true },
                    singleLine = landscape,
                    onHelp = if (landscape) null else ({ showHelp = true }),
                    onStatistics = if (landscape) null else ({ showStatistics = true }),
                )
                Notices(viewModel = viewModel, showUncertified = !landscape && !viewModel.dealIsCertified)

                if (landscape) {
                    Row(modifier = Modifier.fillMaxSize().weight(1f)) {
                        ActionRail(
                            group = RailGroup.SECONDARY,
                            viewModel = viewModel,
                            onSettings = { showSettings = true },
                            onHelp = { showHelp = true },
                            onStatistics = { showStatistics = true },
                            onHint = requestHintWithSound,
                            onUndo = requestUndoWithSound,
                            onReplay = requestReplayWithSound,
                            onNewGame = requestNewWithSound,
                        )
                        SpiderBoard(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxHeight().weight(1f),
                            stockAtSide = true,
                        )
                        ActionRail(
                            group = RailGroup.PRIMARY,
                            viewModel = viewModel,
                            onSettings = { showSettings = true },
                            onHelp = { showHelp = true },
                            onStatistics = { showStatistics = true },
                            onHint = requestHintWithSound,
                            onUndo = requestUndoWithSound,
                            onReplay = requestReplayWithSound,
                            onNewGame = requestNewWithSound,
                        )
                    }
                } else {
                    SpiderBoard(viewModel = viewModel, modifier = Modifier.fillMaxSize().weight(1f))
                    ActionBar(
                        canUndo = session.canUndo,
                        mirrored = mirrored,
                        onUndo = requestUndoWithSound,
                        onHint = requestHintWithSound,
                        onRestart = requestReplayWithSound,
                        onNewGame = requestNewWithSound,
                        onSettings = { showSettings = true },
                    )
                }
            }
            val hintState = viewModel.hintState
            // Loading has no banner of its own: below HINT_PROGRESS_DIALOG_DELAY it resolves
            // silently (most hints do), and past it HintProgressDialog below takes over instead.
            if (hintState != HintUiState.Hidden && hintState != HintUiState.Loading && hintState !is HintUiState.Guided) {
                // A floating overlay, not part of the Column's layout flow: it must never push
                // the board, which a fast, cache-free result can appear and disappear against
                // within a single frame.
                HintNoticeRow(
                    hintState = hintState,
                    onDismiss = viewModel::dismissHint,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }

    if (viewModel.hintShowsProgressDialog) {
        HintProgressDialog(message = stringResource(R.string.hint_thinking), onCancel = viewModel::dismissHint)
    }

    if (showHelp) {
        HelpScreen(onClose = { showHelp = false })
    }

    if (showStatistics) {
        StatisticsScreen(
            statistics = viewModel.statistics,
            suitCount = viewModel.statisticsSuitCount,
            period = viewModel.statisticsPeriod,
            onSuitCountChange = viewModel::showStatisticsFor,
            onPeriodChange = viewModel::showStatisticsFor,
            onReset = viewModel::resetStatistics,
            onClose = { showStatistics = false },
        )
    }

    // Held back for the whole automatic finish, not just shown the instant the board's own state
    // flips to WON — that happens the moment the winning move commits, well before its flight (or,
    // for a multi-move finish, the moves ahead of it) has had a chance to actually play out
    // (`SpiderViewModel.isAutoFinishing`, `docs/games/spider/UI_SPEC.md` "Win Presentation").
    val showWinDialog = session.state.status == GameStatus.WON && !viewModel.isAutoFinishing
    LaunchedEffect(showWinDialog) { if (showWinDialog) soundPlayer.play(SoundEffect.WIN) }
    if (showWinDialog) {
        WinDialog(
            moveCount = session.state.moveCount,
            elapsedSeconds = viewModel.elapsedSeconds,
            skipAnimations = !viewModel.settings.animationsEnabled,
            onNewGame = requestNewWithSound,
            onReplay = requestReplayWithSound,
        ) {
            SolutionComparison(session.state.moveCount, viewModel.solutionMoveCount)
        }
    }

    viewModel.pendingAction?.let { pending ->
        DiscardGameDialog(
            action = pending,
            moveCount = session.state.moveCount,
            elapsedSeconds = viewModel.elapsedSeconds,
            onConfirm = {
                soundPlayer.play(SoundEffect.SHUFFLE)
                viewModel.confirmPendingAction()
            },
            onDismiss = viewModel::dismissPendingAction,
        )
    }

    viewModel.levelUpOffer?.let { offer ->
        LevelUpDialog(
            wins = offer.wins,
            currentLevel = stringResource(offer.from.labelRes()),
            nextLevel = stringResource(offer.to.labelRes()),
            onSwitch = viewModel::acceptLevelUp,
            onStay = viewModel::declineLevelUp,
        )
    }

    if (viewModel.showRestReminderDialog) {
        RestReminderDialog(
            playedSeconds = (viewModel.settings.restReminderInterval.minutes ?: 0) * 60,
            onTakeBreak = viewModel::startRestBreak,
            onKeepPlaying = viewModel::dismissRestReminder,
        )
    }

    viewModel.restBreakRemainingSeconds?.let { remaining ->
        RestBreakDialog(
            remainingSeconds = remaining,
            onCancel = viewModel::cancelRestBreak,
        )
    }

    if (suitCountPickerVisible) {
        SuitCountPickerDialog(
            selected = session.state.suitCount,
            // Null on a raw board: nothing has been played, so the switch forfeits nothing
            // and the dialog should not claim otherwise.
            forfeitsMoveCount = session.state.moveCount.takeIf { viewModel.suitCountSwitchWouldForfeit },
            onSelect = {
                suitCountPickerVisible = false
                if (session.state.moveCount == 0 || session.state.status == GameStatus.WON) soundPlayer.play(SoundEffect.SHUFFLE)
                viewModel.setSuitCount(it)
            },
            onDismiss = { suitCountPickerVisible = false },
        )
    }

    if (dealPickerVisible) {
        // Built once per opening: it walks the whole list, and nothing in it changes while the
        // picker is on screen.
        val progress = remember(viewModel.dealProgress, session.state.suitCount) { viewModel.dealPickerProgress() }
        DealPickerDialog(
            dealCount = viewModel.dealPickerCount,
            currentNumber = viewModel.dealNumber,
            progress = progress,
            onSelect = {
                dealPickerVisible = false
                if (session.state.moveCount == 0 || session.state.status == GameStatus.WON) soundPlayer.play(SoundEffect.SHUFFLE)
                viewModel.requestSelectDeal(it)
            },
            onDismiss = { dealPickerVisible = false },
        )
    }

    if (showSettings) {
        val settings = viewModel.settings
        SettingsScreen(
            animationsEnabled = settings.animationsEnabled,
            hintShowsWinningMove = settings.hintShowsWinningMove,
            hintTimeout = settings.hintTimeout,
            restReminderInterval = settings.restReminderInterval,
            restReminderElapsedSeconds = viewModel.restReminderElapsedSeconds,
            handedness = settings.handedness,
            soundEnabled = settings.soundEnabled,
            languageTag = settings.languageTag,
            themeMode = settings.themeMode,
            nextSuitCount = settings.nextSuitCount,
            availableLanguages = AppLanguages.OPTIONS,
            onAnimationsEnabledChange = viewModel::setAnimationsEnabled,
            onHintShowsWinningMoveChange = viewModel::setHintShowsWinningMove,
            onHintTimeoutChange = viewModel::setHintTimeout,
            onRestReminderIntervalChange = viewModel::setRestReminderInterval,
            onHandednessChange = viewModel::setHandedness,
            onSoundEnabledChange = viewModel::setSoundEnabled,
            onLanguageChange = { tag ->
                viewModel.setLanguageTag(tag)
                // The locale is read in attachBaseContext, so it only takes hold on a
                // fresh Activity; recreating is what makes the change visible now
                // rather than at next launch (`storage/AppLocale.kt`).
                AppLocale.setTag(context, tag)
                (context as? Activity)?.recreate()
            },
            onThemeModeChange = viewModel::setThemeMode,
            onNextSuitCountChange = viewModel::requestSuitCount,
            onClose = { showSettings = false },
        )
    }
}

/**
 * The Loading / No solution / Inconclusive states of the solver-backed hint mode
 * (`docs/games/spider/UI_SPEC.md` "Hint") — [HintUiState.Guided] renders nothing here at all,
 * since it has nothing to say beyond the pulse on the board itself, the same as the plain
 * highlight-only hint. Mirrors Klondike's and FreeCell's own `HintNoticeRow`.
 */
@Composable
internal fun HintNoticeRow(hintState: HintUiState, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val text = when (hintState) {
        HintUiState.Loading -> stringResource(R.string.hint_thinking)
        HintUiState.NoSolution -> stringResource(R.string.hint_no_solution)
        HintUiState.Inconclusive -> stringResource(R.string.hint_inconclusive)
        else -> return
    }
    Surface(
        modifier = modifier.padding(24.dp).testTag("hint_notice"),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false).semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (hintState != HintUiState.Loading) {
                TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.action_dismiss)) }
            }
        }
    }
}
