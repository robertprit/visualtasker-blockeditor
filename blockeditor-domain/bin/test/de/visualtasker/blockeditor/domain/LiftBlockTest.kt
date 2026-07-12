package de.visualtasker.blockeditor.domain

import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.SampleWorkspaceFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiftBlockTest {
    @Test
    fun liftBlock_bridgesMiddleClickInMainChain() {
        val document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val startId = chain[0]
        val clickId = chain[1]
        val repeatId = chain[2]

        val lifted = WorkspaceReducer.liftBlock(document, clickId)

        assertNull(WorkspaceGraph.previousChain(lifted, clickId))
        assertNull(WorkspaceGraph.nextChain(lifted, clickId))
        assertEquals(repeatId, WorkspaceGraph.nextChain(lifted, startId))
        assertTrue(clickId in lifted.rootBlocks)
    }

    @Test
    fun liftBlock_freesStatementSlotHead() {
        val document = SampleWorkspaceFactory.createWithStatementSlot()
        val clickId = document.blocks.entries.first { it.value.type == BlockTypes.ACTION_CLICK_TEXT }.key
        val repeatId = document.blocks.entries.first { it.value.type == BlockTypes.CONTROL_REPEAT }.key

        val lifted = WorkspaceReducer.liftBlock(document, clickId)

        assertNull(WorkspaceGraph.slotContaining(lifted, clickId))
        assertTrue(WorkspaceGraph.statementStack(lifted, repeatId, BlockTypes.SLOT_DO).isEmpty())
        assertTrue(clickId in lifted.rootBlocks)
    }

    @Test
    fun liftBlock_chainHeadKeepsNextScriptAttached() {
        val document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val startId = chain[0]
        val clickId = chain[1]
        val repeatId = chain[2]

        val lifted = WorkspaceReducer.liftBlock(document, startId)

        assertEquals(clickId, WorkspaceGraph.nextChain(lifted, startId))
        assertEquals(repeatId, WorkspaceGraph.nextChain(lifted, clickId))
        assertTrue(startId in lifted.rootBlocks)
    }
}
