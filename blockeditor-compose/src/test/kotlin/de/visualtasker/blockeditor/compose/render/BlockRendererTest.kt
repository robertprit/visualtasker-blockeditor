package de.visualtasker.blockeditor.compose.render

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.BlockNode
import de.visualtasker.blockeditor.domain.FieldValue
import de.visualtasker.blockeditor.registry.BlockDefinition
import de.visualtasker.blockeditor.registry.BlockTypes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        assertEquals(12f, drawableLabelWidth(100f, 12f))
    }

    @Test
    fun `label below narrow canvas is not drawn`() {
        assertFalse(hasDrawableTextArea(width = 12f, height = -1f))
    }

    @Test
    fun `label inside canvas remains drawable`() {
        assertTrue(hasDrawableTextArea(width = 12f, height = 8f))
    }

    @Test
    fun `narrow text box is clamped to valid drawable size`() {
        val size = safeDrawableTextSize(width = 1f, height = 1f)

        assertNotNull(size)
        assertEquals(1f, size!!.width)
        assertEquals(1f, size.height)
    }

    @Test
    fun `invalid text boxes are skipped before Compose constraints are created`() {
        assertNull(safeDrawableTextSize(width = 0f, height = 12f))
        assertNull(safeDrawableTextSize(width = 12f, height = 0f))
        assertNull(safeDrawableTextSize(width = -1f, height = 12f))
        assertNull(safeDrawableTextSize(width = 12f, height = -1f))
        assertNull(safeDrawableTextSize(width = Float.NaN, height = 12f))
        assertNull(safeDrawableTextSize(width = 12f, height = Float.POSITIVE_INFINITY))
    }

    @Test
    fun `text constraint width is only created for positive finite space`() {
        assertEquals(1, safeTextConstraintWidth(computedWidth = 1f))
        assertEquals(12, safeTextConstraintWidth(computedWidth = 12f, requestedMinWidth = 24f))
        assertNull(safeTextConstraintWidth(computedWidth = 0f))
        assertNull(safeTextConstraintWidth(computedWidth = -1f))
        assertNull(safeTextConstraintWidth(computedWidth = Float.NaN))
        assertNull(safeTextConstraintWidth(computedWidth = Float.POSITIVE_INFINITY))
    }

    @Test
    fun `reporter text centering never returns negative origins`() {
        val offset = centeredTextTopLeft(
            containerWidth = 4f,
            containerHeight = 4f,
            contentWidth = 24f,
            contentHeight = 12f,
        )

        assertNotNull(offset)
        assertEquals(0f, offset!!.x)
        assertEquals(0f, offset.y)
    }

    @Test
    fun `empty or collapsed reporter text containers are skipped`() {
        assertNull(
            centeredTextTopLeft(
                containerWidth = 0f,
                containerHeight = 40f,
                contentWidth = 12f,
                contentHeight = 12f,
            ),
        )
        assertNull(
            centeredTextTopLeft(
                containerWidth = 148f,
                containerHeight = 0f,
                contentWidth = 12f,
                contentHeight = 12f,
            ),
        )
    }

    @Test
    fun `compact reporter badge requires finite drawable bounds`() {
        assertEquals(14f, compactReporterBadgeEdge(width = 10f, height = 18f))
        assertNull(compactReporterBadgeEdge(width = 0f, height = 18f))
        assertNull(compactReporterBadgeEdge(width = 18f, height = -1f))
        assertNull(compactReporterBadgeEdge(width = Float.NaN, height = 18f))
        assertNull(compactReporterBadgeEdge(width = 18f, height = Float.POSITIVE_INFINITY))
    }

    @Test
    fun `inline operator label uses semantic operation instead of generic op`() {
        val block = BlockNode(
            id = BlockId("compare"),
            type = "emscript:logic.compare",
            fields = mapOf("operator" to FieldValue.Text("GREATER_OR_EQUAL")),
        )

        assertEquals(">=", block.inlineOperatorLabel(definition(label = "Compare")))
    }

    @Test
    fun `inline operator label keeps legacy symbol documents readable`() {
        val block = BlockNode(
            id = BlockId("compare"),
            type = "emscript:logic.compare",
            fields = mapOf("operator" to FieldValue.Text("==")),
        )

        assertEquals("=", block.inlineOperatorLabel(definition(label = "Compare")))
    }

    @Test
    fun `variable reporter label uses variable field before generic fallback`() {
        val block = BlockNode(
            id = BlockId("counter"),
            type = "${BlockTypes.VARIABLE_REPORTER_PREFIX}var-123",
            fields = mapOf("variable" to FieldValue.Text("counter")),
        )

        assertEquals("counter", block.variableDisplayLabel(definition(label = "Variable")))
    }

    private fun definition(label: String): BlockDefinition = BlockDefinition(
        id = "test",
        label = label,
        category = "test",
        hasPrevious = false,
        hasNext = false,
    )
}
