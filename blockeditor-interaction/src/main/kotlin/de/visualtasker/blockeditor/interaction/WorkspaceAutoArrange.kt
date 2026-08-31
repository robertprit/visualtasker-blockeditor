package de.visualtasker.blockeditor.interaction

import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.withRootOffset
import de.visualtasker.blockeditor.layout.LayoutCache
import kotlin.math.max

data class AutoArrangeConfig(
    val startX: Float = 32f,
    val startY: Float = 32f,
    val columnGap: Float = 56f,
    val rowGap: Float = 32f,
    val maxColumnHeight: Float = 720f,
)

object WorkspaceAutoArrange {
    fun arrangeRoots(
        document: WorkspaceDocument,
        layoutCache: LayoutCache,
        config: AutoArrangeConfig = AutoArrangeConfig(),
    ): WorkspaceDocument {
        var updated = document
        var x = config.startX
        var y = config.startY
        var columnWidth = 0f

        document.rootBlocks
            .filter { it in document.blocks }
            .forEach { rootId ->
                val bounds = layoutCache.flatIndex.visibleBlocks
                    .firstOrNull { it.blockId == rootId }
                    ?.subtreeBounds
                val width = bounds?.width?.coerceAtLeast(1f) ?: 288f
                val height = bounds?.height?.coerceAtLeast(1f) ?: 64f
                if (y > config.startY && y + height > config.maxColumnHeight) {
                    x += columnWidth + config.columnGap
                    y = config.startY
                    columnWidth = 0f
                }
                updated = updated.withRootOffset(rootId, x, y)
                y += height + config.rowGap
                columnWidth = max(columnWidth, width)
            }

        return updated
    }
}

