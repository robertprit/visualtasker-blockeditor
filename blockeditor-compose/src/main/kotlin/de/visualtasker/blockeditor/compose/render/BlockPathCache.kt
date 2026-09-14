package de.visualtasker.blockeditor.compose.render

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import de.visualtasker.blockeditor.compose.shapes.BlockShapes
import de.visualtasker.blockeditor.layout.ContainerBranchLayout
import de.visualtasker.blockeditor.layout.LayoutConstants
import de.visualtasker.blockeditor.registry.BlockDefinition
import de.visualtasker.blockeditor.registry.BlockTypes

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
            definition.isStartStatement() -> "ss"
            definition?.statementInputs?.isNotEmpty() == true -> "c"
            else -> "s"
        }
        val branches = branchDividerYs.joinToString(",") { it.toInt().toString() }
        val headerRows = definition?.metadata?.get("custom.layout.headerRows").orEmpty()
        val valueType = definition?.outputType.orEmpty()
        return "$type:$kind:$valueType:${size.width.toInt()}:${size.height.toInt()}:$headerRows:$branches"
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
        definition?.isReporter == true -> BlockShapes.reporterPath(size, definition.outputType)
        definition.isStartStatement() -> BlockShapes.startStatementPath(size)
        definition?.statementInputs?.isNotEmpty() == true -> {
            val dividers = branchDividerYs.ifEmpty {
                ContainerBranchLayout.branchDividerYs(definition)
            }
            BlockShapes.containerPath(
                size,
                definition.designerHeaderHeight(),
                LayoutConstants.FOOTER_HEIGHT,
                dividers,
            )
        }
        else -> BlockShapes.statementPath(size)
    }

    private fun BlockDefinition?.isStartStatement(): Boolean =
        (this?.id == BlockTypes.EVENT_START || this?.id == "em_on_start") && statementInputs.isEmpty()

    private fun BlockDefinition?.designerHeaderHeight(): Float {
        val rows = this?.metadata?.get("custom.layout.headerRows")
            ?.toIntOrNull()
            ?.coerceAtLeast(1)
            ?: 1
        return LayoutConstants.HEADER_HEIGHT * rows
    }
}
