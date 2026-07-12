package de.visualtasker.blockeditor.interaction

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.Offset2

data class ViewportState(
    val panX: Float = 0f,
    val panY: Float = 0f,
    val scale: Float = 1f,
) {
    /** Inverse zur Canvas-Transformation: translate(pan) → scale(scale, pivot=0). */
    fun localToWorkspace(point: Offset2): Offset2 = Offset2(
        x = (point.x - panX) / scale,
        y = (point.y - panY) / scale,
    )

    @Deprecated("Use localToWorkspace", ReplaceWith("localToWorkspace(point)"))
    fun screenToWorkspace(point: Offset2): Offset2 = localToWorkspace(point)

    fun withTransform(centroid: Offset2, panDelta: Offset2, zoomFactor: Float): ViewportState {
        val newScale = (scale * zoomFactor).coerceIn(MIN_SCALE, MAX_SCALE)
        if (newScale == scale && panDelta.x == 0f && panDelta.y == 0f) return this
        val ratio = newScale / scale
        return copy(
            panX = centroid.x - (centroid.x - panX) * ratio + panDelta.x,
            panY = centroid.y - (centroid.y - panY) * ratio + panDelta.y,
            scale = newScale,
        )
    }

    private companion object {
        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 2.5f
    }
}
