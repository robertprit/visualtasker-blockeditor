@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package de.visualtasker.blockeditor.compose.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import de.visualtasker.blockeditor.compose.layers.EditorCanvasLayer
import de.visualtasker.blockeditor.compose.host.BlockPaletteInsertMode
import de.visualtasker.blockeditor.compose.render.BlockVisualPathProvider
import de.visualtasker.blockeditor.compose.theme.defaultBlockEditorColors
import de.visualtasker.blockeditor.compose.viewmodel.BlockInfoSnapshot
import de.visualtasker.blockeditor.compose.viewmodel.BlockContextMenuRequest
import de.visualtasker.blockeditor.compose.viewmodel.DragRenderState
import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.Offset2
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.WorkspaceGraph
import de.visualtasker.blockeditor.interaction.ViewportState
import de.visualtasker.blockeditor.layout.LayoutCache
import de.visualtasker.blockeditor.registry.BlockDefinition
import de.visualtasker.blockeditor.registry.BlockDesignBlueprint
import de.visualtasker.blockeditor.registry.BlockCategories
import de.visualtasker.blockeditor.registry.BlockRegistry
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal val BlockEditorToolbarTouchTargetDp = 48.dp
internal val BlockEditorTrashDropTargetSizeDp = 96.dp

@Composable
fun BlockEditorScaffold(
    document: WorkspaceDocument,
    layoutCache: LayoutCache,
    registry: BlockRegistry = DefaultBlockRegistry,
    viewport: ViewportState,
    dragRender: DragRenderState?,
    selectedBlockIds: Set<BlockId>,
    selectedBlockCollapsed: Boolean = false,
    canToggleSelectedBlockCollapse: Boolean = false,
    blockContextMenuRequest: BlockContextMenuRequest? = null,
    codePreview: String,
    blockInfo: BlockInfoSnapshot?,
    showBottomPanel: Boolean,
    showFloatingInspector: Boolean = false,
    showToolbox: Boolean = true,
    expandedCategory: String?,
    definitionsForCategory: List<BlockDefinition>,
    showBlockFactory: Boolean,
    onCategoryClick: (String) -> Unit,
    onDismissCategory: () -> Unit,
    onAddBlock: (BlockDefinition) -> Unit,
    onCreateVariable: (String, String) -> Unit,
    onToggleBottomPanel: () -> Unit,
    onCloseTopMostPanel: () -> Boolean = { false },
    onOpenBlockFactory: () -> Unit,
    onDismissBlockFactory: () -> Unit,
    onCreateCustomBlock: (BlockDesignBlueprint) -> Unit,
    onClearWorkspace: () -> Unit,
    showBottomPanelToggle: Boolean = true,
    showBlockFactoryEntry: Boolean = true,
    gridEnabled: Boolean = true,
    showMiniMap: Boolean = true,
    showTopIconBar: Boolean = true,
    paletteInsertMode: BlockPaletteInsertMode = BlockPaletteInsertMode.TapToAdd,
    extraCategories: List<BlockCategories.CategoryMeta> = emptyList(),
    onFitWorkspace: () -> Unit,
    onAutoArrangeWorkspace: () -> Unit,
    onSaveWorkspace: (() -> Unit)? = null,
    onUndo: () -> Boolean,
    onRedo: () -> Boolean,
    onToggleSelectedBlockCollapse: () -> Boolean = { false },
    onDismissBlockContextMenu: () -> Unit = {},
    onToggleSelectedBlockActive: () -> Boolean = { false },
    onReplaceSelectedBlockType: (String) -> Boolean = { false },
    onAddSelectedIfBranch: () -> Boolean = { false },
    onRemoveSelectedIfBranch: () -> Boolean = { false },
    onUpdateBlockNote: (String) -> Boolean = { false },
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onDeleteSelectedBlock: () -> Boolean,
    onViewportChange: (ViewportState) -> Unit,
    onCanvasSizeChange: (Offset2) -> Unit,
    onTap: (Offset2) -> Unit,
    onDoubleTap: (Offset2) -> Unit,
    onLongPressDragStart: (Offset2) -> Boolean,
    onPointerMove: (Offset2) -> Unit,
    onPointerUp: (Offset2) -> Unit,
    onPointerCancel: () -> Unit = {},
    onFieldChange: (String, String) -> Unit,
    onFieldSourceChange: (String, String) -> Unit = { _, _ -> },
    onSetReporterVisualMode: (de.visualtasker.blockeditor.compose.model.ReporterVisualMode) -> Unit = {},
    modifier: Modifier = Modifier,
    soundEffectsEnabled: Boolean = false,
    hapticFeedbackEnabled: Boolean = false,
) {
    BlockEditorScaffold(
        document = document,
        layoutCache = layoutCache,
        registry = registry,
        viewport = viewport,
        dragRender = dragRender,
        selectedBlockIds = selectedBlockIds,
        selectedBlockCollapsed = selectedBlockCollapsed,
        canToggleSelectedBlockCollapse = canToggleSelectedBlockCollapse,
        blockContextMenuRequest = blockContextMenuRequest,
        codePreview = codePreview,
        blockInfo = blockInfo,
        showBottomPanel = showBottomPanel,
        showFloatingInspector = showFloatingInspector,
        showToolbox = showToolbox,
        expandedCategory = expandedCategory,
        definitionsForCategory = definitionsForCategory,
        showBlockFactory = showBlockFactory,
        onCategoryClick = onCategoryClick,
        onDismissCategory = onDismissCategory,
        onAddBlock = onAddBlock,
        onCreateVariable = onCreateVariable,
        onToggleBottomPanel = onToggleBottomPanel,
        onCloseTopMostPanel = onCloseTopMostPanel,
        onOpenBlockFactory = onOpenBlockFactory,
        onDismissBlockFactory = onDismissBlockFactory,
        onCreateCustomBlock = onCreateCustomBlock,
        onClearWorkspace = onClearWorkspace,
        showBottomPanelToggle = showBottomPanelToggle,
        showBlockFactoryEntry = showBlockFactoryEntry,
        gridEnabled = gridEnabled,
        showMiniMap = showMiniMap,
        showTopIconBar = showTopIconBar,
        paletteInsertMode = paletteInsertMode,
        extraCategories = extraCategories,
        onFitWorkspace = onFitWorkspace,
        onAutoArrangeWorkspace = onAutoArrangeWorkspace,
        onSaveWorkspace = onSaveWorkspace,
        onUndo = onUndo,
        onRedo = onRedo,
        onToggleSelectedBlockCollapse = onToggleSelectedBlockCollapse,
        onDismissBlockContextMenu = onDismissBlockContextMenu,
        onToggleSelectedBlockActive = onToggleSelectedBlockActive,
        onReplaceSelectedBlockType = onReplaceSelectedBlockType,
        onAddSelectedIfBranch = onAddSelectedIfBranch,
        onRemoveSelectedIfBranch = onRemoveSelectedIfBranch,
        onUpdateBlockNote = onUpdateBlockNote,
        onZoomIn = onZoomIn,
        onZoomOut = onZoomOut,
        onDeleteSelectedBlock = onDeleteSelectedBlock,
        onViewportChange = onViewportChange,
        onCanvasSizeChange = onCanvasSizeChange,
        onTap = onTap,
        onDoubleTap = onDoubleTap,
        onLongPressDragStart = onLongPressDragStart,
        onPointerMove = onPointerMove,
        onPointerUp = onPointerUp,
        onPointerCancel = onPointerCancel,
        onFieldChange = onFieldChange,
        onFieldSourceChange = onFieldSourceChange,
        onSetReporterVisualMode = onSetReporterVisualMode,
        modifier = modifier,
        soundEffectsEnabled = soundEffectsEnabled,
        hapticFeedbackEnabled = hapticFeedbackEnabled,
        visualPathProvider = BlockVisualPathProvider.Legacy,
    )
}

