package org.finiteplay.freecell.ui.game

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import org.finiteplay.core.storage.AppLocale
import org.finiteplay.freecell.R
import org.finiteplay.core.ui.layout.DealPickerDialog
import org.finiteplay.core.ui.layout.AppLanguages
import org.finiteplay.core.ui.layout.BoardOrientation
import org.finiteplay.core.ui.layout.DiscardGameDialog
import org.finiteplay.core.ui.layout.RestReminderDialog
import org.finiteplay.core.ui.layout.RestBreakDialog
import org.finiteplay.core.ui.layout.Handedness
import org.finiteplay.core.ui.layout.HintProgressDialog
import org.finiteplay.core.ui.sound.AndroidSoundPlayer
import org.finiteplay.core.ui.sound.GatedSoundPlayer
import org.finiteplay.core.ui.sound.SoundEffect
import org.finiteplay.core.ui.table.feltTable
import org.finiteplay.core.ui.theme.LocalAppColors
import org.finiteplay.freecell.layout.GameStatus
import org.finiteplay.freecell.rules.isStuck

/**
 * Status row, board, and the game's actions (Settings, Replay, New Game, Hint, Undo), plus the
 * Settings, Statistics, and Help panels and the Hint notices
 * (`docs/games/freecell/EXECUTION_PLAN.md` F3b/F6).
 */
