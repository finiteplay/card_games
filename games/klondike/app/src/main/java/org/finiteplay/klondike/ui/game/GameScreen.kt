package org.finiteplay.klondike.ui.game

import org.finiteplay.core.session.formatElapsed as sharedFormatElapsed
import org.finiteplay.core.ui.R as CoreR
import android.app.Activity
import android.content.res.Configuration
import org.finiteplay.core.ui.layout.BoardOrientation
import org.finiteplay.core.ui.layout.landscapeRailWidth
import org.finiteplay.core.ui.layout.FinitePlayLogo
import org.finiteplay.core.ui.layout.boardStatusColor
import org.finiteplay.core.ui.layout.boardStatusStyle
import org.finiteplay.core.ui.layout.boardTitleColor
import org.finiteplay.core.ui.layout.boardTitleStyle
import org.finiteplay.core.ui.layout.chromeScale
import org.finiteplay.core.ui.layout.HintProgressDialog
import org.finiteplay.core.ui.layout.RestReminderDialog
import org.finiteplay.core.ui.layout.RestBreakDialog
import org.finiteplay.klondike.TOP_ROW_TO_TABLEAU_GAP
import org.finiteplay.klondike.computeCardSize
import org.finiteplay.klondike.portraitStockWasteAtBottom
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.heightIn
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.finiteplay.core.ui.layout.DealPickerDialog
import org.finiteplay.klondike.R
import org.finiteplay.klondike.debug.DebugDeepestColumnButton
import org.finiteplay.klondike.debug.DebugExportGameArchiveButton
import org.finiteplay.klondike.debug.DebugFixtureButton
import org.finiteplay.klondike.debug.resetCatalogTraversalOnReinstall
import org.finiteplay.klondike.deal.DifficultyTier
import org.finiteplay.klondike.deal.CertifiedDealCatalog
import org.finiteplay.klondike.deal.SolutionCatalog
import org.finiteplay.core.storage.DealProgressStore
import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.GameStatus
import org.finiteplay.klondike.rules.isStuck
import org.finiteplay.klondike.storage.ActiveGameStore
import org.finiteplay.core.storage.AppLocale
import org.finiteplay.klondike.storage.CatalogTraversalStore
import org.finiteplay.klondike.storage.Handedness
import org.finiteplay.klondike.storage.HistoryStore
import org.finiteplay.klondike.storage.GameArchiveStore
import org.finiteplay.klondike.storage.SettingsStore
import org.finiteplay.core.ui.table.feltTable
import org.finiteplay.core.ui.theme.LocalAppColors
import org.finiteplay.core.ui.sound.AndroidSoundPlayer
import org.finiteplay.core.ui.sound.GatedSoundPlayer
import org.finiteplay.core.ui.sound.SoundEffect

/** Which full-screen overlay, if any, sits on top of the board (`docs/games/klondike/UI_SPEC.md`). */
private enum class Overlay { SETTINGS, STATISTICS, HELP }

/**
 * Lines reserved for the status line, whatever the locale. Both orientations run it across
 * the full width, so two is enough for either. The title is not counted: it is the product
 * name, one word in every locale.
 */
private const val STATUS_LINES = 2

/** Opacity of the FinitePlay wordmark: the same in both orientations, watermark or not. */

/**
 * The playable game screen (A1/A2a): committed [GameSession] state comes from
 * [GameViewModel], survives rotation; everything else (drag, selection, highlighting)
 * lives inside [Board] and is discarded on rotation by design. New-game/replay
 * confirmation and the win presentation are modal dialogs; Android Back closes either
 * before it would otherwise leave the game (`UI_SPEC.md`).
 */
