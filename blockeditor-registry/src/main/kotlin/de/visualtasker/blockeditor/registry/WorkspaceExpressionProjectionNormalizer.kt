package de.visualtasker.blockeditor.registry

import de.visualtasker.workflow.core.WorkspaceDocument
import de.visualtasker.workflow.serialization.WorkflowDocumentNormalizer

/** Normalizes persisted visual aliases without changing semantic identities. */
object WorkspaceExpressionProjectionNormalizer {
    fun normalize(document: WorkspaceDocument): WorkspaceDocument =
        WorkflowDocumentNormalizer.normalize(document)
}
