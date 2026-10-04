package org.finiteplay.klondike.ui.game

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Mirrors the system "Remove animations" accessibility setting, which Android reports
 * by zeroing the animator duration scale — one of two independent sources that trigger
 * the app's own skip-animations behavior (`GameScreen.kt`: `rememberReducedMotion() ||
 * !viewModel.persistedAnimationsEnabled`), the other being the in-app Enable Animation
 * toggle turned off.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
