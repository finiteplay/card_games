package org.finiteplay.klondike

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import org.finiteplay.core.storage.AppLocale
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.core.ui.theme.resolveDark
import org.finiteplay.klondike.ui.game.GameScreen
import org.finiteplay.klondike.ui.game.GameViewModel
import org.finiteplay.klondike.ui.game.rememberGameViewModelFactory

class MainActivity : ComponentActivity() {
    /** Applies the in-app language before any resource is resolved (see [AppLocale]). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KlondikeApp()
        }
    }
}

@Composable
fun KlondikeApp() {
    // The view model is created here rather than inside GameScreen because the persisted
    // theme lives on it, and the theme has to wrap the screen rather than sit inside it.
    val viewModel: GameViewModel = viewModel(factory = rememberGameViewModelFactory())
    val darkTheme = viewModel.persistedThemeMode.resolveDark()

    // Re-applied whenever the theme flips: the system bars draw their icons light on a
    // dark board and dark on a light one, and calling this once in onCreate would freeze
    // them at whatever the theme was at launch.
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
        GameScreen(viewModel = viewModel, modifier = Modifier.fillMaxSize().testTag("app_root"))
    }
}

@Preview(showBackground = true)
@Composable
private fun KlondikeAppPreview() {
    KlondikeApp()
}
