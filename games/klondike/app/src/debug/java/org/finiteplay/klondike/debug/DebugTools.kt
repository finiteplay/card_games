package org.finiteplay.klondike.debug

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.launch
import org.finiteplay.klondike.deal.DifficultyTier
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.session.LogEntry
import org.finiteplay.klondike.storage.ArchivedGame
import org.finiteplay.klondike.storage.CatalogTraversalStore
import org.finiteplay.klondike.storage.GameArchiveStore
import org.finiteplay.core.storage.preferencesFileAt
import org.finiteplay.klondike.ui.game.GameViewModel

/**
 * Debug-only entry point for the near-win fixture (`EXECUTION_PLAN.md` A2a). The
 * `src/release` variant of this file renders nothing, so a release build compiles no
 * fixture-loading UI at all — `buildConfig` is disabled, so source-set separation, not
 * a runtime flag, is what keeps this out of release.
 */
@Composable
fun DebugFixtureButton(viewModel: GameViewModel, onLoaded: () -> Unit = {}) {
    TextButton(
        onClick = {
            viewModel.loadFixtureForDebugging(nearWinGameState())
            onLoaded()
        },
        modifier = Modifier.padding(horizontal = 12.dp).testTag("debug_load_near_win"),
    ) {
        Text("Load near-win (debug)")
    }
}

/**
 * Loads the deepest board the rules allow (`deepestColumnGameState`), for looking at the worst
 * case for column height on a given screen. Absent from release by the same source-set
 * separation as the near-win fixture.
 */
@Composable
fun DebugDeepestColumnButton(viewModel: GameViewModel, onLoaded: () -> Unit = {}) {
    TextButton(
        onClick = {
            viewModel.loadFixtureForDebugging(deepestColumnGameState())
            onLoaded()
        },
        modifier = Modifier.padding(horizontal = 12.dp).testTag("debug_load_deepest_column"),
    ) {
        Text("Load deepest column (debug)")
    }
}

/**
 * Debug-only capture of the player's own subjective difficulty rating for the hand that
 * just ended, next to [solverTier] — the grade `INTERIM_SEED_GRADES` assigned the same
 * seed offline (`docs/games/klondike/DESIGN.md` "Difficulty Grading") — so the two can be compared by
 * hand later. Appended as a CSV row to a file in app-private storage; never networked,
 * per this project's "No networking, analytics" invariant. Retrieve with
 * `adb shell run-as org.finiteplay.klondike cat files/difficulty_ratings.csv` (or
 * `adb pull` against a debuggable app's data dir). Shown once per game; replaced by a
 * thank-you line once a rating is recorded. The `src/release` variant renders nothing.
 *
 * Laid out as rows of [RATING_BUTTONS_PER_ROW], not one flat row: all six tiers on a
 * single line overflow an `AlertDialog`'s content width on a phone, and a `Row` clips
 * rather than wraps — so the hardest tiers, the ones this capture most needs ratings
 * for, were silently unreachable.
 */
@Composable
fun DebugDifficultyRatingCapture(seed: Long, moveCount: Int, elapsedSeconds: Int, solverTier: DifficultyTier?) {
    val context = LocalContext.current
    var recorded by remember(seed) { mutableStateOf(false) }
    if (recorded) {
        Text(
            text = "Rating recorded — thanks! (debug)",
            modifier = Modifier.padding(top = 8.dp).testTag("debug_rating_recorded"),
        )
        return
    }
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text("How hard did that feel? (debug)")
        DifficultyTier.entries.chunked(RATING_BUTTONS_PER_ROW).forEach { row ->
            Row {
                row.forEach { tier ->
                    TextButton(
                        onClick = {
                            appendDifficultyRating(context, seed, moveCount, elapsedSeconds, solverTier, tier)
                            recorded = true
                        },
                        modifier = Modifier.testTag("debug_rate_${tier.name.lowercase()}"),
                    ) {
                        Text(tier.name.lowercase().replaceFirstChar(Char::uppercase))
                    }
                }
            }
        }
    }
}

private const val RATING_BUTTONS_PER_ROW = 3

/**
 * Debug-only convenience: repeated `adb install -r` cycles during local testing keep
 * the same app data, so `InterimSolvableDealSeedSource`'s persisted position
 * (`CatalogTraversalStore`) just resumes wherever the last session left off instead of
 * restarting at hand 1 - annoying for anyone manually working through hands in order.
 * Detects a fresh install or an `install -r` update by comparing `PackageManager`'s
 * `lastUpdateTime` against a marker written last time this ran; if it changed, deletes
 * the traversal store's backing file only (`preferencesFileAt`,
 * `CatalogTraversalStore.STORE_NAME`) - settings, history, and the active game are
 * untouched. Plain synchronous file I/O, no coroutines: called from
 * `rememberGameViewModelFactory`'s `initializer { }` block, strictly before
 * `CatalogTraversalStore` is constructed for real, so there's no window where a
 * DataStore instance for that file already exists when it's deleted.
 */
