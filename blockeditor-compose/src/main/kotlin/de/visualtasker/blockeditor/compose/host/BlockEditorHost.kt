package de.visualtasker.blockeditor.compose.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import de.visualtasker.blockeditor.compose.ui.BlockEditorScaffold
import de.visualtasker.blockeditor.compose.render.BlockVisualPathProvider

/**
 * Public Compose entry point for host embedding.
 */
@Composable
fun BlockEditorHost(
    controller: BlockEditorController,
    uiConfig: BlockEditorHostUiConfig = BlockEditorHostUiConfig(),
    modifier: Modifier = Modifier,
) {
    BlockEditorHost(controller, uiConfig, modifier, BlockVisualPathProvider.Legacy)
}

/** Public Compose entry point with an optional host-owned presentation path provider. */
@Composable
fun BlockEditorHost(
    controller: BlockEditorController,
    uiConfig: BlockEditorHostUiConfig = BlockEditorHostUiConfig(),
    modifier: Modifier = Modifier,
    visualPathProvider: BlockVisualPathProvider,
) {
    DisposableEffect(controller) {
        onDispose { controller.close() }
    }

    BlockEditorScaffold(
        document = controller.document,
        layoutCache = controller.layoutCache,
        registry = controller.registry,
        viewport = controller.viewport,
        dragRender = controller.dragRender,
        selectedBlockIds = controller.selectedBlockIds,
        codePreview = controller.codePreview,
        blockInfo = controller.selectedBlockInfo(),
        showBottomPanel = uiConfig.showBottomPanel && controller.showBottomPanel,
        showToolbox = uiConfig.showToolbox,
        expandedCategory = controller.expandedCategory,
        definitionsForCategory = controller.definitionsForExpandedCategory(),
        showBlockFactory = uiConfig.showBlockFactory && controller.showBlockFactory,
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
        onUndo = controller::undo,
        onRedo = controller::redo,
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
        onFieldChange = controller::updateBlockField,
        onFieldSourceChange = controller::updateBlockFieldSource,
        onSetReporterVisualMode = controller::setSelectedReporterVisualMode,
        soundEffectsEnabled = uiConfig.soundEffectsEnabled,
        hapticFeedbackEnabled = uiConfig.hapticFeedbackEnabled,
        visualPathProvider = visualPathProvider,
        modifier = modifier,
    )
}
