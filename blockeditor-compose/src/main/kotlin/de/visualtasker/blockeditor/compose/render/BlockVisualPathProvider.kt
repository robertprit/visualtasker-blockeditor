package de.visualtasker.blockeditor.compose.render

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
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
    data class Success(val path: Path) : BlockVisualPathResult
    data object UseLegacy : BlockVisualPathResult
}

/**
 * Optional host presentation hook. Implementations must not mutate editor state or use
 * the returned path as hit-test, docking, snap, layout, mutation or serialization data.
 */
fun interface BlockVisualPathProvider {
    fun path(request: BlockVisualPathRequest): BlockVisualPathResult

    companion object {
        val Legacy = BlockVisualPathProvider { BlockVisualPathResult.UseLegacy }
    }
}

internal fun resolveBlockVisualPath(
    definition: BlockDefinition?,
    size: Size,
    branchDividerYs: List<Float>,
    provider: BlockVisualPathProvider,
): Path {
    val legacy = BlockPathCache.path(definition, size, branchDividerYs)
    val request = BlockVisualPathRequest(
        definition = definition,
        shape = BlockPathCache.shape(definition),
        targetSize = size,
        branchDividerYs = branchDividerYs.toList(),
    )
    val result = try {
        provider.path(request)
    } catch (_: RuntimeException) {
        BlockVisualPathResult.UseLegacy
    }
    return when (result) {
        is BlockVisualPathResult.Success -> result.path.takeUnless(Path::isEmpty) ?: legacy
        BlockVisualPathResult.UseLegacy -> legacy
    }
}