@Composable
fun BlockEditorScaffold(
    document: WorkspaceDocument,
    layoutCache: LayoutCache,
    registry: BlockRegistry = DefaultBlockRegistry,
    viewport: ViewportState,
    dragRender: DragRenderState?,
    selectedBlockIds: Set<BlockId>,
    selectedBlockCollapsed: Boolean = false,
    canToggleSelectedBlockCollapse: Boolean = false,
    blockContextMenuRequest: BlockContextMenuRequest? = null,
    codePreview: String,
    blockInfo: BlockInfoSnapshot?,
    showBottomPanel: Boolean,
    showFloatingInspector: Boolean = false,
    showToolbox: Boolean = true,
    expandedCategory: String?,
    definitionsForCategory: List<BlockDefinition>,
    showBlockFactory: Boolean,
    onCategoryClick: (String) -> Unit,
    onDismissCategory: () -> Unit,
    onAddBlock: (BlockDefinition) -> Unit,
    onCreateVariable: (String, String) -> Unit,
    onToggleBottomPanel: () -> Unit,
    onCloseTopMostPanel: () -> Boolean = { false },
    onOpenBlockFactory: () -> Unit,
    onDismissBlockFactory: () -> Unit,
    onCreateCustomBlock: (BlockDesignBlueprint) -> Unit,
    onClearWorkspace: () -> Unit,
    showBottomPanelToggle: Boolean = true,
    showBlockFactoryEntry: Boolean = true,
    gridEnabled: Boolean = true,
    paletteInsertMode: BlockPaletteInsertMode = BlockPaletteInsertMode.TapToAdd,
    extraCategories: List<BlockCategories.CategoryMeta> = emptyList(),
    showTopIconBar: Boolean = true,
    onFitWorkspace: () -> Unit,
    onAutoArrangeWorkspace: () -> Unit,
    onSaveWorkspace: (() -> Unit)? = null,
    onUndo: () -> Boolean,
    onRedo: () -> Boolean,
    onToggleSelectedBlockCollapse: () -> Boolean = { false },
    onDismissBlockContextMenu: () -> Unit = {},
    onToggleSelectedBlockActive: () -> Boolean = { false },
    onReplaceSelectedBlockType: (String) -> Boolean = { false },
    onAddSelectedIfBranch: () -> Boolean = { false },
    onRemoveSelectedIfBranch: () -> Boolean = { false },
    onUpdateBlockNote: (String) -> Boolean = { false },
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onDeleteSelectedBlock: () -> Boolean,
    onViewportChange: (ViewportState) -> Unit,
    onCanvasSizeChange: (Offset2) -> Unit,
    onTap: (Offset2) -> Unit,
    onDoubleTap: (Offset2) -> Unit,
    onLongPressDragStart: (Offset2) -> Boolean,
    onPointerMove: (Offset2) -> Unit,
    onPointerUp: (Offset2) -> Unit,
    onPointerCancel: () -> Unit = {},
    onFieldChange: (String, String) -> Unit,
    onFieldSourceChange: (String, String) -> Unit = { _, _ -> },
    onSetReporterVisualMode: (de.visualtasker.blockeditor.compose.model.ReporterVisualMode) -> Unit = {},
    modifier: Modifier = Modifier,
    soundEffectsEnabled: Boolean = false,
    hapticFeedbackEnabled: Boolean = false,
    showMiniMap: Boolean = true,
    visualPathProvider: BlockVisualPathProvider,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = defaultBlockEditorColors().copy(
        event = scheme.primaryContainer,
        action = scheme.secondaryContainer,
        control = scheme.tertiaryContainer,
        logic = scheme.tertiaryContainer,
        debug = scheme.errorContainer,
        variable = scheme.surfaceContainerHighest,
        workspaceBackground = scheme.surfaceContainerLowest,
        gridDot = scheme.outlineVariant.copy(alpha = 0.42f),
        snapHighlight = scheme.primary.copy(alpha = 0.35f),
        blockStroke = scheme.outline,
        blockText = scheme.onSurface,
        slotBackground = scheme.surfaceContainerHigh.copy(alpha = 0.65f),
        unsupportedFill = scheme.errorContainer,
        unsupportedStroke = scheme.error,
        unsupportedText = scheme.onErrorContainer,
    )
    val onTapState = rememberUpdatedState(onTap)
    val onDoubleTapState = rememberUpdatedState(onDoubleTap)
    val onLongPressDragStartState = rememberUpdatedState(onLongPressDragStart)
    val onMove = rememberUpdatedState(onPointerMove)
    val onUp = rememberUpdatedState(onPointerUp)
    val onCancel = rememberUpdatedState(onPointerCancel)
    val onViewport = rememberUpdatedState(onViewportChange)
    val onCanvasSize = rememberUpdatedState(onCanvasSizeChange)
    val viewportState = rememberUpdatedState(viewport)
    val platformView = LocalView.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val trashSizePx = with(density) { BlockEditorTrashDropTargetSizeDp.toPx() }
    val trashMarginPx = with(density) { 16.dp.toPx() }
    var blockDragActive by remember { mutableStateOf(false) }
    var latestDragPoint by remember { mutableStateOf<Offset2?>(null) }
    var canvasSize by remember { mutableStateOf(Offset2(0f, 0f)) }
    val gridVisible = gridEnabled
    val deleteCandidate = blockDragActive &&
        latestDragPoint?.let { isInTrashZone(it, canvasSize, trashSizePx, trashMarginPx) } == true
    var previousSnapTarget by remember { mutableStateOf<String?>(null) }

    val workspaceOutlineColor = scheme.outlineVariant.copy(alpha = 0.55f)
    val workspaceShape = RoundedCornerShape(30.dp)
    BackHandler(
        enabled = showBlockFactory || expandedCategory != null || showBottomPanel,
    ) {
        onCloseTopMostPanel()
    }

    val currentSnapTarget = dragRender?.snapCandidate?.targetConnectionId?.value
    LaunchedEffect(blockDragActive, currentSnapTarget) {
        val previous = previousSnapTarget
        when {
            !blockDragActive -> previousSnapTarget = null
            previous == null && currentSnapTarget != null -> {
                playEditorFeedback(platformView, haptic, BlockEditorFeedbackEvent.SnapEntered, soundEffectsEnabled, hapticFeedbackEnabled)
                previousSnapTarget = currentSnapTarget
            }
            previous != null && currentSnapTarget == null -> {
                playEditorFeedback(platformView, haptic, BlockEditorFeedbackEvent.SnapLost, soundEffectsEnabled, hapticFeedbackEnabled)
                previousSnapTarget = null
            }
            previous != null && currentSnapTarget != null && previous != currentSnapTarget -> {
                playEditorFeedback(platformView, haptic, BlockEditorFeedbackEvent.SnapChanged, soundEffectsEnabled, hapticFeedbackEnabled)
                previousSnapTarget = currentSnapTarget
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(top = 6.dp, end = 6.dp, bottom = 6.dp),
                ) {
                    Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(workspaceShape)
                        .background(colors.workspaceBackground)
                        .border(
                            width = 1.dp,
                            color = workspaceOutlineColor,
                            shape = workspaceShape,
                        )
                        .onSizeChanged { size ->
                            val nextSize = Offset2(size.width.toFloat(), size.height.toFloat())
                            if (!sameCanvasSize(canvasSize, nextSize)) {
                                canvasSize = nextSize
                                onCanvasSize.value(canvasSize)
                            }
                        }
                        .pointerInput(Unit) {
                            coroutineScope {
                                launch {
                                    detectTransformGestures { centroid, pan, zoom, _ ->
                                        Log.d(
                                            BLOCK_SCAFFOLD_LOG_TAG,
                                            "transform blockDragActive=$blockDragActive centroid=${centroid.x},${centroid.y} pan=${pan.x},${pan.y} zoom=$zoom"
                                        )
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
                            onLongPressDragStart = {
                                latestDragPoint = it
                                onLongPressDragStartState.value(it).also { started ->
                                    if (started) {
                                        playEditorFeedback(
                                            platformView = platformView,
                                            haptic = haptic,
                                            event = BlockEditorFeedbackEvent.DragStarted,
                                            soundEnabled = soundEffectsEnabled,
                                            hapticEnabled = hapticFeedbackEnabled,
                                        )
                                    }
                                }
                            },
                            onDrag = {
                                latestDragPoint = it
                                onMove.value(it)
                                autoPanViewportIfNeeded(
                                    point = it,
                                    canvasSize = canvasSize,
                                    viewport = viewportState.value,
                                    onViewportChange = onViewport.value,
                                )
                            },
                            onDragEnd = {
                                latestDragPoint = it
                                val deleteByTrash = isInTrashZone(it, canvasSize, trashSizePx, trashMarginPx)
                                if (deleteByTrash) {
                                    if (onDeleteSelectedBlock()) {
                                        playEditorFeedback(platformView, haptic, BlockEditorFeedbackEvent.Deleted, soundEffectsEnabled, hapticFeedbackEnabled)
                                    }
                                } else {
                                    val hadSnapCandidate = dragRender?.snapCandidate != null
                                    val hadAttachment = dragRender?.session?.rootBlockId?.let { rootBlockId ->
                                        WorkspaceGraph.isValuePlugged(document, rootBlockId) ||
                                            WorkspaceGraph.previousChain(document, rootBlockId) != null ||
                                            WorkspaceGraph.nextChain(document, rootBlockId) != null ||
                                            WorkspaceGraph.slotContaining(document, rootBlockId) != null
                                    } == true
                                    onUp.value(it)
                                    playEditorFeedback(
                                        platformView,
                                        haptic,
                                        when {
                                            hadSnapCandidate -> BlockEditorFeedbackEvent.Docked
                                            hadAttachment -> BlockEditorFeedbackEvent.Undocked
                                            else -> BlockEditorFeedbackEvent.Dropped
                                        },
                                        soundEffectsEnabled,
                                        hapticFeedbackEnabled,
                                    )
                                }
                            },
                            onDragCancel = {
                                onCancel.value()
                            },
                            onBlockDragActiveChange = {
                                blockDragActive = it
                                if (!it) latestDragPoint = null
                            },
                        ),
                    ) {
                    EditorCanvasLayer(
                        document = document,
                        layoutCache = layoutCache,
                        registry = registry,
                        viewport = viewport,
                        dragRender = dragRender,
                        selectedBlockIds = selectedBlockIds,
                        colors = colors,
                        gridVisible = gridVisible,
                        visualPathProvider = visualPathProvider,
                    )
                    if (showTopIconBar) {
                    BlockEditorIconBar(
                        selectedBlockAvailable = selectedBlockIds.isNotEmpty(),
                        selectedBlockCollapsed = selectedBlockCollapsed,
                        canToggleSelectedBlockCollapse = canToggleSelectedBlockCollapse,
                        onFitWorkspace = {
                            Log.d(BLOCK_SCAFFOLD_LOG_TAG, "toolbar fitWorkspace")
                            onFitWorkspace()
                            playEditorFeedback(platformView, haptic, BlockEditorFeedbackEvent.Command, soundEffectsEnabled, hapticFeedbackEnabled)
                        },
                        onAutoArrangeWorkspace = {
                            Log.d(BLOCK_SCAFFOLD_LOG_TAG, "toolbar autoArrange")
                            onAutoArrangeWorkspace()
                            playEditorFeedback(platformView, haptic, BlockEditorFeedbackEvent.Command, soundEffectsEnabled, hapticFeedbackEnabled)
                        },
                        onSaveWorkspace = onSaveWorkspace?.let { save ->
                            {
                                save()
                                playEditorFeedback(platformView, haptic, BlockEditorFeedbackEvent.Command, soundEffectsEnabled, hapticFeedbackEnabled)
                            }
                        },
                        onOpenBlockFactory = {
                            onOpenBlockFactory()
                            playEditorFeedback(platformView, haptic, BlockEditorFeedbackEvent.Command, soundEffectsEnabled, hapticFeedbackEnabled)
                        },
                        onClearWorkspace = {
                            onClearWorkspace()
                            playEditorFeedback(platformView, haptic, BlockEditorFeedbackEvent.Deleted, soundEffectsEnabled, hapticFeedbackEnabled)
                        },
                        showBlockFactoryEntry = showBlockFactoryEntry,
                        onUndo = {
                            onUndo().also { changed ->
                                if (changed) {
                                    playEditorFeedback(platformView, haptic, BlockEditorFeedbackEvent.Command, soundEffectsEnabled, hapticFeedbackEnabled)
                                }
                            }
                        },
                        onRedo = {
                            onRedo().also { changed ->
                                if (changed) {
                                    playEditorFeedback(platformView, haptic, BlockEditorFeedbackEvent.Command, soundEffectsEnabled, hapticFeedbackEnabled)
                                }
                            }
                        },
                        onToggleSelectedBlockCollapse = {
                            onToggleSelectedBlockCollapse().also { changed ->
                                if (changed) {
                                    playEditorFeedback(
                                        platformView,
                                        haptic,
                                        if (selectedBlockCollapsed) BlockEditorFeedbackEvent.Expanded else BlockEditorFeedbackEvent.Collapsed,
                                        soundEffectsEnabled,
                                        hapticFeedbackEnabled,
                                    )
                                }
                            }
                        },
                        onZoomIn = {
                            Log.d(BLOCK_SCAFFOLD_LOG_TAG, "toolbar zoomIn")
                            onZoomIn()
                            playEditorFeedback(platformView, haptic, BlockEditorFeedbackEvent.Command, soundEffectsEnabled, hapticFeedbackEnabled)
                        },
                        onZoomOut = {
                            Log.d(BLOCK_SCAFFOLD_LOG_TAG, "toolbar zoomOut")
                            onZoomOut()
                            playEditorFeedback(platformView, haptic, BlockEditorFeedbackEvent.Command, soundEffectsEnabled, hapticFeedbackEnabled)
                        },
                        onDeleteSelectedBlock = {
                            if (onDeleteSelectedBlock()) {
                                playEditorFeedback(platformView, haptic, BlockEditorFeedbackEvent.Deleted, soundEffectsEnabled, hapticFeedbackEnabled)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                    )
                    }
                    TrashDropTarget(
                        active = deleteCandidate,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                    )
                    FloatingViewportControls(
                        onZoomIn = {
                            onZoomIn()
                            playEditorFeedback(platformView, haptic, BlockEditorFeedbackEvent.Command, soundEffectsEnabled, hapticFeedbackEnabled)
                        },
                        onZoomOut = {
                            onZoomOut()
                            playEditorFeedback(platformView, haptic, BlockEditorFeedbackEvent.Command, soundEffectsEnabled, hapticFeedbackEnabled)
                        },
                        onFitWorkspace = {
                            onFitWorkspace()
                            playEditorFeedback(platformView, haptic, BlockEditorFeedbackEvent.Command, soundEffectsEnabled, hapticFeedbackEnabled)
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 30.dp, bottom = 120.dp),
                    )
                    if (showMiniMap) {
                        BlockEditorMiniMap(
                            layoutCache = layoutCache,
                            viewport = viewport,
                            canvasSize = canvasSize,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 62.dp, end = 14.dp),
                        )
                    }
                    BlockContextDropdown(
                        request = blockContextMenuRequest,
                        blockInfo = blockInfo,
                        onDismiss = onDismissBlockContextMenu,
                        onToggleActive = {
                            onToggleSelectedBlockActive().also { changed ->
                                if (changed) {
                                    playEditorFeedback(platformView, haptic, BlockEditorFeedbackEvent.Command, soundEffectsEnabled, hapticFeedbackEnabled)
                                }
                            }
                        },
                        onToggleCollapse = {
                            onToggleSelectedBlockCollapse().also { changed ->
                                if (changed) {
                                    playEditorFeedback(
                                        platformView,
                                        haptic,
                                        if (selectedBlockCollapsed) BlockEditorFeedbackEvent.Expanded else BlockEditorFeedbackEvent.Collapsed,
                                        soundEffectsEnabled,
                                        hapticFeedbackEnabled,
                                    )
                                }
                            }
                        },
                        onAddBranch = {
                            onAddSelectedIfBranch().also { changed ->
                                if (changed) {
                                    playEditorFeedback(platformView, haptic, BlockEditorFeedbackEvent.Command, soundEffectsEnabled, hapticFeedbackEnabled)
                                }
                            }
                        },
	                        onRemoveBranch = {
	                            onRemoveSelectedIfBranch().also { changed ->
	                                if (changed) {
	                                    playEditorFeedback(platformView, haptic, BlockEditorFeedbackEvent.Command, soundEffectsEnabled, hapticFeedbackEnabled)
	                                }
	                            }
	                        },
	                    )
	                    if (showFloatingInspector && blockInfo != null) {
	                        BlockEditorInspectorBottomSheet(
	                            blockInfo = blockInfo,
	                            onFieldChange = onFieldChange,
	                            onFieldSourceChange = onFieldSourceChange,
	                            onSetReporterVisualMode = onSetReporterVisualMode,
	                            onToggleBlockActive = onToggleSelectedBlockActive,
	                            onToggleBlockCollapse = onToggleSelectedBlockCollapse,
	                            onReplaceBlockType = onReplaceSelectedBlockType,
	                            onAddBranch = onAddSelectedIfBranch,
	                            onRemoveBranch = onRemoveSelectedIfBranch,
	                            onUpdateBlockNote = onUpdateBlockNote,
	                            modifier = Modifier
	                                .align(Alignment.BottomCenter)
	                                .padding(horizontal = 8.dp, vertical = 8.dp),
	                        )
	                    }
	                }

                    if (showBottomPanel) {
                        EditorBottomPanel(
                            code = codePreview,
                            blockInfo = blockInfo,
                            onFieldChange = onFieldChange,
                            onFieldSourceChange = onFieldSourceChange,
                            onSetReporterVisualMode = onSetReporterVisualMode,
                            onToggleBlockActive = onToggleSelectedBlockActive,
                            onToggleBlockCollapse = onToggleSelectedBlockCollapse,
                            onReplaceBlockType = onReplaceSelectedBlockType,
                            onAddBranch = onAddSelectedIfBranch,
                            onRemoveBranch = onRemoveSelectedIfBranch,
                            onUpdateBlockNote = onUpdateBlockNote,
                            onToggleVisible = onToggleBottomPanel,
                        )
                    }
                }

            }
        }

    }

    BlockDesignFactorySheet(
        visible = showBlockFactoryEntry && showBlockFactory,
        onDismiss = onDismissBlockFactory,
        onCreate = onCreateCustomBlock,
    )
}

@Composable
private fun BlockEditorInspectorBottomSheet(
    blockInfo: BlockInfoSnapshot,
    onFieldChange: (String, String) -> Unit,
    onFieldSourceChange: (String, String) -> Unit,
    onSetReporterVisualMode: (de.visualtasker.blockeditor.compose.model.ReporterVisualMode) -> Unit,
    onToggleBlockActive: () -> Boolean,
    onToggleBlockCollapse: () -> Boolean,
    onReplaceBlockType: (String) -> Boolean,
    onAddBranch: () -> Boolean,
    onRemoveBranch: () -> Boolean,
    onUpdateBlockNote: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var sheetHeightDp by remember { mutableFloatStateOf(196f) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(sheetHeightDp.dp),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 14.dp, bottomEnd = 14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 5.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 42.dp, height = 5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            sheetHeightDp = (sheetHeightDp - dragAmount.y / density.density).coerceIn(112f, 340f)
                        }
                    }
            )
            Text(
                text = "Block Inspector",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            BlockInfoCard(
                info = blockInfo,
                onFieldChange = onFieldChange,
                onFieldSourceChange = onFieldSourceChange,
                onSetReporterVisualMode = onSetReporterVisualMode,
                onToggleBlockActive = onToggleBlockActive,
                onToggleBlockCollapse = onToggleBlockCollapse,
                onReplaceBlockType = onReplaceBlockType,
                onAddBranch = onAddBranch,
                onRemoveBranch = onRemoveBranch,
                onUpdateBlockNote = onUpdateBlockNote,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
            )
        }
    }
}

