package org.finiteplay.holdem

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.finiteplay.core.storage.AppLocale
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.core.ui.theme.resolveDark
import org.finiteplay.core.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    /** Applies the in-app language before any resource is resolved (see [AppLocale]). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The scaffold H3 replaces with the table.
        setContent {
            FinitePlayTheme(darkTheme = ThemeMode.SYSTEM.resolveDark()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.game_title))
                }
            }
        }
    }
}
