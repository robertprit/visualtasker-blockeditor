package de.visualtasker.blockeditor.compose.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ExpressiveDarkColors = darkColorScheme(
    primary = Color(0xFF9AD1FF),
    onPrimary = Color(0xFF003355),
    primaryContainer = Color(0xFF1B4A6B),
    onPrimaryContainer = Color(0xFFD2E8FF),
    secondary = Color(0xFF7FD8C8),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF1F4F48),
    onSecondaryContainer = Color(0xFFB8F2E6),
    tertiary = Color(0xFFD0BCFF),
    onTertiary = Color(0xFF381E72),
    tertiaryContainer = Color(0xFF4F378B),
    onTertiaryContainer = Color(0xFFEADDFF),
    background = Color(0xFF0F1117),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF161A22),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF252B36),
    onSurfaceVariant = Color(0xFFC4C7CF),
    outline = Color(0xFF8E9199),
    outlineVariant = Color(0xFF44474F),
)

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val ExpressiveTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)

@Composable
fun BlockEditorTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ExpressiveDarkColors,
        shapes = ExpressiveShapes,
        typography = ExpressiveTypography,
        content = content,
    )
}
