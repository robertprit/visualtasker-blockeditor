package de.visualtasker.blockeditor.compose.shapes

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import kotlin.math.min

object MaterialExpressiveBlockShapeBuilder {
    fun geometry(
        request: BlockShapeRequest,
        tokens: ResolvedBlockShapeTokens,
    ): BlockVisualGeometry {
        val width = request.size.width.coerceAtLeast(tokens.grid)
        val height = request.size.height.coerceAtLeast(tokens.grid)
        val safeRequest = request.copy(size = androidx.compose.ui.geometry.Size(width, height))
        return when (request.family) {
            BlockShapeFamily.Event -> eventGeometry(safeRequest, tokens)
            BlockShapeFamily.Statement,
            BlockShapeFamily.Terminal,
            -> statementGeometry(safeRequest, tokens)
            BlockShapeFamily.Container -> containerGeometry(safeRequest, tokens)
            BlockShapeFamily.Reporter,
            BlockShapeFamily.BooleanReporter,
            BlockShapeFamily.InlineOperator,
            -> reporterGeometry(safeRequest, tokens)
            BlockShapeFamily.Annotation -> annotationGeometry(safeRequest, tokens)
        }
    }

    private fun eventGeometry(
        request: BlockShapeRequest,
        tokens: ResolvedBlockShapeTokens,
    ): BlockVisualGeometry {
        val width = request.size.width
        val height = request.size.height.coerceAtLeast(tokens.eventMinHeight)
        val bodyBottom = height - tokens.stackConnector.depth
        val path = Path().apply {
            val radius = min(tokens.eventCorner, min(width, bodyBottom) / 2f)
            moveTo(radius, 0f)
            lineTo(width - radius, 0f)
            arcTo(Rect(width - radius * 2f, 0f, width, radius * 2f), 270f, 90f, false)
            lineTo(width, bodyBottom - radius)
            arcTo(Rect(width - radius * 2f, bodyBottom - radius * 2f, width, bodyBottom), 0f, 90f, false)
            addBottomPlug(tokens.stackDockX.coerceIn(radius, width - radius), bodyBottom, tokens.stackConnector)
            lineTo(radius, bodyBottom)
            arcTo(Rect(0f, bodyBottom - radius * 2f, radius * 2f, bodyBottom), 90f, 90f, false)
            lineTo(0f, radius)
            arcTo(Rect(0f, 0f, radius * 2f, radius * 2f), 180f, 90f, false)
            close()
        }
        return geometryFor(path, width, height, tokens, hasPrevious = false, hasNext = true)
    }

    private fun statementGeometry(
        request: BlockShapeRequest,
        tokens: ResolvedBlockShapeTokens,
    ): BlockVisualGeometry {
        val width = request.size.width
        val height = request.size.height.coerceAtLeast(tokens.statementMinHeight)
        val bodyBottom = height - if (request.family == BlockShapeFamily.Terminal) 0f else tokens.stackConnector.depth
        val dockX = tokens.stackDockX.coerceIn(tokens.statementCorner, width - tokens.statementCorner)
        val path = Path().apply {
            val radius = min(tokens.statementCorner, min(width, bodyBottom) / 2f)
            moveTo(radius, 0f)
            addTopSocket(dockX, tokens.stackConnector)
            lineTo(width - radius, 0f)
            arcTo(Rect(width - radius * 2f, 0f, width, radius * 2f), 270f, 90f, false)
            lineTo(width, bodyBottom - radius)
            arcTo(Rect(width - radius * 2f, bodyBottom - radius * 2f, width, bodyBottom), 0f, 90f, false)
            if (request.family != BlockShapeFamily.Terminal) {
                addBottomPlug(dockX, bodyBottom, tokens.stackConnector)
            }
            lineTo(radius, bodyBottom)
            arcTo(Rect(0f, bodyBottom - radius * 2f, radius * 2f, bodyBottom), 90f, 90f, false)
            lineTo(0f, radius)
            arcTo(Rect(0f, 0f, radius * 2f, radius * 2f), 180f, 90f, false)
            close()
        }
        return geometryFor(
            path = path,
            width = width,
            height = height,
            tokens = tokens,
            hasPrevious = true,
            hasNext = request.family != BlockShapeFamily.Terminal,
        )
    }