@Composable
fun GameScreen(
    viewModel: GameViewModel = viewModel(
        factory = rememberGameViewModelFactory(),
    ),
    modifier: Modifier = Modifier,
) {
    val session = viewModel.session
    val elapsedSeconds = viewModel.elapsedSeconds
    // Either the system "Remove animations" setting or the persisted in-app one skips animations.
    val skipAnimations = rememberReducedMotion() || !viewModel.persistedAnimationsEnabled

    var isAppForeground by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    viewModel.setForeground(true)
                    isAppForeground = true
                }
                Lifecycle.Event.ON_STOP -> {
                    viewModel.setForeground(false)
                    isAppForeground = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // A real SoundPool-backed player, gated so backgrounding or the Settings toggle
    // silence it independent of anything else (`docs/games/klondike/DESIGN.md` "Sound").
    val context = LocalContext.current
    val androidSoundPlayer = remember { AndroidSoundPlayer(context) }
    DisposableEffect(androidSoundPlayer) { onDispose { androidSoundPlayer.release() } }
    val soundPlayer = remember(androidSoundPlayer) {
        GatedSoundPlayer(androidSoundPlayer, isEnabled = { viewModel.persistedSoundEnabled }, isForeground = { isAppForeground })
    }

    val configuration = LocalConfiguration.current
    val orientation = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        BoardOrientation.LANDSCAPE
    } else {
        BoardOrientation.PORTRAIT
    }

    val pending = viewModel.pendingConfirmation
    val isWon = session.state.status == GameStatus.WON
    // The win dialog waits for Board to finish animating every card into place
    // (including an automatic-finish sweep) before it appears, rather than popping
    // up the instant the state itself becomes won.
    var boardAnimating by remember { mutableStateOf(false) }
    val showWinDialog = isWon && !boardAnimating
    LaunchedEffect(showWinDialog) { if (showWinDialog) soundPlayer.play(SoundEffect.WIN) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    var levelPickerVisible by remember { mutableStateOf(false) }
    var dealPickerVisible by remember { mutableStateOf(false) }
    val stuck = remember(session.state) { !session.state.isWon && isStuck(session.state) }
    var stuckNoticeDismissed by remember(session.state) { mutableStateOf(false) }
    val showStuckNotice = stuck && !stuckNoticeDismissed && pending == null && overlay == null && !isWon

    val anyModalOpen = pending != null || overlay != null || showStuckNotice ||
        viewModel.showRestReminderDialog || viewModel.restBreakRemainingSeconds != null
    LaunchedEffect(anyModalOpen) { viewModel.setModalOpen(anyModalOpen) }
    LaunchedEffect(overlay) { if (overlay == Overlay.STATISTICS) viewModel.refreshStatistics() }

    // Back closes an open modal first; with none open there is nowhere else in this
    // single-screen app to go, so default Back (exit) is left alone.
    BackHandler(enabled = pending != null || overlay != null || showStuckNotice) {
        when {
            pending != null -> viewModel.cancelPendingAction()
            overlay != null -> overlay = null
            else -> stuckNoticeDismissed = true
        }
    }

    val catalogFailure = viewModel.unrecoverableCatalogReason
    if (catalogFailure != null) {
        UnrecoverableScreen(reason = catalogFailure, onRetry = viewModel::retryCatalogLoad)
        return
    }

    if (viewModel.isLoading) {
        // "Loading: static board-colored surface; no animated splash" (UI_SPEC.md).
        Surface(modifier = modifier.fillMaxSize().testTag("loading_screen"), color = MaterialTheme.colorScheme.background) {}
        return
    }

    Surface(modifier = modifier.fillMaxSize().testTag("game_screen"), color = MaterialTheme.colorScheme.background) {
        // The table is cloth, not a fill: the weave and the pool of light are what stop a
        // screen-sized area of one green reading as paper (`core/ui`'s `feltTable`).
        Box(
            modifier = Modifier.fillMaxSize().feltTable(
                base = MaterialTheme.colorScheme.background,
                thread = LocalAppColors.current.card.emptySlot,
            ),
        ) {
            if (orientation == BoardOrientation.LANDSCAPE) {
                FinitePlayLogo(
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(evenSafeDrawingPadding())
                        .padding(bottom = 4.dp),
                    widthFraction = 0.32f,
                )
            }
            Column(modifier = Modifier.fillMaxSize().padding(evenSafeDrawingPadding())) {
                if (orientation == BoardOrientation.PORTRAIT) {
                    StatusRow(
                        state = session.state,
                        elapsedSeconds = elapsedSeconds,
                        dealNumber = viewModel.dealNumber,
                        dealDifficulty = viewModel.dealDifficulty,
                        onStatistics = { overlay = Overlay.STATISTICS },
                        onHelp = { overlay = Overlay.HELP },
                        onSelectLevel = { levelPickerVisible = true },
                        onSelectDeal = { dealPickerVisible = true },
                        lines = STATUS_LINES,
                    )
                    if (viewModel.recoveryNoticeVisible) {
                        RecoveryNoticeRow(onDismiss = viewModel::dismissRecoveryNotice)
                    }
                    // The wordmark is background, not a row: it sat in the layout flow until a
                    // nearly square screen showed what that costs — a strip of height the board
                    // needed more. Drawn behind the board and low in it, so a freshly dealt
                    // board shows it whole and the columns cover it as they grow, which is what
                    // being background means. Where stock and waste have the foot of the board,
                    // it sits above them rather than under them.
                    BoxWithConstraints(modifier = Modifier.weight(1f)) {
                        val logoBottomInset = if (portraitStockWasteAtBottom(maxWidth, maxHeight)) {
                            computeCardSize(maxWidth).height + TOP_ROW_TO_TABLEAU_GAP
                        } else {
                            0.dp
                        }
                        FinitePlayLogo(
                            modifier = Modifier.align(Alignment.BottomCenter)
                                .padding(bottom = logoBottomInset)
                                .fillMaxWidth(0.7f),
                            widthFraction = 1f,
                        )
                    key(viewModel.gameId) {
                        Board(
                            state = session.state,
                            onCommitMove = viewModel::tryCommitMove,
                            modifier = Modifier.fillMaxSize(),
                            orientation = orientation,
                            handedness = viewModel.persistedHandedness,
                            hintMove = viewModel.hint,
                            highlightedMoves = viewModel.legalMoveHighlights,
                            skipAnimations = skipAnimations,
                            automaticMovesEnabled = session.automaticMovesEnabled,
                            undoSignal = viewModel.pendingUndoAnimation,
                            onUndoSignalConsumed = viewModel::consumeUndoAnimationSignal,
                            onAnimationsInFlightChanged = { boardAnimating = it },
                            onPlaySound = soundPlayer::play,
                        )
                    }
                    }
                    ActionBar(
                        orientation = orientation,
                        mirrored = viewModel.persistedHandedness == Handedness.LEFT,
                        canUndo = session.canUndo,
                        hintEnabled = viewModel.hasHint && viewModel.hintState == HintUiState.Hidden,
                        onUndo = viewModel::undo,
                        onNew = viewModel::requestNewGame,
                        onReplay = viewModel::requestReplay,
                        onHint = viewModel::requestHint,
                        onSettings = { overlay = Overlay.SETTINGS },
                    )
                } else {
                    // The status line runs full width above the board, over the
                    // foundations strip. Below it the board is flanked by two single-file
                    // rails: the one at the edge nearest the player's holding hand — the
                    // trailing edge for RIGHT — carries Statistics in its top corner and
                    // Hint over Undo pinned to its bottom corner; the opposite edge
                    // carries the remaining actions, centered. Foundations sit in their
                    // own strip inside Board itself, with the tableau centered after them
                    // and stock/waste in a strip on the opposite side (`UI_SPEC.md`
                    // "Landscape").
                    StatusRow(
                        state = session.state,
                        elapsedSeconds = elapsedSeconds,
                        dealNumber = viewModel.dealNumber,
                        dealDifficulty = viewModel.dealDifficulty,
                        onStatistics = { overlay = Overlay.STATISTICS },
                        onHelp = { overlay = Overlay.HELP },
                        onSelectLevel = { levelPickerVisible = true },
                        onSelectDeal = { dealPickerVisible = true },
                        // One line, not two: landscape puts the level and counters side by side
                        // across the full width, and the spare line was showing up as a band of
                        // empty table above the board on a screen with none to give.
                        lines = 1,
                        showStatistics = false,
                    )
                    if (viewModel.recoveryNoticeVisible) {
                        RecoveryNoticeRow(onDismiss = viewModel::dismissRecoveryNotice)
                    }
                    // The rails' height goes to the action buttons, which need the room
                    // more than a wordmark; landscape's branding is the watermark behind
                    // the board instead.
                    val primaryRail: @Composable () -> Unit = {
                        Column(
                            // No vertical padding: the two groups belong in the corners, and
                            // eight dp at each end is eight dp taken off the gap between them.
                            modifier = Modifier.width(landscapeRailWidth()).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            StatisticsButton(onClick = { overlay = Overlay.STATISTICS })
                            HelpButton(onClick = { overlay = Overlay.HELP })
                            Spacer(Modifier.weight(1f))
                            ActionBar(
                                orientation = orientation,
                                mirrored = viewModel.persistedHandedness == Handedness.LEFT,
                                landscapeGroup = LandscapeActionGroup.PRIMARY,
                                canUndo = session.canUndo,
                                hintEnabled = viewModel.hasHint && viewModel.hintState == HintUiState.Hidden,
                                onUndo = viewModel::undo,
                                onNew = viewModel::requestNewGame,
                                onReplay = viewModel::requestReplay,
                                onHint = viewModel::requestHint,
                                onSettings = { overlay = Overlay.SETTINGS },
                            )
                        }
                    }
                    val secondaryRail: @Composable () -> Unit = {
                        Column(
                            modifier = Modifier.width(landscapeRailWidth()).fillMaxHeight(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ActionBar(
                                orientation = orientation,
                                mirrored = viewModel.persistedHandedness == Handedness.LEFT,
                                landscapeGroup = LandscapeActionGroup.SECONDARY,
                                canUndo = session.canUndo,
                                hintEnabled = viewModel.hasHint && viewModel.hintState == HintUiState.Hidden,
                                onUndo = viewModel::undo,
                                onNew = viewModel::requestNewGame,
                                onReplay = viewModel::requestReplay,
                                onHint = viewModel::requestHint,
                                onSettings = { overlay = Overlay.SETTINGS },
                            )
                        }
                    }
                    val boardArea: @Composable () -> Unit = {
                        key(viewModel.gameId) {
                            Board(
                                state = session.state,
                                onCommitMove = viewModel::tryCommitMove,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                orientation = orientation,
                                handedness = viewModel.persistedHandedness,
                                hintMove = viewModel.hint,
                                highlightedMoves = viewModel.legalMoveHighlights,
                                skipAnimations = skipAnimations,
                                automaticMovesEnabled = session.automaticMovesEnabled,
                                undoSignal = viewModel.pendingUndoAnimation,
                                onUndoSignalConsumed = viewModel::consumeUndoAnimationSignal,
                                onAnimationsInFlightChanged = { boardAnimating = it },
                                onPlaySound = soundPlayer::play,
                            )
                        }
                    }
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (viewModel.persistedHandedness == Handedness.LEFT) {
                            primaryRail()
                            boardArea()
                            secondaryRail()
                        } else {
                            secondaryRail()
                            boardArea()
                            primaryRail()
                        }
                    }
                }
            }
            val hintState = viewModel.hintState
            // Loading has no banner of its own: below HINT_PROGRESS_DIALOG_DELAY it resolves
            // silently (most hints do), and past it HintProgressDialog below takes over instead.
            if (hintState != HintUiState.Hidden && hintState != HintUiState.Loading && hintState !is HintUiState.Guided) {
                // A floating overlay, not part of the Column's layout flow: it must
                // never push the board — it can appear and disappear within a single
                // frame for a cache-hit hint, and doing so inline visibly shifted the
                // whole board down and back for that one frame.
                HintNoticeRow(
                    hintState = hintState,
                    onDismiss = viewModel::dismissHint,
                    modifier = Modifier.align(Alignment.Center).windowInsetsPadding(WindowInsets.safeDrawing),
                )
            }
        }
    }

    if (viewModel.hintShowsProgressDialog) {
        HintProgressDialog(message = stringResource(R.string.hint_thinking), onCancel = viewModel::dismissHint)
    }

    if (overlay == Overlay.SETTINGS) {
        SettingsScreen(
            automaticMovesEnabled = session.automaticMovesEnabled,
            animationsEnabled = viewModel.persistedAnimationsEnabled,
            hintShowsWinningMove = viewModel.persistedHintShowsWinningMove,
            hintTimeout = viewModel.persistedHintTimeout,
            restReminderInterval = viewModel.persistedRestReminderInterval,
            restReminderElapsedSeconds = viewModel.restReminderElapsedSeconds,
            handedness = viewModel.persistedHandedness,
            soundEnabled = viewModel.persistedSoundEnabled,
            drawMode = viewModel.persistedDrawMode,
            difficulty = viewModel.persistedDifficulty,
            languageTag = viewModel.persistedLanguageTag,
            themeMode = viewModel.persistedThemeMode,
            onAutomaticMovesEnabledChange = viewModel::setAutomaticMovesEnabled,
            onAnimationsEnabledChange = viewModel::setAnimationsEnabled,
            onHintShowsWinningMoveChange = viewModel::setHintShowsWinningMove,
            onHintTimeoutChange = viewModel::setHintTimeout,
            onRestReminderIntervalChange = viewModel::setRestReminderInterval,
            onHandednessChange = viewModel::setHandedness,
            onSoundEnabledChange = viewModel::setSoundEnabled,
            onDrawModeChange = viewModel::setDrawMode,
            onDifficultyChange = viewModel::setDifficulty,
            onLanguageChange = { tag ->
                viewModel.setLanguageTag(tag)
                // The locale is read in attachBaseContext, so it only takes hold on a
                // fresh Activity; recreating is what makes the change visible now
                // rather than at next launch (`storage/AppLocale.kt`).
                AppLocale.setTag(context, tag)
                (context as? Activity)?.recreate()
            },
            onThemeModeChange = viewModel::setThemeMode,
            onClose = { overlay = null },
            debugTools = {
                // Loading the fixture replaces the board behind this screen, so close it.
                DebugFixtureButton(viewModel, onLoaded = { overlay = null })
                DebugDeepestColumnButton(viewModel, onLoaded = { overlay = null })
                DebugExportGameArchiveButton()
            },
        )
    }

    if (overlay == Overlay.HELP) {
        HelpScreen(onClose = { overlay = null })
    }

    if (overlay == Overlay.STATISTICS) {
        StatisticsScreen(
            statistics = viewModel.statistics,
            period = viewModel.currentStatisticsPeriod,
            onPeriodChange = viewModel::setStatisticsPeriod,
            drawMode = viewModel.currentStatisticsDrawMode,
            onDrawModeChange = viewModel::setStatisticsDrawMode,
            onReset = viewModel::resetStatistics,
            onClose = { overlay = null },
        )
    }

    if (showStuckNotice) {
        StuckNotice(
            canUndo = session.canUndo,
            onUndo = { viewModel.undo(); stuckNoticeDismissed = true },
            onNewGame = { viewModel.requestNewGame(); stuckNoticeDismissed = true },
            onDismiss = { stuckNoticeDismissed = true },
        )
    }

    if (pending != null) {
        PendingActionConfirmationDialog(
            pending = pending,
            moveCount = session.state.moveCount,
            elapsedSeconds = elapsedSeconds,
            onConfirm = viewModel::confirmPendingAction,
            onDismiss = viewModel::cancelPendingAction,
        )
    }

    if (levelPickerVisible) {
        LevelPickerDialog(
            selected = viewModel.difficulty,
            // Null on a raw board: nothing has been played, so the switch forfeits nothing
            // and the dialog should not claim otherwise.
            forfeitsMoveCount = session.state.moveCount.takeIf { viewModel.levelSwitchWouldForfeit },
            onSelect = {
                levelPickerVisible = false
                viewModel.setDifficulty(it)
            },
            onDismiss = { levelPickerVisible = false },
        )
    }

    if (dealPickerVisible) {
        val pickerCount = viewModel.dealPickerCount
        if (pickerCount != null) {
            // Built once per opening: it walks the whole level's list, and nothing in it changes
            // while the picker is on screen.
            val progress = remember(viewModel.dealProgress) { viewModel.dealPickerProgress() }
            DealPickerDialog(
                dealCount = pickerCount,
                currentNumber = viewModel.dealNumber,
                progress = progress,
                onSelect = {
                    dealPickerVisible = false
                    viewModel.requestSelectDeal(it)
                },
                onDismiss = { dealPickerVisible = false },
            )
        }
    }

    if (showWinDialog) {
        WinDialog(
            state = session.state,
            elapsedSeconds = elapsedSeconds,
            skipAnimations = skipAnimations,
            dealDifficulty = viewModel.dealDifficulty,
            solutionMoveCount = viewModel.solutionMoveCount,
            onNewGame = viewModel::requestNewGame,
            onReplay = viewModel::requestReplay,
        )
    }

    if (viewModel.showRestReminderDialog) {
        RestReminderDialog(
            playedLabel = formatElapsed((viewModel.persistedRestReminderInterval.minutes ?: 0) * 60),
            onTakeBreak = viewModel::startRestBreak,
            onKeepPlaying = viewModel::dismissRestReminder,
        )
    }

    viewModel.restBreakRemainingSeconds?.let { remaining ->
        RestBreakDialog(
            remainingLabel = formatElapsed(remaining),
            onCancel = viewModel::cancelRestBreak,
        )
    }
}

