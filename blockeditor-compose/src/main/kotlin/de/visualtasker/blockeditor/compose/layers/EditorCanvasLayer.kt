package de.visualtasker.blockeditor.compose.layers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.rememberTextMeasurer
import de.visualtasker.blockeditor.compose.render.BlockVisualPathProvider
import de.visualtasker.blockeditor.compose.render.drawBlock
import de.visualtasker.blockeditor.compose.render.drawInlineReporterDockSlotOverlays
import de.visualtasker.blockeditor.compose.theme.BlockEditorColors
import de.visualtasker.blockeditor.compose.theme.defaultBlockEditorColors
import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.compose.viewmodel.DragRenderState
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.WorkspaceGraph
import de.visualtasker.blockeditor.interaction.ViewportState
import de.visualtasker.blockeditor.domain.Offset2
import de.visualtasker.blockeditor.interaction.DragPullMode
import de.visualtasker.blockeditor.layout.ContainerBranchLayout
import de.visualtasker.blockeditor.layout.LayoutCache
import de.visualtasker.blockeditor.layout.LayoutConstants
import de.visualtasker.blockeditor.registry.BlockRegistry
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry

private const val GhostBlockAlpha = 0.42f

/**
 * Ein Canvas: Hintergrund aus Layout-Vorschau (ohne gezogene Blöcke),
 * gezogene Blöcke einmalig an Drag-/Snap-Position.
 */
@Composable
fun EditorCanvasLayer(
    document: WorkspaceDocument,
    layoutCache: LayoutCache,
    viewport: ViewportState,
    dragRender: DragRenderState?,
    selectedBlockIds: Set<BlockId> = emptySet(),
    registry: BlockRegistry = DefaultBlockRegistry,
    modifier: Modifier = Modifier,
    colors: BlockEditorColors = defaultBlockEditorColors(),
    gridVisible: Boolean = true,
) {
    EditorCanvasLayer(
        document,
        layoutCache,
        viewport,
        dragRender,
        selectedBlockIds,
        registry,
        modifier,
        colors,
        gridVisible,
        BlockVisualPathProvider.Legacy,
    )
}

