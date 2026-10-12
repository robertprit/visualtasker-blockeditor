package de.visualtasker.workflow.core

@JvmInline
value class ProjectOperationId(val value: String) {
    init { require(value.isNotBlank()) { "Project operation ID must not be blank." } }
}

sealed interface ProjectOperation {
    val operationId: ProjectOperationId

    data class CreateRoot(override val operationId: ProjectOperationId, val root: ProjectWorkflowRoot) : ProjectOperation
    data class RenameRoot(override val operationId: ProjectOperationId, val rootId: WorkflowRootId, val name: String) : ProjectOperation
    data class DeleteRoot(override val operationId: ProjectOperationId, val rootId: WorkflowRootId) : ProjectOperation
    data class SetActiveRoot(override val operationId: ProjectOperationId, val rootId: WorkflowRootId) : ProjectOperation
    data class CreateDefinition(override val operationId: ProjectOperationId, val definition: ProjectDefinition) : ProjectOperation
    data class DeleteDefinition(override val operationId: ProjectOperationId, val definitionId: DefinitionId) : ProjectOperation
    data class AddResource(override val operationId: ProjectOperationId, val resource: ProjectResource) : ProjectOperation
    data class RemoveResource(override val operationId: ProjectOperationId, val resourceId: ResourceId) : ProjectOperation
    data class PutVariable(override val operationId: ProjectOperationId, val variable: ProjectVariable) : ProjectOperation
    data class RemoveVariable(override val operationId: ProjectOperationId, val variableId: String) : ProjectOperation
    data class BindReference(override val operationId: ProjectOperationId, val reference: ProjectReference) : ProjectOperation
    data class UnbindReference(override val operationId: ProjectOperationId, val referenceId: ProjectReferenceId) : ProjectOperation

    /** The only project operation allowed to mutate a root's canonical workflow. */
    data class ApplyWorkspaceTransaction(
        override val operationId: ProjectOperationId,
        val rootId: WorkflowRootId,
        val transaction: WorkspaceTransaction,
        val stagedPayload: WorkspaceDocument? = null,
    ) : ProjectOperation

    /** Accepts an already validated workspace at shell/import/recorder boundaries. */
    data class ReplaceRootWorkspace(
        override val operationId: ProjectOperationId,
        val rootId: WorkflowRootId,
        val expectedWorkspaceRevision: Long,
        val workspace: WorkspaceDocument,
    ) : ProjectOperation
}

data class ProjectTransaction(
    val transactionId: ProjectOperationId,
    val operations: List<ProjectOperation>,
    val expectedRevision: ProjectRevision? = null,
)

enum class ProjectOperationDiagnosticCode {
    RevisionMismatch,
    DuplicateOperationId,
    DuplicateId,
    RootNotFound,
    DefinitionNotFound,
    ResourceNotFound,
    VariableNotFound,
    ReferenceNotFound,
    ReferencedItem,
    WorkspaceRevisionMismatch,
    WorkspaceMutationRejected,
    ValidationFailed,
}

data class ProjectOperationDiagnostic(
    val code: ProjectOperationDiagnosticCode,
    val message: String,
    val operationId: ProjectOperationId? = null,
)

sealed interface ProjectOperationResult {
    data class Success(
        val project: ProjectDocument,
        val affectedRootIds: Set<WorkflowRootId>,
        val affectedDefinitionIds: Set<DefinitionId>,
        val affectedResourceIds: Set<ResourceId>,
    ) : ProjectOperationResult

    data class Failure(
        val project: ProjectDocument,
        val diagnostic: ProjectOperationDiagnostic,
    ) : ProjectOperationResult
}

object ProjectOperationExecutor {
    fun execute(project: ProjectDocument, operation: ProjectOperation): ProjectOperationResult =
        execute(project, ProjectTransaction(operation.operationId, listOf(operation), project.revision))

