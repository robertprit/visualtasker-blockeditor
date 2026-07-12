package de.visualtasker.blockeditor.interaction

import de.visualtasker.blockeditor.domain.Rect

object ViewportConstraints {
    /**
     * Verschiebt den Viewport minimal, damit [blockBounds] den sichtbaren Bereich schneidet.
     * Beim Zoomen bleibt der Script-Start so im Bild.
     */
    fun keepBlockVisible(
        viewport: ViewportState,
        blockBounds: Rect,
        viewportWidth: Float,
        viewportHeight: Float,
        margin: Float = 24f,
    ): ViewportState {
        if (viewportWidth <= 0f || viewportHeight <= 0f) return viewport

        var panX = viewport.panX
        var panY = viewport.panY
        val scale = viewport.scale

        val minX = margin
        val minY = margin
        val maxX = viewportWidth - margin
        val maxY = viewportHeight - margin

        var left = blockBounds.x * scale + panX
        var top = blockBounds.y * scale + panY

        if (left < minX) panX += minX - left
        if (top < minY) panY += minY - top

        left = blockBounds.x * scale + panX
        top = blockBounds.y * scale + panY
        val right = blockBounds.right * scale + panX
        val bottom = blockBounds.bottom * scale + panY

        if (right > maxX) {
            val candidatePanX = panX - (right - maxX)
            if (blockBounds.x * scale + candidatePanX >= minX) {
                panX = candidatePanX
            }
        }
        if (bottom > maxY) {
            val candidatePanY = panY - (bottom - maxY)
            if (blockBounds.y * scale + candidatePanY >= minY) {
                panY = candidatePanY
            }
        }

        if (panX == viewport.panX && panY == viewport.panY) return viewport
        return viewport.copy(panX = panX, panY = panY)
    }
}
