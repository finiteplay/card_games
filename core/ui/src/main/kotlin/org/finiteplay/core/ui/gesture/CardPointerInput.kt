package org.finiteplay.core.ui.gesture

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Combined tap + drag recognition for one draggable card or pile.
 *
 * Drag starts once the pointer travels past touch slop, which [detectDragGestures] applies for
 * free; a tap that never travels that far reaches [onTap] instead. The two detectors sit in
 * sibling `pointerInput` blocks and do not race, because they disambiguate by *distance* rather
 * than by time.
 *
 * That distinction is the whole reason this exists as one shared modifier. Substituting
 * `detectDragGesturesAfterLongPress` here looks equivalent and is not: it disambiguates by
 * holding, so it consumes events a sibling tap detector is still watching, and the two cancel
 * each other. Requiring a hold instead — to dodge that conflict — makes an ordinary quick drag
 * (about 30 ms of contact) resolve as a tap, so cards simply refuse to move on a real phone.
 * Both mistakes have been made in this repo; neither is visible in a screenshot, and a test that
 * holds before moving passes against a gesture no player performs.
 *
 * Both `pointerInput` blocks are keyed on `Unit` so an unrelated recomposition — another pile's
 * feedback animation, a timer tick — never restarts a gesture already in flight;
 * [rememberUpdatedState] keeps the callbacks current without needing that restart.
 *
 * Callers must also keep the dragged card's node in composition for the life of the gesture.
 * Dropping it from a `forEachIndexed` while it is being dragged (rather than keeping the node
 * and hiding what it draws) destroys the very coroutine running this modifier, so neither
 * [onDragEnd] nor [onDragCancel] ever arrives and the drag payload is stranded.
 *
 * [onDragStart] receives the touch point in this node's own coordinates; translating that into
 * whatever frame the drop lookup uses is the caller's job, and mixing the two frames up silently
 * lands drops on the wrong pile.
 */
@Composable
fun Modifier.cardPointerInput(
    enabled: Boolean,
    onTap: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
): Modifier {
    if (!enabled) return this
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    return this
        .pointerInput(Unit) { detectTapGestures(onTap = { currentOnTap() }) }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset -> currentOnDragStart(offset) },
                onDrag = { change: PointerInputChange, amount: Offset ->
                    change.consume()
                    currentOnDrag(amount)
                },
                onDragEnd = { currentOnDragEnd() },
                onDragCancel = { currentOnDragCancel() },
            )
        }
}
