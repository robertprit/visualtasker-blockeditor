@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package de.visualtasker.blockeditor.compose.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import de.visualtasker.blockeditor.compose.layers.EditorCanvasLayer
import de.visualtasker.blockeditor.compose.render.BlockVisualPathProvider
import de.visualtasker.blockeditor.compose.theme.defaultBlockEditorColors
import de.visualtasker.blockeditor.compose.viewmodel.BlockInfoSnapshot
import de.visualtasker.blockeditor.compose.viewmodel.DragRenderState
import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.Offset2
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.interaction.ViewportState
import de.visualtasker.blockeditor.layout.LayoutCache
import de.visualtasker.blockeditor.registry.BlockDefinition
import de.visualtasker.blockeditor.registry.BlockDesignBlueprint
import de.visualtasker.blockeditor.registry.BlockCategories
import de.visualtasker.blockeditor.registry.BlockRegistry
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import android.view.SoundEffectConstants

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
    codePreview: String,
    blockInfo: BlockInfoSnapshot?,
    showBottomPanel: Boolean,
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
    extraCategories: List<BlockCategories.CategoryMeta> = emptyList(),
    onFitWorkspace: () -> Unit,
    onUndo: () -> Boolean,
    onRedo: () -> Boolean,
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
        codePreview = codePreview,
        blockInfo = blockInfo,
        showBottomPanel = showBottomPanel,
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
        extraCategories = extraCategories,
        onFitWorkspace = onFitWorkspace,
        onUndo = onUndo,
        onRedo = onRedo,
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
    codePreview: String,
    blockInfo: BlockInfoSnapshot?,
    showBottomPanel: Boolean,
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
    extraCategories: List<BlockCategories.CategoryMeta> = emptyList(),
    onFitWorkspace: () -> Unit,
    onUndo: () -> Boolean,
    onRedo: () -> Boolean,
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
    onFieldChange: (String, String) -> Unit,
    onFieldSourceChange: (String, String) -> Unit = { _, _ -> },
    onSetReporterVisualMode: (de.visualtasker.blockeditor.compose.model.ReporterVisualMode) -> Unit = {},
    modifier: Modifier = Modifier,
    soundEffectsEnabled: Boolean = false,
    hapticFeedbackEnabled: Boolean = false,
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

    val workspaceOutlineColor = scheme.outlineVariant.copy(alpha = 0.55f)
    val toolboxColor = scheme.surfaceContainerLowest
    val workspaceShape = RoundedCornerShape(30.dp)
    BackHandler(
        enabled = showBlockFactory || expandedCategory != null || showBottomPanel,
    ) {
        onCloseTopMostPanel()
    }

    Row(modifier = modifier.fillMaxSize()) {
        EditorNavigationRail(
            expandedCategory = expandedCategory,
            onCategoryClick = onCategoryClick,
            onOpenBlockFactory = onOpenBlockFactory,
            onClearWorkspace = onClearWorkspace,
            showBlockFactoryEntry = showBlockFactoryEntry,
            extraCategories = extraCategories,
            containerColor = toolboxColor,
        )

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
                                if (started && hapticFeedbackEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                        },
                        onDrag = {
                            latestDragPoint = it
                            onMove.value(it)
                        },
                        onDragEnd = {
                            latestDragPoint = it
                            val deleteByTrash = isInTrashZone(it, canvasSize, trashSizePx, trashMarginPx)
                            if (deleteByTrash) {
                                if (onDeleteSelectedBlock()) {
                                    playEditorSound(platformView, soundEffectsEnabled)
                                    if (hapticFeedbackEnabled) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                            } else {
                                onUp.value(it)
                                playEditorSound(platformView, soundEffectsEnabled)
                                if (hapticFeedbackEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
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
                BlockEditorIconBar(
                    selectedBlockAvailable = selectedBlockIds.isNotEmpty(),
                    onFitWorkspace = onFitWorkspace,
                    onUndo = onUndo,
                    onRedo = onRedo,
                    onZoomIn = onZoomIn,
                    onZoomOut = onZoomOut,
                    onDeleteSelectedBlock = {
                        if (onDeleteSelectedBlock()) {
                            playEditorSound(platformView, soundEffectsEnabled)
                            if (hapticFeedbackEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                )
                TrashDropTarget(
                    active = deleteCandidate,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                )
                }

                if (showBottomPanel) {
                    EditorBottomPanel(
                        code = codePreview,
                        blockInfo = blockInfo,
                        onFieldChange = onFieldChange,
                        onFieldSourceChange = onFieldSourceChange,
                        onSetReporterVisualMode = onSetReporterVisualMode,
                        onToggleVisible = onToggleBottomPanel,
                    )
                }
            }

            if (expandedCategory != null) {
                CategoryPalettePanel(
                    category = expandedCategory,
                    definitions = definitionsForCategory,
                    onAddBlock = onAddBlock,
                    onCreateVariable = onCreateVariable,
                    onDismiss = onDismissCategory,
                    containerColor = toolboxColor,
                    modifier = Modifier.align(Alignment.TopStart),
                )
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

private fun playEditorSound(
    platformView: android.view.View,
    enabled: Boolean,
) {
    if (!enabled) return
    platformView.playSoundEffect(SoundEffectConstants.CLICK)
    runCatching {
        val tone = ToneGenerator(AudioManager.STREAM_SYSTEM, 32)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 35)
        platformView.postDelayed({ tone.release() }, 80L)
    }
}

@Composable
private fun BlockEditorIconBar(
    selectedBlockAvailable: Boolean,
    onFitWorkspace: () -> Unit,
    onUndo: () -> Boolean,
    onRedo: () -> Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onDeleteSelectedBlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
    ) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
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
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            ),
        ) {
            Icon(icon, contentDescription = null)
        }
    }
}