/** Localized label for [tier], per `docs/games/klondike/DESIGN.md` "Difficulty Grading". */
@Composable
private fun DifficultyTier.label(): String = stringResource(
    when (this) {
        DifficultyTier.TRIVIAL -> R.string.level_trivial
        DifficultyTier.INSANE -> R.string.level_insane
        DifficultyTier.EASY -> R.string.level_easy
        DifficultyTier.MEDIUM -> R.string.level_medium
        DifficultyTier.HARD -> R.string.level_hard
        DifficultyTier.EXPERT -> R.string.level_expert
    },
)

/** The status row's accent for a level, cool-to-warm with rising difficulty (`ui/theme/Theme.kt`). */
@Composable
private fun DifficultyTier.accent(): Color {
    val accents = LocalAppColors.current.difficulty
    return when (this) {
        DifficultyTier.TRIVIAL -> accents.trivial
        DifficultyTier.EASY -> accents.easy
        DifficultyTier.MEDIUM -> accents.medium
        DifficultyTier.HARD -> accents.hard
        DifficultyTier.EXPERT -> accents.expert
        DifficultyTier.INSANE -> accents.insane
    }
}

/**
 * One line: the title at the leading edge, the hand level and moves/timer centered in the
 * space it leaves, and Statistics in the trailing corner.
 *
 * The status line reserves a fixed [lines], however short the translation. Status text
 * varies enough in length across the 31 locales — Ukrainian being the longest — that
 * letting it size itself moved everything below it by a line per language; reserving the
 * space costs a blank line in English and keeps the board, and the action bar under it,
 * in exactly the same place everywhere (`docs/games/klondike/UI_SPEC.md` "Status Row").
 */
