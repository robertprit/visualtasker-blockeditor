package de.visualtasker.blockeditor.compose.layers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.rememberTextMeasurer
import de.visualtasker.blockeditor.compose.render.drawBlock
import de.visualtasker.blockeditor.compose.theme.defaultBlockEditorColors
import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.Offset2
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.layout.ContainerBranchLayout
import de.visualtasker.blockeditor.layout.LayoutCache
import de.visualtasker.blockeditor.registry.BlockRegistry
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry

@Composable
fun WorkspaceLayer(
    document: WorkspaceDocument,
    layoutCache: LayoutCache,
    excludedBlockIds: Set<BlockId>,
    dragRootId: BlockId? = null,
    registry: BlockRegistry = DefaultBlockRegistry,
    modifier: Modifier = Modifier,
) {
    val colors = remember { defaultBlockEditorColors() }
    val textMeasurer = rememberTextMeasurer()
    val exclusionKey = buildExclusionKey(excludedBlockIds, dragRootId)

    key(exclusionKey) {
        Canvas(modifier = modifier.fillMaxSize()) {
            layoutCache.flatIndex.visibleBlocks
                .asSequence()
                .filter { it.blockId !in excludedBlockIds }
                .sortedBy { it.zIndex }
                .forEach { layout ->
                    val block = document.blocks[layout.blockId] ?: return@forEach
                    val definition = registry.getDefinition(block.type)
                    val blockTopLeft = Offset2(layout.bounds.x, layout.bounds.y)
                    val (branchDividers, branchSections) = ContainerBranchLayout.containerVisuals(
                        blockId = layout.blockId,
                        blockTopLeft = blockTopLeft,
                        layout = layoutCache,
                    )
                    val inlineLayout = layoutCache.flatIndex.inlineReporterLayouts
                        .find { it.blockId == layout.blockId }
                        ?.relativeTo(layout)
                    drawBlock(
                        block = block,
                        definition = definition,
                        topLeft = Offset(layout.bounds.x, layout.bounds.y),
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
}

private fun buildExclusionKey(excludedBlockIds: Set<BlockId>, dragRootId: BlockId?): String =
    buildString {
        append(dragRootId?.value ?: "none")
        append(':')
        excludedBlockIds
            .map { it.value }
            .sorted()
            .joinTo(this, separator = ",")
    }
