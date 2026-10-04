package org.finiteplay.core.ui.layout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.finiteplay.core.ui.R
import org.finiteplay.core.ui.theme.LocalAppColors

/**
 * One open-source component an app ships, as the acknowledgements list states it. Names, owners and
 * licence names are proper nouns and stay as written in every language. The authoritative record is
 * `COPYRIGHT.txt`, which also governs what may be added (`AGENTS.md` "Licensing").
 */
data class OpenSourceNotice(val component: String, val owner: String, val license: String)

/**
 * What every FinitePlay app ships inside the app: the same AndroidX and Kotlin libraries, all
 * Apache License 2.0 (`COPYRIGHT.txt`). A dependency added to one app only is passed to
 * [AboutSettingsGroup] beside these.
 */
val SHARED_OPEN_SOURCE_NOTICES = listOf(
    OpenSourceNotice(
        component = "AndroidX Activity, Lifecycle, DataStore and Compose",
        owner = "The Android Open Source Project",
        license = "Apache License 2.0",
    ),
    OpenSourceNotice(
        component = "Kotlin standard library and kotlinx.coroutines",
        owner = "JetBrains s.r.o. and the Kotlin Programming Language contributors",
        license = "Apache License 2.0",
    ),
)

/**
 * The About group, last in Settings: the running build's version, the publisher's website, the
 * privacy policy, and the open-source acknowledgements in a dialog.
 *
 * The version is read from the package rather than compiled in: `buildConfig` is disabled in this
 * project, so `BuildConfig.VERSION_NAME` does not exist, and the package manager is the one source
 * that cannot drift from what was actually installed. The links open in the player's browser — the
 * app itself makes no network request.
 */
@Composable
fun AboutSettingsGroup(extraNotices: List<OpenSourceNotice> = emptyList()) {
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull()
            .orEmpty()
    }
    val uriHandler = LocalUriHandler.current
    var showAcknowledgements by remember { mutableStateOf(false) }

    SettingsGroup(stringResource(R.string.settings_group_about)) {
        InfoSettingRow(
            label = stringResource(R.string.setting_version),
            value = versionName,
            testTag = "settings_version",
        )
        val website = stringResource(R.string.company_website)
        LinkSettingRow(stringResource(R.string.about_website), website, "about_website") { uriHandler.openUri(website) }
        val privacy = stringResource(R.string.privacy_policy_url)
        LinkSettingRow(stringResource(R.string.about_privacy_policy), null, "about_privacy_policy") { uriHandler.openUri(privacy) }
        LinkSettingRow(stringResource(R.string.about_acknowledgements), null, "about_acknowledgements") { showAcknowledgements = true }
    }

    if (showAcknowledgements) {
        AcknowledgementsDialog(SHARED_OPEN_SOURCE_NOTICES + extraNotices) { showAcknowledgements = false }
    }
}

/** A tappable row that leads somewhere else: its label, an optional address beneath, drawn as a link. */
@Composable
private fun LinkSettingRow(label: String, detail: String?, testTag: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag(testTag),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = LocalAppColors.current.action.statistics,
            textDecoration = TextDecoration.Underline,
        )
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AcknowledgementsDialog(notices: List<OpenSourceNotice>, onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier.testTag("acknowledgements_dialog"),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_acknowledgements)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.acknowledgements_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                for (notice in notices) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                        Column {
                            Text(notice.component, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(notice.owner, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(notice.license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
