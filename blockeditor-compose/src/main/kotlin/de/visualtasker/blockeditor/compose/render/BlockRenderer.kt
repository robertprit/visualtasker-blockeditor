package de.visualtasker.blockeditor.compose.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import de.visualtasker.blockeditor.compose.theme.BlockEditorColors
import de.visualtasker.blockeditor.domain.BlockNode
import de.visualtasker.blockeditor.domain.FieldValue
import de.visualtasker.blockeditor.domain.asString
import de.visualtasker.blockeditor.compose.icons.drawBlockTypeIcon
import de.visualtasker.blockeditor.compose.model.ReporterFamily
import de.visualtasker.blockeditor.compose.model.ReporterVisualMode
import de.visualtasker.blockeditor.compose.model.reporterVisualModeFor
import de.visualtasker.blockeditor.compose.model.resolveReporterFamily
import de.visualtasker.blockeditor.compose.shapes.BlockShapes
import de.visualtasker.blockeditor.layout.BranchSectionKind
import de.visualtasker.blockeditor.layout.BranchSectionLayout
import de.visualtasker.blockeditor.layout.InlineReporterLayout
import de.visualtasker.blockeditor.layout.LayoutConstants
import de.visualtasker.blockeditor.registry.BlockDefinition
import de.visualtasker.blockeditor.registry.BlockRegistry
import de.visualtasker.blockeditor.registry.BlockTypes
import kotlin.math.pow

