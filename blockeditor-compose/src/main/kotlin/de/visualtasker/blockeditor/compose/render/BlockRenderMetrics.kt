package de.visualtasker.blockeditor.compose.render

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import de.visualtasker.blockeditor.layout.LayoutConstants

@Immutable
data class BlockRenderMetrics(
    val reporterDockSize: Size = Size(LayoutConstants.REPORTER_WIDTH, LayoutConstants.REPORTER_HEIGHT),
    val reporterDockRadius: CornerRadius = CornerRadius(
        LayoutConstants.REPORTER_HEIGHT / 2f,
        LayoutConstants.REPORTER_HEIGHT / 2f,
    ),
    val inlineOperatorCorner: CornerRadius = CornerRadius(8f, 8f),
    val branchSectionCorner: CornerRadius = CornerRadius(4f, 4f),
    val normalStrokeWidth: Float = 2f,
    val selectedStrokeWidth: Float = 4f,
    val dockStrokeWidth: Float = 1.8f,
    val dockConnectedStrokeWidth: Float = 1.2f,
)

internal val DefaultBlockRenderMetrics = BlockRenderMetrics()