@Composable
fun GameScreen(viewModel: FreeCellViewModel, modifier: Modifier = Modifier) {
    val catalogFailure = viewModel.unrecoverableCatalogReason
    if (catalogFailure != null) {
        UnrecoverableScreen(reason = catalogFailure, onRetry = viewModel::retryCatalogLoad)
        return
    }

    val session = viewModel.session
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showStatistics by remember { mutableStateOf(false) }
    var dealPickerVisible by remember { mutableStateOf(false) }

    // A real SoundPool-backed player, gated so backgrounding or the Settings toggle silence it
    // independent of anything else — the same shared core:ui player Klondike and Spider use
    // (`docs/PLATFORM.md` "Sound").
    val androidSoundPlayer = remember { AndroidSoundPlayer(context) }
    DisposableEffect(androidSoundPlayer) { onDispose { androidSoundPlayer.release() } }
    val soundPlayer = remember(androidSoundPlayer) {
        GatedSoundPlayer(androidSoundPlayer, isEnabled = { viewModel.settings.soundEnabled }, isForeground = { viewModel.isForeground })
    }

    val mirrored = viewModel.settings.handedness == Handedness.LEFT
    val isWon = session.state.status == GameStatus.WON
    val requestHintWithSound = {
        soundPlayer.play(SoundEffect.HINT)
        viewModel.requestHint()
    }
    val requestUndoWithSound = {
        soundPlayer.play(SoundEffect.UNDO)
        viewModel.undo()
    }
    val requestNewWithSound = {
        if (!session.hasPlayerActed || isWon) soundPlayer.play(SoundEffect.SHUFFLE)
        viewModel.requestNewGame()
    }
    val requestReplayWithSound = {
        if (!session.hasPlayerActed || isWon) soundPlayer.play(SoundEffect.SHUFFLE)
        viewModel.requestReplay()
    }
    // The dialog waits for FreeCellBoard to finish animating every card into place — including an
    // automatic-finish sweep — before it appears, rather than popping up the instant the state
    // itself becomes won (mirrors Klondike's own `boardAnimating`).
    var boardAnimating by remember { mutableStateOf(false) }
    val showWinDialog = isWon && !viewModel.isAutoFinishing && !boardAnimating
    val stuck = remember(session.state) { !isWon && isStuck(session.state) }
    var stuckNoticeDismissed by remember(session.state) { mutableStateOf(false) }
    val showStuckNotice = stuck && !stuckNoticeDismissed &&
        viewModel.pendingAction == null && !showSettings && !showHelp && !showStatistics

    val anyModalOpen = viewModel.pendingAction != null || showSettings || showHelp || showStatistics || showStuckNotice ||
        viewModel.showRestReminderDialog || viewModel.restBreakRemainingSeconds != null
    LaunchedEffect(anyModalOpen) { viewModel.setModalOpen(anyModalOpen) }

    // Back closes an open modal first; with none open there is nowhere else in this
    // single-screen app to go, so default Back (exit) is left alone.
    BackHandler(enabled = viewModel.pendingAction != null || showSettings || showHelp || showStatistics || showStuckNotice) {
        when {
            viewModel.pendingAction != null -> viewModel.dismissPendingAction()
            showSettings -> showSettings = false
            showHelp -> showHelp = false
            showStatistics -> showStatistics = false
            else -> stuckNoticeDismissed = true
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = modifier
            .testTag("app_root")
            .feltTable(
                base = MaterialTheme.colorScheme.background,
                thread = LocalAppColors.current.card.emptySlot,
            ),
    ) { padding ->
        if (viewModel.isLoading) {
            // Nothing interactive until the persisted game has been checked: a fresh deal shown
            // here would be one the player could touch and then have replaced under them.
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                StatusRow(
                    dealNumber = viewModel.dealNumber,
                    moves = session.state.moveCount,
                    elapsedSeconds = viewModel.elapsedSeconds,
                    won = session.state.status == GameStatus.WON,
                    onSelectDeal = { dealPickerVisible = true },
                    onHelp = { showHelp = true },
                    onStatistics = { showStatistics = true },
                )
                if (viewModel.recoveryNoticeVisible) {
                    RecoveryNotice(onDismiss = viewModel::dismissRecoveryNotice)
                }
                key(viewModel.gameId) {
                    FreeCellBoard(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize().weight(1f),
                        onAnimationsInFlightChanged = { boardAnimating = it },
                        onPlaySound = soundPlayer::play,
                    )
                }
                ActionBar(
                    orientation = BoardOrientation.PORTRAIT,
                    canUndo = session.canUndo,
                    mirrored = mirrored,
                    onUndo = requestUndoWithSound,
                    onReplay = requestReplayWithSound,
                    onNewGame = requestNewWithSound,
                    onHint = requestHintWithSound,
                    onSettings = { showSettings = true },
                )
            }
            val hintState = viewModel.hintState
            // Loading has no banner of its own: below HINT_PROGRESS_DIALOG_DELAY it resolves
            // silently (most hints do), and past it HintProgressDialog below takes over instead.
            if (hintState != HintUiState.Hidden && hintState != HintUiState.Loading && hintState !is HintUiState.Guided) {
                // A floating overlay, not part of the Column's layout flow: it must never push
                // the board, which a fast, cache-free search can appear and disappear against
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
            period = viewModel.statisticsPeriod,
            onPeriodChange = viewModel::showStatisticsFor,
            onReset = viewModel::resetStatistics,
            onClose = { showStatistics = false },
        )
    }

    LaunchedEffect(showWinDialog) { if (showWinDialog) soundPlayer.play(SoundEffect.WIN) }
    if (showWinDialog) {
        val bests = viewModel.allTimeStatistics
        WinDialog(
            moveCount = session.state.moveCount,
            elapsedSeconds = viewModel.elapsedSeconds,
            skipAnimations = !viewModel.settings.animationsEnabled,
            bestElapsedMillis = bests.session.bestElapsedMillis,
            bestMoveCount = bests.session.bestMoveCount,
            solutionMoveCount = viewModel.solutionMoveCount,
            onNewGame = requestNewWithSound,
            onReplay = requestReplayWithSound,
        )
    }

    if (showStuckNotice) {
        StuckNotice(
            canUndo = session.canUndo,
            onUndo = { requestUndoWithSound(); stuckNoticeDismissed = true },
            onNewGame = { requestNewWithSound(); stuckNoticeDismissed = true },
            onDismiss = { stuckNoticeDismissed = true },
        )
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

    val pickerCount = viewModel.dealPickerCount
    if (dealPickerVisible && pickerCount != null) {
        // Built once per opening: it walks the whole catalog, and nothing in it changes while the
        // picker is on screen.
        val progress = remember(viewModel.dealProgress) { viewModel.dealPickerProgress() }
        DealPickerDialog(
            dealCount = pickerCount,
            currentNumber = viewModel.dealNumber,
            progress = progress,
            onSelect = {
                dealPickerVisible = false
                if (!session.hasPlayerActed || isWon) soundPlayer.play(SoundEffect.SHUFFLE)
                viewModel.requestSelectDeal(it)
            },
            onDismiss = { dealPickerVisible = false },
        )
    }

    if (showSettings) {
        val settings = viewModel.settings
        SettingsScreen(
            automaticMovesEnabled = settings.automaticMovesEnabled,
            animationsEnabled = settings.animationsEnabled,
            hintShowsWinningMove = settings.hintShowsWinningMove,
            hintTimeout = settings.hintTimeout,
            restReminderInterval = settings.restReminderInterval,
            restReminderElapsedSeconds = viewModel.restReminderElapsedSeconds,
            handedness = settings.handedness,
            soundEnabled = settings.soundEnabled,
            languageTag = settings.languageTag,
            themeMode = settings.themeMode,
            availableLanguages = AppLanguages.OPTIONS,
            onAutomaticMovesEnabledChange = viewModel::setAutomaticMovesEnabled,
            onAnimationsEnabledChange = viewModel::setAnimationsEnabled,
            onHintShowsWinningMoveChange = viewModel::setHintShowsWinningMove,
            onHintTimeoutChange = viewModel::setHintTimeout,
            onRestReminderIntervalChange = viewModel::setRestReminderInterval,
            onHandednessChange = viewModel::setHandedness,
            onSoundEnabledChange = viewModel::setSoundEnabled,
            onLanguageChange = { tag ->
                viewModel.setLanguageTag(tag)
                // The locale is read in attachBaseContext, so it only takes hold on a fresh
                // Activity; recreating is what makes the change visible now rather than at next
                // launch (`storage/AppLocale.kt`).
                AppLocale.setTag(context, tag)
                (context as? Activity)?.recreate()
            },
            onThemeModeChange = viewModel::setThemeMode,
            onClose = { showSettings = false },
        )
    }
}