internal fun DrawScope.drawBlock(
    block: BlockNode,
    definition: BlockDefinition?,
    topLeft: Offset,
    width: Float,
    height: Float,
    textMeasurer: TextMeasurer,
    colors: BlockEditorColors,
    registry: BlockRegistry,
    branchDividerYs: List<Float> = emptyList(),
    branchSections: List<BranchSectionLayout> = emptyList(),
    inlineReporterLayout: InlineReporterLayout? = null,
    visualPathProvider: BlockVisualPathProvider = BlockVisualPathProvider.Legacy,
    selected: Boolean = false,
    selectionColor: Color = Color(0xFF42A5F5),
) {
    val category = definition?.category ?: "unknown"
    val unsupported = definition == null
    val fillColor = when {
        unsupported -> colors.unsupportedFill
        block.isStartBlock() -> block.startBlockColor() ?: colors.forCategory(category)
        else -> colors.forCategory(category)
    }
    val strokeColor = if (unsupported) colors.unsupportedStroke else colors.blockStroke
    val textColor = if (unsupported) colors.unsupportedText else contrastTextColor(fillColor)
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
        if (selected) {
            drawPath(path, selectionColor, style = Stroke(width = 4f))
        }
        branchDividerYs.forEach { dividerY ->
            val stem = BlockShapes.branchStemTabPath(dividerY)
            drawPath(stem, fillColor, style = Fill)
            drawPath(stem, strokeColor, style = Stroke(width = 2f, pathEffect = strokePathEffect))
            if (selected) {
                drawPath(stem, selectionColor, style = Stroke(width = 4f))
            }
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
            val sectionTextSize = safeDrawableTextSize(
                width = bounds.width - LayoutConstants.SLOT_PADDING * 2,
                height = bounds.height,
            )
            val sectionLabelLayout = sectionTextSize?.let {
                measureTextSafely(textMeasurer, section.label, sectionLabelStyle, it)
            }
            val labelTop = sectionLabelLayout?.let {
                bounds.y + (bounds.height - it.size.height) / 2f
            } ?: bounds.y
            drawTextSafely(
                textMeasurer = textMeasurer,
                text = section.label,
                topLeft = Offset(bounds.x + LayoutConstants.SLOT_PADDING, labelTop),
                style = sectionLabelStyle,
                availableWidth = sectionTextSize?.width ?: 0f,
                availableHeight = sectionTextSize?.height ?: 0f,
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
        val label = block.structuralLabel(definition, "Unsupported: ${blockType.substringAfterLast('.')}")
        val isReporter = definition?.isReporter == true
        val isInlineReporter = isReporter && definition?.inputsInline == true
        val reporterMode = reporterVisualModeFor(block)
        val reporterFamily = resolveReporterFamily(blockType, definition)
        val boolValue = when (val value = block.fields["value"]) {
            is FieldValue.Bool -> value.value
            is FieldValue.Text -> value.value.equals("true", ignoreCase = true)
            else -> false
        }

        if (isReporter && reporterMode == ReporterVisualMode.COMPACT && reporterFamily != null) {
            when (reporterFamily) {
                ReporterFamily.BOOLEAN -> {
                    val iconSize = (minOf(width, height) - 6f).coerceAtLeast(14f)
                    val iconTopLeft = Offset((width - iconSize) / 2f, (height - iconSize) / 2f)
                    drawBooleanTriangleIcon(
                        value = boolValue,
                        topLeft = iconTopLeft,
                        tint = textColor,
                        size = iconSize,
                    )
                }
                else -> {
                    drawReporterCompactBadge(
                        family = reporterFamily,
                        width = width,
                        height = height,
                        tint = textColor,
                        textMeasurer = textMeasurer,
                    )
                }
            }
            return@translate
        }

        if (isInlineReporter && inlineReporterLayout != null) {
            val operator = block.inlineOperatorLabel(definition)
            val operatorStyle = TextStyle(
                color = textColor,
                fontSize = 13.sp,
            )
            val operatorTextSize = safeDrawableTextSize(
                width = inlineReporterLayout.operatorBounds.width,
                height = inlineReporterLayout.operatorBounds.height,
            ) ?: return@translate
            val operatorLayout = measureTextSafely(textMeasurer, operator, operatorStyle, operatorTextSize)
            val operatorTop = inlineReporterLayout.operatorBounds.y +
                (inlineReporterLayout.operatorBounds.height - operatorLayout.size.height) / 2f
            drawTextSafely(
                textMeasurer = textMeasurer,
                text = operator,
                topLeft = Offset(
                    inlineReporterLayout.operatorBounds.x +
                        (inlineReporterLayout.operatorBounds.width - operatorLayout.size.width) / 2f,
                    operatorTop,
                ),
                style = operatorStyle,
                availableWidth = operatorTextSize.width,
                availableHeight = operatorTextSize.height,
            )
            listOf(
                inlineReporterLayout.leftInputName to inlineReporterLayout.leftSlot,
                inlineReporterLayout.rightInputName to inlineReporterLayout.rightSlot,
            )
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
            val displayName = block.variableDisplayLabel(definition)
            val textStyle = TextStyle(
                color = textColor,
                fontSize = 13.sp,
            )
            val reporterTextSize = safeDrawableTextSize(width, height) ?: return@translate
            val textLayout = measureTextSafely(textMeasurer, displayName, textStyle, reporterTextSize)
            val textTopLeft = centeredTextTopLeft(
                containerWidth = width,
                containerHeight = height,
                contentWidth = textLayout.size.width.toFloat(),
                contentHeight = textLayout.size.height.toFloat(),
            ) ?: return@translate
            drawTextSafely(
                textMeasurer = textMeasurer,
                text = displayName,
                topLeft = textTopLeft,
                style = textStyle,
                availableWidth = width - textTopLeft.x,
                availableHeight = height - textTopLeft.y,
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
        val headerTextSize = safeDrawableTextSize(
            width = drawableTextWidth,
            height = size.height - topLeft.y,
        ) ?: return@translate
        val textLayout = measureTextSafely(textMeasurer, displayLabel, textStyle, headerTextSize)
        val textTopLeft = Offset(
            labelX,
            (headerHeight - textLayout.size.height) / 2f,
        )
        val drawableTextHeight = size.height - topLeft.y - textTopLeft.y
        if (!hasDrawableTextArea(drawableTextWidth, drawableTextHeight)) return@translate
        drawTextSafely(
            textMeasurer = textMeasurer,
            text = displayLabel,
            topLeft = textTopLeft,
            style = textStyle,
            availableWidth = drawableTextWidth,
            availableHeight = drawableTextHeight,
        )
    }
}

internal fun BlockNode.inlineOperatorLabel(definition: BlockDefinition?): String {
    val raw = fields["operator"]?.asString()?.trim().orEmpty()
    return stableOperatorLabel(raw)
        ?: definition?.label?.takeIf { it.isNotBlank() }
        ?: "op"
}

internal fun BlockNode.variableDisplayLabel(definition: BlockDefinition?): String =
    fields["variable"]?.asString()?.takeIf { it.isNotBlank() }
        ?: fields["name"]?.asString()?.takeIf { it.isNotBlank() }
        ?: definition?.label?.takeIf { it.isNotBlank() }
        ?: type.removePrefix(BlockTypes.VARIABLE_REPORTER_PREFIX).takeIf { it != type && it.isNotBlank() }
        ?: "var"

private fun stableOperatorLabel(raw: String): String? = when (raw) {
    "ADD", "add", "+" -> "+"
    "SUBTRACT", "subtract", "-" -> "-"
    "MULTIPLY", "multiply", "*" -> "x"
    "DIVIDE", "divide", "/" -> "/"
    "MODULO", "modulo", "%" -> "%"
    "EQUAL", "EQUALS", "eq", "==" -> "="
    "NOT_EQUAL", "NOT_EQUALS", "ne", "!=" -> "!="
    "LESS", "LESS_THAN", "lt", "<" -> "<"
    "LESS_OR_EQUAL", "LESS_THAN_OR_EQUALS", "lte", "<=" -> "<="
    "GREATER", "GREATER_THAN", "gt", ">" -> ">"
    "GREATER_OR_EQUAL", "GREATER_THAN_OR_EQUALS", "gte", ">=" -> ">="
    "AND", "and" -> "AND"
    "OR", "or" -> "OR"
    "NOT", "not" -> "NOT"
    else -> null
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

private fun BlockNode.structuralLabel(
    definition: BlockDefinition?,
    fallback: String,
): String {
    if (!isStartBlock()) {
        val base = definition?.label ?: fallback
        val parameterNames = definition
            ?.fields
            .orEmpty()
            .filterNot { it.key.endsWith(".source") }
            .joinToString(" ") { it.key }
        val chips = if (fields["displayMode"]?.asString() == "detailed") {
            buildList {
                val paramCount = definition?.fields.orEmpty().size
                if (paramCount > 0) add("$paramCount params")
                if (fields["active"]?.asString() == "false") add("inactive")
                if (metadata["macro.import.status"] in setOf("diagnostic", "legacy")) add("diagnostic")
                if (metadata["macro.import.status"] == "unknown") add("unknown")
                if (fields.keys.any { it.endsWith(".source") }) add("has source")
            }.joinToString(" ")
        } else {
            ""
        }
        return listOf(base, parameterNames, chips)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }
    return fields["script"]?.asString()
        ?.removeSuffix(".ems")
        ?.takeIf { it.isNotBlank() }
        ?: fallback
}

private fun BlockNode.isStartBlock(): Boolean =
    type == BlockTypes.EVENT_START ||
        type == "em_on_start" ||
        metadata["macro.import.canonicalCommand"] == "EVENT.ON_START"

private fun BlockEditorColors.forCategory(category: String): Color = when (category) {
    "event" -> event
    "action" -> action
    "emscript" -> action
    "control" -> control
    "logic" -> logic
    "debug" -> debug
    "variable" -> variable
    else -> variable
}

private fun BlockNode.startBlockColor(): Color? = when (fields["color"]?.asString()) {
    "blue" -> Color(0xFF5E97F6)
    "green" -> Color(0xFF43A047)
    "violet" -> Color(0xFF7E57C2)
    "orange" -> Color(0xFFFFB300)
    "red" -> Color(0xFFE53935)
    "gray" -> Color(0xFF78909C)
    else -> null
}

internal fun contrastTextColor(background: Color): Color =
    if (relativeLuminance(background) > 0.48f) Color(0xFF111827) else Color(0xFFF8FAFC)

private fun relativeLuminance(color: Color): Float {
    fun channel(value: Float): Float =
        if (value <= 0.03928f) {
            value / 12.92f
        } else {
            ((value + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        }
    return 0.2126f * channel(color.red) +
        0.7152f * channel(color.green) +
        0.0722f * channel(color.blue)
}

internal fun drawableLabelWidth(requestedWidth: Float, canvasRemainingWidth: Float): Float =
    minOf(requestedWidth, canvasRemainingWidth).coerceAtLeast(0f)

internal fun hasDrawableTextArea(width: Float, height: Float): Boolean = width > 0f && height > 0f

internal fun safeDrawableTextSize(width: Float, height: Float): Size? =
    if (width.isFinite() && height.isFinite() && width > 0f && height > 0f) {
        Size(width, height)
    } else {
        null
    }

internal fun safeTextConstraintWidth(computedWidth: Float, requestedMinWidth: Float = 0f): Int? {
    if (!computedWidth.isFinite() || !requestedMinWidth.isFinite()) return null
    val availableWidth = computedWidth.coerceAtLeast(0f)
    if (availableWidth <= 0f) return null
    val minWidth = requestedMinWidth.coerceAtLeast(0f).coerceAtMost(availableWidth)
    return maxOf(minWidth, availableWidth).toInt().coerceAtLeast(1)
}

internal fun centeredTextTopLeft(
    containerWidth: Float,
    containerHeight: Float,
    contentWidth: Float,
    contentHeight: Float,
): Offset? {
    safeDrawableTextSize(containerWidth, containerHeight) ?: return null
    return Offset(
        x = ((containerWidth - contentWidth) / 2f).coerceAtLeast(0f),
        y = ((containerHeight - contentHeight) / 2f).coerceAtLeast(0f),
    )
}

private fun measureTextSafely(
    textMeasurer: TextMeasurer,
    text: String,
    style: TextStyle,
    availableSize: Size,
): TextLayoutResult {
    safeTextConstraintWidth(availableSize.width) ?: error("Text measurement requires positive finite width.")
    return textMeasurer.measure(
        text = text,
        style = style,
        overflow = TextOverflow.Clip,
        softWrap = false,
        maxLines = 1,
    )
}

private fun DrawScope.drawTextSafely(
    textMeasurer: TextMeasurer,
    text: String,
    topLeft: Offset,
    style: TextStyle,
    availableWidth: Float,
    availableHeight: Float,
) {
    val drawSize = safeDrawableTextSize(availableWidth, availableHeight) ?: return
    drawText(
        textMeasurer = textMeasurer,
        text = text,
        topLeft = topLeft,
        style = style,
        overflow = TextOverflow.Clip,
        softWrap = false,
        maxLines = 1,
        size = drawSize,
    )
}

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

private fun DrawScope.drawBooleanTriangleIcon(
    value: Boolean,
    topLeft: Offset,
    tint: Color,
    size: Float,
) {
    val triangle = Path().apply {
        if (value) {
            moveTo(topLeft.x + size / 2f, topLeft.y + 2f)
            lineTo(topLeft.x + size - 2f, topLeft.y + size - 2f)
            lineTo(topLeft.x + 2f, topLeft.y + size - 2f)
        } else {
            moveTo(topLeft.x + 2f, topLeft.y + 2f)
            lineTo(topLeft.x + size - 2f, topLeft.y + 2f)
            lineTo(topLeft.x + size / 2f, topLeft.y + size - 2f)
        }
        close()
    }
    drawPath(path = triangle, color = tint.copy(alpha = 0.92f), style = Fill)
    drawPath(path = triangle, color = tint.copy(alpha = 0.55f), style = Stroke(width = 1.8f))
}

private fun DrawScope.drawReporterCompactBadge(
    family: ReporterFamily,
    width: Float,
    height: Float,
    tint: Color,
    textMeasurer: TextMeasurer,
) {
    val edge = compactReporterBadgeEdge(width, height) ?: return
    val chipTopLeft = Offset((width - edge) / 2f, (height - edge) / 2f)
    val shapeColor = tint.copy(alpha = 0.18f)
    val shapeStroke = tint.copy(alpha = 0.58f)
    when (family) {
        ReporterFamily.STRING, ReporterFamily.OPERATOR_STRING -> {
            drawRoundRect(shapeColor, chipTopLeft, Size(edge, edge), CornerRadius(3f, 3f))
            drawRoundRect(shapeStroke, chipTopLeft, Size(edge, edge), CornerRadius(3f, 3f), style = Stroke(1.8f))
        }
        ReporterFamily.NUMBER, ReporterFamily.OPERATOR_NUM -> {
            val radius = CornerRadius(edge / 2.6f, edge / 2.6f)
            drawRoundRect(shapeColor, chipTopLeft, Size(edge, edge), radius)
            drawRoundRect(shapeStroke, chipTopLeft, Size(edge, edge), radius, style = Stroke(1.8f))
        }
        ReporterFamily.ANY, ReporterFamily.OPERATOR_ANY -> {
            val center = Offset(chipTopLeft.x + edge / 2f, chipTopLeft.y + edge / 2f)
            drawCircle(shapeColor, edge / 2f, center)
            drawCircle(shapeStroke, edge / 2f, center, style = Stroke(1.8f))
        }
        ReporterFamily.CUSTOM, ReporterFamily.OPERATOR_CUSTOM, ReporterFamily.OPERATOR_BOOL -> {
            val diamond = Path().apply {
                moveTo(chipTopLeft.x + edge / 2f, chipTopLeft.y)
                lineTo(chipTopLeft.x + edge, chipTopLeft.y + edge / 2f)
                lineTo(chipTopLeft.x + edge / 2f, chipTopLeft.y + edge)
                lineTo(chipTopLeft.x, chipTopLeft.y + edge / 2f)
                close()
            }
            drawPath(diamond, shapeColor, style = Fill)
            drawPath(diamond, shapeStroke, style = Stroke(1.8f))
        }
        ReporterFamily.BOOLEAN -> return
    }
    val symbol = when (family) {
        ReporterFamily.STRING, ReporterFamily.OPERATOR_STRING -> "S"
        ReporterFamily.NUMBER, ReporterFamily.OPERATOR_NUM -> "N"
        ReporterFamily.ANY, ReporterFamily.OPERATOR_ANY -> "ANY"
        ReporterFamily.CUSTOM, ReporterFamily.OPERATOR_CUSTOM -> "C"
        ReporterFamily.OPERATOR_BOOL -> "B"
        ReporterFamily.BOOLEAN -> return
    }
    val symbolStyle = TextStyle(color = tint, fontSize = 11.sp)
    val layout = textMeasurer.measure(symbol, symbolStyle)
    val symbolOffset = centeredTextTopLeft(edge, edge, layout.size.width.toFloat(), layout.size.height.toFloat()) ?: return
    val symbolTopLeft = chipTopLeft + symbolOffset
    if (!symbolTopLeft.x.isFinite() || !symbolTopLeft.y.isFinite()) return
    drawTextSafely(
        textMeasurer = textMeasurer,
        text = symbol,
        topLeft = symbolTopLeft,
        style = symbolStyle,
        availableWidth = edge,
        availableHeight = edge,
    )
}

internal fun compactReporterBadgeEdge(width: Float, height: Float): Float? {
    if (!width.isFinite() || !height.isFinite()) return null
    if (width <= 0f || height <= 0f) return null
    return (minOf(width, height) - 6f).coerceAtLeast(14f)
}
