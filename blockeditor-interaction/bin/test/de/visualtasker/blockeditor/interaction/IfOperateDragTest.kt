package de.visualtasker.blockeditor.interaction

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.withRootOffset
import de.visualtasker.blockeditor.layout.LayoutEngine
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry
import de.visualtasker.blockeditor.registry.createNode
import org.junit.Assert.assertTrue
import org.junit.Test

class IfOperateDragTest {
    private val layoutEngine = LayoutEngine(DefaultBlockRegistry)

    @Test
    fun dragIf_includesConnectedOperateReporter() {
        val startId = BlockId("start")
        val ifId = BlockId("if")
        val operateId = BlockId("operate")

        val start = DefaultBlockRegistry.getDefinition(BlockTypes.EVENT_START)!!.createNode(startId)
        var ifBlock = DefaultBlockRegistry.getDefinition(BlockTypes.CONTROL_IF)!!.createNode(ifId)
        val operate = DefaultBlockRegistry.getDefinition(BlockTypes.LOGIC_OPERATE)!!.createNode(operateId)

        val conditionInput = ifBlock.valueInputs.first { it.name == "CONDITION" }
        val operateOutput = operate.output!!
        ifBlock = ifBlock.copy(
            previous = ifBlock.previous!!.copy(connectedTo = start.next!!.id),
            valueInputs = ifBlock.valueInputs.map {
                if (it.name == "CONDITION") {
                    it.copy(connection = conditionInput.connection.copy(connectedTo = operateOutput.id))
                } else {
                    it
                }
            },
        )
        val connectedOperate = operate.copy(
            output = operateOutput.copy(connectedTo = conditionInput.connection.id),
        )
        val connectedStart = start.copy(
            next = start.next!!.copy(connectedTo = ifBlock.previous!!.id),
        )

        val document = WorkspaceDocument(
            id = "if-operate-drag",
            blocks = mapOf(
                startId to connectedStart,
                ifId to ifBlock,
                operateId to connectedOperate,
            ),
            rootBlocks = listOf(startId),
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
        assertTrue("Operate should move with If", operateId in included)
    }
}
