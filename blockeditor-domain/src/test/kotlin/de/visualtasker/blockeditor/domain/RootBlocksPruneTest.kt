package de.visualtasker.blockeditor.domain

import de.visualtasker.blockeditor.registry.SampleWorkspaceFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootBlocksPruneTest {
    @Test
    fun topLevelRoots_keepsStartAndFloatingBlocks() {
        val document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val lifted = WorkspaceReducer.liftBlock(document, chain[5])
        val floatId = chain[5]

        val tops = WorkspaceGraph.topLevelRoots(lifted)

        assertTrue(chain[0] in tops)
        assertTrue(floatId in tops)
        assertFalse(chain[1] in tops)
    }

    @Test
    fun pruneRootBlocks_dropsStaleChainMember() {
        val document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val stale = chain[2]
        val roots = document.rootBlocks + stale

        val pruned = WorkspaceGraph.pruneRootBlocks(document, roots)

        assertFalse(stale in pruned)
        assertEquals(document.rootBlocks.size, pruned.size)
    }

    @Test
    fun liftBlock_prunesStaleRootEntries() {
        val document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val clickId = chain[1]
        val stale = document.copy(rootBlocks = document.rootBlocks + clickId)

        val lifted = WorkspaceReducer.liftBlock(stale, clickId)

        assertTrue(clickId in lifted.rootBlocks)
        assertEquals(2, lifted.rootBlocks.size)
        assertTrue(chain[0] in lifted.rootBlocks)
    }
}
