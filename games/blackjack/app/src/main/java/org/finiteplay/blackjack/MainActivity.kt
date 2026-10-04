package org.finiteplay.blackjack

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.finiteplay.blackjack.storage.BlackjackLedgerStore
import org.finiteplay.blackjack.storage.BlackjackRoundStore
import org.finiteplay.blackjack.storage.BlackjackSettingsStore
import org.finiteplay.blackjack.ui.BlackjackViewModel
import org.finiteplay.blackjack.ui.GameScreen
import org.finiteplay.core.storage.AppLocale
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.core.ui.theme.resolveDark

class MainActivity : ComponentActivity() {
    /** Applies the in-app language before any resource is resolved (see [AppLocale]). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BlackjackApp() }
    }
}

/** The table, behind the shared theme and system-bar styling. */
@Composable
fun BlackjackApp() {
    val viewModel: BlackjackViewModel = viewModel(factory = rememberBlackjackViewModelFactory())
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

/** Builds [BlackjackViewModel] against app-private storage (`context.filesDir`). */
@Composable
private fun rememberBlackjackViewModelFactory(): ViewModelProvider.Factory {
    val context = LocalContext.current.applicationContext
    val filesDir = context.filesDir
    return remember(filesDir) {
        viewModelFactory {
            initializer {
                BlackjackViewModel(
                    roundStore = BlackjackRoundStore(filesDir),
                    ledgerStore = BlackjackLedgerStore(filesDir),
                    settingsStore = BlackjackSettingsStore(filesDir),
                )
            }
        }
    }
}
