package de.visualtasker.blockeditor.compose.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import de.visualtasker.blockeditor.compose.theme.blockEditorColors
import de.visualtasker.blockeditor.domain.BlockNode
import de.visualtasker.blockeditor.domain.asString
import de.visualtasker.blockeditor.compose.icons.drawBlockTypeIcon
import de.visualtasker.blockeditor.compose.shapes.BlockShapes
import de.visualtasker.blockeditor.layout.BranchSectionKind
import de.visualtasker.blockeditor.layout.BranchSectionLayout
import de.visualtasker.blockeditor.layout.InlineReporterLayout
import de.visualtasker.blockeditor.layout.LayoutConstants
import de.visualtasker.blockeditor.registry.BlockDefinition
import de.visualtasker.blockeditor.registry.BlockRegistry
import de.visualtasker.blockeditor.registry.BlockTypes

internal fun DrawScope.drawBlock(
    block: BlockNode,
    definition: BlockDefinition?,
    topLeft: Offset,
    width: Float,
    height: Float,
    textMeasurer: TextMeasurer,
    colors: de.visualtasker.blockeditor.compose.theme.BlockEditorColors,
    registry: BlockRegistry,
    branchDividerYs: List<Float> = emptyList(),
    branchSections: List<BranchSectionLayout> = emptyList(),
    inlineReporterLayout: InlineReporterLayout? = null,
    visualPathProvider: BlockVisualPathProvider = BlockVisualPathProvider.Legacy,
) {
    val category = definition?.category ?: "unknown"
    val unsupported = definition == null
    val fillColor = if (unsupported) colors.unsupportedFill else blockEditorColors(category)
    val strokeColor = if (unsupported) colors.unsupportedStroke else colors.blockStroke
    val textColor = if (unsupported) colors.unsupportedText else colors.blockText
    val strokePathEffect = if (unsupported) PathEffect.dashPathEffect(floatArrayOf(12f, 8f)) else null
    val path = resolveBlockVisualPath(
        definition = definition,
        size = Size(width, height),
        branchDividerYs = branchDividerYs,
        provider = visualPathProvider,
    )
    translate(topLeft.x, topLeft.y) {
        drawPath(path, fillColor, style = Fill)
        drawPath(path, strokeColor, style = Stroke(width = 2f, pathEffect = strokePathEffect))
        branchDividerYs.forEach { dividerY ->
            val stem = BlockShapes.branchStemTabPath(dividerY)
            drawPath(stem, fillColor, style = Fill)
            drawPath(stem, strokeColor, style = Stroke(width = 2f, pathEffect = strokePathEffect))
        }
        val sectionLabelStyle = TextStyle(
            color = textColor.copy(alpha = 0.85f),
            fontSize = 11.sp,
        )
        branchSections.forEach { section ->
            val bounds = section.bounds
            val sectionColor = when (section.kind) {
                BranchSectionKind.HeaderCondition,
                BranchSectionKind.ElifCondition,
                -> Color.Black.copy(alpha = 0.22f)
                BranchSectionKind.BranchDivider -> Color.Black.copy(alpha = 0.28f)
            }
            drawRoundRect(
                color = sectionColor,
                topLeft = Offset(bounds.x, bounds.y),
                size = Size(bounds.width, bounds.height),
                cornerRadius = CornerRadius(4f, 4f),
            )
            if (section.kind == BranchSectionKind.BranchDivider) {
                drawLine(
                    color = strokeColor.copy(alpha = 0.7f),
                    start = Offset(bounds.x, bounds.y + bounds.height / 2f),
                    end = Offset(bounds.x + bounds.width, bounds.y + bounds.height / 2f),
                    strokeWidth = 2f,
                )
            }
            val labelTop = bounds.y + (bounds.height - textMeasurer.measure(section.label, sectionLabelStyle).size.height) / 2f
            drawText(
                textMeasurer = textMeasurer,
                text = section.label,
                topLeft = Offset(bounds.x + LayoutConstants.SLOT_PADDING, labelTop),
                style = sectionLabelStyle,
            )
            if (section.inputName != null) {
                val dockX = bounds.right - LayoutConstants.REPORTER_WIDTH - LayoutConstants.SLOT_PADDING
                val dockY = bounds.y + (bounds.height - LayoutConstants.REPORTER_HEIGHT) / 2f
                drawRoundRect(
                    color = strokeColor,
                    topLeft = Offset(dockX, dockY),
                    size = Size(LayoutConstants.REPORTER_WIDTH, LayoutConstants.REPORTER_HEIGHT),
                    cornerRadius = CornerRadius(6f, 6f),
                    style = Stroke(width = 2f),
                )
                drawCircle(
                    color = strokeColor,
                    radius = LayoutConstants.ANCHOR_RADIUS * 0.55f,
                    center = Offset(dockX, dockY + LayoutConstants.REPORTER_HEIGHT / 2f),
                    style = Stroke(width = 2f),
                )
            }
        }
        val blockType = definition?.id ?: block.type
        val label = block.displayLabel(definition?.label ?: "Unsupported: ${blockType.substringAfterLast('.')}")
        val isReporter = definition?.isReporter == true
        val isInlineReporter = definition?.inputsInline == true

        if (isInlineReporter && inlineReporterLayout != null) {
            val operator = block.fields["operator"]?.asString() ?: "add"
            val operatorStyle = TextStyle(
                color = textColor,
                fontSize = 13.sp,
            )
            val operatorLayout = textMeasurer.measure(operator, operatorStyle)
            val operatorTop = inlineReporterLayout.operatorBounds.y +
                (inlineReporterLayout.operatorBounds.height - operatorLayout.size.height) / 2f
            drawText(
                textMeasurer = textMeasurer,
                text = operator,
                topLeft = Offset(
                    inlineReporterLayout.operatorBounds.x +
                        (inlineReporterLayout.operatorBounds.width - operatorLayout.size.width) / 2f,
                    operatorTop,
                ),
                style = operatorStyle,
            )
            listOf("Input1" to inlineReporterLayout.leftSlot, "Input2" to inlineReporterLayout.rightSlot)
                .forEach { (inputName, slot) ->
                    val connected = block.valueInputs.find { it.name == inputName }
                        ?.connection?.connectedTo != null
                    if (!connected) {
                        drawRoundRect(
                            color = strokeColor,
                            topLeft = Offset(slot.x, slot.y),
                            size = Size(slot.width, slot.height),
                            cornerRadius = CornerRadius(6f, 6f),
                            style = Stroke(width = 2f),
                        )
                    }
                }
            return@translate
        }

        val isVariableReporter = isReporter && (
            blockType.startsWith(BlockTypes.VARIABLE_REPORTER_PREFIX) ||
                blockType == BlockTypes.VARIABLE_GET
            )
        if (isVariableReporter) {
            val displayName = block.fields["variable"]?.asString()?.takeIf { it.isNotBlank() } ?: label
            val textStyle = TextStyle(
                color = textColor,
                fontSize = 13.sp,
            )
            val textLayout = textMeasurer.measure(displayName, textStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = displayName,
                topLeft = Offset(
                    (width - textLayout.size.width) / 2f,
                    (height - textLayout.size.height) / 2f,
                ),
                style = textStyle,
            )
            return@translate
        }

        val iconSize = if (isReporter) 18f else 22f
        val textStyle = TextStyle(
            color = textColor,
            fontSize = if (isReporter) 12.sp else 14.sp,
        )
        val headerHeight = if (isReporter) height else LayoutConstants.HEADER_HEIGHT
        val iconTopLeft = Offset(LayoutConstants.SLOT_PADDING, LayoutConstants.SLOT_PADDING)
        drawBlockTypeIcon(
            type = blockType,
            topLeft = iconTopLeft,
            tint = textColor,
            size = iconSize,
        )
        if (!isReporter) {
            drawGroupDragIndicator(textColor.copy(alpha = 0.78f), LayoutConstants.SLOT_PADDING + iconSize / 2f, headerHeight)
        }
        val labelX = iconTopLeft.x + iconSize + 8f
        val hasHeaderCondition = branchSections.any { it.kind == BranchSectionKind.HeaderCondition }
        val maxTextWidth = if (hasHeaderCondition) {
            (LayoutConstants.NESTED_INDENT - labelX - 4f).coerceAtLeast(0f)
        } else {
            (width - labelX - LayoutConstants.SLOT_PADDING).coerceAtLeast(0f)
        }
        val drawableTextWidth = drawableLabelWidth(
            requestedWidth = maxTextWidth,
            canvasRemainingWidth = size.width - topLeft.x - labelX,
        )
        if (drawableTextWidth <= 0f) return@translate
        val displayLabel = truncateLabel(label, drawableTextWidth, textMeasurer, textStyle)
        val textLayout = textMeasurer.measure(displayLabel, textStyle)
        val textTopLeft = Offset(
            labelX,
            (headerHeight - textLayout.size.height) / 2f,
        )
        val drawableTextHeight = size.height - topLeft.y - textTopLeft.y
        if (!hasDrawableTextArea(drawableTextWidth, drawableTextHeight)) return@translate
        drawText(
            textMeasurer = textMeasurer,
            text = displayLabel,
            topLeft = textTopLeft,
            style = textStyle,
        )
    }
}

