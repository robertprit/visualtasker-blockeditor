package de.visualtasker.blockeditor.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp

@Composable
internal fun CompatibleWrappingRow(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 0.dp,
    verticalSpacing: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeables = measurables.map { measurable ->
            measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        }
        val positions = wrappingPositions(
            sizes = placeables.map { it.width to it.height },
            availableWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE,
            horizontalSpacing = horizontalSpacing.roundToPx(),
            verticalSpacing = verticalSpacing.roundToPx(),
        )
        val contentWidth = positions.maxOfOrNull { it.x + placeables[it.index].width } ?: 0
        val contentHeight = positions.maxOfOrNull { it.y + placeables[it.index].height } ?: 0

        layout(
            width = constraints.constrainWidth(contentWidth),
            height = constraints.constrainHeight(contentHeight),
        ) {
            positions.forEach { position ->
                placeables[position.index].placeRelative(position.x, position.y)
            }
        }
    }
}

internal data class WrappingPosition(val index: Int, val x: Int, val y: Int)

internal fun wrappingPositions(
    sizes: List<Pair<Int, Int>>,
    availableWidth: Int,
    horizontalSpacing: Int,
    verticalSpacing: Int,
): List<WrappingPosition> {
    var x = 0
    var y = 0
    var rowHeight = 0
    return sizes.mapIndexed { index, (width, height) ->
        var itemX = if (x == 0) 0 else x + horizontalSpacing
        if (x > 0 && itemX + width > availableWidth) {
            x = 0
            y += rowHeight + verticalSpacing
            rowHeight = 0
            itemX = 0
        }
        val position = WrappingPosition(index = index, x = itemX, y = y)
        x = itemX + width
        rowHeight = maxOf(rowHeight, height)
        position
    }
}
