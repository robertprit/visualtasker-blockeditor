package de.visualtasker.blockeditor.domain

data class Rect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height

    fun contains(px: Float, py: Float): Boolean =
        px >= x && px <= right && py >= y && py <= bottom
}