/**
 * The safe-area inset, with the two horizontal sides widened to match each other.
 *
 * A landscape phone puts its cutout and its navigation bar on opposite edges, so the raw
 * inset is lopsided — on a tablet, 66 dp of padding on the left and none on the right, which
 * pushed the whole board off centre and left one rail visibly further from the edge than the
 * other. Taking the larger of the two for both keeps every pixel out of the unsafe area and
 * puts the board back in the middle of the screen, at the cost of a strip of table on the
 * side that did not need it.
 */
@Composable
private fun evenSafeDrawingPadding(): PaddingValues {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val direction = LocalLayoutDirection.current
    val horizontal = maxOf(
        insets.calculateStartPadding(direction),
        insets.calculateEndPadding(direction),
    )
    return PaddingValues(
        start = horizontal,
        end = horizontal,
        top = insets.calculateTopPadding(),
        bottom = insets.calculateBottomPadding(),
    )
}

@Composable
private fun StatusRow(
    state: GameState,
    elapsedSeconds: Int,
    dealNumber: Int?,
    dealDifficulty: DifficultyTier?,
    onStatistics: () -> Unit,
    onHelp: () -> Unit,
    onSelectLevel: () -> Unit,
    onSelectDeal: () -> Unit,
    lines: Int,
    showStatistics: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
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
        // The title keeps the leading edge; the level and counters center in whatever it
        // leaves, so they read as one line with it rather than a block under it.
        //
        // The height for [lines] is reserved on the *box*, not by padding the text out to
        // that many lines: a two-line reservation on the text itself puts a one-line status
        // on the first of those lines, which floats it above the title's centre. Reserving
        // the height here keeps the row the same height in every locale and still centres
        // whatever the text actually occupies.
        val statusStyle = boardStatusStyle()
        val reservedHeight = with(LocalDensity.current) { (statusStyle.lineHeight * lines).toDp() }
        Box(
            modifier = Modifier.weight(1f).heightIn(min = reservedHeight).padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            // One Text, not a Row of two: only a single string can reserve exactly two
            // lines and wrap between the level and the counters when a translation needs
            // it. The level keeps the difficulty's color through a span; the name is
            // always spelled out, so color adds emphasis rather than carrying meaning.
            val wonSuffix = if (state.isWon) " · " + stringResource(CoreR.string.status_won) else ""
            val counters = stringResource(CoreR.string.status_moves_time, state.moveCount, formatElapsed(elapsedSeconds)) + wonSuffix
            // The level is a link, not merely coloured text: it is the shortest route to
            // changing level, and Settings is a long way to go for something named on screen
            // (`UI_SPEC.md` "Status Row"). Only the level span is clickable — the counters
            // beside it are not a control, and the two share one Text for the wrapping
            // reason above, so a link annotation is what separates them.
            val levelLink = LinkAnnotation.Clickable(
                tag = "level",
                styles = TextLinkStyles(SpanStyle(color = dealDifficulty?.accent() ?: Color.Unspecified, fontWeight = FontWeight.Bold)),
            ) { onSelectLevel() }
            // The deal number is its own link: the level changes which list a game is drawn from,
            // the number picks a deal within it, and a player reaching for one should not land on
            // the other.
            val dealLink = LinkAnnotation.Clickable(
                tag = "deal",
                styles = TextLinkStyles(SpanStyle(color = dealDifficulty?.accent() ?: Color.Unspecified, fontWeight = FontWeight.Bold)),
            ) { onSelectDeal() }
            // Portrait breaks between the level and the counters; landscape keeps them on one
            // line. The reserved two lines are already there for the locales that need them, so
            // portrait spends the second line on separating what the deal *is* from how the
            // game is going, and landscape - where height is the scarce dimension - does not.
            val status = buildAnnotatedString {
                if (dealDifficulty != null && dealNumber != null) {
                    withLink(levelLink) { append(dealDifficulty.label()) }
                    append(" ")
                    withLink(dealLink) { append(stringResource(CoreR.string.deal_number, dealNumber)) }
                    // Portrait is the orientation that shows Statistics here, so it is also the
                    // one with a second line to spend: the level goes above, the counters below.
                    if (showStatistics) appendLine() else append("  ")
                }
                append(counters)
            }
            Text(
                text = status,
                style = statusStyle,
                color = boardStatusColor(),
                textAlign = TextAlign.Center,
                // Reserved height and maximum height are different questions: [lines] fixes
                // how much room the row always takes, so the board below it never moves, while
                // a long translation may still use a second line rather than ellipsizing.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("status_row"),
            )
        }
        // Landscape shows these in its own rail instead, since the status line there spans
        // the full width above the board. Help sits before Statistics: both read rather than
        // act, and Help is the one a new player needs first.
        if (showStatistics) {
            HelpButton(onClick = onHelp)
            StatisticsButton(onClick = onStatistics)
        }
    }
}

