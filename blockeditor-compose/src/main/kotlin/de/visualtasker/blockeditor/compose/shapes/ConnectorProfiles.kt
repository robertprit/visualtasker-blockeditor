package de.visualtasker.blockeditor.compose.shapes

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Path

@Immutable
data class StackConnectorProfile(
    val width: Float,
    val depth: Float,
    val shoulder: Float,
) {
    init {
        require(width > 0f && width.isFinite()) { "Stack connector width must be positive and finite." }
        require(depth > 0f && depth.isFinite()) { "Stack connector depth must be positive and finite." }
        require(shoulder > 0f && shoulder.isFinite()) { "Stack connector shoulder must be positive and finite." }
    }

    val halfWidth: Float get() = width / 2f
}

@Immutable
data class ValueConnectorProfile(
    val width: Float,
    val height: Float,
    val shoulder: Float,
) {
    init {
        require(width > 0f && width.isFinite()) { "Value connector width must be positive and finite." }
        require(height > 0f && height.isFinite()) { "Value connector height must be positive and finite." }
        require(shoulder > 0f && shoulder.isFinite()) { "Value connector shoulder must be positive and finite." }
    }
}

fun Path.addTopSocket(
    centerX: Float,
    profile: StackConnectorProfile,
) {
    lineTo(centerX - profile.halfWidth, 0f)
    cubicTo(
        centerX - profile.shoulder,
        0f,
        centerX - profile.shoulder,
        profile.depth,
        centerX,
        profile.depth,
    )
    cubicTo(
        centerX + profile.shoulder,
        profile.depth,
        centerX + profile.shoulder,
        0f,
        centerX + profile.halfWidth,
        0f,
    )
}

fun Path.addBottomPlug(
    centerX: Float,
    bodyBottom: Float,
    profile: StackConnectorProfile,
) {
    lineTo(centerX + profile.halfWidth, bodyBottom)
    cubicTo(
        centerX + profile.shoulder,
        bodyBottom,
        centerX + profile.shoulder,
        bodyBottom + profile.depth,
        centerX,
        bodyBottom + profile.depth,
    )
    cubicTo(
        centerX - profile.shoulder,
        bodyBottom + profile.depth,
        centerX - profile.shoulder,
        bodyBottom,
        centerX - profile.halfWidth,
        bodyBottom,
    )
}
