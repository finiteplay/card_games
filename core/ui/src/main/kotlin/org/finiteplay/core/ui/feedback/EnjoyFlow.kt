package org.finiteplay.core.ui.feedback

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.finiteplay.core.ui.R

private enum class EnjoyStep { ASK, FEEDBACK }

/**
 * "Enjoying this game?" (`docs/PLATFORM.md` "Feedback and rating"). **Yes** opens the game's Google Play page to rate
 * it; **Not really** opens the feedback form. Tapping outside the dialog or going Back is neither: it is asked again
 * later, not recorded as an answer.
 *
 * [onFinished] is called once with whether the player answered, and is where a caller records that and closes the
 * flow. The caller decides when the question is due (`core/session`'s `shouldAskEnjoy`) and shows this only when no
 * other dialog is up.
 */
@Composable
fun EnjoyFlow(onFinished: (answered: Boolean) -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(EnjoyStep.ASK) }
    when (step) {
        EnjoyStep.ASK -> AlertDialog(
            modifier = Modifier.testTag("enjoy_dialog"),
            onDismissRequest = { onFinished(false) },
            title = { Text(stringResource(R.string.enjoy_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    BeggingCat(Modifier.testTag("enjoy_illustration"))
                    Spacer(Modifier.height(28.dp))
                    Text(stringResource(R.string.enjoy_body))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.enjoy_plea), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        openStoreListing(context)
                        onFinished(true)
                    },
                    modifier = Modifier.testTag("enjoy_yes"),
                ) { Text(stringResource(R.string.enjoy_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { step = EnjoyStep.FEEDBACK }, modifier = Modifier.testTag("enjoy_no")) {
                    Text(stringResource(R.string.enjoy_no))
                }
            },
        )
        EnjoyStep.FEEDBACK -> FeedbackDialog(onDone = { onFinished(true) })
    }
}

/** The Google Play id of the app running as [packageName]: debug builds are installed as `<id>.dev`, and the page to rate is the release app's. */
internal fun storePackageId(packageName: String): String = packageName.removeSuffix(".dev")

/** This app's page on Google Play: in the Play app when there is one, otherwise in the browser. */
fun openStoreListing(context: Context) {
    val id = storePackageId(context.packageName)
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(market)
    } catch (e: ActivityNotFoundException) {
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(web) }
    }
}
