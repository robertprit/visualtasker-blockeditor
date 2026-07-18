package de.visualtasker.blockeditor.compose.render

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockRendererTest {
    @Test
    fun `zero-width label space is not drawn`() {
        assertFalse(drawableLabelWidth(0f, 100f) > 0f)
    }

    @Test
    fun `label beyond narrow canvas is not drawn`() {
        assertFalse(drawableLabelWidth(100f, -1f) > 0f)
    }

    @Test
    fun `positive label space is bounded by canvas`() {
        assertTrue(drawableLabelWidth(100f, 12f) > 0f)
        org.junit.Assert.assertEquals(12f, drawableLabelWidth(100f, 12f))
    }

    @Test
    fun `label below narrow canvas is not drawn`() {
        assertFalse(hasDrawableTextArea(width = 12f, height = -1f))
    }

    @Test
    fun `label inside canvas remains drawable`() {
        assertTrue(hasDrawableTextArea(width = 12f, height = 8f))
    }
}