    fun execute(project: ProjectDocument, transaction: ProjectTransaction): ProjectOperationResult {
        if (transaction.expectedRevision != null && transaction.expectedRevision != project.revision) {
            return failure(project, ProjectOperationDiagnosticCode.RevisionMismatch, "Expected project revision ${transaction.expectedRevision.value}, found ${project.revision.value}.")
        }
        val duplicateOperation = transaction.operations.groupBy { it.operationId }.entries.firstOrNull { it.value.size > 1 }
        if (duplicateOperation != null) {
            return failure(project, ProjectOperationDiagnosticCode.DuplicateOperationId, "Duplicate operation ${duplicateOperation.key.value}.", duplicateOperation.key)
        }
        var staged = project
        val roots = linkedSetOf<WorkflowRootId>()
        val definitions = linkedSetOf<DefinitionId>()
        val resources = linkedSetOf<ResourceId>()
        transaction.operations.forEach { operation ->
            val applied = apply(staged, operation)
            if (applied is ProjectOperationResult.Failure) return applied.copy(project = project)
            applied as ProjectOperationResult.Success
            staged = applied.project
            roots += applied.affectedRootIds
            definitions += applied.affectedDefinitionIds
            resources += applied.affectedResourceIds
        }
        val committed = staged.copy(revision = ProjectRevision(project.revision.value + 1L))
        val issues = ProjectValidator.validate(committed)
        if (issues.isNotEmpty()) {
            return failure(project, ProjectOperationDiagnosticCode.ValidationFailed, issues.joinToString("; ") { it.message })
        }
        return ProjectOperationResult.Success(committed, roots, definitions, resources)
    }

