package org.finiteplay.freecell

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.finiteplay.core.storage.AppLocale
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.core.ui.theme.resolveDark
import org.finiteplay.freecell.debug.debugMoveRecorder
import org.finiteplay.freecell.deal.FreeCellCertifiedDealCatalog
import org.finiteplay.freecell.deal.FreeCellSolutionCatalog
import org.finiteplay.core.storage.DealProgressStore
import org.finiteplay.freecell.storage.FreeCellActiveGameStore
import org.finiteplay.freecell.storage.FreeCellHistoryStore
import org.finiteplay.freecell.storage.FreeCellSettingsStore
import org.finiteplay.freecell.storage.FreeCellTraversalStore
import org.finiteplay.freecell.ui.game.FreeCellViewModel
import org.finiteplay.freecell.ui.game.GameScreen

class MainActivity : ComponentActivity() {
    /** Applies the in-app language before any resource is resolved (see [AppLocale]). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FreeCellApp()
        }
    }
}

/**
 * A real, playable game that survives being killed, behind the shared theme and system-bar
 * styling (`docs/games/freecell/EXECUTION_PLAN.md` F3a/F3b).
 */
@Composable
fun FreeCellApp() {
    val viewModel: FreeCellViewModel = viewModel(factory = rememberFreeCellViewModelFactory())
    val darkTheme = viewModel.settings.themeMode.resolveDark()

    val activity = LocalActivity.current as? ComponentActivity
    SideEffect {
        val style = if (darkTheme) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    FinitePlayTheme(darkTheme = darkTheme) {
        val lifecycleOwner = LocalLifecycleOwner.current
        // The timer must stop when the app leaves the foreground, and a save must be current
        // before the process can be killed (`docs/PLATFORM.md` "Persistence").
        DisposableEffect(lifecycleOwner, viewModel) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> viewModel.setForeground(true)
                    Lifecycle.Event.ON_STOP -> viewModel.setForeground(false)
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        GameScreen(viewModel = viewModel, modifier = Modifier.fillMaxSize())
    }
}

/** Builds [FreeCellViewModel] against app-private storage (`context.filesDir`), per F3a/F3b. */
@Composable
private fun rememberFreeCellViewModelFactory(): ViewModelProvider.Factory {
    val context = LocalContext.current.applicationContext
    val filesDir = context.filesDir
    return remember(filesDir) {
        viewModelFactory {
            initializer {
                FreeCellViewModel(
                    store = FreeCellActiveGameStore(filesDir),
                    settingsStore = FreeCellSettingsStore(filesDir),
                    historyStore = FreeCellHistoryStore(filesDir),
                    traversalStore = FreeCellTraversalStore(filesDir),
                    dealProgressStore = DealProgressStore(filesDir),
                    catalogLoader = { FreeCellCertifiedDealCatalog.load(context) },
                    solutionCatalog = FreeCellSolutionCatalog(context),
                    moveRecorder = debugMoveRecorder(filesDir),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FreeCellAppPreview() {
    FreeCellApp()
}