fun resetCatalogTraversalOnReinstall(context: Context) {
    val lastUpdateTime = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    val marker = File(context.filesDir, "debug_install_marker")
    val lastSeen = marker.takeIf { it.exists() }?.readText()?.toLongOrNull()
    if (lastSeen == lastUpdateTime) return
    preferencesFileAt(context.filesDir, CatalogTraversalStore.STORE_NAME).delete()
    marker.writeText(lastUpdateTime.toString())
}

/**
 * Debug-only export of the game archive (`GameArchiveStore`) to a readable text file,
 * because the archive itself is a binary blob inside DataStore and the whole point of
 * keeping it is offline analysis. Retrieve with
 * `adb shell run-as org.finiteplay.klondike cat files/game_archive.txt`. Never networked,
 * per this project's "No networking, analytics" invariant; the `src/release` variant
 * renders nothing, so no release build can export anything.
 *
 * Reads through its own [GameArchiveStore] on the app's files directory rather than
 * through [GameViewModel] — same directory, same store name, so it sees exactly what the
 * game wrote, without widening the view model's API for a debug affordance.
 */
@Composable
fun DebugExportGameArchiveButton() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exported by remember { mutableStateOf<Int?>(null) }
    TextButton(
        onClick = {
            scope.launch {
                val games = GameArchiveStore(context.filesDir).all()
                File(context.filesDir, "game_archive.txt").writeText(formatGameArchive(games))
                exported = games.size
            }
        },
        modifier = Modifier.padding(horizontal = 12.dp).testTag("debug_export_game_archive"),
    ) {
        Text(exported?.let { "Exported $it games (debug)" } ?: "Export game archive (debug)")
    }
}

/**
 * Two lines per archived game: a header line of scalar fields, then that game's whole
 * move log as whitespace-separated tokens. Chosen over CSV because a move log is
 * variable-length and would either blow out the column count or need quoting; chosen over
 * JSON because this module has no JSON dependency and a hand-rolled writer is more code
 * than the format is worth.
 */
internal fun formatGameArchive(games: List<ArchivedGame>): String = buildString {
    appendLine("# gameId seed drawMode outcome elapsedMs moveCount timestampMs countedForStatistics autoMovesAtDeal")
    appendLine("# then one line of move tokens: D=draw R=recycle T<from>.<idx>><to> TF<col> W<col> WF F<suit>><col> U=undo A0/A1=automation !=autofinish")
    for (game in games) {
        appendLine(
            "${game.gameId} ${game.seed} ${game.drawMode} ${game.outcome} ${game.elapsedMillis} " +
                "${game.moveCount} ${game.timestampMillis} ${game.countedForStatistics} ${game.initialAutomaticMovesEnabled}",
        )
        appendLine(game.moves.joinToString(" ") { tokenFor(it) })
    }
}

private fun tokenFor(entry: LogEntry): String = when (entry) {
    LogEntry.Undo -> "U"
    LogEntry.AutoFinish -> "!"
    is LogEntry.SetAutomaticMoves -> if (entry.enabled) "A1" else "A0"
    is LogEntry.PlayerMove -> when (val move = entry.move) {
        Move.Draw -> "D"
        Move.Recycle -> "R"
        Move.WasteToFoundation -> "WF"
        is Move.TableauToTableau -> "T${move.fromColumn}.${move.fromIndex}>${move.toColumn}"
        is Move.TableauToFoundation -> "TF${move.fromColumn}"
        is Move.WasteToTableau -> "W${move.toColumn}"
        is Move.FoundationToTableau -> "F${move.suit.name.first()}>${move.toColumn}"
    }
}

private fun appendDifficultyRating(
    context: Context,
    seed: Long,
    moveCount: Int,
    elapsedSeconds: Int,
    solverTier: DifficultyTier?,
    playerTier: DifficultyTier,
) {
    val file = File(context.filesDir, "difficulty_ratings.csv")
    if (!file.exists()) file.appendText("timestampMs,seed,moveCount,elapsedSeconds,solverTier,playerTier\n")
    file.appendText("${System.currentTimeMillis()},$seed,$moveCount,$elapsedSeconds,${solverTier ?: "NONE"},$playerTier\n")
}
