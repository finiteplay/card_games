package org.finiteplay.core.ui.feedback

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.finiteplay.core.ui.R
import java.util.Locale

/**
 * The feedback form: what the message is about, the message, and whether to add the build and device it comes
 * from. **Send** opens the player's email app with a draft to the support address (`feedback_email`); nothing is
 * sent until they send it from there, so the game itself makes no network request and asks for no permission
 * (`docs/PLATFORM.md` "Feedback and rating"). With no email app, the address is shown instead.
 *
 * It fills the screen, with Cancel and Send pinned along the bottom *above* the keyboard: a dialog that stays
 * a card is pushed under the status bar by the keyboard on a small phone, and its buttons end up behind it.
 *
 * [onDone] is called once the draft has been opened, or the form cancelled, and is where a caller closes it.
 */
@Composable
fun FeedbackDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    var category by rememberSaveable { mutableStateOf(FeedbackCategory.PROBLEM) }
    var message by rememberSaveable { mutableStateOf("") }
    var includeInfo by rememberSaveable { mutableStateOf(true) }
    val email = stringResource(R.string.feedback_email)
    val noEmailApp = stringResource(R.string.feedback_no_email_app, email)

    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize().testTag("feedback_dialog"), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    stringResource(R.string.feedback_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    for (option in FeedbackCategory.entries) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(selected = category == option, role = Role.RadioButton, onClick = { category = option })
                                .testTag("feedback_category_${option.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = category == option, onClick = null)
                            Text(stringResource(option.labelRes()), modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it.take(FEEDBACK_MAX_CHARS) },
                        label = { Text(stringResource(R.string.feedback_message_label)) },
                        minLines = 4,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("feedback_message"),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .padding(top = 4.dp)
                            .toggleable(value = includeInfo, role = Role.Checkbox, onValueChange = { includeInfo = it })
                            .testTag("feedback_include_info"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = includeInfo, onCheckedChange = null)
                        Text(stringResource(R.string.feedback_include_info), modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        stringResource(R.string.feedback_privacy_note, email),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                    TextButton(onClick = onDone, modifier = Modifier.testTag("feedback_cancel")) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(
                        enabled = canSendFeedback(message),
                        onClick = {
                            val info = if (includeInfo) deviceInfo(context) else null
                            val subject = feedbackSubject(appLabel(context), versionName(context), category)
                            if (!openEmailDraft(context, email, subject, feedbackBody(message, info))) {
                                Toast.makeText(context, noEmailApp, Toast.LENGTH_LONG).show()
                            }
                            onDone()
                        },
                        modifier = Modifier.testTag("feedback_send"),
                    ) { Text(stringResource(R.string.feedback_send)) }
                }
            }
        }
    }
}

private fun FeedbackCategory.labelRes(): Int = when (this) {
    FeedbackCategory.PROBLEM -> R.string.feedback_category_problem
    FeedbackCategory.IDEA -> R.string.feedback_category_idea
    FeedbackCategory.OTHER -> R.string.feedback_category_other
}

/**
 * A new email to [to]. `mailto:` with extras reaches only email apps, never a messenger or a file picker, and
 * needs no permission and no network from this app.
 */
internal fun feedbackMailIntent(to: String, subject: String, body: String): Intent =
    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

/** Opens the email app on a new message to [to]; false when the device has none. */
private fun openEmailDraft(context: Context, to: String, subject: String, body: String): Boolean =
    try {
        context.startActivity(feedbackMailIntent(to, subject, body))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

private fun appLabel(context: Context): String =
    runCatching { context.applicationInfo.loadLabel(context.packageManager).toString() }.getOrDefault(context.packageName)

private fun versionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

private fun deviceInfo(context: Context) = DeviceInfo(
    app = appLabel(context),
    versionName = versionName(context),
    androidRelease = Build.VERSION.RELEASE.orEmpty(),
    sdk = Build.VERSION.SDK_INT,
    device = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
    language = Locale.getDefault().toLanguageTag(),
)
