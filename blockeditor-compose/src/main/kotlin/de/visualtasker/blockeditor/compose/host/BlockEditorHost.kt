package de.visualtasker.blockeditor.compose.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import de.visualtasker.blockeditor.compose.ui.BlockEditorScaffold
import de.visualtasker.blockeditor.compose.render.BlockVisualPathProvider
import de.visualtasker.blockeditor.domain.BlockId
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
        modifier = modifier,
    )
}
