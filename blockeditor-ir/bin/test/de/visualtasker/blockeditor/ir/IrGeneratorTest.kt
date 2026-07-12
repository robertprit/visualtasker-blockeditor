package de.visualtasker.blockeditor.ir

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry
import de.visualtasker.blockeditor.registry.SampleWorkspaceFactory
import de.visualtasker.blockeditor.registry.createNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IrGeneratorTest {
    private val generator = IrGenerator()

    @Test
    fun sampleWorkspace_emitsStartChain() {
        val script = generator.generate(SampleWorkspaceFactory.createDemo())
        assertEquals("demo-workspace", script.name)
        assertEquals(6, script.statements.size)
        assertEquals(4, script.statements.filterIsInstance<IrStatement.ClickText>().size)
        assertEquals(2, script.statements.filterIsInstance<IrStatement.Repeat>().size)
    }

    @Test
    fun ifElseIfElse_emitsBranches() {
        val startId = BlockId("start")
        val ifId = BlockId("if")
        val thenId = BlockId("then")
        val elifId = BlockId("elif")
        val elseId = BlockId("else")

        val start = DefaultBlockRegistry.getDefinition(BlockTypes.EVENT_START)!!.createNode(startId)
        val ifDef = DefaultBlockRegistry.getDefinition(BlockTypes.CONTROL_IF_ELSEIF_ELSE)!!
        var ifBlock = ifDef.createNode(ifId)
        val thenBlock = DefaultBlockRegistry.getDefinition(BlockTypes.ACTION_CLICK_TEXT)!!.createNode(thenId)
            .copy(fields = mapOf("text" to de.visualtasker.blockeditor.domain.FieldValue.Text("then")))
        val elifBlock = DefaultBlockRegistry.getDefinition(BlockTypes.ACTION_CLICK_TEXT)!!.createNode(elifId)
            .copy(fields = mapOf("text" to de.visualtasker.blockeditor.domain.FieldValue.Text("elif")))
        val elseBlock = DefaultBlockRegistry.getDefinition(BlockTypes.ACTION_CLICK_TEXT)!!.createNode(elseId)
            .copy(fields = mapOf("text" to de.visualtasker.blockeditor.domain.FieldValue.Text("else")))

        fun connectSlot(slotName: String, head: de.visualtasker.blockeditor.domain.BlockNode): de.visualtasker.blockeditor.domain.BlockNode {
            val slot = ifBlock.statementInputs.first { it.name == slotName }
            val stmtConn = slot.connection
            val connected = head.copy(previous = head.previous!!.copy(connectedTo = stmtConn.id))
            ifBlock = ifBlock.copy(
                statementInputs = ifBlock.statementInputs.map {
                    if (it.name == slotName) {
                        it.copy(connection = stmtConn.copy(connectedTo = connected.previous!!.id))
                    } else {
                        it
                    }
                },
            )
            return connected
        }

        val thenConnected = connectSlot(BlockTypes.SLOT_THEN, thenBlock)
        val elifConnected = connectSlot(BlockTypes.SLOT_ELIF, elifBlock)
        val elseConnected = connectSlot(BlockTypes.SLOT_ELSE, elseBlock)

        val connectedIf = ifBlock.copy(
            previous = ifBlock.previous!!.copy(connectedTo = start.next!!.id),
        )
        val connectedStart = start.copy(
            next = start.next!!.copy(connectedTo = connectedIf.previous!!.id),
        )

        val document = WorkspaceDocument(
            id = "if-test",
            blocks = mapOf(
                startId to connectedStart,
                ifId to connectedIf,
                thenId to thenConnected,
                elifId to elifConnected,
                elseId to elseConnected,
            ),
            rootBlocks = listOf(startId),
        )

        val script = generator.generate(document)
        val ifStmt = script.statements.single() as IrStatement.If

        assertEquals("then", (ifStmt.thenBranch.single() as IrStatement.ClickText).text)
        assertEquals(
            "elif",
            (ifStmt.elseIfBranches.single().body.single() as IrStatement.ClickText).text,
        )
        assertEquals("else", (ifStmt.elseBranch.single() as IrStatement.ClickText).text)
    }

    @Test
    fun booleanReporter_emitsLiteral() {
        val startId = BlockId("start")
        val ifId = BlockId("if")
        val boolId = BlockId("bool")

        val start = DefaultBlockRegistry.getDefinition(BlockTypes.EVENT_START)!!.createNode(startId)
        val ifBlock = DefaultBlockRegistry.getDefinition(BlockTypes.CONTROL_IF)!!.createNode(ifId)
        val boolBlock = DefaultBlockRegistry.getDefinition(BlockTypes.LOGIC_BOOLEAN)!!.createNode(boolId)
            .copy(fields = mapOf("value" to de.visualtasker.blockeditor.domain.FieldValue.Bool(false)))

        val conditionInput = ifBlock.valueInputs.first { it.name == "CONDITION" }
        val connectedBool = boolBlock
        val boolOutput = connectedBool.output!!
        val connectedIf = ifBlock.copy(
            previous = ifBlock.previous!!.copy(connectedTo = start.next!!.id),
            valueInputs = ifBlock.valueInputs.map {
                if (it.name == "CONDITION") {
                    it.copy(connection = conditionInput.connection.copy(connectedTo = boolOutput.id))
                } else {
                    it
                }
            },
        )
        val connectedBoolWithOutput = connectedBool.copy(
            output = boolOutput.copy(connectedTo = conditionInput.connection.id),
        )
        val connectedStart = start.copy(
            next = start.next!!.copy(connectedTo = connectedIf.previous!!.id),
        )

        val document = WorkspaceDocument(
            id = "bool-test",
            blocks = mapOf(
                startId to connectedStart,
                ifId to connectedIf,
                boolId to connectedBoolWithOutput,
            ),
            rootBlocks = listOf(startId, boolId),
        )

        val script = generator.generate(document)
        val ifStmt = script.statements.single() as IrStatement.If
        assertEquals(IrExpression.LiteralBoolean(false), ifStmt.condition)
    }

    @Test
    fun operateReporter_emitsComparison() {
        val startId = BlockId("start")
        val ifId = BlockId("if")
        val operateId = BlockId("operate")
        val varAId = BlockId("varA")
        val varBId = BlockId("varB")

        val start = DefaultBlockRegistry.getDefinition(BlockTypes.EVENT_START)!!.createNode(startId)
        val ifBlock = DefaultBlockRegistry.getDefinition(BlockTypes.CONTROL_IF)!!.createNode(ifId)
        var operateBlock = DefaultBlockRegistry.getDefinition(BlockTypes.LOGIC_OPERATE)!!.createNode(operateId)
            .copy(fields = mapOf("operator" to de.visualtasker.blockeditor.domain.FieldValue.Text("lt")))
        val varA = DefaultBlockRegistry.getDefinition(BlockTypes.VARIABLE_GET)!!.createNode(varAId)
            .copy(fields = mapOf("variable" to de.visualtasker.blockeditor.domain.FieldValue.Text("a")))
        val varB = DefaultBlockRegistry.getDefinition(BlockTypes.VARIABLE_GET)!!.createNode(varBId)
            .copy(fields = mapOf("variable" to de.visualtasker.blockeditor.domain.FieldValue.Text("b")))

        fun connectValue(parent: de.visualtasker.blockeditor.domain.BlockNode, inputName: String, child: de.visualtasker.blockeditor.domain.BlockNode): Pair<de.visualtasker.blockeditor.domain.BlockNode, de.visualtasker.blockeditor.domain.BlockNode> {
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

        val (operateWithA, connectedVarA) = connectValue(operateBlock, "Input1", varA)
        val (operateWithB, connectedVarB) = connectValue(operateWithA, "Input2", varB)
        operateBlock = operateWithB

        val conditionInput = ifBlock.valueInputs.first { it.name == "CONDITION" }
        val operateOutput = operateBlock.output!!
        val connectedIf = ifBlock.copy(
            previous = ifBlock.previous!!.copy(connectedTo = start.next!!.id),
            valueInputs = ifBlock.valueInputs.map {
                if (it.name == "CONDITION") {
                    it.copy(connection = conditionInput.connection.copy(connectedTo = operateOutput.id))
                } else {
                    it
                }
            },
        )
        val connectedOperate = operateBlock.copy(
            output = operateOutput.copy(connectedTo = conditionInput.connection.id),
        )
        val connectedStart = start.copy(
            next = start.next!!.copy(connectedTo = connectedIf.previous!!.id),
        )

        val document = WorkspaceDocument(
            id = "operate-test",
            blocks = mapOf(
                startId to connectedStart,
                ifId to connectedIf,
                operateId to connectedOperate,
                varAId to connectedVarA,
                varBId to connectedVarB,
            ),
            rootBlocks = listOf(startId, operateId),
        )

        val script = generator.generate(document)
        val ifStmt = script.statements.single() as IrStatement.If
        val condition = ifStmt.condition as IrExpression.Operate
        assertEquals("lt", condition.operator)
        assertEquals(IrExpression.GetVariable("a"), condition.a)
        assertEquals(IrExpression.GetVariable("b"), condition.b)
        assertEquals(null, condition.c)
    }
}
