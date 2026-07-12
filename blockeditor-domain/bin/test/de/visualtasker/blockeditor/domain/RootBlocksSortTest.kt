package de.visualtasker.blockeditor.domain

import de.visualtasker.blockeditor.registry.SampleWorkspaceFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class RootBlocksSortTest {
    @Test
    fun sortRootBlocks_putsScriptStartFirst() {
        val document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val lifted = WorkspaceReducer.liftBlock(document, chain[5])
        val shuffled = lifted.rootBlocks.shuffled()

        val sorted = WorkspaceGraph.sortRootBlocks(lifted, shuffled)

        assertEquals(chain[0], sorted.first())
    }

    @Test
    fun pruneRootBlocks_returnsSortedRoots() {
        val document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val lifted = WorkspaceReducer.liftBlock(document, chain[5])
        val shuffled = lifted.rootBlocks.shuffled()

        val pruned = WorkspaceGraph.pruneRootBlocks(lifted, shuffled)

        assertEquals(chain[0], pruned.first())
        assertEquals(2, pruned.size)
    }
}
