package org.finiteplay.freecell.ui.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.finiteplay.freecell.R

/**
 * Non-playable error state for a bundled catalog that fails verification
 * (`docs/games/freecell/UI_SPEC.md` "Screens and States" > "Unrecoverable"). FreeCell ships a
 * certified catalog from the start (`DEALS.md`), so there is no uncertified deal to fall back to
 * the way Spider's own FOUR-suit path has. Offers no gameplay affordance — no board, no action
 * bar, nothing but the diagnostic and a way to retry.
 *
 * [reason] should be [org.finiteplay.solitaire.catalog.catalog.CatalogLoadResult.Invalid.reason]
 * or an equivalent expected/actual version or hash description.
 */
@Composable
fun UnrecoverableScreen(reason: String, onRetry: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize().testTag("unrecoverable_screen"), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text(stringResource(R.string.unrecoverable_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.unrecoverable_body),
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(reason, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 16.dp).testTag("unrecoverable_reason"))
            TextButton(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.unrecoverable_retry))
            }
        }
    }
}
