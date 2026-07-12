package de.visualtasker.blockeditor.compose.layers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.rememberTextMeasurer
import de.visualtasker.blockeditor.compose.render.drawBlock
import de.visualtasker.blockeditor.compose.theme.defaultBlockEditorColors
import de.visualtasker.blockeditor.compose.viewmodel.DragRenderState
import de.visualtasker.blockeditor.domain.Offset2
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.layout.ContainerBranchLayout
import de.visualtasker.blockeditor.layout.LayoutCache
import de.visualtasker.blockeditor.registry.BlockRegistry
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry

@Composable
fun DragLayer(
    document: WorkspaceDocument,
    layoutCache: LayoutCache,
    dragRender: DragRenderState,
    registry: BlockRegistry = DefaultBlockRegistry,
    modifier: Modifier = Modifier,
) {
    val session = dragRender.session
    val colors = remember { defaultBlockEditorColors() }
    val textMeasurer = rememberTextMeasurer()
    val offset = dragRender.snapCandidate?.snapOffset ?: session.dragOffset
    val blocks = remember(layoutCache.documentVersion, session.rootBlockId) {
        layoutCache.flatIndex.visibleBlocks
            .filter { it.blockId in session.includedBlocks }
            .sortedBy { it.zIndex }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        blocks.forEach { layout ->
            val block = document.blocks[layout.blockId] ?: return@forEach
            val definition = registry.getDefinition(block.type)
            val x = layout.bounds.x + offset.x
            val y = layout.bounds.y + offset.y
            val blockTopLeft = Offset2(layout.bounds.x, layout.bounds.y)
            val (branchDividers, branchSections) = ContainerBranchLayout.containerVisuals(
                blockId = layout.blockId,
                blockTopLeft = blockTopLeft,
                layout = layoutCache,
            )
            val inlineLayout = layoutCache.flatIndex.inlineReporterLayouts
                .find { it.blockId == layout.blockId }
            drawBlock(
                block = block,
                definition = definition,
                topLeft = Offset(x, y),
                width = layout.bounds.width,
                height = layout.bounds.height,
                textMeasurer = textMeasurer,
                colors = colors,
                registry = registry,
                branchDividerYs = branchDividers,
                branchSections = branchSections,
                inlineReporterLayout = inlineLayout,
            )
        }
    }
}
