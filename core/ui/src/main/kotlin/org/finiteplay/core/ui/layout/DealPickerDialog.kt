package org.finiteplay.core.ui.layout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.finiteplay.core.storage.DealStatus
import org.finiteplay.core.ui.R
import org.finiteplay.core.ui.theme.LocalAppColors

/** Which deals the picker lists: all of them, or only those the player has not touched / has played but not won / has won. */
private enum class DealFilter { ALL, NOT_PLAYED, PLAYED, WON }

private fun DealFilter.matches(status: DealStatus?): Boolean = when (this) {
    DealFilter.ALL -> true
    DealFilter.NOT_PLAYED -> status == null
    DealFilter.PLAYED -> status == DealStatus.PLAYED
    DealFilter.WON -> status == DealStatus.WON
}

/**
 * The list of a game's deals, each marked not played, played or won, opened from the deal number
 * on the status row. Picking one starts it.
 *
 * Deals are numbered 1..[dealCount] and [progress] holds the ones the player has touched; a number
 * absent from it is not played. The list opens scrolled to [currentNumber], and a filter row
 * narrows it for a catalog too long to scroll — "what have I not tried yet" is the question a
 * list this size exists to answer.
 *
 * Picking is the caller's to confirm: choosing a deal while one is in progress forfeits it, which
 * is a question for the same dialog New Game asks, not this one.
 */
@Composable
fun DealPickerDialog(
    dealCount: Int,
    currentNumber: Int?,
    progress: Map<Int, DealStatus>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var filter by remember { mutableStateOf(DealFilter.ALL) }
    val numbers = remember(filter, dealCount, progress) {
        if (filter == DealFilter.ALL) null else (1..dealCount).filter { filter.matches(progress[it]) }
    }
    val count = numbers?.size ?: dealCount
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = ((currentNumber ?: 1) - 3).coerceIn(0, (dealCount - 1).coerceAtLeast(0)),
    )

    AlertDialog(
        modifier = Modifier.testTag("deal_picker_dialog"),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.deal_picker_title)) },
        text = {
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                ) {
                    for (option in DealFilter.entries) {
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option },
                            label = { Text(stringResource(option.labelRes()), maxLines = 1) },
                            modifier = Modifier.testTag("deal_filter_${option.name.lowercase()}"),
                        )
                    }
                }
                if (count == 0) {
                    Text(
                        stringResource(R.string.deal_picker_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.heightIn(max = 360.dp).testTag("deal_picker_list"),
                    ) {
                        items(
                            count = count,
                            key = { numbers?.get(it) ?: (it + 1) },
                        ) { index ->
                            val number = numbers?.get(index) ?: (index + 1)
                            DealRow(number, progress[number], number == currentNumber) { onSelect(number) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun DealRow(number: Int, status: DealStatus?, isCurrent: Boolean, onClick: () -> Unit) {
    val (icon, tint) = status.icon()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .testTag("deal_row_$number"),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(end = 12.dp))
        Text(
            stringResource(R.string.deal_number, number),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (isCurrent) stringResource(R.string.deal_current) else stringResource(status.labelRes()),
            style = MaterialTheme.typography.labelLarge,
            color = if (isCurrent) LocalAppColors.current.action.statistics else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DealStatus?.icon(): Pair<ImageVector, Color> = when (this) {
    DealStatus.WON -> Icons.Filled.CheckCircle to LocalAppColors.current.action.new
    DealStatus.PLAYED -> Icons.Filled.PlayCircleOutline to LocalAppColors.current.action.hint
    null -> Icons.Filled.RadioButtonUnchecked to MaterialTheme.colorScheme.outline
}

private fun DealStatus?.labelRes(): Int = when (this) {
    DealStatus.WON -> R.string.deal_status_won
    DealStatus.PLAYED -> R.string.deal_status_played
    null -> R.string.deal_status_not_played
}

private fun DealFilter.labelRes(): Int = when (this) {
    DealFilter.ALL -> R.string.deal_filter_all
    DealFilter.NOT_PLAYED -> R.string.deal_status_not_played
    DealFilter.PLAYED -> R.string.deal_status_played
    DealFilter.WON -> R.string.deal_status_won
}