/**
 * Statistics reads the record rather than acting on the game in play, so it sits apart
 * from the action bar, in the top corner conventional for it.
 */
/** Help explains the game rather than acting on it, so it keeps Statistics company. */
@Composable
private fun HelpButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp * chromeScale()).testTag("help_button"),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
            contentDescription = stringResource(R.string.help_title),
            tint = LocalAppColors.current.action.replay,
            modifier = Modifier.size(24.dp * chromeScale()),
        )
    }
}

@Composable
private fun StatisticsButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp * chromeScale()).testTag("statistics_button"),
    ) {
        Icon(
            imageVector = Icons.Filled.BarChart,
            contentDescription = stringResource(CoreR.string.action_statistics),
            tint = LocalAppColors.current.action.statistics,
            modifier = Modifier.size(24.dp * chromeScale()),
        )
    }
}


/**
 * Unobtrusive notice after a corrupt saved game was discarded; the game itself stays
 * playable on a fresh deal (`UI_SPEC.md` "Screens and States" > "Recovery").
 */
@Composable
private fun RecoveryNoticeRow(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("recovery_notice"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.recovery_notice_text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.action_dismiss)) }
    }
}

/**
 * The on-device hint search's non-guidance results (`docs/games/klondike/DESIGN.md` "On-Device Hint
 * Search"): a loading notice while it runs, not dismissible since the search cannot be
 * cancelled mid-flight, or the announcement once it proves there is no path to a win
 * from the current board, or that the search could not tell within its budget — both
 * dismissible immediately and self-dismissed by [GameViewModel] after
 * [HINT_NOTICE_AUTO_DISMISS] if the player leaves them alone (`docs/games/klondike/UI_SPEC.md` "Hint":
 * "a brief dismissible notice"). A found guidance move is shown by [Board] highlighting
 * the card instead, so this row never appears for [HintUiState.Guided].
 */