    private fun containerGeometry(
        request: BlockShapeRequest,
        tokens: ResolvedBlockShapeTokens,
    ): BlockVisualGeometry {
        val width = request.size.width
        val height = request.size.height.coerceAtLeast(tokens.containerHeaderHeight + tokens.containerFooterHeight)
        val bodyBottom = height - tokens.stackConnector.depth
        val dockX = tokens.stackDockX.coerceIn(tokens.containerCorner, width - tokens.containerCorner)
        val path = Path().apply {
            val radius = min(tokens.containerCorner, min(width, bodyBottom) / 2f)
            moveTo(radius, 0f)
            addTopSocket(dockX, tokens.stackConnector)
            lineTo(width - radius, 0f)
            arcTo(Rect(width - radius * 2f, 0f, width, radius * 2f), 270f, 90f, false)
            lineTo(width, bodyBottom - radius)
            arcTo(Rect(width - radius * 2f, bodyBottom - radius * 2f, width, bodyBottom), 0f, 90f, false)
            addBottomPlug(dockX, bodyBottom, tokens.stackConnector)
            lineTo(radius, bodyBottom)
            arcTo(Rect(0f, bodyBottom - radius * 2f, radius * 2f, bodyBottom), 90f, 90f, false)
            lineTo(0f, radius)
            arcTo(Rect(0f, 0f, radius * 2f, radius * 2f), 180f, 90f, false)
            close()
        }
        val branchBounds = request.branchDividerYs
            .filter { it > tokens.containerHeaderHeight && it < bodyBottom }
            .map { y ->
                Rect(
                    left = tokens.containerBodyIndent,
                    top = y,
                    right = width - tokens.nodeHorizontalPadding,
                    bottom = (y + tokens.branchHeaderHeight).coerceAtMost(bodyBottom),
                )
            }
        return geometryFor(path, width, height, tokens, hasPrevious = true, hasNext = true, branchBounds = branchBounds)
    }

    private fun reporterGeometry(
        request: BlockShapeRequest,
        tokens: ResolvedBlockShapeTokens,
    ): BlockVisualGeometry {
        val width = request.size.width
        val height = request.size.height
        val radius = min(height / 2f, width / 2f)
        val path = Path().apply {
            addRoundRect(RoundRect(Rect(0f, 0f, width, height), CornerRadius(radius, radius)))
        }
        val bounds = Rect(0f, 0f, width, height)
        return BlockVisualGeometry(
            path = path,
            visualBounds = bounds,
            contentBounds = bounds.deflate(tokens.nodeHorizontalPadding, tokens.nodeVerticalPadding),
            interactionBounds = bounds,
            docks = listOf(
                BlockDockGeometry(
                    kind = BlockDockKind.ValueOutput,
                    center = Offset(0f, height / 2f),
                    bounds = Rect(0f, 0f, tokens.stackConnector.depth, height),
                ),
            ),
        )
    }

    private fun annotationGeometry(
        request: BlockShapeRequest,
        tokens: ResolvedBlockShapeTokens,
    ): BlockVisualGeometry {
        val width = request.size.width
        val height = request.size.height
        val radius = min(tokens.branchCorner, min(width, height) / 2f)
        val path = Path().apply {
            addRoundRect(RoundRect(Rect(0f, 0f, width, height), CornerRadius(radius, radius)))
        }
        val bounds = Rect(0f, 0f, width, height)
        return BlockVisualGeometry(
            path = path,
            visualBounds = bounds,
            contentBounds = bounds.deflate(tokens.nodeHorizontalPadding, tokens.nodeVerticalPadding),
            interactionBounds = bounds,
        )
    }

    private fun geometryFor(
        path: Path,
        width: Float,
        height: Float,
        tokens: ResolvedBlockShapeTokens,
        hasPrevious: Boolean,
        hasNext: Boolean,
        branchBounds: List<Rect> = emptyList(),
    ): BlockVisualGeometry {
        val bounds = Rect(0f, 0f, width, height)
        val dockX = tokens.stackDockX.coerceIn(0f, width)
        val docks = buildList {
            if (hasPrevious) {
                add(
                    BlockDockGeometry(
                        BlockDockKind.PreviousStack,
                        Offset(dockX, 0f),
                        Rect(dockX - tokens.stackConnector.halfWidth, 0f, dockX + tokens.stackConnector.halfWidth, tokens.stackConnector.depth),
                    ),
                )
            }
            if (hasNext) {
                add(
                    BlockDockGeometry(
                        BlockDockKind.NextStack,
                        Offset(dockX, height),
                        Rect(
                            dockX - tokens.stackConnector.halfWidth,
                            height - tokens.stackConnector.depth,
                            dockX + tokens.stackConnector.halfWidth,
                            height,
                        ),
                    ),
                )
            }
        }
        return BlockVisualGeometry(
            path = path,
            visualBounds = bounds,
            contentBounds = bounds.deflate(tokens.nodeHorizontalPadding, tokens.nodeVerticalPadding),
            interactionBounds = bounds,
            docks = docks,
            branchBounds = branchBounds,
        )
    }

    private fun Rect.deflate(horizontal: Float, vertical: Float): Rect =
        Rect(
            left = (left + horizontal).coerceAtMost(right),
            top = (top + vertical).coerceAtMost(bottom),
            right = (right - horizontal).coerceAtLeast(left),
            bottom = (bottom - vertical).coerceAtLeast(top),
        )
}