private fun DrawScope.drawGroupDragIndicator(
    color: Color,
    centerX: Float,
    headerHeight: Float,
) {
    val top = (headerHeight / 2f) + 11f
    repeat(3) { index ->
        val y = top + index * 4.5f
        drawLine(
            color = color,
            start = Offset(centerX - 7f, y),
            end = Offset(centerX + 7f, y),
            strokeWidth = 2f,
        )
    }
}

private fun BlockNode.displayLabel(fallback: String): String {
    if (!isStartBlock()) return fallback
    return fields["script"]?.asString()
        ?.removeSuffix(".ems")
        ?.takeIf { it.isNotBlank() }
        ?: fallback
}

private fun BlockNode.isStartBlock(): Boolean =
    type == BlockTypes.EVENT_START ||
        type == "em_on_start" ||
        metadata["macro.import.canonicalCommand"] == "EVENT.ON_START"

internal fun drawableLabelWidth(requestedWidth: Float, canvasRemainingWidth: Float): Float =
    minOf(requestedWidth, canvasRemainingWidth).coerceAtLeast(0f)

internal fun hasDrawableTextArea(width: Float, height: Float): Boolean = width > 0f && height > 0f

private fun truncateLabel(
    label: String,
    maxWidth: Float,
    textMeasurer: TextMeasurer,
    style: TextStyle,
): String {
    if (maxWidth <= 0f) return ""
    if (textMeasurer.measure(label, style).size.width <= maxWidth) return label
    var truncated = label
    while (truncated.length > 1 &&
        textMeasurer.measure("$truncated…", style).size.width > maxWidth
    ) {
        truncated = truncated.dropLast(1)
    }
    return if (truncated.length < label.length) "$truncated…" else truncated
}
