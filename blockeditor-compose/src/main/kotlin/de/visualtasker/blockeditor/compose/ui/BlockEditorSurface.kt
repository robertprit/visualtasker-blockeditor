package de.visualtasker.blockeditor.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import de.visualtasker.blockeditor.compose.layers.EditorCanvasLayer
import de.visualtasker.blockeditor.compose.layers.EditorChromeLayer
import de.visualtasker.blockeditor.compose.theme.defaultBlockEditorColors
import de.visualtasker.blockeditor.compose.viewmodel.DragRenderState
import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.Offset2
import de.visualtasker.blockeditor.domain.WorkspaceAction
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.interaction.ViewportState
import de.visualtasker.blockeditor.layout.LayoutCache
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun BlockEditorSurface(
    document: WorkspaceDocument,
    layoutCache: LayoutCache,
    viewport: ViewportState,
    dragRender: DragRenderState?,
    selectedBlockIds: Set<BlockId> = emptySet(),
    onAction: (WorkspaceAction) -> Unit,
    onClearWorkspace: () -> Unit,
    onViewportChange: (ViewportState) -> Unit,
    onCanvasSizeChange: (Offset2) -> Unit,
    onTap: (Offset2) -> Unit,
    onDoubleTap: (Offset2) -> Unit,
    onLongPressDragStart: (Offset2) -> Boolean,
    onPointerMove: (Offset2) -> Unit,
    onPointerUp: (Offset2) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = defaultBlockEditorColors()
    val onTapState = rememberUpdatedState(onTap)
    val onDoubleTapState = rememberUpdatedState(onDoubleTap)
    val onLongPressDragStartState = rememberUpdatedState(onLongPressDragStart)
    val onMove = rememberUpdatedState(onPointerMove)
    val onUp = rememberUpdatedState(onPointerUp)
    val onViewport = rememberUpdatedState(onViewportChange)
    val onCanvasSize = rememberUpdatedState(onCanvasSizeChange)
    val viewportState = rememberUpdatedState(viewport)
    var blockDragActive by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        EditorChromeLayer(
            onAction = onAction,
            onClearWorkspace = onClearWorkspace,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp)
                .background(colors.workspaceBackground)
                .onSizeChanged { size ->
                    onCanvasSize.value(Offset2(size.width.toFloat(), size.height.toFloat()))
                }
                .pointerInput(Unit) {
                    coroutineScope {
                        launch {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                if (blockDragActive) return@detectTransformGestures
                                val vp = viewportState.value
                                onViewport.value(
                                    vp.withTransform(
                                        centroid = Offset2(centroid.x, centroid.y),
                                        panDelta = Offset2(pan.x, pan.y),
                                        zoomFactor = zoom,
                                    ),
                                )
                            }
                        }
                    }
                }
                .workspacePointerGestures(
                    onTap = { onTapState.value(it) },
                    onDoubleTap = { onDoubleTapState.value(it) },
                    onLongPressDragStart = { onLongPressDragStartState.value(it) },
                    onDrag = { onMove.value(it) },
                    onDragEnd = { onUp.value(it) },
                    onBlockDragActiveChange = { blockDragActive = it },
                ),
        ) {
            EditorCanvasLayer(
                document = document,
                layoutCache = layoutCache,
                viewport = viewport,
                dragRender = dragRender,
                selectedBlockIds = selectedBlockIds,
            )
        }
    }
}
