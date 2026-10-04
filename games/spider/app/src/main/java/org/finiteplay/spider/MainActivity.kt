package org.finiteplay.spider

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.finiteplay.spider.deal.SpiderCertifiedDealCatalog
import org.finiteplay.spider.debug.debugMoveRecorder
import org.finiteplay.spider.deal.SpiderSolutionCatalog
import org.finiteplay.core.storage.DealProgressStore
import org.finiteplay.spider.storage.SpiderActiveGameStore
import org.finiteplay.spider.storage.SpiderHistoryStore
import org.finiteplay.spider.storage.SpiderSettingsStore
import org.finiteplay.spider.storage.SpiderTraversalStore
import org.finiteplay.core.storage.AppLocale
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.core.ui.theme.ThemeMode
import org.finiteplay.core.ui.theme.resolveDark
import org.finiteplay.spider.ui.game.GameScreen
import org.finiteplay.spider.ui.game.SpiderViewModel

class MainActivity : ComponentActivity() {
    /** Applies the in-app language before any resource is resolved (see [AppLocale]). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SpiderApp()
        }
    }
}

/**
 * A real, playable game that survives being killed, behind the shared theme and system-bar
 * styling (`docs/games/spider/EXECUTION_PLAN.md` S3a/S3b).
 */
@Composable
fun SpiderApp() {
    val viewModel: SpiderViewModel = viewModel(factory = rememberSpiderViewModelFactory())
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

/** Builds [SpiderViewModel] against app-private storage (`context.filesDir`), per S3b. */
@Composable
private fun rememberSpiderViewModelFactory(): ViewModelProvider.Factory {
    val context = LocalContext.current.applicationContext
    val filesDir = context.filesDir
    return remember(filesDir) {
        viewModelFactory {
            initializer {
                val catalog = SpiderCertifiedDealCatalog.load(context)
                SpiderViewModel(
                    store = SpiderActiveGameStore(filesDir),
                    settingsStore = SpiderSettingsStore(filesDir),
                    historyStore = SpiderHistoryStore(filesDir),
                    traversalStore = SpiderTraversalStore(filesDir),
                    dealProgressStore = DealProgressStore(filesDir),
                    certifiedCatalog = when (catalog) {
                        is SpiderCertifiedDealCatalog.LoadResult.Valid -> catalog.catalog
                        is SpiderCertifiedDealCatalog.LoadResult.Invalid -> null
                    },
                    solutionCatalog = SpiderSolutionCatalog(context),
                    moveRecorder = debugMoveRecorder(filesDir),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SpiderAppPreview() {
    SpiderApp()
}
