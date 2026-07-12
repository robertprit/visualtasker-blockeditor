package de.visualtasker.blockeditor.registry

import de.visualtasker.blockeditor.domain.WorkspaceAction
import de.visualtasker.blockeditor.domain.WorkspaceReducer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceBootstrapTest {
    @Test
    fun empty_hasNoBlocks() {
        val document = WorkspaceBootstrap.empty()
        assertEquals("workspace", document.id)
        assertTrue(document.blocks.isEmpty())
        assertTrue(document.rootBlocks.isEmpty())
    }

    @Test
    fun starter_hasSingleEventStartRoot() {
        val document = WorkspaceBootstrap.starter()
        assertEquals(1, document.blocks.size)
        assertEquals(1, document.rootBlocks.size)
        val root = document.blocks[document.rootBlocks.first()]!!
        assertEquals(BlockTypes.EVENT_START, root.type)
    }

    @Test
    fun starter_isNotSameInstanceAsSampleFactoryCreate() {
        val bootstrap = WorkspaceBootstrap.starter()
        val sample = SampleWorkspaceFactory.create()
        assertEquals(bootstrap.blocks.size, sample.blocks.size)
        assertEquals(bootstrap.rootBlocks.size, sample.rootBlocks.size)
    }

    @Test
    fun starter_canBeReducedFurther() {
        val factory = DefaultBlockRegistry.asFactory()
        val starter = WorkspaceBootstrap.starter()
        val extended = WorkspaceReducer.reduce(
            starter,
            WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_CLICK_TEXT, 96f, 120f),
            factory,
        )
        assertEquals(2, extended.blocks.size)
    }
}
