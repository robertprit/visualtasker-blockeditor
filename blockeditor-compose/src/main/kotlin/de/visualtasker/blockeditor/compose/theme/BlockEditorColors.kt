package de.visualtasker.blockeditor.compose.theme

import androidx.compose.ui.graphics.Color
import de.visualtasker.blockeditor.registry.BlockCategories

private val categoryOverrides = mutableMapOf<String, Color>()

fun setBlockCategoryColorOverride(category: String, color: Color?) {
    if (color == null) {
        categoryOverrides.remove(category)
    } else {
        categoryOverrides[category] = color
    }
}

fun clearBlockCategoryColorOverrides() {
    categoryOverrides.clear()
}

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

fun blockEditorColors(category: String): Color = categoryOverrides[category] ?: defaultBlockCategoryColor(category)

fun defaultBlockCategoryColor(category: String): Color = when (category) {
    BlockCategories.EVENT -> Color(0xFFB78B00)
    BlockCategories.ACTION -> Color(0xFF3E6F91)
    BlockCategories.FEEDBACK -> Color(0xFF8A5F76)
    BlockCategories.EMSCRIPT -> Color(0xFF56687A)
    BlockCategories.INPUT -> Color(0xFF4B6F8F)
    BlockCategories.PERCEPTION -> Color(0xFF3F735F)
    BlockCategories.CONTROL -> Color(0xFF87684A)
    BlockCategories.LOGIC -> Color(0xFF586E4B)
    BlockCategories.VARIABLES -> Color(0xFF6D607E)
    BlockCategories.FLOW -> Color(0xFF7B6750)
    BlockCategories.RUNTIME -> Color(0xFF686E78)
    BlockCategories.DEBUG -> Color(0xFF75617A)
    BlockCategories.VARIABLE -> Color(0xFF5A716B)
    BlockCategories.CUSTOM -> Color(0xFF66707A)
    else -> Color(0xFF66707A)
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
    blockStroke = Color(0xFFB3BDC8),
    blockText = Color(0xFFF5F7FA),
    slotBackground = Color(0x33000000),
    unsupportedFill = Color(0xFF5F523A),
    unsupportedStroke = Color(0xFFE4CFA4),
    unsupportedText = Color(0xFFFFF0CF),
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
    blockStroke = Color(0xFF39434D),
    blockText = Color(0xFF102027),
    slotBackground = Color(0x33FFFFFF),
    unsupportedFill = Color(0xFFFFF1D2),
    unsupportedStroke = Color(0xFF826B2A),
    unsupportedText = Color(0xFF3C2F12),
)
