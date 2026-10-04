package org.finiteplay.blackjack.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.finiteplay.blackjack.R
import org.finiteplay.blackjack.rules.Chips
import org.finiteplay.blackjack.rules.Settlement
import org.finiteplay.core.ui.R as CoreR
import org.finiteplay.core.ui.layout.BoardOrientation
import org.finiteplay.core.session.formatElapsed
import org.finiteplay.core.ui.layout.Handedness
import org.finiteplay.core.ui.layout.RestBreakDialog
import org.finiteplay.core.ui.layout.RestReminderDialog
import org.finiteplay.core.ui.layout.landscapeRailWidth
import org.finiteplay.core.ui.sound.AndroidSoundPlayer
import org.finiteplay.core.ui.sound.GatedSoundPlayer
import org.finiteplay.core.ui.sound.SoundEffect
import org.finiteplay.core.ui.table.feltTable
import org.finiteplay.core.ui.theme.LocalAppColors

/** Pacing of the dealer's reveal (`UI_SPEC.md` "Motion"): between cards, and before results. */
private const val DEALER_STEP_MS = 600L
private const val RESULTS_DELAY_MS = 400L
private const val DEALER_STEP_SKIP_MS = 150L

/**
 * The table, the bar and the panels. Everything shown is derived from [BlackjackViewModel], whose
 * [BlackjackViewModel.session] only ever holds a round whose latest card has been saved.
 */
