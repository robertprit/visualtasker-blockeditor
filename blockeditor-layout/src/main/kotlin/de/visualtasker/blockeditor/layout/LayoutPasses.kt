package de.visualtasker.blockeditor.layout

import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.registry.BlockDefinition
import de.visualtasker.blockeditor.registry.BlockRegistry

class LayoutMeasurePass(
    private val registry: BlockRegistry,
) {
    fun measure(document: WorkspaceDocument): MeasuredLayoutTree =
        MeasuredLayoutTree(
            documentVersion = document.version,
            blocks = document.blocks.mapValues { (_, block) ->
                val definition = registry.getDefinition(block.type)
                MeasuredBlockLayout(
                    blockId = block.id,
                    width = measuredWidth(definition),
                    height = measuredHeight(block.collapsed, definition),
                    collapsed = block.collapsed,
                )
            },
        )

    private fun measuredWidth(definition: BlockDefinition?): Float = when {
        definition?.isReporter == true -> LayoutConstants.REPORTER_WIDTH
        definition?.statementInputs?.isNotEmpty() == true -> LayoutConstants.CONTROL_CONTAINER_WIDTH
        else -> LayoutConstants.STANDARD_WIDTH
    }

    private fun measuredHeight(collapsed: Boolean, definition: BlockDefinition?): Float = when {
        collapsed -> LayoutConstants.COLLAPSED_HEIGHT
        definition?.isReporter == true -> LayoutConstants.REPORTER_HEIGHT
        else -> LayoutConstants.HEADER_HEIGHT
    }
}

object LayoutPlacePass {
    fun fromFlatIndex(
        document: WorkspaceDocument,
        flatIndex: FlatLayoutIndex,
    ): PlacedLayoutTree =
        PlacedLayoutTree(
            documentVersion = document.version,
            blocks = flatIndex.visibleBlocks.associate { layout ->
                layout.blockId to PlacedBlockLayout(
                    blockId = layout.blockId,
                    bounds = layout.bounds,
                    subtreeBounds = layout.subtreeBounds,
                    zIndex = layout.zIndex,
                )
            },
        )
}
