package de.visualtasker.blockeditor.compose.render

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockRendererTest {
    @Test
    fun `zero-width label space is not drawn`() {
        assertFalse(hasDrawableLabelSpace(0f))
    }

    @Test
    fun `negative label space is not drawn`() {
        assertFalse(hasDrawableLabelSpace(-1f))
    }

    @Test
    fun `positive label space remains drawable`() {
        assertTrue(hasDrawableLabelSpace(1f))
    }
}
