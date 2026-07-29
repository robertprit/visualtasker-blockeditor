package de.visualtasker.blockeditor.compose.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import de.visualtasker.blockeditor.compose.shapes.BlockShapeFamily
import de.visualtasker.blockeditor.compose.shapes.BlockShapeRequest
import de.visualtasker.blockeditor.compose.shapes.BlockShapeTokens
import de.visualtasker.blockeditor.compose.shapes.BlockVisualGeometry
import de.visualtasker.blockeditor.compose.shapes.MaterialExpressiveBlockShapeBuilder
import de.visualtasker.blockeditor.registry.BlockDefinition

/** Presentation-only shape classification. It has no layout or connection authority. */
enum class BlockVisualShape {
    Statement,
    Reporter,
    InlineReporter,
    Container,
}

data class BlockVisualPathRequest(
    val definition: BlockDefinition?,
    val shape: BlockVisualShape,
    val targetSize: Size,
    val branchDividerYs: List<Float> = emptyList(),
)

sealed interface BlockVisualPathResult {
    data class Geometry(val geometry: BlockVisualGeometry) : BlockVisualPathResult
    data class LegacyPath(val path: Path) : BlockVisualPathResult
    data object Unsupported : BlockVisualPathResult
}

/**
 * Optional host presentation hook. Implementations must not mutate editor state or use
 * the returned path as hit-test, docking, snap, layout, mutation or serialization data.
 */
fun interface BlockVisualPathProvider {
    fun path(request: BlockVisualPathRequest): BlockVisualPathResult

    companion object {
        val Legacy = BlockVisualPathProvider { BlockVisualPathResult.Unsupported }
    }
}

object MaterialExpressiveBlockVisualPathProvider : BlockVisualPathProvider {
    override fun path(request: BlockVisualPathRequest): BlockVisualPathResult {
        val geometry = MaterialExpressiveBlockShapeBuilder.geometry(
            request = request.toShapeRequest(),
            tokens = BlockShapeTokens().toPx(androidx.compose.ui.unit.Density(1f)),
        )
        return BlockVisualPathResult.Geometry(geometry)
    }
}

internal fun resolveBlockVisualPath(
    definition: BlockDefinition?,
    size: Size,
    branchDividerYs: List<Float>,
    provider: BlockVisualPathProvider,
): Path {
    val legacy = BlockPathCache.path(definition, size, branchDividerYs)
    if (provider === BlockVisualPathProvider.Legacy) return legacy

    val shape = BlockPathCache.shape(definition)
    val request = BlockVisualPathRequest(
        definition = definition?.presentationSnapshot(),
        shape = shape,
        targetSize = size,
        branchDividerYs = branchDividerYs.toList(),
    )
    val result = try {
        provider.path(request)
    } catch (_: RuntimeException) {
        BlockVisualPathResult.Unsupported
    }
    return when (result) {
        is BlockVisualPathResult.Geometry -> result.geometry.path
            .takeUnless(Path::isEmpty)
            ?.let(::copyPath)
            ?: legacy
        is BlockVisualPathResult.LegacyPath -> result.path
            .takeUnless(Path::isEmpty)
            ?.let(::copyPath)
            ?: legacy
        BlockVisualPathResult.Unsupported -> legacy
    }
}

private fun copyPath(source: Path): Path = Path().apply {
    addPath(source, Offset.Zero)
}

private fun BlockVisualPathRequest.toShapeRequest(): BlockShapeRequest =
    BlockShapeRequest(
        blockType = definition?.id,
        size = targetSize,
        family = when (shape) {
            BlockVisualShape.Statement -> when {
                definition?.hasPrevious == false -> BlockShapeFamily.Event
                definition?.hasNext == false -> BlockShapeFamily.Terminal
                else -> BlockShapeFamily.Statement
            }
            BlockVisualShape.Reporter -> BlockShapeFamily.Reporter
            BlockVisualShape.InlineReporter -> BlockShapeFamily.InlineOperator
            BlockVisualShape.Container -> BlockShapeFamily.Container
        },
        branchDividerYs = branchDividerYs,
    )

private fun BlockDefinition.presentationSnapshot(): BlockDefinition = copy(
    fields = fields.map { field -> field.copy(options = field.options.toList()) },
    valueInputs = valueInputs.map { input -> input.copy(accepts = input.accepts.toSet()) },
    statementInputs = statementInputs.toList(),
)
