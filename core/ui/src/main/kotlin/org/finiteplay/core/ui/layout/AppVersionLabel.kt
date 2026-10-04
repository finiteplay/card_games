package org.finiteplay.core.ui.layout

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.finiteplay.core.ui.R

/**
 * The running build's version, last and quiet at the foot of Settings: the one thing a bug report
 * has to quote and nothing a player needs while playing.
 *
 * Read from the package rather than compiled in: `buildConfig` is disabled in this project, so
 * `BuildConfig.VERSION_NAME` does not exist, and the package manager is the one source that cannot
 * drift from what was actually installed.
 */
@Composable
fun AppVersionLabel() {
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull()
            .orEmpty()
    }
    Text(
        text = stringResource(R.string.settings_version, versionName),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp)
            .testTag("settings_version"),
        textAlign = TextAlign.Center,
    )
}
