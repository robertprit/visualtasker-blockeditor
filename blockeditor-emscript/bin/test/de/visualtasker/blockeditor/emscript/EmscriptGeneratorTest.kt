package de.visualtasker.blockeditor.emscript

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry
import de.visualtasker.blockeditor.registry.SampleWorkspaceFactory
import de.visualtasker.blockeditor.registry.createNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmscriptGeneratorTest {
    private val generator = EmscriptGenerator()

    @Test
    fun generatesDeterministicIndentedScript() {
        val document = SampleWorkspaceFactory.createDemo()
        val first = generator.generate(document)
        val second = generator.generate(document)
        assertEquals(first, second)
        assertTrue(first.contains("CLICK \"OK\""))
        assertTrue(first.contains("REPEAT 3"))
        assertTrue(first.contains("END"))
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
        assertTrue(script.contains("IF FALSE"))
        assertTrue(script.contains("ELSEIF FALSE"))
        assertTrue(script.contains("ELSE"))
        assertTrue(script.contains("END"))
    }
}