@Composable
fun EditorCanvasLayer(
    document: WorkspaceDocument,
    layoutCache: LayoutCache,
    viewport: ViewportState,
    dragRender: DragRenderState?,
    selectedBlockIds: Set<BlockId> = emptySet(),
    registry: BlockRegistry = DefaultBlockRegistry,
    modifier: Modifier = Modifier,
    colors: BlockEditorColors = defaultBlockEditorColors(),
    gridVisible: Boolean = true,
    visualPathProvider: BlockVisualPathProvider,
) {
    val textMeasurer = rememberTextMeasurer()
    val draggedIds = dragRender?.session?.includedBlocks.orEmpty()
    val movesWithDrag: (BlockId) -> Boolean = { blockId ->
        blockId in draggedIds ||
            WorkspaceGraph.isAttachedToDraggedAncestor(document, blockId, draggedIds)
    }
    val staticLayout = dragRender?.staticLayoutCache ?: layoutCache
    val dragLayout = dragRender?.dragLayoutCache ?: layoutCache
    val dragOffset = dragRender?.let { render ->
        render.snapCandidate?.snapOffset ?: render.session.dragOffset
    }
    val snapTargetId = dragRender?.snapCandidate?.targetConnectionId
    val ghostRootId = dragRender
        ?.takeIf { it.session.pullMode == DragPullMode.Single }
        ?.session
        ?.rootBlockId

    Canvas(modifier = modifier.fillMaxSize()) {
        if (gridVisible) {
            drawBlockEditorDotGrid(viewport, colors)
        }
        translate(viewport.panX, viewport.panY) {
            scale(viewport.scale, viewport.scale, pivot = Offset.Zero) {
                staticLayout.flatIndex.visibleBlocks
                    .sortedBy { it.zIndex }
                    .forEach { layout ->
                        if (movesWithDrag(layout.blockId) && layout.blockId != ghostRootId) return@forEach
                        val block = document.blocks[layout.blockId] ?: return@forEach
                        val definition = registry.getDefinition(block.type)
                        val blockTopLeft = Offset2(layout.bounds.x, layout.bounds.y)
                        val (branchDividers, branchSections) = ContainerBranchLayout.containerVisuals(
                            blockId = layout.blockId,
                            blockTopLeft = blockTopLeft,
                            layout = staticLayout,
                        )
                        val inlineLayout = staticLayout.flatIndex.inlineReporterLayouts
                            .find { it.blockId == layout.blockId }
                            ?.relativeTo(layout)
                        val drawStaticBlock = {
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
                                visualPathProvider = visualPathProvider,
                                selected = layout.blockId in selectedBlockIds,
                            )
                        }
                        if (layout.blockId == ghostRootId) {
                            drawContext.canvas.saveLayer(
                                Rect(
                                    left = layout.bounds.x,
                                    top = layout.bounds.y,
                                    right = layout.bounds.x + layout.bounds.width,
                                    bottom = layout.bounds.y + layout.bounds.height,
                                ),
                                Paint().apply { alpha = GhostBlockAlpha },
                            )
                            drawStaticBlock()
                            drawContext.canvas.restore()
                        } else {
                            drawStaticBlock()
                        }
                    }
                staticLayout.flatIndex.inlineReporterLayouts
                    .sortedBy { it.zIndex }
                    .forEach { inlineLayout ->
                        if (movesWithDrag(inlineLayout.blockId) && inlineLayout.blockId != ghostRootId) return@forEach
                        val block = document.blocks[inlineLayout.blockId] ?: return@forEach
                        drawInlineReporterDockSlotOverlays(
                            block = block,
                            inlineReporterLayout = inlineLayout,
                        )
                    }

                if (dragRender != null && dragOffset != null) {
                    val session = dragRender.session
                    val rootLayout = dragLayout.flatIndex.visibleBlocks
                        .find { it.blockId == session.rootBlockId }
                    val rootOrigin = session.originalLayoutPosition
                    val rootX = rootLayout?.bounds?.x ?: rootOrigin.x
                    val rootY = rootLayout?.bounds?.y ?: rootOrigin.y

                    dragLayout.flatIndex.visibleBlocks
                        .filter { movesWithDrag(it.blockId) }
                        .sortedBy { it.zIndex }
                        .forEach { layout ->
                            val block = document.blocks[layout.blockId] ?: return@forEach
                            val definition = registry.getDefinition(block.type)
                            val relX = layout.bounds.x - rootX
                            val relY = layout.bounds.y - rootY
                            val blockTopLeft = Offset2(layout.bounds.x, layout.bounds.y)
                            val (branchDividers, branchSections) = ContainerBranchLayout.containerVisuals(
                                blockId = layout.blockId,
                                blockTopLeft = blockTopLeft,
                                layout = dragLayout,
                            )
                            val inlineLayout = dragLayout.flatIndex.inlineReporterLayouts
                                .find { it.blockId == layout.blockId }
                                ?.relativeTo(layout)
                            drawBlock(
                                block = block,
                                definition = definition,
                                topLeft = Offset(
                                    rootOrigin.x + relX + dragOffset.x,
                                    rootOrigin.y + relY + dragOffset.y,
                                ),
                                width = layout.bounds.width,
                                height = layout.bounds.height,
                                textMeasurer = textMeasurer,
                                colors = colors,
                                registry = registry,
                                branchDividerYs = branchDividers,
                                branchSections = branchSections,
                                inlineReporterLayout = inlineLayout,
                                visualPathProvider = visualPathProvider,
                            )
                        }
                }

                if (snapTargetId != null) {
                    val target = staticLayout.flatIndex.connectionAnchors
                        .find { it.connectionId == snapTargetId }
                    if (target != null) {
                        val radius = LayoutConstants.ANCHOR_RADIUS * 2.5f
                        drawCircle(
                            color = colors.snapHighlight,
                            radius = radius,
                            center = Offset(target.x, target.y),
                        )
                        drawCircle(
                            color = Color(0xFF1565C0),
                            radius = LayoutConstants.ANCHOR_RADIUS,
                            center = Offset(target.x, target.y),
                            style = Stroke(width = 3f),
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawBlockEditorDotGrid(
    viewport: ViewportState,
    colors: BlockEditorColors,
) {
    val baseSpacing = 24.dp.toPx()
    val spacing = (baseSpacing * viewport.scale).coerceIn(12.dp.toPx(), 48.dp.toPx())
    val radius = 1.25.dp.toPx()
    val startX = positiveModulo(viewport.panX, spacing)
    val startY = positiveModulo(viewport.panY, spacing)
    var x = startX
    while (x <= size.width) {
        var y = startY
        while (y <= size.height) {
            drawCircle(colors.gridDot, radius, Offset(x, y))
            y += spacing
        }
        x += spacing
    }
}

private fun positiveModulo(value: Float, mod: Float): Float {
    if (mod <= 0f || !value.isFinite() || !mod.isFinite()) return 0f
    val remainder = value % mod
    return if (remainder < 0f) remainder + mod else remainder
}
