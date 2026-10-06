package de.visualtasker.blockeditor.serialization

import de.visualtasker.workflow.semantics.DefaultWorkflowSemanticDefinitions
import de.visualtasker.workflow.semantics.WorkflowSemanticDefinitionProvider
import de.visualtasker.workflow.core.WorkspaceDocument
import de.visualtasker.workflow.serialization.WORKFLOW_SCHEMA_VERSION
import de.visualtasker.workflow.serialization.WorkflowCompatibilityDiagnostic
import de.visualtasker.workflow.serialization.WorkflowCompatibilitySeverity
import de.visualtasker.workflow.serialization.WorkflowDecodeResult
import de.visualtasker.workflow.serialization.WorkflowDefinitionResolver
import de.visualtasker.workflow.serialization.WorkflowDefinitionShape
import de.visualtasker.workflow.serialization.WorkflowSerializationException
import de.visualtasker.workflow.serialization.WorkflowSerializer

const val WORKSPACE_SCHEMA_VERSION: Int = WORKFLOW_SCHEMA_VERSION

object WorkspaceSerializer {
    fun serialize(document: WorkspaceDocument): String = WorkflowSerializer.serialize(document)

    fun decode(
        raw: String,
        definitions: WorkflowSemanticDefinitionProvider = DefaultWorkflowSemanticDefinitions,
    ): WorkspaceDecodeResult = WorkflowSerializer.decode(raw, definitions.asWorkflowDefinitionResolver()).toLegacy()

    fun deserialize(raw: String): WorkspaceDocument = try {
        WorkflowSerializer.deserialize(raw)
    } catch (error: WorkflowSerializationException) {
        throw WorkspaceSerializationException(error.message ?: "Malformed workspace JSON.", error)
    }
}

private fun WorkflowSemanticDefinitionProvider.asWorkflowDefinitionResolver(): WorkflowDefinitionResolver =
    WorkflowDefinitionResolver { type ->
        semanticDefinition(type)?.let { definition ->
            WorkflowDefinitionShape(
                valueInputs = definition.valueInputs.mapTo(linkedSetOf()) { it.name },
                statementInputs = definition.statementInputs,
            )
        }
    }

private fun WorkflowDecodeResult.toLegacy(): WorkspaceDecodeResult = when (this) {
    is WorkflowDecodeResult.Decoded -> WorkspaceDecodeResult.Decoded(
        document = document,
        diagnostics = diagnostics.map(WorkflowCompatibilityDiagnostic::toLegacy),
    )
    is WorkflowDecodeResult.UnsupportedSchema -> WorkspaceDecodeResult.UnsupportedSchema(
        version = version,
        diagnostics = diagnostics.map(WorkflowCompatibilityDiagnostic::toLegacy),
    )
    is WorkflowDecodeResult.Malformed -> WorkspaceDecodeResult.Malformed(
        reason = reason,
        diagnostics = diagnostics.map(WorkflowCompatibilityDiagnostic::toLegacy),
    )
}

private fun WorkflowCompatibilityDiagnostic.toLegacy(): WorkspaceCompatibilityDiagnostic =
    WorkspaceCompatibilityDiagnostic(
        severity = when (severity) {
            WorkflowCompatibilitySeverity.INFO -> WorkspaceCompatibilitySeverity.INFO
            WorkflowCompatibilitySeverity.WARNING -> WorkspaceCompatibilitySeverity.WARNING
            WorkflowCompatibilitySeverity.ERROR -> WorkspaceCompatibilitySeverity.ERROR
        },
        code = code,
        message = message,
        blockId = blockId,
    )
