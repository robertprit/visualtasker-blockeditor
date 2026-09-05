package de.visualtasker.blockeditor.emscript

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry
import de.visualtasker.blockeditor.registry.SampleWorkspaceFactory
import de.visualtasker.blockeditor.registry.createNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class EmscriptGeneratorTest {
    private val generator = EmscriptGenerator()

    @Test
    fun generatesDeterministicIndentedScript() {
        val document = SampleWorkspaceFactory.createDemo()
        val first = generator.generate(document)
        val second = generator.generate(document)
        assertEquals(first, second)
        assertTrue(first.contains("click(\"OK\");"))
        assertTrue(first.contains("repeat (3) {"))
        assertFalse(first.contains("# Script"))
        assertFalse(first.contains("\nEND\n"))
    }

    @Test
    fun ifElseIfElse_emitsElseifKeyword() {
        val startId = BlockId("start")
        val ifId = BlockId("if")
        val thenId = BlockId("then")
        val elifId = BlockId("elif")
        val elseId = BlockId("else")

        val start = DefaultBlockRegistry.getDefinition(BlockTypes.EVENT_START)!!.createNode(startId)
        val ifDef = DefaultBlockRegistry.getDefinition(BlockTypes.CONTROL_IF_ELSEIF_ELSE)!!
        var ifBlock = ifDef.createNode(ifId)
        val thenBlock = DefaultBlockRegistry.getDefinition(BlockTypes.ACTION_CLICK_TEXT)!!.createNode(thenId)
        val elifBlock = DefaultBlockRegistry.getDefinition(BlockTypes.ACTION_CLICK_TEXT)!!.createNode(elifId)
        val elseBlock = DefaultBlockRegistry.getDefinition(BlockTypes.ACTION_CLICK_TEXT)!!.createNode(elseId)

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
        assertTrue(script.contains("if (false) {"))
        assertTrue(script.contains("} else if (false) {"))
        assertTrue(script.contains("} else {"))
    }

    @Test fun emitsContractShapedWaitOutputAndSetSubset() {
        val script = generator.generate(
            de.visualtasker.blockeditor.ir.IrScript(
                "safe",
                listOf(
                    de.visualtasker.blockeditor.ir.IrStatement.Wait(500),
                    de.visualtasker.blockeditor.ir.IrStatement.Beep(),
                    de.visualtasker.blockeditor.ir.IrStatement.Beep(880, 150, 75),
                    de.visualtasker.blockeditor.ir.IrStatement.Vibrate(listOf(0L, 80L, 40L, 120L)),
                    de.visualtasker.blockeditor.ir.IrStatement.Log("hello"),
                    de.visualtasker.blockeditor.ir.IrStatement.SetVariable("count", "3"),
                ),
            ),
        )
        assertEquals(
            "wait(500);\nbeep();\nbeep(880, 150, 75);\nvibrate(0, 80, 40, 120);\nlog(\"hello\");\nset count = 3;",
            script,
        )
    }

    @Test fun sanitizesVariableNamesAcceptedByEditor() {
        val script = generator.generate(
            de.visualtasker.blockeditor.ir.IrScript(
                "safe",
                listOf(
                    de.visualtasker.blockeditor.ir.IrStatement.SetVariable("1 user name", "3"),
                    de.visualtasker.blockeditor.ir.IrStatement.While(
                        de.visualtasker.blockeditor.ir.IrExpression.GetVariable("1 user name"),
                        emptyList(),
                    ),
                ),
            ),
        )
        assertEquals("set _1_user_name = 3;\nwhile (_1_user_name) {\n}", script)
    }

    @Test fun emitsCompareReporterConditions() {
        val script = generator.generate(
            de.visualtasker.blockeditor.ir.IrScript(
                "compare",
                listOf(
                    de.visualtasker.blockeditor.ir.IrStatement.If(
                        condition = de.visualtasker.blockeditor.ir.IrExpression.Compare(
                            operator = "GREATER_OR_EQUAL",
                            left = de.visualtasker.blockeditor.ir.IrExpression.LiteralNumber(3.0),
                            right = de.visualtasker.blockeditor.ir.IrExpression.LiteralNumber(2.0),
                        ),
                        thenBranch = listOf(de.visualtasker.blockeditor.ir.IrStatement.Wait(100)),
                    ),
                ),
            ),
        )
        assertEquals("if ((3 >= 2)) {\n    wait(100);\n}", script)
    }

    @Test fun emitsEmptyCompareSlotsAsBooleanFallbacks() {
        val script = generator.generate(
            de.visualtasker.blockeditor.ir.IrScript(
                "compare",
                listOf(
                    de.visualtasker.blockeditor.ir.IrStatement.If(
                        condition = de.visualtasker.blockeditor.ir.IrExpression.Compare(
                            operator = "EQUAL",
                            left = de.visualtasker.blockeditor.ir.IrExpression.LiteralBoolean(false),
                            right = de.visualtasker.blockeditor.ir.IrExpression.LiteralBoolean(false),
                        ),
                        thenBranch = emptyList(),
                    ),
                ),
            ),
        )
        assertEquals("if ((false == false)) {\n}", script)
    }

    @Test fun emitsScreenContainsAndLiteralTextExpressions() {
        val script = generator.generate(
            de.visualtasker.blockeditor.ir.IrScript(
                "semantic-expressions",
                listOf(
                    de.visualtasker.blockeditor.ir.IrStatement.While(
                        de.visualtasker.blockeditor.ir.IrExpression.ScreenContains("Ready"),
                        emptyList(),
                    ),
                    de.visualtasker.blockeditor.ir.IrStatement.If(
                        condition = de.visualtasker.blockeditor.ir.IrExpression.LiteralText("manual condition"),
                        thenBranch = emptyList(),
                    ),
                ),
            ),
        )
        assertEquals("while (screenContains(\"Ready\")) {\n}\nif (\"manual condition\") {\n}", script)
    }

    @Test fun allowsUnknownCustomCommandCallsAfterNameSanitizing() {
        val script = generator.generate(
            de.visualtasker.blockeditor.ir.IrScript(
                "custom-command",
                listOf(de.visualtasker.blockeditor.ir.IrStatement.CommandCall("Custom.Plugin.run", "\"payload\"")),
            ),
        )
        assertEquals("Custom.Plugin.run(\"payload\");", script)
    }

    @Test fun escapesControlCharactersInTextLiterals() {
        val script = generator.generate(
            de.visualtasker.blockeditor.ir.IrScript(
                "escaped-text",
                listOf(
                    de.visualtasker.blockeditor.ir.IrStatement.ClickText("A\tB"),
                    de.visualtasker.blockeditor.ir.IrStatement.Log("line 1\nline 2"),
                    de.visualtasker.blockeditor.ir.IrStatement.While(
                        de.visualtasker.blockeditor.ir.IrExpression.ScreenContains("He said \"OK\""),
                        emptyList(),
                    ),
                ),
            ),
        )

        assertEquals(
            "click(\"A\\tB\");\nlog(\"line 1\\nline 2\");\nwhile (screenContains(\"He said \\\"OK\\\"\")) {\n}",
            script,
        )
    }
}
