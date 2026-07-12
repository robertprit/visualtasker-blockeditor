package de.visualtasker.blockeditor.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import de.visualtasker.blockeditor.registry.BlockTypes

internal fun DrawScope.drawBlockTypeIcon(
    type: String,
    topLeft: Offset,
    tint: Color,
    size: Float,
) {
    translate(topLeft.x, topLeft.y) {
        scale(size / 24f, size / 24f, pivot = Offset.Zero) {
            when (type) {
                BlockTypes.EVENT_START -> drawPlayIcon(tint)
                BlockTypes.ACTION_CLICK_TEXT -> drawTapIcon(tint)
                BlockTypes.ACTION_WAIT -> drawHourglassIcon(tint)
                BlockTypes.DEBUG_LOG -> drawLogIcon(tint)
                BlockTypes.CONTROL_REPEAT -> drawRepeatIcon(tint)
                BlockTypes.CONTROL_WHILE -> drawLoopIcon(tint)
                BlockTypes.CONTROL_IF,
                BlockTypes.CONTROL_IF_ELSE,
                -> drawBranchIcon(tint)
                BlockTypes.CONTROL_IF_ELSEIF_ELSE -> drawTreeIcon(tint)
                BlockTypes.LOGIC_SCREEN_CONTAINS -> drawSearchIcon(tint)
                BlockTypes.LOGIC_BOOLEAN -> drawToggleIcon(tint)
                BlockTypes.LOGIC_AND -> drawJoinIcon(tint)
                BlockTypes.LOGIC_OR -> drawForkIcon(tint)
                BlockTypes.LOGIC_OPERATE -> drawOperateIcon(tint)
                BlockTypes.VARIABLE_GET -> drawVariableIcon(tint)
                BlockTypes.VARIABLE_SET -> drawEditIcon(tint)
                else -> when {
                    type.startsWith(BlockTypes.VARIABLE_REPORTER_PREFIX) -> drawVariableIcon(tint)
                    else -> drawGenericIcon(tint)
                }
            }
        }
    }
}

internal fun DrawScope.drawSlotIcon(
    slotName: String,
    topLeft: Offset,
    tint: Color,
    size: Float,
) {
    translate(topLeft.x, topLeft.y) {
        scale(size / 24f, size / 24f, pivot = Offset.Zero) {
            when (slotName) {
                BlockTypes.SLOT_THEN -> drawCheckIcon(tint)
                BlockTypes.SLOT_ELIF -> drawBranchIcon(tint)
                BlockTypes.SLOT_ELSE -> drawCloseIcon(tint)
                BlockTypes.SLOT_DO, BlockTypes.SLOT_BODY -> drawLoopIcon(tint)
                else -> drawGenericIcon(tint)
            }
        }
    }
}

private fun DrawScope.drawPlayIcon(color: Color) {
    val path = Path().apply {
        moveTo(8f, 5f)
        lineTo(19f, 12f)
        lineTo(8f, 19f)
        close()
    }
    drawPath(path, color, style = Fill)
}

private fun DrawScope.drawTapIcon(color: Color) {
    drawCircle(color, radius = 5f, center = Offset(12f, 10f), style = Fill)
    drawCircle(color, radius = 8f, center = Offset(12f, 10f), style = Stroke(width = 2f))
    drawLine(color, Offset(12f, 18f), Offset(12f, 22f), strokeWidth = 2f)
}

private fun DrawScope.drawHourglassIcon(color: Color) {
    val path = Path().apply {
        moveTo(7f, 4f)
        lineTo(17f, 4f)
        lineTo(12f, 12f)
        lineTo(17f, 20f)
        lineTo(7f, 20f)
        lineTo(12f, 12f)
        close()
    }
    drawPath(path, color, style = Stroke(width = 2f))
}

private fun DrawScope.drawLogIcon(color: Color) {
    drawRoundRect(color, topLeft = Offset(6f, 5f), size = Size(12f, 14f), cornerRadius = CornerRadius(2f, 2f), style = Stroke(width = 2f))
    drawLine(color, Offset(8f, 9f), Offset(16f, 9f), strokeWidth = 2f)
    drawLine(color, Offset(8f, 13f), Offset(14f, 13f), strokeWidth = 2f)
}

