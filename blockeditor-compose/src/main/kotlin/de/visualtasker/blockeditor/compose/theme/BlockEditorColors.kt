package de.visualtasker.blockeditor.compose.theme

import androidx.compose.ui.graphics.Color

data class BlockEditorColors(
    val event: Color,
    val action: Color,
    val control: Color,
    val logic: Color,
    val debug: Color,
    val variable: Color,
    val workspaceBackground: Color,
    val snapHighlight: Color,
    val dragShadow: Color,
    val blockStroke: Color,
    val blockText: Color,
    val slotBackground: Color,
)

fun blockEditorColors(category: String): Color = when (category) {
    "event" -> Color(0xFFFFC107)
    "action" -> Color(0xFF42A5F5)
    "control" -> Color(0xFFFF7043)
    "logic" -> Color(0xFF66BB6A)
    "debug" -> Color(0xFFAB47BC)
    "variable" -> Color(0xFF26A69A)
    else -> Color(0xFF78909C)
}

fun defaultBlockEditorColors(): BlockEditorColors = darkBlockEditorColors()

fun darkBlockEditorColors(): BlockEditorColors = BlockEditorColors(
    event = blockEditorColors("event"),
    action = blockEditorColors("action"),
    control = blockEditorColors("control"),
    logic = blockEditorColors("logic"),
    debug = blockEditorColors("debug"),
    variable = blockEditorColors("variable"),
    workspaceBackground = Color(0xFF12151C),
    snapHighlight = Color(0x6642A5F5),
    dragShadow = Color(0x66000000),
    blockStroke = Color(0xFF90A4AE),
    blockText = Color(0xFFF5F7FA),
    slotBackground = Color(0x33000000),
)

fun lightBlockEditorColors(): BlockEditorColors = BlockEditorColors(
    event = blockEditorColors("event"),
    action = blockEditorColors("action"),
    control = blockEditorColors("control"),
    logic = blockEditorColors("logic"),
    debug = blockEditorColors("debug"),
    variable = blockEditorColors("variable"),
    workspaceBackground = Color(0xFFECEFF4),
    snapHighlight = Color(0x6642A5F5),
    dragShadow = Color(0x33000000),
    blockStroke = Color(0xFF263238),
    blockText = Color(0xFF102027),
    slotBackground = Color(0x33FFFFFF),
)
