package org.finiteplay.spider.ui.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.finiteplay.core.ui.R as CoreR
import org.finiteplay.spider.R
import org.finiteplay.spider.layout.SuitCount

/**
 * The status row's suit-count picker (`docs/games/spider/UI_SPEC.md` "Status Row"), the same
 * shape as Klondike's own `LevelPickerDialog`: picking an option deals a new game at it
 * immediately (`SpiderViewModel.setSuitCount`), so there is no separate confirm step — only a
 * cancel, and, when the current deal has moves behind it, a warning about what selecting would
 * forfeit.
 */
@Composable
fun SuitCountPickerDialog(
    selected: SuitCount,
    forfeitsMoveCount: Int?,
    onSelect: (SuitCount) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag("suit_count_picker_dialog"),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_suit_count)) },
        text = {
            Column {
                if (forfeitsMoveCount != null) {
                    Text(
                        text = stringResource(R.string.suit_count_switch_forfeits, forfeitsMoveCount),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                for (option in SuitCount.entries) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == selected, onClick = { onSelect(option) })
                            .padding(vertical = 6.dp)
                            .testTag("suit_count_option_${option.name}"),
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(
                            text = stringResource(option.labelRes()),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.action_cancel)) } },
    )
}