    private fun apply(project: ProjectDocument, operation: ProjectOperation): ProjectOperationResult {
        fun success(
            next: ProjectDocument,
            roots: Set<WorkflowRootId> = emptySet(),
            definitions: Set<DefinitionId> = emptySet(),
            resources: Set<ResourceId> = emptySet(),
        ) = ProjectOperationResult.Success(next, roots, definitions, resources)
        fun reject(code: ProjectOperationDiagnosticCode, message: String) = failure(project, code, message, operation.operationId)
        return when (operation) {
            is ProjectOperation.CreateRoot -> if (operation.root.id in project.roots) reject(ProjectOperationDiagnosticCode.DuplicateId, "Root ${operation.root.id.value} already exists.") else success(
                project.copy(
                    roots = project.roots + (operation.root.id to operation.root),
                    manifest = if (project.manifest.activeRootId == null) project.manifest.copy(activeRootId = operation.root.id) else project.manifest,
                ),
                setOf(operation.root.id),
            )
            is ProjectOperation.RenameRoot -> project.roots[operation.rootId]?.let { root ->
                success(project.copy(roots = project.roots + (operation.rootId to root.copy(name = operation.name))), setOf(operation.rootId))
            } ?: reject(ProjectOperationDiagnosticCode.RootNotFound, "Root ${operation.rootId.value} is missing.")
            is ProjectOperation.DeleteRoot -> {
                if (operation.rootId !in project.roots) reject(ProjectOperationDiagnosticCode.RootNotFound, "Root ${operation.rootId.value} is missing.")
                else if (project.references.values.any { it.source.ownerId == operation.rootId.value }) reject(ProjectOperationDiagnosticCode.ReferencedItem, "Root ${operation.rootId.value} still owns references.")
                else success(
                    project.copy(
                        roots = project.roots - operation.rootId,
                        manifest = project.manifest.copy(activeRootId = project.manifest.activeRootId?.takeUnless { it == operation.rootId } ?: (project.roots.keys - operation.rootId).sortedBy { it.value }.firstOrNull()),
                    ),
                    setOf(operation.rootId),
                )
            }
            is ProjectOperation.SetActiveRoot -> if (operation.rootId !in project.roots) reject(ProjectOperationDiagnosticCode.RootNotFound, "Root ${operation.rootId.value} is missing.") else success(
                project.copy(manifest = project.manifest.copy(activeRootId = operation.rootId)),
                setOf(operation.rootId),
            )
            is ProjectOperation.CreateDefinition -> if (operation.definition.id in project.definitions) reject(ProjectOperationDiagnosticCode.DuplicateId, "Definition ${operation.definition.id.value} already exists.") else success(
                project.copy(definitions = project.definitions + (operation.definition.id to operation.definition)),
                definitions = setOf(operation.definition.id),
            )
            is ProjectOperation.DeleteDefinition -> when {
                operation.definitionId !in project.definitions -> reject(ProjectOperationDiagnosticCode.DefinitionNotFound, "Definition ${operation.definitionId.value} is missing.")
                project.whereUsed(operation.definitionId).isNotEmpty() -> reject(ProjectOperationDiagnosticCode.ReferencedItem, "Definition ${operation.definitionId.value} is still referenced.")
                else -> success(project.copy(definitions = project.definitions - operation.definitionId), definitions = setOf(operation.definitionId))
            }
            is ProjectOperation.AddResource -> if (operation.resource.id in project.resources) reject(ProjectOperationDiagnosticCode.DuplicateId, "Resource ${operation.resource.id.value} already exists.") else success(
                project.copy(resources = project.resources + (operation.resource.id to operation.resource)),
                resources = setOf(operation.resource.id),
            )
            is ProjectOperation.RemoveResource -> when {
                operation.resourceId !in project.resources -> reject(ProjectOperationDiagnosticCode.ResourceNotFound, "Resource ${operation.resourceId.value} is missing.")
                project.whereUsed(operation.resourceId).isNotEmpty() -> reject(ProjectOperationDiagnosticCode.ReferencedItem, "Resource ${operation.resourceId.value} is still referenced.")
                else -> success(project.copy(resources = project.resources - operation.resourceId), resources = setOf(operation.resourceId))
            }
            is ProjectOperation.PutVariable -> success(project.copy(variables = project.variables + (operation.variable.definition.id to operation.variable)))
            is ProjectOperation.RemoveVariable -> when {
                operation.variableId !in project.variables -> reject(ProjectOperationDiagnosticCode.VariableNotFound, "Variable ${operation.variableId} is missing.")
                project.references.values.any { (it.target as? ProjectReferenceTarget.Variable)?.variableId == operation.variableId } -> reject(ProjectOperationDiagnosticCode.ReferencedItem, "Variable ${operation.variableId} is still referenced.")
                else -> success(project.copy(variables = project.variables - operation.variableId))
            }
            is ProjectOperation.BindReference -> if (operation.reference.id in project.references) reject(ProjectOperationDiagnosticCode.DuplicateId, "Reference ${operation.reference.id.value} already exists.") else success(
                project.copy(references = project.references + (operation.reference.id to operation.reference)),
            )
            is ProjectOperation.UnbindReference -> if (operation.referenceId !in project.references) reject(ProjectOperationDiagnosticCode.ReferenceNotFound, "Reference ${operation.referenceId.value} is missing.") else success(
                project.copy(references = project.references - operation.referenceId),
            )
            is ProjectOperation.ApplyWorkspaceTransaction -> project.roots[operation.rootId]?.let { root ->
                val result = operation.stagedPayload?.let {
                    WorkspaceOperationExecutor.executeWithStagedPayload(root.workspace, operation.transaction, it)
                } ?: WorkspaceOperationExecutor.execute(root.workspace, operation.transaction)
                when (result) {
                    is WorkspaceOperationResult.Success -> success(
                        project.copy(roots = project.roots + (operation.rootId to root.copy(workspace = result.document))),
                        setOf(operation.rootId),
                    )
                    is WorkspaceOperationResult.Failure -> reject(ProjectOperationDiagnosticCode.WorkspaceMutationRejected, result.diagnostic.message)
                }
            } ?: reject(ProjectOperationDiagnosticCode.RootNotFound, "Root ${operation.rootId.value} is missing.")
            is ProjectOperation.ReplaceRootWorkspace -> project.roots[operation.rootId]?.let { root ->
                when {
                    root.workspace.version != operation.expectedWorkspaceRevision -> reject(ProjectOperationDiagnosticCode.WorkspaceRevisionMismatch, "Expected workspace revision ${operation.expectedWorkspaceRevision}, found ${root.workspace.version}.")
                    operation.workspace.id != root.workspace.id -> reject(ProjectOperationDiagnosticCode.WorkspaceMutationRejected, "Replacement workspace ID does not match the root workspace.")
                    else -> success(project.copy(roots = project.roots + (operation.rootId to root.copy(workspace = operation.workspace))), setOf(operation.rootId))
                }
            } ?: reject(ProjectOperationDiagnosticCode.RootNotFound, "Root ${operation.rootId.value} is missing.")
        }
    }

    private fun failure(project: ProjectDocument, code: ProjectOperationDiagnosticCode, message: String, operationId: ProjectOperationId? = null) =
        ProjectOperationResult.Failure(project, ProjectOperationDiagnostic(code, message, operationId))
}