private fun DrawScope.drawRepeatIcon(color: Color) {
    drawArc(color, -40f, 260f, false, topLeft = Offset(5f, 6f), size = Size(14f, 12f), style = Stroke(width = 2f))
    val arrow = Path().apply {
        moveTo(16f, 6f)
        lineTo(19f, 6f)
        lineTo(19f, 9f)
        close()
    }
    drawPath(arrow, color, style = Fill)
}

private fun DrawScope.drawLoopIcon(color: Color) {
    drawArc(color, 20f, 320f, false, topLeft = Offset(5f, 5f), size = Size(14f, 14f), style = Stroke(width = 2f))
}

private fun DrawScope.drawBranchIcon(color: Color) {
    drawLine(color, Offset(6f, 12f), Offset(18f, 12f), strokeWidth = 2f)
    drawLine(color, Offset(12f, 6f), Offset(12f, 18f), strokeWidth = 2f)
}

private fun DrawScope.drawTreeIcon(color: Color) {
    drawLine(color, Offset(12f, 4f), Offset(12f, 20f), strokeWidth = 2f)
    drawLine(color, Offset(12f, 10f), Offset(18f, 14f), strokeWidth = 2f)
    drawLine(color, Offset(12f, 14f), Offset(6f, 18f), strokeWidth = 2f)
}

private fun DrawScope.drawSearchIcon(color: Color) {
    drawCircle(color, radius = 5f, center = Offset(10f, 10f), style = Stroke(width = 2f))
    drawLine(color, Offset(14f, 14f), Offset(19f, 19f), strokeWidth = 2f)
}

private fun DrawScope.drawToggleIcon(color: Color) {
    drawRoundRect(color, topLeft = Offset(5f, 9f), size = Size(14f, 6f), cornerRadius = CornerRadius(3f, 3f), style = Fill)
    drawCircle(Color.White, radius = 3f, center = Offset(15f, 12f), style = Fill)
}

private fun DrawScope.drawJoinIcon(color: Color) {
    drawCircle(color, radius = 4f, center = Offset(8f, 12f), style = Stroke(width = 2f))
    drawCircle(color, radius = 4f, center = Offset(16f, 12f), style = Stroke(width = 2f))
    drawLine(color, Offset(12f, 12f), Offset(12f, 12f), strokeWidth = 2f)
}

private fun DrawScope.drawForkIcon(color: Color) {
    drawLine(color, Offset(6f, 6f), Offset(12f, 12f), strokeWidth = 2f)
    drawLine(color, Offset(18f, 6f), Offset(12f, 12f), strokeWidth = 2f)
    drawLine(color, Offset(12f, 12f), Offset(12f, 18f), strokeWidth = 2f)
}

private fun DrawScope.drawVariableIcon(color: Color) {
    drawLine(color, Offset(6f, 18f), Offset(18f, 6f), strokeWidth = 2f)
    drawLine(color, Offset(8f, 6f), Offset(6f, 10f), strokeWidth = 2f)
    drawLine(color, Offset(16f, 18f), Offset(18f, 14f), strokeWidth = 2f)
}

private fun DrawScope.drawEditIcon(color: Color) {
    val path = Path().apply {
        moveTo(6f, 18f)
        lineTo(10f, 18f)
        lineTo(18f, 10f)
        lineTo(14f, 6f)
        lineTo(6f, 14f)
        close()
    }
    drawPath(path, color, style = Stroke(width = 2f))
}

private fun DrawScope.drawCheckIcon(color: Color) {
    drawLine(color, Offset(5f, 12f), Offset(10f, 17f), strokeWidth = 2f)
    drawLine(color, Offset(10f, 17f), Offset(19f, 7f), strokeWidth = 2f)
}

private fun DrawScope.drawCloseIcon(color: Color) {
    drawLine(color, Offset(7f, 7f), Offset(17f, 17f), strokeWidth = 2f)
    drawLine(color, Offset(17f, 7f), Offset(7f, 17f), strokeWidth = 2f)
}

private fun DrawScope.drawOperateIcon(color: Color) {
    drawLine(color, Offset(6f, 12f), Offset(18f, 12f), strokeWidth = 2f)
    drawLine(color, Offset(12f, 6f), Offset(12f, 18f), strokeWidth = 2f)
}

private fun DrawScope.drawGenericIcon(color: Color) {
    drawRoundRect(color, topLeft = Offset(6f, 6f), size = Size(12f, 12f), cornerRadius = CornerRadius(2f, 2f), style = Stroke(width = 2f))
}
