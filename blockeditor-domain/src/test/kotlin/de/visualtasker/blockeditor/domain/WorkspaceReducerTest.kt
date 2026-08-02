package de.visualtasker.blockeditor.domain

import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry
import de.visualtasker.blockeditor.registry.SampleWorkspaceFactory
import de.visualtasker.blockeditor.registry.asFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceReducerTest {
    private val factory = DefaultBlockRegistry.asFactory()

    @Test
    fun connect_linksStatementStackViaPrevious() {
        var document = WorkspaceDocument(id = "reducer-test")
        document = reduce(document, WorkspaceAction.InstantiateBlock(BlockTypes.CONTROL_REPEAT, 0f, 0f))
        val repeatId = document.rootBlocks.single()
        document = reduce(document, WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 40f, 120f))
        val waitId = document.rootBlocks.last()

        val repeat = document.blocks[repeatId]!!
        val wait = document.blocks[waitId]!!
        document = reduce(
            document,
            WorkspaceAction.Connect(repeat.statementInputs.first().connection.id, wait.previous!!.id),
        )

        assertEquals(listOf(waitId), WorkspaceGraph.statementStack(document, repeatId, BlockTypes.SLOT_DO))
    }

    @Test
    fun collapse_setsCollapsedFlag() {
        var document = WorkspaceDocument(id = "collapse-test")
        document = reduce(document, WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 0f, 0f))
        val blockId = document.rootBlocks.single()
        document = reduce(document, WorkspaceAction.Collapse(blockId))
        assertTrue(document.blocks[blockId]!!.collapsed)
        document = reduce(document, WorkspaceAction.Expand(blockId))
        assertFalse(document.blocks[blockId]!!.collapsed)
    }

    @Test
    fun connect_chainsNextAndPrevious() {
        var document = WorkspaceDocument(id = "chain-test")
        document = reduce(document, WorkspaceAction.InstantiateBlock(BlockTypes.EVENT_START, 0f, 0f))
        val startId = document.rootBlocks.single()
        document = reduce(document, WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 0f, 80f))
        val waitId = document.rootBlocks.last()

        val start = document.blocks[startId]!!
        val wait = document.blocks[waitId]!!
        document = reduce(document, WorkspaceAction.Connect(start.next!!.id, wait.previous!!.id))

        assertEquals(waitId, WorkspaceGraph.nextChain(document, startId))
        assertEquals(startId, WorkspaceGraph.previousChain(document, waitId))
        assertFalse(waitId in document.rootBlocks)
    }

    @Test
    fun rootPositions_arePersistentDocumentState() {
        var document = WorkspaceDocument(id = "root-position-test")
        document = reduce(document, WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 12f, 34f))
        val blockId = document.rootBlocks.single()

        assertEquals(Offset2(12f, 34f), document.rootOffset(blockId))
        assertFalse(document.blocks[blockId]!!.metadata.containsKey(META_ROOT_X))
        assertFalse(document.blocks[blockId]!!.metadata.containsKey(META_ROOT_Y))

        document = reduce(document, WorkspaceAction.MoveRoot(blockId, 56f, 78f))

        assertEquals(Offset2(56f, 78f), document.rootOffset(blockId))
        assertEquals(WorkspacePoint(56f, 78f), document.rootPositions[blockId])
    }

    @Test
    fun connect_prunesPositionForBlockThatStopsBeingRoot() {
        var document = WorkspaceDocument(id = "root-prune-test")
        document = reduce(document, WorkspaceAction.InstantiateBlock(BlockTypes.EVENT_START, 0f, 0f))
        val startId = document.rootBlocks.single()
        document = reduce(document, WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 0f, 80f))
        val waitId = document.rootBlocks.last()

        document = reduce(
            document,
            WorkspaceAction.Connect(
                document.blocks[startId]!!.next!!.id,
                document.blocks[waitId]!!.previous!!.id,
            ),
        )

        assertFalse(waitId in document.rootBlocks)
        assertFalse(document.rootPositions.containsKey(waitId))
    }

    @Test
    fun connect_insertsBlockIntoMiddleOfChain() {
        val document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val clickId = chain[3]
        val repeatId = chain[2]
        val nextRepeatId = chain[4]

        var lifted = WorkspaceReducer.liftBlock(document, clickId)
        val repeat = lifted.blocks[repeatId]!!
        val click = lifted.blocks[clickId]!!
        lifted = reduce(
            lifted,
            WorkspaceAction.Connect(repeat.next!!.id, click.previous!!.id),
        )

        assertEquals(clickId, WorkspaceGraph.nextChain(lifted, repeatId))
        assertEquals(nextRepeatId, WorkspaceGraph.nextChain(lifted, clickId))
    }

    @Test
    fun connect_stacksIntoOccupiedStatementSlot() {
        val document = SampleWorkspaceFactory.createWithStatementSlot()
        val waitId = document.blocks.entries.first { it.value.type == BlockTypes.ACTION_WAIT }.key
        val repeatId = document.blocks.entries.first { it.value.type == BlockTypes.CONTROL_REPEAT }.key
        val clickId = document.blocks.entries.first { it.value.type == BlockTypes.ACTION_CLICK_TEXT }.key

        var lifted = WorkspaceReducer.liftBlock(document, waitId)
        val repeat = lifted.blocks[repeatId]!!
        val wait = lifted.blocks[waitId]!!
        lifted = reduce(
            lifted,
            WorkspaceAction.Connect(repeat.statementInputs.first().connection.id, wait.previous!!.id),
        )

        assertEquals(listOf(waitId, clickId), WorkspaceGraph.statementStack(lifted, repeatId, BlockTypes.SLOT_DO))

        lifted = WorkspaceReducer.liftBlock(lifted, clickId)
        val click = lifted.blocks[clickId]!!
        lifted = reduce(
            lifted,
            WorkspaceAction.Connect(repeat.statementInputs.first().connection.id, click.previous!!.id),
        )

        assertEquals(listOf(clickId, waitId), WorkspaceGraph.statementStack(lifted, repeatId, BlockTypes.SLOT_DO))
    }

    @Test
    fun connect_doesNotReplaceOccupiedValueInputWithoutExplicitDisconnect() {
        var document = WorkspaceDocument(id = "occupied-value-input")
        document = reduce(document, WorkspaceAction.InstantiateBlock(BlockTypes.LOGIC_AND, 0f, 0f))
        val andId = document.rootBlocks.single()
        document = reduce(document, WorkspaceAction.InstantiateBlock(BlockTypes.LOGIC_BOOLEAN, 0f, 80f))
        val firstReporterId = document.rootBlocks.last()
        document = reduce(document, WorkspaceAction.InstantiateBlock(BlockTypes.LOGIC_BOOLEAN, 0f, 140f))
        val secondReporterId = document.rootBlocks.last()

        val and = document.blocks[andId]!!
        val aInput = and.valueInputs.first { it.name == "A" }.connection.id
        val bInput = and.valueInputs.first { it.name == "B" }.connection.id
        val firstOutput = document.blocks[firstReporterId]!!.output!!.id
        val secondOutput = document.blocks[secondReporterId]!!.output!!.id

        document = reduce(document, WorkspaceAction.Connect(firstOutput, aInput))
        val afterBlockedReplace = reduce(document, WorkspaceAction.Connect(secondOutput, aInput))

        assertEquals(firstOutput, afterBlockedReplace.blocks[andId]!!.valueInputs.first { it.name == "A" }.connection.connectedTo)
        assertEquals(aInput, afterBlockedReplace.blocks[firstReporterId]!!.output!!.connectedTo)
        assertEquals(null, afterBlockedReplace.blocks[secondReporterId]!!.output!!.connectedTo)
        assertTrue(secondReporterId in afterBlockedReplace.rootBlocks)

        val afterSecondSlot = reduce(afterBlockedReplace, WorkspaceAction.Connect(secondOutput, bInput))

        assertEquals(firstOutput, afterSecondSlot.blocks[andId]!!.valueInputs.first { it.name == "A" }.connection.connectedTo)
        assertEquals(secondOutput, afterSecondSlot.blocks[andId]!!.valueInputs.first { it.name == "B" }.connection.connectedTo)
        assertEquals(bInput, afterSecondSlot.blocks[secondReporterId]!!.output!!.connectedTo)
    }

    private fun reduce(document: WorkspaceDocument, action: WorkspaceAction): WorkspaceDocument =
        WorkspaceReducer.reduce(document, action, factory)
}
