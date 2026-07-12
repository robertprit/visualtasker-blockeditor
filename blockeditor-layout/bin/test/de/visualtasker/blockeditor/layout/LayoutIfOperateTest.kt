package de.visualtasker.blockeditor.layout

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry
import de.visualtasker.blockeditor.registry.createNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutIfOperateTest {
    private val engine = LayoutEngine(DefaultBlockRegistry)

    @Test
    fun pluggedOperate_layoutsOnceInsideIf_evenWithStaleRootEntry() {
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
            id = "layout-if-operate",
            blocks = mapOf(
                startId to connectedStart,
                ifId to ifBlock,
                operateId to connectedOperate,
            ),
            rootBlocks = listOf(startId, operateId),
        )

        val cache = engine.build(document)
        val operateLayouts = cache.flatIndex.visibleBlocks.filter { it.blockId == operateId }
        assertEquals(1, operateLayouts.size)
        val ifLayout = cache.flatIndex.visibleBlocks.single { it.blockId == ifId }
        assertTrue(operateLayouts.single().bounds.x >= ifLayout.bounds.x)
    }
}
