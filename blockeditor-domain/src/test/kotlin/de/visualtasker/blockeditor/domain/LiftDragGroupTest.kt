package de.visualtasker.blockeditor.domain

import de.visualtasker.blockeditor.registry.SampleWorkspaceFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiftDragGroupTest {
    @Test
    fun liftDragGroup_preservesIncludedChainBelow() {
        val document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val clickId = chain[1]
        val repeatId = chain[2]
        val included = chain.drop(1).toSet()

        val lifted = WorkspaceReducer.liftDragGroup(document, clickId, included)

        assertEquals(repeatId, WorkspaceGraph.nextChain(lifted, clickId))
        assertEquals(chain[3], WorkspaceGraph.nextChain(lifted, repeatId))
        assertNull(WorkspaceGraph.previousChain(lifted, clickId))
        assertEquals(chain[0], WorkspaceGraph.chainFrom(lifted, chain[0]).single())
    }

    @Test
    fun liftDragGroup_singleBlock_bridgesMiddleLikeLiftBlock() {
        val document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val clickId = chain[1]
        val repeatId = chain[2]

        val lifted = WorkspaceReducer.liftDragGroup(document, clickId, setOf(clickId))

        assertNull(WorkspaceGraph.nextChain(lifted, clickId))
        assertEquals(repeatId, WorkspaceGraph.nextChain(lifted, chain[0]))
    }
}
