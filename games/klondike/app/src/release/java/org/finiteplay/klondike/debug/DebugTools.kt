package org.finiteplay.klondike.debug

import android.content.Context
import androidx.compose.runtime.Composable
import org.finiteplay.klondike.deal.DifficultyTier
import org.finiteplay.klondike.ui.game.GameViewModel

/** Release counterpart of the debug near-win fixture button: renders nothing. */
@Composable
fun DebugFixtureButton(viewModel: GameViewModel, onLoaded: () -> Unit = {}) {
}

/** Release counterpart of the deepest-column fixture button: renders nothing. */
@Composable
fun DebugDeepestColumnButton(viewModel: GameViewModel, onLoaded: () -> Unit = {}) {
}

/** Release counterpart of the debug difficulty-rating capture: renders nothing. */
@Composable
fun DebugDifficultyRatingCapture(seed: Long, moveCount: Int, elapsedSeconds: Int, solverTier: DifficultyTier?) {
}

/** Release counterpart of the debug game-archive export: renders nothing, so no release build can export the archive. */
@Composable
fun DebugExportGameArchiveButton() {
}

/** Release counterpart of the debug reinstall-reset hook: no-op. */
fun resetCatalogTraversalOnReinstall(context: Context) {
}
