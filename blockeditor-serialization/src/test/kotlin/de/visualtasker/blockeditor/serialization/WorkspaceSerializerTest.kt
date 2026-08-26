package de.visualtasker.blockeditor.serialization

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.BlockNode
import de.visualtasker.blockeditor.domain.FieldValue
import de.visualtasker.blockeditor.domain.Offset2
import de.visualtasker.blockeditor.domain.WorkspaceAction
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.WorkspacePoint
import de.visualtasker.blockeditor.domain.WorkspaceReducer
import de.visualtasker.blockeditor.domain.VariableDefinition
import de.visualtasker.blockeditor.domain.VariableRegistry
import de.visualtasker.blockeditor.domain.VariableScope
import de.visualtasker.blockeditor.domain.asString
import de.visualtasker.blockeditor.domain.rootOffset
import de.visualtasker.blockeditor.domain.withRootOffset
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry
import de.visualtasker.blockeditor.registry.SampleWorkspaceFactory
import de.visualtasker.blockeditor.registry.WorkspaceBootstrap
import de.visualtasker.blockeditor.registry.asFactory
import de.visualtasker.blockeditor.registry.createNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceSerializerTest {
    @Test
    fun roundTrip_preservesWorkspace() {
        val original = SampleWorkspaceFactory.createDemo()
        val json = WorkspaceSerializer.serialize(original)
        val restored = WorkspaceSerializer.deserialize(json)

        assertEquals(original.id, restored.id)
        assertEquals(original.blocks.size, restored.blocks.size)
        assertEquals(original.rootBlocks, restored.rootBlocks)
        assertTrue(json.contains("\"type\":\"event.start\""))
    }

    @Test
    fun sameDocument_serializesIdenticallyTwice() {
        val document = WorkspaceBootstrap.starter()
        val first = WorkspaceSerializer.serialize(document)
        val second = WorkspaceSerializer.serialize(document)
        assertEquals(first, second)
    }

    @Test
    fun serializeDeserializeSerialize_isByteIdentical() {
        val document = SampleWorkspaceFactory.createDemo()
        val first = WorkspaceSerializer.serialize(document)
        val restored = WorkspaceSerializer.deserialize(first)
        val second = WorkspaceSerializer.serialize(restored)
        assertEquals(first, second)
    }

    @Test
    fun rootPositions_roundTripAsDocumentState() {
        var document = WorkspaceBootstrap.empty()
        document = WorkspaceReducer.reduce(
            document,
            WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 42f, 84f),
            DefaultBlockRegistry.asFactory(),
        )
        val rootId = document.rootBlocks.single()

        val json = WorkspaceSerializer.serialize(document)
        val restored = WorkspaceSerializer.deserialize(json)

        assertTrue(json.contains("\"rootPositions\""))
        assertEquals(WorkspacePoint(42f, 84f), restored.rootPositions[rootId])
        assertEquals(document.rootOffset(rootId), restored.rootOffset(rootId))
    }

    @Test
    fun variablesAndStableBindingsRoundTrip() {
        val getter = BlockNode(
            id = BlockId("getter"),
            type = "variable.reporter.score-id",
            fields = mapOf("variable" to FieldValue.Text("score")),
        )
        val setter = BlockNode(
            id = BlockId("setter"),
            type = "emscript:variable.assign",
            fields = mapOf(
                "variableId" to FieldValue.Text("score-id"),
                "name" to FieldValue.Text("score"),
                "value" to FieldValue.Text("0"),
            ),
        )
        val document = WorkspaceDocument(
            id = "variable-roundtrip",
            blocks = mapOf(getter.id to getter, setter.id to setter),
            rootBlocks = listOf(getter.id, setter.id),
            variables = VariableRegistry(
                mapOf(
                    "score-id" to VariableDefinition(
                        id = "score-id",
                        name = "score",
                        type = "Number",
                        scope = VariableScope.Script,
                        defaultValue = "0",
                    ),
                ),
            ),
        )

        val restored = WorkspaceSerializer.deserialize(WorkspaceSerializer.serialize(document))

        assertEquals("score-id", restored.variables.variables.getValue("score-id").id)
        assertEquals("score", restored.variables.variables.getValue("score-id").name)
        assertEquals("Number", restored.variables.variables.getValue("score-id").type)
        assertEquals("0", restored.variables.variables.getValue("score-id").defaultValue)
        assertEquals("variable.reporter.score-id", restored.blocks.getValue(getter.id).type)
        assertEquals("score-id", restored.blocks.getValue(setter.id).fields.getValue("variableId").asString())
    }

    @Test
    fun startBlockColor_roundTripsAsEditableField() {
        val factory = DefaultBlockRegistry.asFactory()
        var document = WorkspaceDocument(id = "start-color")
        document = WorkspaceReducer.reduce(
            document,
            WorkspaceAction.InstantiateBlock(BlockTypes.EVENT_START, 12f, 16f),
            factory,
        )
        val startId = document.rootBlocks.single()
        document = WorkspaceReducer.reduce(
            document,
            WorkspaceAction.UpdateField(startId, "color", FieldValue.Text("violet")),
            factory,
        )

        val restored = WorkspaceSerializer.deserialize(WorkspaceSerializer.serialize(document))

        assertEquals("violet", restored.blocks[startId]!!.fields["color"]!!.asString())
    }

    @Test
    fun legacyRootMetadata_deserializesIntoRootPositions() {
        val blockId = BlockId("legacy-root")
        val block = DefaultBlockRegistry.getDefinition(BlockTypes.ACTION_WAIT)!!
            .createNode(blockId)
            .withRootOffset(11f, 22f)
        val legacyJson = WorkspaceSerializer.serialize(
            WorkspaceDocument(
                id = "legacy-root-position",
                blocks = mapOf(blockId to block),
                rootBlocks = listOf(blockId),
            ),
        ).replace(""""rootPositions":[],""", "")

        val restored = WorkspaceSerializer.deserialize(legacyJson)

        assertEquals(WorkspacePoint(11f, 22f), restored.rootPositions[blockId])
        assertEquals(Offset2(11f, 22f), restored.rootOffset(blockId))
    }

    @Test
    fun schemaVersion_isPresent() {
        val json = WorkspaceSerializer.serialize(WorkspaceBootstrap.starter())
        assertTrue(json.contains("\"schemaVersion\":$WORKSPACE_SCHEMA_VERSION"))
    }

    @Test
    fun blockOrdering_isStableAcrossReorderedMapInsertion() {
        val factory = DefaultBlockRegistry.asFactory()
        var document = WorkspaceDocument(id = "ordering")
        document = WorkspaceReducer.reduce(
            document,
            WorkspaceAction.InstantiateBlock(BlockTypes.EVENT_START, 10f, 10f),
            factory,
        )
        document = WorkspaceReducer.reduce(
            document,
            WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_CLICK_TEXT, 10f, 120f),
            factory,
        )
        document = WorkspaceReducer.reduce(
            document,
            WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 10f, 220f),
            factory,
        )

        val reversedBlocks = linkedMapOf<BlockId, de.visualtasker.blockeditor.domain.BlockNode>()
        document.blocks.entries.reversed().forEach { reversedBlocks[it.key] = it.value }
        val reordered = document.copy(blocks = reversedBlocks)

        val canonical = WorkspaceSerializer.serialize(document)
        assertEquals(canonical, WorkspaceSerializer.serialize(reordered))

        val blockEntryIds = Regex("""\{"id":"([^"]+)","node":""")
            .findAll(canonical)
            .map { it.groupValues[1] }
            .toList()
        assertTrue(blockEntryIds.isNotEmpty())
        assertEquals(blockEntryIds.sorted(), blockEntryIds)
    }

    @Test
    fun connectionOrdering_isStable() {
        val document = SampleWorkspaceFactory.createWithStatementSlot()
        val first = WorkspaceSerializer.serialize(document)
        val second = WorkspaceSerializer.serialize(WorkspaceSerializer.deserialize(first))
        assertEquals(first, second)
        assertTrue(first.contains("\"valueInputs\""))
        assertTrue(first.contains("\"statementInputs\""))
    }

    @Test
    fun branchInputOrder_roundTripsWithoutAlphabeticResort() {
        val ifId = BlockId("if-elif-else")
        val block = DefaultBlockRegistry.getDefinition(BlockTypes.CONTROL_IF_ELSEIF_ELSE)!!
            .createNode(ifId)
            .withRootOffset(10f, 20f)
        val document = WorkspaceDocument(
            id = "branch-order",
            blocks = mapOf(ifId to block),
            rootBlocks = listOf(ifId),
        )

        val restored = WorkspaceSerializer.deserialize(WorkspaceSerializer.serialize(document))

        assertEquals(
            listOf(BlockTypes.SLOT_THEN, BlockTypes.SLOT_ELIF, BlockTypes.SLOT_ELSE),
            restored.blocks.getValue(ifId).statementInputs.map { it.name },
        )
        assertEquals(
            listOf("CONDITION", "ELIF_CONDITION"),
            restored.blocks.getValue(ifId).valueInputs.map { it.name },
        )
    }

    @Test
    fun unsupportedSchemaVersion_failsDeterministically() {
        val baseline = WorkspaceSerializer.serialize(WorkspaceBootstrap.starter())
        val unsupported = baseline.replace(
            "\"schemaVersion\":$WORKSPACE_SCHEMA_VERSION",
            "\"schemaVersion\":99",
        )
        val failure = try {
            WorkspaceSerializer.deserialize(unsupported)
            null
        } catch (error: WorkspaceSerializationException) {
            error
        }
        assertNotEquals(null, failure)
        assertTrue(failure!!.message!!.contains("Unsupported workspace schema version"))
    }

    @Test
    fun malformedJson_failsDeterministically() {
        val failure = try {
            WorkspaceSerializer.deserialize("{not-json")
            null
        } catch (error: WorkspaceSerializationException) {
            error
        }
        assertNotEquals(null, failure)
        assertTrue(failure!!.message!!.contains("Malformed workspace JSON"))
    }

    @Test
    fun blankDocument_failsDeterministically() {
        val failure = try {
            WorkspaceSerializer.deserialize("   ")
            null
        } catch (error: WorkspaceSerializationException) {
            error
        }
        assertNotEquals(null, failure)
        assertEquals("Workspace document is blank.", failure!!.message)
    }

    @Test
    fun transientEditorVersion_doesNotAddNonCanonicalFields() {
        val starter = WorkspaceBootstrap.starter()
        val mutated = starter.copy(version = starter.version + 5)
        val json = WorkspaceSerializer.serialize(mutated)
        assertFalse(json.contains("drag"))
        assertFalse(json.contains("viewport"))
        assertFalse(json.contains("selection"))
        assertTrue(json.contains("\"version\":${starter.version + 5}"))
    }
}
