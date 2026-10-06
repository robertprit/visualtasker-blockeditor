package de.visualtasker.blockeditor.compose.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import de.visualtasker.blockeditor.compose.debug.BlockEditorDropTrace
import de.visualtasker.blockeditor.compose.ui.BlockEditorScaffold
import de.visualtasker.blockeditor.compose.render.BlockVisualPathProvider
import de.visualtasker.workflow.core.BlockId
import de.visualtasker.blockeditor.registry.BlockTypes

/**
 * Public Compose entry point for host embedding.
 */
@Composable
fun BlockEditorHost(
    controller: BlockEditorController,
    uiConfig: BlockEditorHostUiConfig = BlockEditorHostUiConfig(),
    modifier: Modifier = Modifier,
    selectedBlockIds: Set<BlockId>? = null,
    onSaveWorkspace: (() -> Unit)? = null,
    disposeControllerOnDispose: Boolean = true,
) {
    BlockEditorHost(
        controller = controller,
        uiConfig = uiConfig,
        modifier = modifier,
        visualPathProvider = BlockVisualPathProvider.Legacy,
        selectedBlockIds = selectedBlockIds,
        onSaveWorkspace = onSaveWorkspace,
        disposeControllerOnDispose = disposeControllerOnDispose,
    )
}

/** Public Compose entry point with an optional host-owned presentation path provider. */
@Composable
fun BlockEditorHost(
    controller: BlockEditorController,
    uiConfig: BlockEditorHostUiConfig = BlockEditorHostUiConfig(),
    modifier: Modifier = Modifier,
    visualPathProvider: BlockVisualPathProvider,
    selectedBlockIds: Set<BlockId>? = null,
    onSaveWorkspace: (() -> Unit)? = null,
    disposeControllerOnDispose: Boolean = true,
) {
    val documentVersion = controller.document.version
    BlockEditorDropTrace.markRevision(documentVersion, "T16_COMPOSE_OBSERVED_STATE")
    BlockEditorDropTrace.markRevision(documentVersion, "T17_FIRST_RECOMPOSITION_ENTER")
    SideEffect {
        BlockEditorDropTrace.markRevision(documentVersion, "T18_FIRST_RECOMPOSITION_RETURN")
    }
    LaunchedEffect(documentVersion) {
        repeat(MAX_DROP_TRACE_FRAMES) {
            withFrameNanos {
                BlockEditorDropTrace.frameNow(documentVersion)
            }
            if (it == 0) {
                BlockEditorDropTrace.completeWithoutAnimation(documentVersion)
            }
            if (BlockEditorDropTrace.isStableRevision(documentVersion)) return@LaunchedEffect
        }
    }

    if (disposeControllerOnDispose) {
        DisposableEffect(controller) {
            onDispose { controller.close() }
        }
    }

    BlockEditorScaffold(
        document = controller.document,
        layoutCache = controller.layoutCache,
        registry = controller.registry,
        viewport = controller.viewport,
        dragRender = controller.dragRender,
        selectedBlockIds = selectedBlockIds ?: controller.selectedBlockIds,
        selectedBlockCollapsed = controller.selectedBlockCollapsed,
        canToggleSelectedBlockCollapse = controller.canToggleSelectedBlockCollapse,
        blockContextMenuRequest = controller.blockContextMenuRequest,
        codePreview = controller.codePreview,
        blockInfo = controller.selectedBlockInfo(),
        showBottomPanel = uiConfig.showBottomPanel && controller.showBottomPanel,
        showFloatingInspector = uiConfig.showFloatingInspector,
        showToolbox = uiConfig.showToolbox,
        expandedCategory = controller.expandedCategory,
        definitionsForCategory = controller.definitionsForExpandedCategory(),
        showBlockFactory = uiConfig.showBlockFactory && controller.showBlockFactory,
        extraCategories = uiConfig.extraCategories,
        showBlockFactoryEntry = uiConfig.showBlockFactoryEntry,
        gridEnabled = uiConfig.gridEnabled,
        showMiniMap = uiConfig.showMiniMap,
        showTopIconBar = uiConfig.showTopIconBar,
        paletteInsertMode = uiConfig.paletteInsertMode,
        onCategoryClick = controller::onCategoryClick,
        onDismissCategory = controller::dismissCategory,
        onAddBlock = controller::addBlockFromPalette,
        onCreateVariable = controller::createVariable,
        onToggleBottomPanel = controller::toggleBottomPanel,
        onCloseTopMostPanel = controller::closeTopMostPanel,
        onOpenBlockFactory = if (uiConfig.showBlockFactory) controller::openBlockFactory else ({ }),
        onDismissBlockFactory = controller::dismissBlockFactory,
        onCreateCustomBlock = controller::createCustomBlock,
        onClearWorkspace = if (uiConfig.allowClearWorkspace) controller::clearWorkspace else ({ }),
        showBottomPanelToggle = uiConfig.showBottomPanelToggle,
        onFitWorkspace = { controller.fitWorkspaceToCanvas(force = true) },
        onAutoArrangeWorkspace = controller::autoArrangeWorkspace,
        onSaveWorkspace = onSaveWorkspace,
        onUndo = controller::undo,
        onRedo = controller::redo,
        onToggleSelectedBlockCollapse = controller::toggleSelectedBlockCollapse,
        onDismissBlockContextMenu = controller::dismissBlockContextMenu,
        onToggleSelectedBlockActive = controller::toggleSelectedBlockActive,
        onReplaceSelectedBlockType = controller::replaceSelectedBlockType,
        onAddSelectedIfBranch = {
            controller.addSelectedIfBranch(BlockTypes.CONTROL_IF, BlockTypes.CONTROL_IF_ELSEIF_ELSE)
        },
        onRemoveSelectedIfBranch = {
            controller.removeSelectedIfBranch(BlockTypes.CONTROL_IF, BlockTypes.CONTROL_IF_ELSEIF_ELSE)
        },
        onUpdateBlockNote = controller::updateSelectedBlockNote,
        onZoomIn = controller::zoomIn,
        onZoomOut = controller::zoomOut,
        onDeleteSelectedBlock = controller::deleteSelectedBlock,
        onViewportChange = controller::onViewportChange,
        onCanvasSizeChange = controller::onCanvasSizeChange,
        onTap = controller::onTap,
        onDoubleTap = controller::onDoubleTap,
        onLongPressDragStart = controller::onLongPressDragStart,
        onPointerMove = controller::onPointerMove,
        onPointerUp = controller::onPointerUp,
        onPointerCancel = controller::cancelActiveDrag,
        onFieldChange = controller::updateBlockField,
        onFieldSourceChange = controller::updateBlockFieldSource,
        onSetReporterVisualMode = controller::setSelectedReporterVisualMode,
        soundEffectsEnabled = uiConfig.soundEffectsEnabled,
        hapticFeedbackEnabled = uiConfig.hapticFeedbackEnabled,
        visualPathProvider = visualPathProvider,
        modifier = modifier.dropTraceLayoutAndDraw(documentVersion),
    )
}

private fun Modifier.dropTraceLayoutAndDraw(documentVersion: Long): Modifier =
    this
        .layout { measurable, constraints ->
            BlockEditorDropTrace.markRevision(documentVersion, "T19_FIRST_MEASURE_LAYOUT_ENTER")
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.placeRelative(0, 0)
                BlockEditorDropTrace.markRevision(documentVersion, "T20_FIRST_MEASURE_LAYOUT_RETURN")
            }
        }
        .drawWithContent {
            drawContent()
            BlockEditorDropTrace.markRevision(documentVersion, "T21_FIRST_DRAW_AFTER_PUBLICATION")
        }

private const val MAX_DROP_TRACE_FRAMES = 360
