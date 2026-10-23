package org.finiteplay.holdem

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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import org.finiteplay.core.storage.AppLocale
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.core.ui.theme.resolveDark
import org.finiteplay.holdem.storage.HoldemGameStorage
import org.finiteplay.holdem.storage.HoldemHandStore
import org.finiteplay.holdem.storage.HoldemSettingsStore
import org.finiteplay.holdem.storage.HoldemTournamentStore
import org.finiteplay.holdem.ui.GameScreen
import org.finiteplay.holdem.ui.HoldemViewModel
import org.finiteplay.core.ui.feedback.EnjoyController
import org.finiteplay.core.storage.EngagementStore

class MainActivity : ComponentActivity() {
    /** Applies the in-app language before any resource is resolved (see [AppLocale]). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HoldemApp() }
    }
}

/** The table, behind the shared theme and system-bar styling. */
@Composable
fun HoldemApp() {
    val viewModel: HoldemViewModel = viewModel(factory = rememberHoldemViewModelFactory())
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

/** Builds [HoldemViewModel] against app-private storage (`context.filesDir`). */
@Composable
private fun rememberHoldemViewModelFactory(): ViewModelProvider.Factory {
    val filesDir = LocalContext.current.applicationContext.filesDir
    return remember(filesDir) {
        viewModelFactory {
            initializer {
                HoldemViewModel(
                    storage = HoldemGameStorage(HoldemTournamentStore(filesDir), HoldemHandStore(filesDir)),
                    settingsStore = HoldemSettingsStore(filesDir),
                    enjoy = EnjoyController(EngagementStore(filesDir)),
                )
            }
        }
    }
}