@Composable
fun GameScreen(viewModel: BlackjackViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showStatistics by remember { mutableStateOf(false) }

    val androidSoundPlayer = remember { AndroidSoundPlayer(context) }
    DisposableEffect(androidSoundPlayer) { onDispose { androidSoundPlayer.release() } }
    val soundPlayer = remember(androidSoundPlayer) {
        GatedSoundPlayer(androidSoundPlayer, isEnabled = { viewModel.settings.soundEnabled }, isForeground = { viewModel.isForeground })
    }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val orientation = if (landscape) BoardOrientation.LANDSCAPE else BoardOrientation.PORTRAIT
    val mirrored = viewModel.settings.handedness == Handedness.LEFT
    val round = viewModel.session

    // The dealer's reveal is presented in steps; nothing it reveals is in the state until the
    // round settles, so the steps are a view over the settled state. The progress is saved with the
    // instance state, so a rotation mid-reveal does not replay it.
    val seed = round?.state?.seed
    var dealerFaceUp by rememberSaveable(seed) { mutableStateOf(1) }
    var resultsShown by rememberSaveable(seed) { mutableStateOf(false) }
    val settledState = round?.state?.takeIf { it.isSettled }
    val stepMs = if (viewModel.settings.animationsEnabled) DEALER_STEP_MS else DEALER_STEP_SKIP_MS
    LaunchedEffect(seed, settledState != null) {
        val state = settledState ?: return@LaunchedEffect
        if (resultsShown) return@LaunchedEffect
        // Turn the hole card, then each further dealer card, one at a time; results only after the last lands.
        if (dealerFaceUp < 2) {
            delay(stepMs)
            dealerFaceUp = 2
            soundPlayer.play(SoundEffect.MOVE)
        }
        while (dealerFaceUp < state.dealer.size) {
            delay(stepMs)
            dealerFaceUp += 1
            soundPlayer.play(SoundEffect.MOVE)
        }
        delay(if (viewModel.settings.animationsEnabled) RESULTS_DELAY_MS else DEALER_STEP_SKIP_MS)
        resultsShown = true
        if (state.settlement!!.total > 0) soundPlayer.play(SoundEffect.WIN)
    }
    // A card dealt to the player is heard when it appears.
    LaunchedEffect(round?.state?.shoePosition, seed) { if (round != null && !round.state.isSettled) soundPlayer.play(SoundEffect.MOVE) }

    val settlement: Settlement? = if (resultsShown) settledState?.settlement else null
    // The bankroll already includes a settled round, so until its results are shown the status row
    // reads what it was before — showing the new figure first would give the result away.
    val shownBankroll = if (settledState != null && !resultsShown) viewModel.bankroll - settledState.settlement!!.total else viewModel.bankroll

    val anyPanelOpen = showSettings || showHelp || showStatistics
    BackHandler(enabled = anyPanelOpen || viewModel.resetOfferVisible) {
        when {
            showSettings -> showSettings = false
            showHelp -> showHelp = false
            showStatistics -> showStatistics = false
            else -> viewModel.dismissResetOffer()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = modifier
            .testTag("app_root")
            .feltTable(base = MaterialTheme.colorScheme.background, thread = LocalAppColors.current.card.emptySlot),
    ) { padding ->
        if (viewModel.isLoading) {
            // Nothing interactive until the saved round has been checked.
            Box(modifier = Modifier.fillMaxSize().padding(padding).testTag("loading"), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val bar = BarState(
            table = viewModel.tableState,
            legal = viewModel.legal,
            needsReset = viewModel.needsReset,
            canStepBetDown = viewModel.canStepBetDown,
            canStepBetUp = viewModel.canStepBetUp,
            // Next round waits for the reveal to finish: the settlement is already paid, but pressing it
            // early would skip past the results.
            busy = viewModel.busy || (settledState != null && !resultsShown),
            canHint = viewModel.canHint,
            suggested = viewModel.hint,
        )
        val actionBar: @Composable (BoardOrientation) -> Unit = { barOrientation ->
            ActionBar(
                orientation = barOrientation,
                mirrored = mirrored,
                bar = bar,
                selectedBetStep = Chips.BET_STEP,
                onDecision = viewModel::decide,
                onBet = viewModel::stepBet,
                onDeal = viewModel::deal,
                onReset = viewModel::resetBankroll,
                onHint = viewModel::requestHint,
                onSettings = { showSettings = true },
            )
        }
        val animate = viewModel.settings.animationsEnabled
        val table: @Composable (Modifier) -> Unit = { tableModifier ->
            Box(modifier = tableModifier) {
                val state = round?.state
                if (state == null) {
                    EmptyTable(bet = viewModel.selectedBet, animate = animate)
                } else {
                    CompositionLocalProvider(LocalAnimateCards provides animate) {
                        Table(
                            view = TableView(state, dealerFaceUp = dealerFaceUp, resultsShown = resultsShown || !state.isSettled),
                            landscape = landscape,
                        )
                    }
                }
                val result = settledState?.settlement
                // The one place the result is stated: shown once the dealer's reveal has finished, and kept
                // until the next round starts.
                if (resultsShown && result != null) {
                    ResultCelebration(resultKindOf(result), result.total, result.insuranceDelta, seed ?: 0L, animate, landscape)
                }
            }
        }
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val heading: @Composable () -> Unit = {
                StatusRow(onHelp = { showHelp = true }, onStatistics = { showStatistics = true })
                if (viewModel.recoveryNoticeVisible) RecoveryNotice(onDismiss = viewModel::dismissRecoveryNotice)
            }
            // The bankroll and the bet sit just above the action bar, within the thumb's reach.
            val hud: @Composable () -> Unit = {
                val staked = round?.state?.takeIf { !it.isSettled }?.hands?.sumOf { it.bet }
                ChipsHud(
                    bankroll = shownBankroll,
                    bet = staked ?: viewModel.selectedBet,
                    animate = animate,
                )
            }
            if (landscape) {
                Column(modifier = Modifier.fillMaxSize()) {
                    heading()
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                            table(Modifier.weight(1f))
                            hud()
                        }
                        actionBar(BoardOrientation.LANDSCAPE)
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    heading()
                    table(Modifier.weight(1f))
                    hud()
                    actionBar(BoardOrientation.PORTRAIT)
                }
            }
        }
    }

    if (viewModel.resetOfferVisible && resultsShown) {
        AlertDialog(
            modifier = Modifier.testTag("reset_offer"),
            onDismissRequest = viewModel::dismissResetOffer,
            title = { Text(stringResource(R.string.reset_offer_title)) },
            text = { Text(stringResource(R.string.reset_offer_body, Chips.STARTING_BANKROLL)) },
            confirmButton = {
                TextButton(onClick = viewModel::resetBankroll, modifier = Modifier.testTag("reset_offer_confirm")) {
                    Text(stringResource(R.string.action_reset_chips))
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissResetOffer) { Text(stringResource(R.string.reset_offer_not_now)) } },
        )
    }

    if (viewModel.showRestReminderDialog) {
        RestReminderDialog(
            playedLabel = formatElapsed((viewModel.settings.restReminderInterval.minutes ?: 0) * 60),
            onTakeBreak = viewModel::startRestBreak,
            onKeepPlaying = viewModel::dismissRestReminder,
        )
    }
    viewModel.restBreakRemainingSeconds?.let { remaining ->
        RestBreakDialog(remainingLabel = formatElapsed(remaining), onCancel = viewModel::cancelRestBreak)
    }

    if (showHelp) HelpScreen(onClose = { showHelp = false })
    if (showStatistics) {
        StatisticsScreen(
            statistics = viewModel.ledger.statistics,
            bankroll = viewModel.bankroll,
            onReset = viewModel::resetStatistics,
            onClose = { showStatistics = false },
        )
    }
    if (showSettings) {
        SettingsScreen(viewModel = viewModel, onClose = { showSettings = false })
    }
}
