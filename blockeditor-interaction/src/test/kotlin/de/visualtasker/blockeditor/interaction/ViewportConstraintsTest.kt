package de.visualtasker.blockeditor.interaction

import de.visualtasker.blockeditor.domain.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewportConstraintsTest {
    private val startBounds = Rect(40f, 40f, 288f, 44f)

    @Test
    fun keepBlockVisible_shiftsPanWhenStartScrollsOffTopAfterZoom() {
        val viewport = ViewportState(panX = 0f, panY = -200f, scale = 2f)

        val adjusted = ViewportConstraints.keepBlockVisible(
            viewport = viewport,
            blockBounds = startBounds,
            viewportWidth = 400f,
            viewportHeight = 600f,
        )

        val top = startBounds.y * adjusted.scale + adjusted.panY
        assertTrue(top >= 24f)
    }

    @Test
    fun keepBlockVisible_shiftsPanWhenStartScrollsOffLeftAfterZoom() {
        val viewport = ViewportState(panX = -300f, panY = 0f, scale = 1.5f)

        val adjusted = ViewportConstraints.keepBlockVisible(
            viewport = viewport,
            blockBounds = startBounds,
            viewportWidth = 400f,
            viewportHeight = 600f,
        )

        val left = startBounds.x * adjusted.scale + adjusted.panX
        assertTrue(left >= 24f)
    }

    @Test
    fun keepBlockVisible_leavesViewportUnchangedWhenStartAlreadyVisible() {
        val viewport = ViewportState(panX = 10f, panY = 20f, scale = 1f)

        val adjusted = ViewportConstraints.keepBlockVisible(
            viewport = viewport,
            blockBounds = startBounds,
            viewportWidth = 400f,
            viewportHeight = 600f,
        )

        assertEquals(viewport, adjusted)
    }
}