@Composable
private fun FloatingViewportControls(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFitWorkspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BlockEditorToolbarIconButton(
                description = "Vergrößern",
                icon = Icons.Filled.ZoomIn,
                onClick = onZoomIn,
            )
            BlockEditorToolbarIconButton(
                description = "Verkleinern",
                icon = Icons.Filled.ZoomOut,
                onClick = onZoomOut,
            )
            BlockEditorToolbarIconButton(
                description = "Workspace einpassen",
                icon = Icons.Filled.CenterFocusStrong,
                onClick = onFitWorkspace,
            )
        }
    }
}

@Composable
private fun BlockEditorMiniMap(
    layoutCache: LayoutCache,
    viewport: ViewportState,
    canvasSize: Offset2,
    modifier: Modifier = Modifier,
) {
    val blocks = layoutCache.flatIndex.visibleBlocks
    if (blocks.size <= 1 || canvasSize.x <= 0f || canvasSize.y <= 0f) return
    val left = blocks.minOf { it.subtreeBounds.x }
    val top = blocks.minOf { it.subtreeBounds.y }
    val right = blocks.maxOf { it.subtreeBounds.right }
    val bottom = blocks.maxOf { it.subtreeBounds.bottom }
    val contentWidth = (right - left).coerceAtLeast(1f)
    val contentHeight = (bottom - top).coerceAtLeast(1f)
    val miniWidth = 112.dp
    val miniHeight = 78.dp
    val viewportColor = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier.size(width = miniWidth, height = miniHeight),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.66f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Canvas(Modifier.fillMaxSize().padding(7.dp)) {
            val scale = minOf(size.width / contentWidth, size.height / contentHeight)
            val drawWidth = contentWidth * scale
            val drawHeight = contentHeight * scale
            val offsetX = (size.width - drawWidth) / 2f
            val offsetY = (size.height - drawHeight) / 2f
            blocks.forEach { block ->
                val bounds = block.subtreeBounds
                drawRoundRect(
                    color = Color(0xFFBDA7FF).copy(alpha = 0.64f),
                    topLeft = androidx.compose.ui.geometry.Offset(
                        offsetX + (bounds.x - left) * scale,
                        offsetY + (bounds.y - top) * scale,
                    ),
                    size = Size(
                        (bounds.width * scale).coerceAtLeast(2f),
                        (bounds.height * scale).coerceAtLeast(2f),
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
                )
            }
            val visibleLeft = -viewport.panX / viewport.scale
            val visibleTop = -viewport.panY / viewport.scale
            val visibleRight = (canvasSize.x - viewport.panX) / viewport.scale
            val visibleBottom = (canvasSize.y - viewport.panY) / viewport.scale
            drawRect(
                color = viewportColor,
                topLeft = androidx.compose.ui.geometry.Offset(
                    offsetX + (visibleLeft - left) * scale,
                    offsetY + (visibleTop - top) * scale,
                ),
                size = Size(
                    ((visibleRight - visibleLeft) * scale).coerceAtLeast(4f),
                    ((visibleBottom - visibleTop) * scale).coerceAtLeast(4f),
                ),
                style = Stroke(width = 1.4.dp.toPx()),
            )
        }
    }
}

