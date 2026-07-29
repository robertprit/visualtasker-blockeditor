package de.visualtasker.blockeditor.compose.shapes

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.visualtasker.blockeditor.layout.LayoutConstants

@Immutable
data class BlockShapeTokens(
    val grid: Dp = 4.dp,
    val statementCorner: Dp = 14.dp,
    val eventCorner: Dp = 20.dp,
    val containerCorner: Dp = 16.dp,
    val branchCorner: Dp = 10.dp,
    val stackDockX: Dp = LayoutConstants.STACK_DOCK_X.dp,
    val stackConnectorWidth: Dp = LayoutConstants.STACK_CONNECTOR_WIDTH.dp,
    val stackConnectorDepth: Dp = LayoutConstants.STACK_CONNECTOR_DEPTH.dp,
    val stackConnectorShoulder: Dp = 7.dp,
    val statementMinHeight: Dp = 60.dp,
    val eventMinHeight: Dp = 64.dp,
    val containerSpineWidth: Dp = 20.dp,
    val containerBodyIndent: Dp = 52.dp,
    val containerHeaderHeight: Dp = 64.dp,
    val branchHeaderHeight: Dp = 38.dp,
    val containerFooterHeight: Dp = 24.dp,
    val nodeHorizontalPadding: Dp = 16.dp,
    val nodeVerticalPadding: Dp = 12.dp,
    val nodeGap: Dp = 8.dp,
    val normalStroke: Dp = 1.dp,
    val selectedStroke: Dp = 2.dp,
) {
    fun toPx(density: Density): ResolvedBlockShapeTokens = with(density) {
        ResolvedBlockShapeTokens(
            grid = grid.toPx(),
            statementCorner = statementCorner.toPx(),
            eventCorner = eventCorner.toPx(),
            containerCorner = containerCorner.toPx(),
            branchCorner = branchCorner.toPx(),
            stackDockX = stackDockX.toPx(),
            stackConnector = StackConnectorProfile(
                width = stackConnectorWidth.toPx(),
                depth = stackConnectorDepth.toPx(),
                shoulder = stackConnectorShoulder.toPx(),
            ),
            statementMinHeight = statementMinHeight.toPx(),
            eventMinHeight = eventMinHeight.toPx(),
            containerSpineWidth = containerSpineWidth.toPx(),
            containerBodyIndent = containerBodyIndent.toPx(),
            containerHeaderHeight = containerHeaderHeight.toPx(),
            branchHeaderHeight = branchHeaderHeight.toPx(),
            containerFooterHeight = containerFooterHeight.toPx(),
            nodeHorizontalPadding = nodeHorizontalPadding.toPx(),
            nodeVerticalPadding = nodeVerticalPadding.toPx(),
            nodeGap = nodeGap.toPx(),
            normalStroke = normalStroke.toPx(),
            selectedStroke = selectedStroke.toPx(),
        )
    }
}

@Immutable
data class ResolvedBlockShapeTokens(
    val grid: Float,
    val statementCorner: Float,
    val eventCorner: Float,
    val containerCorner: Float,
    val branchCorner: Float,
    val stackDockX: Float,
    val stackConnector: StackConnectorProfile,
    val statementMinHeight: Float,
    val eventMinHeight: Float,
    val containerSpineWidth: Float,
    val containerBodyIndent: Float,
    val containerHeaderHeight: Float,
    val branchHeaderHeight: Float,
    val containerFooterHeight: Float,
    val nodeHorizontalPadding: Float,
    val nodeVerticalPadding: Float,
    val nodeGap: Float,
    val normalStroke: Float,
    val selectedStroke: Float,
)
