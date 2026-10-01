package de.visualtasker.blockeditor.registry

import de.visualtasker.blockeditor.domain.FieldValue
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.asString

/** Normalizes persisted visual aliases without changing semantic identities. */
object WorkspaceExpressionProjectionNormalizer {
    fun normalize(document: WorkspaceDocument): WorkspaceDocument {
        val normalizedBlocks = document.blocks.mapValues { (_, block) ->
            when (block.type) {
                BlockTypes.VARIABLE_GET -> normalizeLegacyVariableReporter(document, block)
                BlockTypes.LOGIC_BOOLEAN -> block.copy(type = BlockTypes.LITERAL_BOOLEAN)
                else -> block
            }
        }
        return if (normalizedBlocks == document.blocks) document else document.copy(blocks = normalizedBlocks)
    }

    private fun normalizeLegacyVariableReporter(
        document: WorkspaceDocument,
        block: de.visualtasker.blockeditor.domain.BlockNode,
    ): de.visualtasker.blockeditor.domain.BlockNode {
        val variableId = block.fields["variableId"]?.asString()?.takeIf(String::isNotBlank)
            ?: block.fields["variable"]?.asString()?.takeIf { it in document.variables.variables }
            ?: return block
        val variable = document.variables.variables[variableId]
        val variableLabel = block.fields["variableLabel"]?.asString()?.takeIf(String::isNotBlank)
            ?: variable?.name
            ?: variableId
        val outputType = variable?.type?.ifBlank { null } ?: block.output?.provides ?: "Any"
        return block.copy(
            type = VariableReporterFactory.reporterId(variableId),
            fields = block.fields + mapOf(
                "variable" to FieldValue.Text(variableLabel),
                "variableId" to FieldValue.Text(variableId),
                "variableLabel" to FieldValue.Text(variableLabel),
            ),
            output = block.output?.copy(
                accepts = setOf(outputType),
                provides = outputType,
            ),
        )
    }
}
