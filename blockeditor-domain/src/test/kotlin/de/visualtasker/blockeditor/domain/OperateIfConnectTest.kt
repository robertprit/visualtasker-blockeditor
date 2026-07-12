package de.visualtasker.blockeditor.domain

import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry
import de.visualtasker.blockeditor.registry.asFactory
import de.visualtasker.blockeditor.registry.createNode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperateIfConnectTest {
    private val factory = DefaultBlockRegistry.asFactory()

    @Test
    fun connect_operateOutput_plugsIntoIfCondition() {
        var document = WorkspaceDocument(id = "connect-test")
        document = WorkspaceReducer.reduce(document, WorkspaceAction.InstantiateBlock(BlockTypes.CONTROL_IF, 40f, 40f), factory)
        val ifId = document.rootBlocks.single()
        document = WorkspaceReducer.reduce(document, WorkspaceAction.InstantiateBlock(BlockTypes.LOGIC_OPERATE, 200f, 40f), factory)
        val operateId = document.rootBlocks.single { it != ifId }

        val ifBlock = document.blocks[ifId]!!
        val operate = document.blocks[operateId]!!
        val conditionInput = ifBlock.valueInputs.first { it.name == "CONDITION" }

        document = WorkspaceReducer.reduce(
            document,
            WorkspaceAction.Connect(operate.output!!.id, conditionInput.connection.id),
            factory,
        )

        val updatedOperate = document.blocks[operateId]!!
        val updatedIf = document.blocks[ifId]!!
        assertTrue(
            updatedOperate.output?.connectedTo == conditionInput.connection.id,
        )
        assertTrue(
            updatedIf.valueInputs.first { it.name == "CONDITION" }.connection.connectedTo == operate.output!!.id,
        )
        assertFalse(operateId in document.rootBlocks)
        assertTrue(WorkspaceGraph.isValuePlugged(document, operateId))
        assertTrue(WorkspaceGraph.valueInputParent(document, operateId)?.first == ifId)
    }
}
