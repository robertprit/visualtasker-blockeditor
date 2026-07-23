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
    val gridDot: Color,
    val snapHighlight: Color,
    val dragShadow: Color,
    val blockStroke: Color,
    val blockText: Color,
    val slotBackground: Color,
    val unsupportedFill: Color,
    val unsupportedStroke: Color,
    val unsupportedText: Color,
)

fun blockEditorColors(category: String): Color = when (category) {
    "event" -> Color(0xFFFFC107)
    "action" -> Color(0xFF42A5F5)
    "emscript" -> Color(0xFF5E97F6)
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
    gridDot = Color(0x334B5563),
    snapHighlight = Color(0x6642A5F5),
    dragShadow = Color(0x66000000),
    blockStroke = Color(0xFF90A4AE),
    blockText = Color(0xFFF5F7FA),
    slotBackground = Color(0x33000000),
    unsupportedFill = Color(0xFF93000A),
    unsupportedStroke = Color(0xFFFFDAD6),
    unsupportedText = Color(0xFFFFDAD6),
)

fun lightBlockEditorColors(): BlockEditorColors = BlockEditorColors(
    event = blockEditorColors("event"),
    action = blockEditorColors("action"),
    control = blockEditorColors("control"),
    logic = blockEditorColors("logic"),
    debug = blockEditorColors("debug"),
    variable = blockEditorColors("variable"),
    workspaceBackground = Color(0xFFECEFF4),
    gridDot = Color(0x33475569),
    snapHighlight = Color(0x6642A5F5),
    dragShadow = Color(0x33000000),
    blockStroke = Color(0xFF263238),
    blockText = Color(0xFF102027),
    slotBackground = Color(0x33FFFFFF),
    unsupportedFill = Color(0xFFFFDAD6),
    unsupportedStroke = Color(0xFFBA1A1A),
    unsupportedText = Color(0xFF410002),
)