@Composable
private fun TrashDropTarget(
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .size(BlockEditorTrashDropTargetSizeDp)
            .border(
                width = 2.dp,
                color = if (active) scheme.error else scheme.outline,
                shape = CircleShape,
            )
            .semantics {
                contentDescription = if (active) {
                    "Papierkorb aktiv"
                } else {
                    "Zum Löschen hier ablegen"
                }
            },
        shape = CircleShape,
        color = if (active) scheme.errorContainer else scheme.surfaceContainerHigh,
        contentColor = if (active) scheme.onErrorContainer else scheme.onSurfaceVariant,
        tonalElevation = if (active) 6.dp else 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

private fun isInTrashZone(
    point: Offset2,
    canvasSize: Offset2,
    trashSizePx: Float,
    marginPx: Float,
): Boolean {
    if (canvasSize.x <= 0f || canvasSize.y <= 0f) return false
    val left = canvasSize.x - trashSizePx - marginPx
    val top = canvasSize.y - trashSizePx - marginPx
    val right = canvasSize.x - marginPx
    val bottom = canvasSize.y - marginPx
    return point.x in left..right && point.y in top..bottom
}

private fun sameCanvasSize(
    previous: Offset2,
    next: Offset2,
    tolerance: Float = 0.5f,
): Boolean =
    kotlin.math.abs(previous.x - next.x) <= tolerance &&
        kotlin.math.abs(previous.y - next.y) <= tolerance

private fun autoPanViewportIfNeeded(
    point: Offset2,
    canvasSize: Offset2,
    viewport: ViewportState,
    onViewportChange: (ViewportState) -> Unit,
    edgePx: Float = 72f,
    stepPx: Float = 24f,
) {
    if (canvasSize.x <= edgePx * 2f || canvasSize.y <= edgePx * 2f) return
    val panX = when {
        point.x < edgePx -> stepPx
        point.x > canvasSize.x - edgePx -> -stepPx
        else -> 0f
    }
    val panY = when {
        point.y < edgePx -> stepPx
        point.y > canvasSize.y - edgePx -> -stepPx
        else -> 0f
    }
    if (panX == 0f && panY == 0f) return
    onViewportChange(
        viewport.withTransform(
            centroid = Offset2(canvasSize.x / 2f, canvasSize.y / 2f),
            panDelta = Offset2(panX, panY),
            zoomFactor = 1f,
        ),
    )
}

@Composable
private fun BlockContextDropdown(
    request: BlockContextMenuRequest?,
    blockInfo: BlockInfoSnapshot?,
    onDismiss: () -> Unit,
    onToggleActive: () -> Boolean,
    onToggleCollapse: () -> Boolean,
    onAddBranch: () -> Boolean,
    onRemoveBranch: () -> Boolean,
) {
    if (request == null || blockInfo == null || request.blockId != blockInfo.blockId) return
    val density = LocalDensity.current
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        offset = with(density) {
            DpOffset(
                x = request.screenPoint.x.toDp(),
                y = request.screenPoint.y.toDp(),
            )
        },
    ) {
        DropdownMenuItem(
            text = { Text(if (blockInfo.active) "Deaktivieren" else "Aktivieren") },
            onClick = {
                onDismiss()
                onToggleActive()
            },
        )
        DropdownMenuItem(
            text = { Text(if (blockInfo.collapsed) "Ausklappen" else "Einklappen") },
            onClick = {
                onDismiss()
                onToggleCollapse()
            },
        )
        if (blockInfo.branchCount > 0) {
            DropdownMenuItem(
                text = { Text("Branch hinzufügen") },
                enabled = blockInfo.canAddBranch,
                onClick = {
                    onDismiss()
                    onAddBranch()
                },
            )
            DropdownMenuItem(
                text = { Text("Branch entfernen") },
                enabled = blockInfo.canRemoveBranch,
                onClick = {
                    onDismiss()
                    onRemoveBranch()
                },
            )
        }
    }
}

