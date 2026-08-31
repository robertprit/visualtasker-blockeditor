package de.visualtasker.blockeditor.compose.render

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class ConnectionVisualStyle(
    val accent: Color,
    val fillAlpha: Float,
    val strokeAlpha: Float,
    val anchorAlpha: Float,
)

internal fun reporterDockVisualStyle(
    dataType: String?,
    connected: Boolean,
): ConnectionVisualStyle {
    val accent = when (dataType?.lowercase()) {
        "boolean", "bool" -> Color(0xFFFFD54F)
        "number", "int", "float", "double" -> Color(0xFF8BD17C)
        "string", "text" -> Color(0xFF64D8CB)
        "image", "region", "template" -> Color(0xFFFFB36B)
        else -> Color(0xFFBCA7FF)
    }
    return ConnectionVisualStyle(
        accent = accent,
        fillAlpha = if (connected) 0.30f else 0.58f,
        strokeAlpha = if (connected) 0.46f else 0.86f,
        anchorAlpha = if (connected) 0.42f else 0.82f,
    )
}

