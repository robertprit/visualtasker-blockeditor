package de.visualtasker.blockeditor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.full.memberProperties

class StatementInputTest {
    @Test
    fun statementInput_hasNoChildrenList() {
        val properties = StatementInput::class.memberProperties.map { it.name }
        assertFalse("StatementInput must not store children", properties.contains("children"))
    }

    @Test
    fun statementStack_usesConnectionsNotChildren() {
        val repeatId = BlockId("repeat")
        val aId = BlockId("a")
        val bId = BlockId("b")

        val repeatStmtConn = Connection(
            id = ConnectionId("repeat:DO:stmt"),
            owner = repeatId,
            kind = ConnectionKind.StatementInput,
            slotName = "DO",
        )
        val aPrev = Connection(
            id = ConnectionId("a:previous"),
            owner = aId,
            kind = ConnectionKind.Previous,
            connectedTo = repeatStmtConn.id,
        )
        val aNext = Connection(
            id = ConnectionId("a:next"),
            owner = aId,
            kind = ConnectionKind.Next,
            connectedTo = ConnectionId("b:previous"),
        )
        val bPrev = Connection(
            id = ConnectionId("b:previous"),
            owner = bId,
            kind = ConnectionKind.Previous,
            connectedTo = aNext.id,
        )
        val bNext = Connection(ConnectionId("b:next"), bId, ConnectionKind.Next)

        val repeat = BlockNode(
            id = repeatId,
            type = "control.repeat",
            next = Connection(ConnectionId("repeat:next"), repeatId, ConnectionKind.Next),
            statementInputs = listOf(
                StatementInput("DO", repeatStmtConn.copy(connectedTo = aPrev.id)),
            ),
        )
        val a = BlockNode(id = aId, type = "action.wait", previous = aPrev, next = aNext)
        val b = BlockNode(id = bId, type = "action.wait", previous = bPrev, next = bNext)

        val document = WorkspaceDocument(
            id = "test",
            blocks = mapOf(repeatId to repeat, aId to a, bId to b),
            rootBlocks = listOf(repeatId),
        )

        val stack = WorkspaceGraph.statementStack(document, repeatId, "DO")
        assertEquals(listOf(aId, bId), stack)
        assertEquals(aId, WorkspaceGraph.statementStackHead(document, repeatId, "DO"))
    }
}