@Composable
internal fun HintNoticeRow(hintState: HintUiState, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val text = when (hintState) {
        HintUiState.Loading -> stringResource(R.string.hint_thinking)
        HintUiState.NoSolution -> stringResource(R.string.hint_no_solution)
        is HintUiState.Inconclusive -> stringResource(R.string.hint_inconclusive)
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
                // Weighted so the longest message (Inconclusive) wraps within whatever
                // width Dismiss doesn't need, instead of the unweighted Row measuring
                // both children against the same full width and letting Text greedily
                // claim it all, pushing Dismiss past the Surface's clipped edge.
                modifier = Modifier.weight(1f, fill = false).semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (hintState != HintUiState.Loading) {
                TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.action_dismiss)) }
            }
        }
    }
}

/** `:core:session`'s stopwatch formatting; kept as an alias so call sites here are unchanged. */
internal fun formatElapsed(totalSeconds: Int): String = sharedFormatElapsed(totalSeconds)

/**
 * Builds [GameViewModel] against app-private storage (`context.filesDir`), per S1. Public
 * because `MainActivity` creates the view model itself: the persisted theme lives on it,
 * and the theme has to wrap this screen rather than sit inside it.
 */
@Composable
fun rememberGameViewModelFactory(): ViewModelProvider.Factory {
    val context = LocalContext.current.applicationContext
    val filesDir = context.filesDir
    return remember(filesDir) {
        viewModelFactory {
            initializer {
                resetCatalogTraversalOnReinstall(context) // debug-only; no-op in release
                val catalog = CertifiedDealCatalog.load(context)
                GameViewModel(
                    activeGameStore = ActiveGameStore(filesDir),
                    settingsStore = SettingsStore(filesDir),
                    historyStore = HistoryStore(filesDir),
                    gameArchiveStore = GameArchiveStore(filesDir),
                    catalogTraversalStore = CatalogTraversalStore(filesDir),
                    dealProgressStore = DealProgressStore(filesDir),
                    solutionCatalog = SolutionCatalog(context),
                    certifiedCatalog = when (catalog) {
                        is CertifiedDealCatalog.LoadResult.Valid -> catalog.catalog
                        is CertifiedDealCatalog.LoadResult.Invalid -> null
                    },
                    catalogLoadFailure = (catalog as? CertifiedDealCatalog.LoadResult.Invalid)?.reason,
                    catalogLoader = { CertifiedDealCatalog.load(context) },
                )
            }
        }
    }
}
