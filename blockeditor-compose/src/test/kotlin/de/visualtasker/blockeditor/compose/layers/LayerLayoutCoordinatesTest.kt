package de.visualtasker.blockeditor.compose.layers

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.Rect
import de.visualtasker.blockeditor.layout.BlockLayout
import de.visualtasker.blockeditor.layout.InlineReporterLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class LayerLayoutCoordinatesTest {
    @Test
    fun inlineReporterLayoutIsRebasedToBlockLocalCoordinates() {
        val blockId = BlockId("add")
        val blockLayout = BlockLayout(
            blockId = blockId,
            bounds = Rect(120f, 80f, 160f, 32f),
            subtreeBounds = Rect(120f, 80f, 160f, 32f),
            zIndex = 1,
            collapsed = false,
        )
        val inlineLayout = InlineReporterLayout(
            blockId = blockId,
            leftSlot = Rect(132f, 84f, 44f, 24f),
            operatorBounds = Rect(184f, 84f, 32f, 24f),
            rightSlot = Rect(224f, 84f, 44f, 24f),
            leftInputName = "left",
            rightInputName = "right",
            zIndex = 1,
        )

        val relative = inlineLayout.relativeTo(blockLayout)

        assertEquals(12f, relative.leftSlot.x, 0.001f)
        assertEquals(4f, relative.leftSlot.y, 0.001f)
        assertEquals(64f, relative.operatorBounds.x, 0.001f)
        assertEquals(4f, relative.operatorBounds.y, 0.001f)
        assertEquals(104f, relative.rightSlot.x, 0.001f)
        assertEquals(4f, relative.rightSlot.y, 0.001f)
        assertEquals("left", relative.leftInputName)
        assertEquals("right", relative.rightInputName)
    }
}
