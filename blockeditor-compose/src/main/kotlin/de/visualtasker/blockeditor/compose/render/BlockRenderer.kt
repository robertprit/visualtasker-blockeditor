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
import de.visualtasker.blockeditor.registry.VisualTaskerCommandCatalog
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
    renderMetrics: BlockRenderMetrics = DefaultBlockRenderMetrics,
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
        drawPath(path, strokeColor, style = Stroke(width = renderMetrics.normalStrokeWidth, pathEffect = strokePathEffect))
        if (selected) {
            drawPath(path, selectionColor, style = Stroke(width = renderMetrics.selectedStrokeWidth))
        }
        val sectionLabelStyle = TextStyle(
            color = textColor.copy(alpha = 0.85f),
            fontSize = 11.sp,
        )
        branchSections.forEach { section ->
            val bounds = section.bounds
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
                val valueInput = block.valueInputs.find { it.name == section.inputName }
                val dockX = bounds.right - renderMetrics.reporterDockSize.width - LayoutConstants.SLOT_PADDING
                val dockY = bounds.y + (bounds.height - renderMetrics.reporterDockSize.height) / 2f
                val connected = valueInput?.connection?.connectedTo != null
                drawReporterDockSlot(
                    topLeft = Offset(dockX, dockY),
                    size = renderMetrics.reporterDockSize,
                    outlineColor = textColor,
                    backgroundColor = colors.slotBackground,
                    connected = connected,
                    dataType = valueInput?.connection?.accepts?.firstOrNull(),
                    metrics = renderMetrics,
                    showOutputAnchor = true,
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

        if (isReporter && (block.collapsed || reporterMode == ReporterVisualMode.COMPACT) && reporterFamily != null) {
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

        if (isReporter && block.collapsed) {
            val markerStyle = TextStyle(color = textColor.copy(alpha = 0.86f), fontSize = 12.sp)
            val markerTextSize = safeDrawableTextSize(width, height) ?: return@translate
            val markerLayout = measureTextSafely(textMeasurer, "...", markerStyle, markerTextSize)
            drawTextSafely(
                textMeasurer = textMeasurer,
                text = "...",
                topLeft = centeredTextTopLeft(
                    containerWidth = width,
                    containerHeight = height,
                    contentWidth = markerLayout.size.width.toFloat(),
                    contentHeight = markerLayout.size.height.toFloat(),
                ) ?: return@translate,
                style = markerStyle,
                availableWidth = markerTextSize.width,
                availableHeight = markerTextSize.height,
            )
            return@translate
        }

        if (isInlineReporter && inlineReporterLayout != null) {
            val operator = block.inlineOperatorLabel(definition)
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.18f),
                topLeft = Offset(inlineReporterLayout.operatorBounds.x, inlineReporterLayout.operatorBounds.y),
                size = Size(inlineReporterLayout.operatorBounds.width, inlineReporterLayout.operatorBounds.height),
                cornerRadius = renderMetrics.inlineOperatorCorner,
                style = Fill,
            )
            drawRoundRect(
                color = textColor.copy(alpha = 0.42f),
                topLeft = Offset(inlineReporterLayout.operatorBounds.x, inlineReporterLayout.operatorBounds.y),
                size = Size(inlineReporterLayout.operatorBounds.width, inlineReporterLayout.operatorBounds.height),
                cornerRadius = renderMetrics.inlineOperatorCorner,
                style = Stroke(width = 1.4f),
            )
            listOf(
                inlineReporterLayout.leftInputName to inlineReporterLayout.leftSlot,
                inlineReporterLayout.rightInputName to inlineReporterLayout.rightSlot,
            ).forEach { (inputName, slot) ->
                val valueInput = block.valueInputs.find { it.name == inputName }
                val connected = valueInput?.connection?.connectedTo != null
                drawReporterDockSlot(
                    topLeft = Offset(slot.x, slot.y),
                    size = Size(slot.width, slot.height),
                    outlineColor = textColor,
                    backgroundColor = colors.slotBackground,
                    connected = connected,
                    dataType = valueInput?.connection?.accepts?.firstOrNull(),
                    metrics = renderMetrics,
                    showOutputAnchor = false,
                )
            }
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
        val maxTextWidth = headerLabelWidth(
            blockWidth = width,
            labelX = labelX,
            hasHeaderCondition = hasHeaderCondition,
            collapsedCommand = block.collapsed && !isReporter,
            dockWidth = renderMetrics.reporterDockSize.width,
        )
        val drawableTextWidth = maxTextWidth.coerceAtLeast(0f)
        if (drawableTextWidth <= 0f) return@translate
        val displayLabel = truncateLabel(label, drawableTextWidth, textMeasurer, textStyle)
        val headerTextSize = safeDrawableTextSize(
            width = drawableTextWidth,
            height = headerHeight,
        ) ?: return@translate
        val textLayout = measureTextSafely(textMeasurer, displayLabel, textStyle, headerTextSize)
        val textTopLeft = Offset(
            labelX,
            (headerHeight - textLayout.size.height) / 2f,
        )
        val drawableTextHeight = headerHeight - textTopLeft.y
        if (!hasDrawableTextArea(drawableTextWidth, drawableTextHeight)) return@translate
        drawTextSafely(
            textMeasurer = textMeasurer,
            text = displayLabel,
            topLeft = textTopLeft,
            style = textStyle,
            availableWidth = drawableTextWidth,
            availableHeight = drawableTextHeight,
        )
        if (!isReporter && blockType.startsWith(BlockTypes.EMSCRIPT_COMMAND_PREFIX)) {
            drawCommandRuntimeBadge(
                block = block,
                definition = definition,
                topLeft = Offset(width - 24f - LayoutConstants.SLOT_PADDING, (headerHeight - 16f) / 2f),
                textMeasurer = textMeasurer,
                textColor = textColor,
            )
        }
        if (block.collapsed && !isReporter) {
            val markerWidth = 28f
            val markerHeight = 18f
            val markerLeft = (width - markerWidth - LayoutConstants.SLOT_PADDING).coerceAtLeast(labelX)
            val markerTop = (headerHeight - markerHeight) / 2f
            drawRoundRect(
                color = textColor.copy(alpha = 0.18f),
                topLeft = Offset(markerLeft, markerTop),
                size = Size(markerWidth, markerHeight),
                cornerRadius = CornerRadius(markerHeight / 2f, markerHeight / 2f),
                style = Fill,
            )
            val markerStyle = TextStyle(color = textColor.copy(alpha = 0.82f), fontSize = 12.sp)
            val markerTextSize = safeDrawableTextSize(markerWidth, markerHeight) ?: return@translate
            val markerLayout = measureTextSafely(textMeasurer, "...", markerStyle, markerTextSize)
            drawTextSafely(
                textMeasurer = textMeasurer,
                text = "...",
                topLeft = Offset(
                    markerLeft + (markerWidth - markerLayout.size.width) / 2f,
                    markerTop + (markerHeight - markerLayout.size.height) / 2f,
                ),
                style = markerStyle,
                availableWidth = markerTextSize.width,
                availableHeight = markerTextSize.height,
            )
        }
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

private fun DrawScope.drawReporterDockSlot(
    topLeft: Offset,
    size: Size,
    outlineColor: Color,
    backgroundColor: Color,
    connected: Boolean,
    dataType: String?,
    metrics: BlockRenderMetrics,
    showOutputAnchor: Boolean,
) {
    val radius = if (size == metrics.reporterDockSize) {
        metrics.reporterDockRadius
    } else {
        CornerRadius(size.height / 2f, size.height / 2f)
    }
    val style = reporterDockVisualStyle(dataType, connected)
    drawRoundRect(
        color = backgroundColor.copy(alpha = style.fillAlpha),
        topLeft = topLeft,
        size = size,
        cornerRadius = radius,
        style = Fill,
    )
    drawRoundRect(
        color = style.accent.copy(alpha = style.strokeAlpha),
        topLeft = topLeft,
        size = size,
        cornerRadius = radius,
        style = Stroke(width = if (connected) metrics.dockConnectedStrokeWidth else metrics.dockStrokeWidth),
    )
    if (showOutputAnchor) {
        drawCircle(
            color = outlineColor.copy(alpha = style.anchorAlpha),
            radius = LayoutConstants.ANCHOR_RADIUS * 0.46f,
            center = Offset(topLeft.x, topLeft.y + size.height / 2f),
            style = Stroke(width = 1.7f),
        )
    }
}

internal fun DrawScope.drawInlineReporterDockSlotOverlays(
    block: BlockNode,
    inlineReporterLayout: InlineReporterLayout,
) {
    listOf(
        inlineReporterLayout.leftInputName to inlineReporterLayout.leftSlot,
        inlineReporterLayout.rightInputName to inlineReporterLayout.rightSlot,
    ).forEach { (inputName, slot) ->
        val valueInput = block.valueInputs.find { it.name == inputName }
        val connected = valueInput?.connection?.connectedTo != null
        val style = reporterDockVisualStyle(
            dataType = valueInput?.connection?.accepts?.firstOrNull(),
            connected = connected,
        )
        val strokeWidth = if (connected) 2.4f else 1.8f
        val innerHeight = (slot.height - strokeWidth * 2f).coerceAtLeast(0f)

        if (!connected) {
            drawRoundRect(
                color = style.accent.copy(alpha = 0.14f),
                topLeft = Offset(slot.x, slot.y),
                size = Size(slot.width, slot.height),
                cornerRadius = CornerRadius(slot.height / 2f, slot.height / 2f),
                style = Fill,
            )
        }
        drawRoundRect(
            color = style.accent.copy(alpha = if (connected) 0.72f else 0.9f),
            topLeft = Offset(slot.x, slot.y),
            size = Size(slot.width, slot.height),
            cornerRadius = CornerRadius(slot.height / 2f, slot.height / 2f),
            style = Stroke(width = strokeWidth),
        )
        drawRoundRect(
            color = Color.White.copy(alpha = if (connected) 0.24f else 0.16f),
            topLeft = Offset(slot.x + strokeWidth, slot.y + strokeWidth),
            size = Size(
                (slot.width - strokeWidth * 2f).coerceAtLeast(0f),
                innerHeight,
            ),
            cornerRadius = CornerRadius(innerHeight / 2f, innerHeight / 2f),
            style = Stroke(width = 0.9f),
        )
    }
}

private fun DrawScope.drawCommandRuntimeBadge(
    block: BlockNode,
    definition: BlockDefinition?,
    topLeft: Offset,
    textMeasurer: TextMeasurer,
    textColor: Color,
) {
    if (!topLeft.x.isFinite() || topLeft.x < LayoutConstants.SLOT_PADDING) return
    val runtimeStatus = definition
        ?.metadata
        ?.get(VisualTaskerCommandCatalog.METADATA_RUNTIME_STATUS)
        .orEmpty()
    val pluginOwner = definition
        ?.metadata
        ?.get(VisualTaskerCommandCatalog.METADATA_PLUGIN_OWNER)
        .orEmpty()
    val disabled = block.fields["active"]?.asString() == "false"
    val badgeText = when {
        disabled -> "off"
        runtimeStatus == "adapter-gated" || pluginOwner != "visualtasker.core" -> "plug"
        runtimeStatus == "simulate" -> "dry"
        else -> "core"
    }
    val badgeColor = when (badgeText) {
        "off" -> Color(0xFF9CA3AF)
        "plug" -> Color(0xFFFFB74D)
        "dry" -> Color(0xFF4DB6AC)
        else -> Color(0xFF81C784)
    }
    val badgeSize = Size(24f, 16f)
    drawRoundRect(
        color = badgeColor.copy(alpha = 0.28f),
        topLeft = topLeft,
        size = badgeSize,
        cornerRadius = CornerRadius(8f, 8f),
        style = Fill,
    )
    drawRoundRect(
        color = badgeColor.copy(alpha = 0.72f),
        topLeft = topLeft,
        size = badgeSize,
        cornerRadius = CornerRadius(8f, 8f),
        style = Stroke(width = 1.1f),
    )
    val style = TextStyle(color = textColor.copy(alpha = 0.88f), fontSize = 7.sp)
    val layout = textMeasurer.measure(badgeText, style)
    drawTextSafely(
        textMeasurer = textMeasurer,
        text = badgeText,
        topLeft = Offset(
            topLeft.x + (badgeSize.width - layout.size.width) / 2f,
            topLeft.y + (badgeSize.height - layout.size.height) / 2f,
        ),
        style = style,
        availableWidth = badgeSize.width,
        availableHeight = badgeSize.height,
    )
}

private fun BlockNode.structuralLabel(
    definition: BlockDefinition?,
    fallback: String,
): String {
    if (!isStartBlock()) {
        if (type.startsWith(BlockTypes.EMSCRIPT_COMMAND_PREFIX)) {
            return commandCanvasLabel(definition)
        }
        val base = definition?.label ?: fallback
        val detailed = fields["displayMode"]?.asString() == "detailed"
        val parameterNames = if (detailed) {
            definition
                ?.fields
                .orEmpty()
                .filterNot { it.key.endsWith(".source") }
                .joinToString(" ") { it.key }
        } else {
            ""
        }
        val chips = if (detailed) {
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

private fun BlockNode.commandCanvasLabel(definition: BlockDefinition?): String {
    val metadataShortName = definition
        ?.metadata
        ?.get(VisualTaskerCommandCatalog.METADATA_SHORT_NAME)
        ?.takeIf { it.isNotBlank() }
    val fieldCommand = fields["command"]
        ?.asString()
        ?.takeIf { it.isNotBlank() }
    return metadataShortName
        ?: fieldCommand?.substringAfterLast('.')
        ?: definition?.label?.takeIf { it.isNotBlank() }
        ?: type.removePrefix(BlockTypes.EMSCRIPT_COMMAND_PREFIX).substringAfterLast('.')
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

internal fun headerLabelWidth(
    blockWidth: Float,
    labelX: Float,
    hasHeaderCondition: Boolean,
    collapsedCommand: Boolean,
    dockWidth: Float,
): Float {
    val reservedRightWidth = when {
        hasHeaderCondition -> dockWidth + LayoutConstants.SLOT_PADDING * 2f
        collapsedCommand -> 36f + LayoutConstants.SLOT_PADDING
        else -> LayoutConstants.SLOT_PADDING
    }
    return (blockWidth - labelX - reservedRightWidth).coerceAtLeast(0f)
}

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
