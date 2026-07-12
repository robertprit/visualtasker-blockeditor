package de.visualtasker.blockeditor.interaction

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.WorkspaceAction
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.WorkspaceReducer
import de.visualtasker.blockeditor.layout.LayoutEngine
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry
import de.visualtasker.blockeditor.registry.asFactory
import org.junit.Assert.assertTrue
import org.junit.Test

class IfOperateUserFlowDragTest {
    private val factory = DefaultBlockRegistry.asFactory()
    private val layoutEngine = LayoutEngine(DefaultBlockRegistry)

    @Test
    fun userFlow_dragIf_movesOperateAndVariables() {
        var document = WorkspaceDocument(id = "user-flow")
        document = WorkspaceReducer.reduce(
            document,
            WorkspaceAction.InstantiateBlock(BlockTypes.CONTROL_IF, 80f, 80f),
            factory,
        )
        val ifId = document.rootBlocks.single()
        document = WorkspaceReducer.reduce(
            document,
            WorkspaceAction.InstantiateBlock(BlockTypes.LOGIC_OPERATE, 80f, 200f),
            factory,
        )
        val operateId = document.rootBlocks.single { it != ifId }

        val ifBlock = document.blocks[ifId]!!
        val operate = document.blocks[operateId]!!
        val condition = ifBlock.valueInputs.first { it.name == "CONDITION" }
        document = WorkspaceReducer.reduce(
            document,
            WorkspaceAction.Connect(operate.output!!.id, condition.connection.id),
            factory,
        )

        document = WorkspaceReducer.reduce(
            document,
            WorkspaceAction.InstantiateBlock(BlockTypes.VARIABLE_GET, 40f, 300f),
            factory,
        )
        val var1Id = document.rootBlocks.single { it != ifId }
        document = WorkspaceReducer.reduce(
            document,
            WorkspaceAction.InstantiateBlock(BlockTypes.VARIABLE_GET, 40f, 360f),
            factory,
        )
        val var2Id = document.rootBlocks.single { it !in setOf(ifId, var1Id) }

        val operateBlock = document.blocks[operateId]!!
        val input1 = operateBlock.valueInputs.first { it.name == "Input1" }
        val input2 = operateBlock.valueInputs.first { it.name == "Input2" }
        val var1 = document.blocks[var1Id]!!
        val var2 = document.blocks[var2Id]!!
        document = WorkspaceReducer.reduce(
            document,
            WorkspaceAction.Connect(var1.output!!.id, input1.connection.id),
            factory,
        )
        document = WorkspaceReducer.reduce(
            document,
            WorkspaceAction.Connect(var2.output!!.id, input2.connection.id),
            factory,
        )

        val begin = DragOperations.beginDrag(
            document = document,
            layoutCache = layoutEngine.build(document),
            blockId = ifId,
            pointer = de.visualtasker.blockeditor.domain.Offset2(0f, 0f),
            viewport = ViewportState(),
            pullMode = DragPullMode.Single,
        )

        val included = begin.dragSession!!.includedBlocks
        assertTrue(ifId in included)
        assertTrue(operateId in included)
        assertTrue(var1Id in included)
        assertTrue(var2Id in included)
        assertTrue(de.visualtasker.blockeditor.domain.WorkspaceGraph.isValuePlugged(document, operateId))
    }
}
