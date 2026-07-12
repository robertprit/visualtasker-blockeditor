package de.visualtasker.blockeditor.interaction

import de.visualtasker.blockeditor.layout.LayoutEngine
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.SampleWorkspaceFactory
import org.junit.Assert.assertTrue
import org.junit.Test

class StartBlockDragTest {
    @Test
    fun beginDrag_startBlockAlwaysPullsStackBelow() {
        val document = SampleWorkspaceFactory.createDemo()
        val startId = SampleWorkspaceFactory.mainChain(document).first()
        val layout = LayoutEngine().build(document)
        val begin = DragOperations.beginDrag(
            document = document,
            layoutCache = layout,
            blockId = startId,
            pointer = de.visualtasker.blockeditor.domain.Offset2(0f, 0f),
            ViewportState(),
        )
        val session = begin.dragSession!!
        assertTrue(session.includedBlocks.size > 1)
        assertTrue(session.pullMode == DragPullMode.StackBelow)
        assertTrue(document.blocks[startId]?.type == BlockTypes.EVENT_START)
    }
}
