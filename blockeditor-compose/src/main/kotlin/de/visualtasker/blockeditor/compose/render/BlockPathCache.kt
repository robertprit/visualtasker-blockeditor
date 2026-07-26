package de.visualtasker.blockeditor.compose.render

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import de.visualtasker.blockeditor.compose.shapes.BlockShapes
import de.visualtasker.blockeditor.layout.ContainerBranchLayout
import de.visualtasker.blockeditor.layout.LayoutConstants
import de.visualtasker.blockeditor.registry.BlockDefinition

/** Gecachte Pfade – kein Path-Alloc pro Frame. */
internal object BlockPathCache {
    private val cache = mutableMapOf<String, Path>()

    fun path(
        definition: BlockDefinition?,
        size: Size,
        branchDividerYs: List<Float> = emptyList(),
    ): Path {
        val key = buildKey(definition, size, branchDividerYs)
        return cache.getOrPut(key) {
            buildPath(definition, size, branchDividerYs)
        }
    }

    private fun buildKey(
        definition: BlockDefinition?,
        size: Size,
        branchDividerYs: List<Float>,
    ): String {
        val type = definition?.id ?: "unknown"
        val kind = when {
            definition?.isReporter == true && definition.inputsInline -> "ir"
            definition?.isReporter == true -> "r"
            definition?.statementInputs?.isNotEmpty() == true -> "c"
            else -> "s"
        }
        val branches = branchDividerYs.joinToString(",") { it.toInt().toString() }
        return "$type:$kind:${size.width.toInt()}:${size.height.toInt()}:$branches"
    }

    fun shape(definition: BlockDefinition?): BlockVisualShape = when {
        definition?.isReporter == true && definition.inputsInline -> BlockVisualShape.InlineReporter
        definition?.isReporter == true -> BlockVisualShape.Reporter
        definition?.statementInputs?.isNotEmpty() == true -> BlockVisualShape.Container
        else -> BlockVisualShape.Statement
    }

    private fun buildPath(
        definition: BlockDefinition?,
        size: Size,
        branchDividerYs: List<Float>,
    ): Path = when {
        definition?.isReporter == true && definition.inputsInline -> BlockShapes.inlineReporterPath(size)
        definition?.isReporter == true -> BlockShapes.reporterPath(size)
        definition?.statementInputs?.isNotEmpty() == true -> {
            val dividers = branchDividerYs.ifEmpty {
                ContainerBranchLayout.branchDividerYs(definition)
            }
            BlockShapes.containerPath(
                size,
                LayoutConstants.HEADER_HEIGHT,
                LayoutConstants.FOOTER_HEIGHT,
                dividers,
            )
        }
        else -> BlockShapes.statementPath(size)
    }
}
