package de.visualtasker.blockeditor.compose.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import de.visualtasker.blockeditor.domain.Offset2
import kotlin.math.hypot

@Composable
fun Modifier.workspacePointerGestures(
    onTap: (Offset2) -> Unit,
    onDoubleTap: (Offset2) -> Unit,
    onLongPressDragStart: (Offset2) -> Boolean,
    onDrag: (Offset2) -> Unit,
    onDragEnd: (Offset2) -> Unit,
    onDragCancel: () -> Unit = {},
    onBlockDragActiveChange: (Boolean) -> Unit = {},
): Modifier {
    val viewConfiguration = LocalViewConfiguration.current
    val onTapState = rememberUpdatedState(onTap)
    val onDoubleTapState = rememberUpdatedState(onDoubleTap)
    val onLongPressDragStartState = rememberUpdatedState(onLongPressDragStart)
    val onDragState = rememberUpdatedState(onDrag)
    val onDragEndState = rememberUpdatedState(onDragEnd)
    val onDragCancelState = rememberUpdatedState(onDragCancel)
    val onBlockDragActiveChangeState = rememberUpdatedState(onBlockDragActiveChange)

    val longPressTimeout = (viewConfiguration.longPressTimeoutMillis / 2).coerceAtLeast(1L)
    val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
    val touchSlop = viewConfiguration.touchSlop

    return pointerInput(longPressTimeout, doubleTapTimeout, touchSlop) {
        var lastTapTime = 0L
        var lastTapPoint: Offset2? = null

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val pointerId = down.id
            val start = Offset2(down.position.x, down.position.y)
            val pressDeadline = System.currentTimeMillis() + longPressTimeout
            var longPressReady = false
            var exceededSlop = false
            var dragStarted = false

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break

                if (!change.pressed) {
                    if (!dragStarted && !exceededSlop) {
                        val now = System.currentTimeMillis()
                        val previous = lastTapPoint
                        val isDoubleTap = previous != null &&
                            now - lastTapTime <= doubleTapTimeout &&
                            distance(start, previous) <= touchSlop
                        if (isDoubleTap) {
                            onDoubleTapState.value(start)
                            lastTapTime = 0L
                            lastTapPoint = null
                        } else {
                            onTapState.value(start)
                            lastTapTime = now
                            lastTapPoint = start
                        }
                    }
                    break
                }

                if (!longPressReady && !dragStarted) {
                    if (distance(start, Offset2(change.position.x, change.position.y)) > touchSlop) {
                        exceededSlop = true
                        break
                    }
                    if (System.currentTimeMillis() >= pressDeadline) {
                        longPressReady = true
                        if (onLongPressDragStartState.value(start)) {
                            dragStarted = true
                            onBlockDragActiveChangeState.value(true)
                            var latest = start
                            var completed = false
                            try {
                                drag(pointerId) { dragChange ->
                                    dragChange.consume()
                                    latest = Offset2(dragChange.position.x, dragChange.position.y)
                                    onDragState.value(latest)
                                }
                                onDragEndState.value(latest)
                                completed = true
                            } finally {
                                onBlockDragActiveChangeState.value(false)
                                if (!completed) {
                                    onDragCancelState.value()
                                }
                            }
                        }
                        break
                    }
                }
            }
        }
    }
}

private fun distance(a: Offset2, b: Offset2): Float =
    hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()
