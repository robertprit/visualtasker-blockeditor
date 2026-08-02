package de.visualtasker.blockeditor.compose.layers

import de.visualtasker.blockeditor.domain.Rect
import de.visualtasker.blockeditor.layout.BlockLayout
import de.visualtasker.blockeditor.layout.InlineReporterLayout

internal fun InlineReporterLayout.relativeTo(blockLayout: BlockLayout): InlineReporterLayout =
    shiftedBy(dx = -blockLayout.bounds.x, dy = -blockLayout.bounds.y)

private fun InlineReporterLayout.shiftedBy(dx: Float, dy: Float): InlineReporterLayout =
    copy(
        leftSlot = leftSlot.shiftedBy(dx, dy),
        operatorBounds = operatorBounds.shiftedBy(dx, dy),
        rightSlot = rightSlot.shiftedBy(dx, dy),
    )

private fun Rect.shiftedBy(dx: Float, dy: Float): Rect =
    copy(x = x + dx, y = y + dy)
