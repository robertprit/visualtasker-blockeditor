package de.visualtasker.blockeditor.interaction

import de.visualtasker.blockeditor.domain.WorkspaceGraph
import de.visualtasker.blockeditor.layout.LayoutEngine
import de.visualtasker.blockeditor.registry.SampleWorkspaceFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DragLayoutPreviewTest {
    private val layoutEngine = LayoutEngine()

    @Test
    fun layoutDocument_bridgesAboveWhenDraggingSingleMiddleBlock() {
        val document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val clickId = chain[3]

        val preview = DragLayoutPreview.layoutDocument(document, clickId, setOf(clickId))

        assertNull(WorkspaceGraph.nextChain(preview, clickId))
        assertEquals(
            chain[4],
            WorkspaceGraph.nextChain(preview, chain[2]),
        )
    }

    @Test
    fun layoutDocument_collapsesDraggedBlockGapInStaticPreview() {
        val document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val clickId = chain[3]
        val repeatBelowId = chain[4]

        val before = layoutEngine.build(document).flatIndex.visibleBlocks
            .first { it.blockId == repeatBelowId }.bounds.y
        val previewLayout = layoutEngine.build(
            DragLayoutPreview.layoutDocument(document, clickId, setOf(clickId)),
        )
        val after = previewLayout.flatIndex.visibleBlocks
            .first { it.blockId == repeatBelowId }.bounds.y

        assertTrue("Static preview should close the gap left by the dragged block", after < before)
    }

    @Test
    fun layoutDocument_keepsIncludedChainAttachedWhileDraggingGroup() {
        val document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val repeatId = chain[2]
        val clickBelowId = chain[3]
        val included = chain.drop(2).toSet()

        val preview = DragLayoutPreview.layoutDocument(document, repeatId, included)

        assertEquals(clickBelowId, WorkspaceGraph.nextChain(preview, repeatId))
        assertNotEquals(repeatId, WorkspaceGraph.nextChain(preview, chain[1]))
    }
}
