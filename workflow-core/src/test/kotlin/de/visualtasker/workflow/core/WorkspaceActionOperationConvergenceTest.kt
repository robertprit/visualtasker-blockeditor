package de.visualtasker.workflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceActionOperationConvergenceTest {
    @Test
    fun semanticActionPublishesThroughOneCanonicalRevision() {
        val before = WorkspaceDocument(id = "action-convergence")

        val after = WorkspaceReducer.reduce(
            before,
            WorkspaceAction.InstantiateBlock("statement", 20f, 40f, BlockId("one")),
            factory,
        )

        assertEquals(before.version + 1, after.version)
        assertTrue(after.blocks.containsKey(BlockId("one")))
        assertTrue(after.canonical!!.entities.any { it.legacySourceId == "one" })
    }

    @Test
    fun compoundEditorActionIsAtomicAndAdvancesOneRevision() {
        val before = WorkspaceDocument(id = "compound-action")
        val first = statement(BlockId("first"))
        val second = statement(BlockId("second"))

        val after = WorkspaceReducer.reduceAll(
            before,
            listOf(
                WorkspaceAction.CreateBlock(first, 20f, 40f),
                WorkspaceAction.CreateBlock(second, 20f, 140f),
                WorkspaceAction.Connect(first.next!!.id, second.previous!!.id),
            ),
            factory,
        )

        assertEquals(before.version + 1, after.version)
        assertEquals(second.previous.id, after.blocks.getValue(first.id).next!!.connectedTo)
        assertEquals(first.next.id, after.blocks.getValue(second.id).previous!!.connectedTo)
    }

    @Test
    fun failedCompoundActionRollsBackPayloadIdentityAndRevision() {
        val before = WorkspaceDocument(id = "compound-rollback")

        val after = WorkspaceReducer.reduceAll(
            before,
            listOf(
                WorkspaceAction.InstantiateBlock("statement", 20f, 40f, BlockId("created")),
                WorkspaceAction.Connect(ConnectionId("missing:next"), ConnectionId("missing:previous")),
            ),
            factory,
        )

        assertEquals(before, after)
    }

    @Test
    fun projectionOnlyActionsDoNotAdvanceCanonicalRevision() {
        val created = WorkspaceReducer.reduce(
            WorkspaceDocument(id = "projection-only"),
            WorkspaceAction.InstantiateBlock("statement", 20f, 40f, BlockId("one")),
            factory,
        )

        val moved = WorkspaceReducer.reduce(created, WorkspaceAction.MoveRoot(BlockId("one"), 240f, 360f), factory)
        val collapsed = WorkspaceReducer.reduce(moved, WorkspaceAction.Collapse(BlockId("one")), factory)

        assertEquals(created.version, moved.version)
        assertEquals(created.version, collapsed.version)
        assertEquals(WorkspacePoint(240f, 360f), collapsed.rootPositions[BlockId("one")])
        assertTrue(collapsed.blocks.getValue(BlockId("one")).collapsed)
    }

    @Test
    fun semanticPayloadReplacementUsesExplicitOperationAndKeepsIdentity() {
        val created = WorkspaceReducer.reduce(
            WorkspaceDocument(id = "payload-replacement"),
            WorkspaceAction.InstantiateBlock("statement", 20f, 40f, BlockId("one")),
            factory,
        )
        val entityId = created.canonical!!.entities.single { it.legacySourceId == "one" }.ref.id
        val replacement = created.blocks.getValue(BlockId("one")).copy(
            type = "alternate",
            fields = mapOf("label" to FieldValue.Text("Alternate")),
            metadata = mapOf("owner" to "preserved"),
        )

        val after = WorkspaceReducer.reduce(created, WorkspaceAction.ReplaceBlockShape(replacement), factory)

        assertEquals(created.version + 1, after.version)
        assertEquals(entityId, after.canonical!!.entities.single { it.legacySourceId == "one" }.ref.id)
        assertEquals("alternate", after.blocks.getValue(BlockId("one")).type)
        assertEquals("preserved", after.blocks.getValue(BlockId("one")).metadata["owner"])
    }

    @Test
    fun invalidTypedPropertyDoesNotPublishStagedPayload() {
        val created = WorkspaceReducer.reduce(
            WorkspaceDocument(id = "invalid-property"),
            WorkspaceAction.InstantiateBlock("statement", 20f, 40f, BlockId("one")),
            factory,
        )

        val after = WorkspaceReducer.reduce(
            created,
            WorkspaceAction.UpdateField(BlockId("one"), "count", FieldValue.Text("not-a-number")),
            factory,
        )

        assertEquals(created, after)
        assertFalse(after.blocks.getValue(BlockId("one")).fields["count"] is FieldValue.Text)
    }

    @Test
    fun repeatedEditorConnectionProducesStableRelationIdentity() {
        fun connected(): WorkspaceDocument {
            val first = statement(BlockId("first"))
            val second = statement(BlockId("second"))
            return WorkspaceReducer.reduceAll(
                WorkspaceDocument(id = "stable-relations"),
                listOf(
                    WorkspaceAction.CreateBlock(first, 20f, 40f),
                    WorkspaceAction.CreateBlock(second, 20f, 140f),
                    WorkspaceAction.Connect(first.next!!.id, second.previous!!.id),
                ),
                factory,
            )
        }

        val first = connected()
        val second = connected()

        assertEquals(first.canonical, second.canonical)
        assertEquals(first.blocks, second.blocks)
    }

    private val factory = BlockFactory { definitionId, id ->
        when (definitionId) {
            "statement", "alternate" -> statement(id).copy(
                type = definitionId,
                fields = mapOf(
                    "label" to FieldValue.Text(definitionId),
                    "count" to FieldValue.Number(1.0),
                ),
            )
            else -> null
        }
    }

    private fun statement(id: BlockId): BlockNode = BlockNode(
        id = id,
        type = "statement",
        previous = Connection(ConnectionId("${id.value}:previous"), id, ConnectionKind.Previous),
        next = Connection(ConnectionId("${id.value}:next"), id, ConnectionKind.Next),
    )
}