@Composable
private fun BlockEditorIconBar(
    selectedBlockAvailable: Boolean,
    selectedBlockCollapsed: Boolean,
    canToggleSelectedBlockCollapse: Boolean,
    onFitWorkspace: () -> Unit,
    onAutoArrangeWorkspace: () -> Unit,
    onSaveWorkspace: (() -> Unit)?,
    onOpenBlockFactory: () -> Unit,
    onClearWorkspace: () -> Unit,
    showBlockFactoryEntry: Boolean,
    onUndo: () -> Boolean,
    onRedo: () -> Boolean,
    onToggleSelectedBlockCollapse: () -> Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onDeleteSelectedBlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = Color(0xFF221C2C).copy(alpha = 0.94f),
        contentColor = Color(0xFFECE6F3),
        tonalElevation = 3.dp,
    ) {
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (onSaveWorkspace != null) {
                BlockEditorToolbarIconButton(
                    description = "Speichern",
                    icon = Icons.Filled.Save,
                    onClick = onSaveWorkspace,
                )
            }
            BlockEditorToolbarIconButton(
                description = "Rückgängig",
                icon = Icons.AutoMirrored.Filled.Undo,
                onClick = { onUndo() },
            )
            BlockEditorToolbarIconButton(
                description = "Wiederholen",
                icon = Icons.AutoMirrored.Filled.Redo,
                onClick = { onRedo() },
            )
            BlockEditorToolbarIconButton(
                description = if (selectedBlockCollapsed) "Block ausklappen" else "Block einklappen",
                icon = if (selectedBlockCollapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                enabled = canToggleSelectedBlockCollapse,
                selected = selectedBlockCollapsed,
                onClick = { onToggleSelectedBlockCollapse() },
            )
            BlockEditorToolbarIconButton(
                description = "Verkleinern",
                icon = Icons.Filled.ZoomOut,
                onClick = onZoomOut,
            )
            BlockEditorToolbarIconButton(
                description = "Vergrößern",
                icon = Icons.Filled.ZoomIn,
                onClick = onZoomIn,
            )
            BlockEditorToolbarIconButton(
                description = "Workspace einpassen",
                icon = Icons.Filled.CenterFocusStrong,
                onClick = onFitWorkspace,
            )
            BlockEditorToolbarIconButton(
                description = "Workspace aufräumen",
                icon = Icons.Filled.GridView,
                onClick = onAutoArrangeWorkspace,
            )
            if (showBlockFactoryEntry) {
                BlockEditorToolbarIconButton(
                    description = "Blockdesigner",
                    icon = Icons.Filled.Add,
                    onClick = onOpenBlockFactory,
                )
            }
            BlockEditorToolbarIconButton(
                description = "Workspace leeren",
                icon = Icons.Filled.Close,
                danger = true,
                onClick = onClearWorkspace,
            )
            BlockEditorToolbarIconButton(
                description = "Ausgewählten Block löschen",
                icon = Icons.Filled.Delete,
                enabled = selectedBlockAvailable,
                danger = true,
                onClick = onDeleteSelectedBlock,
            )
        }
    }
}

@Composable
private fun BlockEditorToolbarIconButton(
    description: String,
    icon: ImageVector,
    selected: Boolean = false,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(BlockEditorToolbarTouchTargetDp).semantics {
                contentDescription = description
                this.selected = selected
            },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = when {
                    selected -> MaterialTheme.colorScheme.primary
                    else -> Color.Transparent
                },
                contentColor = when {
                    selected -> MaterialTheme.colorScheme.onPrimary
                    danger -> MaterialTheme.colorScheme.error
                    else -> Color(0xFFECE6F3)
                },
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            ),
        ) {
            Icon(icon, contentDescription = null)
        }
    }
}

private const val BLOCK_SCAFFOLD_LOG_TAG = "VTWSS/BlockScaffold"
