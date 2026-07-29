package de.visualtasker.blockeditor.interaction

import de.visualtasker.blockeditor.layout.LayoutConstants
import de.visualtasker.blockeditor.layout.LayoutEngine
import de.visualtasker.blockeditor.registry.SampleWorkspaceFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartDragTest {
    private val layoutEngine = LayoutEngine()

    @Test
    fun staticDocument_liftStartKeepsScriptInDragSubtree() {
        val document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val startId = chain[0]

        val layoutDoc = DragLayoutPreview.layoutDocument(document, startId, chain.toSet())
        val staticLayout = layoutEngine.build(layoutDoc)
        val begin = DragOperations.beginDrag(
            document = document,
            layoutCache = staticLayout,
            blockId = startId,
            pointer = de.visualtasker.blockeditor.domain.Offset2(0f, 0f),
            ViewportState(),
        )

        val included = begin.dragSession!!.includedBlocks
        assertTrue(chain.drop(1).all { it in included })

        val visible = staticLayout.flatIndex.visibleBlocks.map { it.blockId }.toSet()
        assertTrue(visible.containsAll(chain.toSet()))
    }

    @Test
    fun previousAnchor_alignsWithStackDockAxis() {
        val document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val repeatId = chain[2]
        val layout = layoutEngine.build(document).flatIndex
        val repeatLayout = layout.visibleBlocks.first { it.blockId == repeatId }
        val previous = document.blocks[repeatId]!!.previous!!.id
        val anchor = layout.connectionAnchors.first { it.connectionId == previous }

        assertEquals(repeatLayout.bounds.x + LayoutConstants.STACK_DOCK_X, anchor.x, 0.01f)
        assertEquals(repeatLayout.bounds.y, anchor.y, 0.01f)
    }
}
