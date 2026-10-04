package de.visualtasker.blockeditor.interaction

import de.visualtasker.workflow.core.BlockId
import de.visualtasker.workflow.core.ConnectionKind
import de.visualtasker.workflow.core.WorkspaceDocument
import de.visualtasker.workflow.core.withRootOffset
import de.visualtasker.blockeditor.layout.LayoutEngine
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry
import de.visualtasker.blockeditor.registry.createNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineOperateDragTest {
    private val layoutEngine = LayoutEngine(DefaultBlockRegistry)

    @Test
    fun dragOperate_includesPluggedVariableReporters() {
        val operateId = BlockId("operate")
        val varAId = BlockId("varA")
        val varBId = BlockId("varB")

        var operate = DefaultBlockRegistry.getDefinition(BlockTypes.LOGIC_OPERATE)!!.createNode(operateId)
            .withRootOffset(40f, 40f)
        val varA = DefaultBlockRegistry.getDefinition(BlockTypes.VARIABLE_GET)!!.createNode(varAId)
            .copy(fields = mapOf("variable" to de.visualtasker.workflow.core.FieldValue.Text("a")))
            .withRootOffset(200f, 40f)
        val varB = DefaultBlockRegistry.getDefinition(BlockTypes.VARIABLE_GET)!!.createNode(varBId)
            .copy(fields = mapOf("variable" to de.visualtasker.workflow.core.FieldValue.Text("b")))
            .withRootOffset(200f, 100f)

        fun connectValue(parent: de.visualtasker.workflow.core.BlockNode, inputName: String, child: de.visualtasker.workflow.core.BlockNode): Pair<de.visualtasker.workflow.core.BlockNode, de.visualtasker.workflow.core.BlockNode> {
            val input = parent.valueInputs.first { it.name == inputName }
            val childOutput = child.output!!
            val updatedParent = parent.copy(
                valueInputs = parent.valueInputs.map {
                    if (it.name == inputName) {
                        it.copy(connection = it.connection.copy(connectedTo = childOutput.id))
                    } else {
                        it
                    }
                },
            )
            val updatedChild = child.copy(
                output = childOutput.copy(connectedTo = input.connection.id),
            )
            return updatedParent to updatedChild
        }

        val (withA, connectedA) = connectValue(operate, "Input1", varA)
        val (withB, connectedB) = connectValue(withA, "Input2", varB)

        val document = WorkspaceDocument(
            id = "drag-operate",
            blocks = mapOf(
                operateId to withB,
                varAId to connectedA,
                varBId to connectedB,
            ),
            rootBlocks = listOf(operateId, varAId, varBId),
        )

        val begin = DragOperations.beginDrag(
            document = document,
            layoutCache = layoutEngine.build(document),
            blockId = operateId,
            pointer = de.visualtasker.workflow.core.Offset2(0f, 0f),
            viewport = ViewportState(),
            pullMode = DragPullMode.Single,
        )

        val included = begin.dragSession!!.includedBlocks
        assertTrue(operateId in included)
        assertTrue(varAId in included)
        assertTrue(varBId in included)
    }

    @Test
    fun valuePluggedReporter_notTopLevelRoot() {
        val operateId = BlockId("operate")
        val varId = BlockId("var")

        var operate = DefaultBlockRegistry.getDefinition(BlockTypes.LOGIC_OPERATE)!!.createNode(operateId)
        val variable = DefaultBlockRegistry.getDefinition(BlockTypes.VARIABLE_GET)!!.createNode(varId)
        val input = operate.valueInputs.first { it.name == "Input1" }
        val output = variable.output!!
        operate = operate.copy(
            valueInputs = operate.valueInputs.map {
                if (it.name == "Input1") {
                    it.copy(connection = it.connection.copy(connectedTo = output.id))
                } else {
                    it
                }
            },
        )
        val connectedVar = variable.copy(
            output = output.copy(connectedTo = input.connection.id),
        )

        val document = WorkspaceDocument(
            id = "plugged-var",
            blocks = mapOf(operateId to operate, varId to connectedVar),
            rootBlocks = listOf(operateId, varId),
        )

        assertFalse(varId in de.visualtasker.workflow.core.WorkspaceGraph.topLevelRoots(document))
    }
}
