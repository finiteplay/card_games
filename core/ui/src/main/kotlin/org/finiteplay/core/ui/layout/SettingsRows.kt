package org.finiteplay.core.ui.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.finiteplay.core.ui.theme.LocalAppColors

/**
 * A titled card grouping related settings.
 *
 * Grouped rather than one flat list because a screenful of switches with no structure reads as a
 * wall, and the thing a grouping makes visible — which game a setting affects, this one or the
 * next — is exactly what a player needs to know before touching it.
 */
@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
                color = LocalAppColors.current.action.settings,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
            )
            content()
        }
    }
}

/** One on/off setting: label at the start, switch at the end. */
@Composable
fun SwitchSettingRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, testTag: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f, fill = false),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag).semantics { contentDescription = label },
        )
    }
}

/**
 * A read-only label/value row — for information the player cannot change here, like
 * [org.finiteplay.core.session.RestReminderWindow]'s own running total, alongside the setting
 * that configures it. Shares [DropdownSettingRow]'s label-at-start/value-at-end layout so the two
 * line up when they sit next to each other, but carries no click target of its own.
 */
@Composable
fun InfoSettingRow(label: String, value: String, testTag: String) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(testTag),
        )
    }
}

/**
 * A labelled dropdown for one mutually-exclusive setting — for the cases where a row of chips
 * would wrap into an unreadable block and a switch cannot express more than two states.
 */
@Composable
fun <T> DropdownSettingRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    testTag: String,
) {
    var expanded by remember { mutableStateOf(false) }
    // Label at the start, current value at the end, on one row — the pattern both iOS and Android
    // system settings use, and the one the switch rows beside it already follow. Stacking the
    // value under its label makes every second row break the alignment.
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Box {
            val selectedLabel = optionLabel(selected)
            TextButton(
                onClick = { expanded = true },
                modifier = Modifier.testTag(testTag).semantics { contentDescription = "$label: $selectedLabel" },
            ) {
                // The value gives way before the label does: a truncated setting name leaves the
                // row meaningless, while a truncated value is still recoverable by opening the
                // menu it labels.
                Text(
                    selectedLabel,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 168.dp),
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                for (option in options) {
                    val text = optionLabel(option)
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        },
                        modifier = Modifier.testTag("${testTag}_${optionTag(option)}"),
                    )
                }
            }
        }
    }
}

/** Stable per-option test tag: enum name where there is one, otherwise the value itself. */
private fun optionTag(option: Any?): String = when (option) {
    is Enum<*> -> option.name.lowercase()
    else -> option.toString().ifEmpty { "system" }.lowercase()
}
