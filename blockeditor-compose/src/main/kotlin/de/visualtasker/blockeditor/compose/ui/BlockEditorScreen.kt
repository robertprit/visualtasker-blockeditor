package de.visualtasker.blockeditor.compose.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.visualtasker.blockeditor.compose.viewmodel.BlockEditorViewModel

@Composable
fun BlockEditorScreen(
    viewModel: BlockEditorViewModel,
    modifier: Modifier = Modifier,
) {
    BlockEditorScaffold(
        document = viewModel.document,
        layoutCache = viewModel.layoutCache,
        viewport = viewModel.viewport,
        dragRender = viewModel.dragRender,
        selectedBlockIds = viewModel.selectedBlockIds,
        codePreview = viewModel.codePreview,
        blockInfo = viewModel.selectedBlockInfo(),
        showBottomPanel = viewModel.showBottomPanel,
        expandedCategory = viewModel.expandedCategory,
        definitionsForCategory = viewModel.definitionsForExpandedCategory(),
        showBlockFactory = viewModel.showBlockFactory,
        onCategoryClick = viewModel::onCategoryClick,
        onDismissCategory = viewModel::dismissCategory,
        onAddBlock = viewModel::addBlockFromPalette,
        onCreateVariable = viewModel::createVariable,
        onToggleBottomPanel = viewModel::toggleBottomPanel,
        onOpenBlockFactory = viewModel::openBlockFactory,
        onDismissBlockFactory = viewModel::dismissBlockFactory,
        onCreateCustomBlock = viewModel::createCustomBlock,
        onClearWorkspace = viewModel::clearWorkspace,
        onViewportChange = viewModel::onViewportChange,
        onCanvasSizeChange = viewModel::onCanvasSizeChange,
        onTap = viewModel::onTap,
        onDoubleTap = viewModel::onDoubleTap,
        onLongPressDragStart = viewModel::onLongPressDragStart,
        onPointerMove = viewModel::onPointerMove,
        onPointerUp = viewModel::onPointerUp,
        onFieldChange = viewModel::updateBlockField,
        modifier = modifier.fillMaxSize(),
    )
}
