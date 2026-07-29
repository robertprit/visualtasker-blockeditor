package de.visualtasker.blockeditor.compose.shapes

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path

enum class BlockShapeFamily {
    Event,
    Statement,
    Terminal,
    Container,
    Reporter,
    BooleanReporter,
    InlineOperator,
    Annotation,
}

data class BlockShapeRequest(
    val blockType: String?,
    val size: Size,
    val family: BlockShapeFamily,
    val branchDividerYs: List<Float> = emptyList(),
) {
    init {
        require(size.width.isFinite() && size.height.isFinite()) { "Block size must be finite." }
        require(size.width >= 0f && size.height >= 0f) { "Block size must not be negative." }
    }
}

@Immutable
data class BlockDockGeometry(
    val kind: BlockDockKind,
    val center: Offset,
    val bounds: Rect,
)

enum class BlockDockKind {
    PreviousStack,
    NextStack,
    ValueInput,
    ValueOutput,
    StatementInput,
}

data class BlockVisualGeometry(
    val path: Path,
    val visualBounds: Rect,
    val contentBounds: Rect,
    val interactionBounds: Rect,
    val docks: List<BlockDockGeometry> = emptyList(),
    val branchBounds: List<Rect> = emptyList(),
) {
    init {
        require(!path.isEmpty) { "Block path must not be empty." }
        require(visualBounds.isPositiveFinite()) { "Visual bounds must be positive and finite." }
        require(contentBounds.isFiniteRect()) { "Content bounds must be finite." }
        require(interactionBounds.isPositiveFinite()) { "Interaction bounds must be positive and finite." }
    }
}

fun Rect.isFiniteRect(): Boolean =
    left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()

fun Rect.isPositiveFinite(): Boolean =
    isFiniteRect() && width > 0f && height > 0f
