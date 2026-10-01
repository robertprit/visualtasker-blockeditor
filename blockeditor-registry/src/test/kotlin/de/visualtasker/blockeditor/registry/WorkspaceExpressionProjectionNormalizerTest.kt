package de.visualtasker.blockeditor.registry

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.ConnectionId
import de.visualtasker.blockeditor.domain.FieldValue
import de.visualtasker.blockeditor.domain.VariableDefinition
import de.visualtasker.blockeditor.domain.VariableRegistry
import de.visualtasker.blockeditor.domain.VariableScope
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WorkspaceExpressionProjectionNormalizerTest {
    @Test
    fun legacyVariableGetNormalizesWithoutChangingIdentityConnectionOrType() {
        val blockId = BlockId("legacy-get")
        val legacy = DefaultBlockRegistry.getDefinition(BlockTypes.VARIABLE_GET)!!
            .createNode(blockId)
            .copy(
                fields = mapOf("variable" to FieldValue.Text("stable-id")),
                output = DefaultBlockRegistry.getDefinition(BlockTypes.VARIABLE_GET)!!
                    .createNode(blockId)
                    .output
                    ?.copy(connectedTo = ConnectionId("parent:value")),
            )
        val document = WorkspaceDocument(
            id = "legacy-variable",
            blocks = mapOf(blockId to legacy),
            rootBlocks = listOf(blockId),
            variables = VariableRegistry(
                mapOf("stable-id" to VariableDefinition("stable-id", "Renamable label", "Number", VariableScope.Script)),
            ),
        )

        val normalized = WorkspaceExpressionProjectionNormalizer.normalize(document)
        val reporter = normalized.blocks.getValue(blockId)

        assertEquals(blockId, reporter.id)
        assertEquals("variable.reporter.stable-id", reporter.type)
        assertEquals(FieldValue.Text("stable-id"), reporter.fields["variableId"])
        assertEquals(FieldValue.Text("Renamable label"), reporter.fields["variableLabel"])
        assertEquals(legacy.output?.id, reporter.output?.id)
        assertEquals(legacy.output?.connectedTo, reporter.output?.connectedTo)
        assertEquals("Number", reporter.output?.provides)
        assertEquals(normalized, WorkspaceExpressionProjectionNormalizer.normalize(normalized))
    }

    @Test
    fun unresolvedLegacyVariableIdentityIsPreservedInsteadOfGuessedFromLabel() {
        val blockId = BlockId("unresolved-get")
        val legacy = DefaultBlockRegistry.getDefinition(BlockTypes.VARIABLE_GET)!!
            .createNode(blockId)
            .copy(fields = mapOf("variable" to FieldValue.Text("display-only")))
        val document = WorkspaceDocument(id = "unresolved", blocks = mapOf(blockId to legacy))

        val normalized = WorkspaceExpressionProjectionNormalizer.normalize(document)

        assertSame(legacy, normalized.blocks.getValue(blockId))
    }

    @Test
    fun legacyBooleanNormalizesToCanonicalLiteralIdempotently() {
        val blockId = BlockId("legacy-bool")
        val legacy = DefaultBlockRegistry.getDefinition(BlockTypes.LOGIC_BOOLEAN)!!
            .createNode(blockId)
            .copy(fields = mapOf("value" to FieldValue.Bool(true)))
        val document = WorkspaceDocument(id = "legacy-bool", blocks = mapOf(blockId to legacy))

        val normalized = WorkspaceExpressionProjectionNormalizer.normalize(document)
        val literal = normalized.blocks.getValue(blockId)

        assertEquals(BlockTypes.LITERAL_BOOLEAN, literal.type)
        assertEquals(FieldValue.Bool(true), literal.fields["value"])
        assertEquals(legacy.output, literal.output)
        assertEquals(normalized, WorkspaceExpressionProjectionNormalizer.normalize(normalized))
    }
}
