package de.visualtasker.workflow.core

import kotlinx.serialization.Serializable

const val PROJECT_SCHEMA_VERSION = 1

@JvmInline
@Serializable
value class ProjectId(val value: String) {
    init { require(value.isNotBlank()) { "Project ID must not be blank." } }
}

@JvmInline
@Serializable
value class WorkflowRootId(val value: String) {
    init { require(value.isNotBlank()) { "Workflow root ID must not be blank." } }
}

@JvmInline
@Serializable
value class DefinitionId(val value: String) {
    init { require(value.isNotBlank()) { "Definition ID must not be blank." } }
}

@JvmInline
@Serializable
value class ResourceId(val value: String) {
    init { require(value.isNotBlank()) { "Resource ID must not be blank." } }
}

@JvmInline
@Serializable
value class ProjectReferenceId(val value: String) {
    init { require(value.isNotBlank()) { "Project reference ID must not be blank." } }
}

@JvmInline
@Serializable
value class ProjectRevision(val value: Long) {
    init { require(value >= 0L) { "Project revision must not be negative." } }
}

data class ProjectManifest(
    val name: String,
    val activeRootId: WorkflowRootId? = null,
    val metadata: Map<String, String> = emptyMap(),
)

data class ProjectWorkflowRoot(
    val id: WorkflowRootId,
    val name: String,
    val workspace: WorkspaceDocument,
    val metadata: Map<String, String> = emptyMap(),
)

enum class ProjectDefinitionKind { Function, Subworkflow, CustomFlow }

data class ProjectDefinition(
    val id: DefinitionId,
    val name: String,
    val kind: ProjectDefinitionKind,
    val workspace: WorkspaceDocument? = null,
    val metadata: Map<String, String> = emptyMap(),
)

enum class ProjectResourceType {
    Image,
    Template,
    Region,
    Point,
    Path,
    Shape,
    Capture,
    Observation,
    Provider,
}

enum class ProjectResourceScope { Embedded, ProjectLocal, AppShared, External }

data class ProjectResource(
    val id: ResourceId,
    val name: String,
    val type: ProjectResourceType,
    val scope: ProjectResourceScope,
    /** URI, relative project path, content-address or provider key. Never bitmap bytes. */
    val location: String,
    val contentHash: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

data class ProjectVariable(
    val definition: VariableDefinition,
    val providerBinding: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

sealed interface ProjectReferenceSource {
    val ownerId: String

    data class Root(
        val rootId: WorkflowRootId,
        val entityId: SemanticEntityId? = null,
    ) : ProjectReferenceSource {
        override val ownerId: String get() = rootId.value
    }

    data class Definition(
        val definitionId: DefinitionId,
        val entityId: SemanticEntityId? = null,
    ) : ProjectReferenceSource {
        override val ownerId: String get() = definitionId.value
    }
}

sealed interface ProjectReferenceTarget {
    val targetId: String

    data class Definition(val definitionId: DefinitionId) : ProjectReferenceTarget {
        override val targetId: String get() = definitionId.value
    }

    data class Resource(val resourceId: ResourceId) : ProjectReferenceTarget {
        override val targetId: String get() = resourceId.value
    }

    data class Variable(val variableId: String) : ProjectReferenceTarget {
        override val targetId: String get() = variableId
    }

    data class External(val providerReference: String) : ProjectReferenceTarget {
        override val targetId: String get() = providerReference
    }
}

enum class ProjectReferenceKind { Call, Resource, Variable, Provider }

data class ProjectReference(
    val id: ProjectReferenceId,
    val source: ProjectReferenceSource,
    val target: ProjectReferenceTarget,
    val kind: ProjectReferenceKind,
    val metadata: Map<String, String> = emptyMap(),
)

data class ProjectDocument(
    val schemaVersion: Int = PROJECT_SCHEMA_VERSION,
    val id: ProjectId,
    val revision: ProjectRevision = ProjectRevision(0L),
    val manifest: ProjectManifest,
    val roots: Map<WorkflowRootId, ProjectWorkflowRoot>,
    val definitions: Map<DefinitionId, ProjectDefinition> = emptyMap(),
    val variables: Map<String, ProjectVariable> = emptyMap(),
    val resources: Map<ResourceId, ProjectResource> = emptyMap(),
    val references: Map<ProjectReferenceId, ProjectReference> = emptyMap(),
    val metadata: Map<String, String> = emptyMap(),
) {
    fun activeRoot(): ProjectWorkflowRoot? = manifest.activeRootId?.let(roots::get)

    fun whereUsed(definitionId: DefinitionId): List<ProjectReference> = references.values
        .filter { (it.target as? ProjectReferenceTarget.Definition)?.definitionId == definitionId }
        .sortedBy { it.id.value }

    fun whereUsed(resourceId: ResourceId): List<ProjectReference> = references.values
        .filter { (it.target as? ProjectReferenceTarget.Resource)?.resourceId == resourceId }
        .sortedBy { it.id.value }

    companion object {
        fun fromLegacyWorkspace(workspace: WorkspaceDocument, projectName: String = workspace.id): ProjectDocument {
            val projectId = ProjectId("project:${workspace.id}")
            val rootId = WorkflowRootId("root:${workspace.id}")
            return ProjectDocument(
                id = projectId,
                revision = ProjectRevision(workspace.version.coerceAtLeast(0L)),
                manifest = ProjectManifest(projectName, rootId),
                roots = mapOf(rootId to ProjectWorkflowRoot(rootId, projectName, workspace)),
                metadata = mapOf("migration.source" to "workspace-schema"),
            )
        }
    }
}

enum class ProjectVariableLifetime { Invocation, WorkflowRun, Project, Application, Provider }
enum class ProjectVariablePersistence { None, Session, ProjectDocument, AppStore, Provider }

data class ProjectVariableScopeContract(
    val scope: VariableScope,
    val lifetime: ProjectVariableLifetime,
    val persistence: ProjectVariablePersistence,
    val providerRequired: Boolean,
    val runtimeImplemented: Boolean,
)

object ProjectVariableScopes {
    fun contract(scope: VariableScope): ProjectVariableScopeContract = when (scope) {
        VariableScope.Local -> ProjectVariableScopeContract(scope, ProjectVariableLifetime.Invocation, ProjectVariablePersistence.None, false, true)
        VariableScope.Script -> ProjectVariableScopeContract(scope, ProjectVariableLifetime.WorkflowRun, ProjectVariablePersistence.Session, false, true)
        VariableScope.Global -> ProjectVariableScopeContract(scope, ProjectVariableLifetime.Project, ProjectVariablePersistence.ProjectDocument, false, false)
        VariableScope.Project -> ProjectVariableScopeContract(scope, ProjectVariableLifetime.Project, ProjectVariablePersistence.ProjectDocument, false, false)
        VariableScope.AppShared -> ProjectVariableScopeContract(scope, ProjectVariableLifetime.Application, ProjectVariablePersistence.AppStore, false, false)
        VariableScope.External -> ProjectVariableScopeContract(scope, ProjectVariableLifetime.Provider, ProjectVariablePersistence.Provider, true, false)
    }
}

enum class ProjectValidationCode {
    UnsupportedSchema,
    ActiveRootMissing,
    RootKeyMismatch,
    DefinitionKeyMismatch,
    ResourceKeyMismatch,
    ReferenceKeyMismatch,
    VariableKeyMismatch,
    DuplicateWorkspaceId,
    InvalidWorkspace,
    CrossRootRelation,
    ReferenceSourceMissing,
    ReferenceSourceEntityMissing,
    ReferenceTargetMissing,
    ReferenceKindMismatch,
    ExternalVariableProviderMissing,
    ResourceLocationMissing,
}

data class ProjectValidationIssue(
    val code: ProjectValidationCode,
    val message: String,
    val rootId: WorkflowRootId? = null,
    val referenceId: ProjectReferenceId? = null,
)

object ProjectValidator {
    fun validate(project: ProjectDocument): List<ProjectValidationIssue> = buildList {
        if (project.schemaVersion != PROJECT_SCHEMA_VERSION) {
            add(ProjectValidationIssue(ProjectValidationCode.UnsupportedSchema, "Unsupported project schema ${project.schemaVersion}."))
        }
        project.manifest.activeRootId?.takeIf { it !in project.roots }?.let {
            add(ProjectValidationIssue(ProjectValidationCode.ActiveRootMissing, "Active root ${it.value} is missing.", it))
        }
        project.roots.forEach { (key, root) ->
            if (key != root.id) add(ProjectValidationIssue(ProjectValidationCode.RootKeyMismatch, "Root key ${key.value} does not match ${root.id.value}.", key))
        }
        project.definitions.forEach { (key, definition) ->
            if (key != definition.id) add(ProjectValidationIssue(ProjectValidationCode.DefinitionKeyMismatch, "Definition key ${key.value} does not match ${definition.id.value}."))
        }
        project.resources.forEach { (key, resource) ->
            if (key != resource.id) add(ProjectValidationIssue(ProjectValidationCode.ResourceKeyMismatch, "Resource key ${key.value} does not match ${resource.id.value}."))
            if (resource.location.isBlank()) add(ProjectValidationIssue(ProjectValidationCode.ResourceLocationMissing, "Resource ${key.value} has no location."))
        }
        project.variables.forEach { (key, variable) ->
            if (key != variable.definition.id) add(ProjectValidationIssue(ProjectValidationCode.VariableKeyMismatch, "Variable key $key does not match ${variable.definition.id}."))
            if (variable.definition.scope == VariableScope.External && variable.providerBinding.isNullOrBlank()) {
                add(ProjectValidationIssue(ProjectValidationCode.ExternalVariableProviderMissing, "External variable $key requires a provider binding."))
            }
        }
        project.references.forEach { (key, reference) ->
            if (key != reference.id) add(ProjectValidationIssue(ProjectValidationCode.ReferenceKeyMismatch, "Reference key ${key.value} does not match ${reference.id.value}.", referenceId = key))
            validateReference(project, reference)?.let(::add)
        }
        val workspaceOwners = project.roots.values.groupBy { it.workspace.id }
        workspaceOwners.filterValues { it.size > 1 }.forEach { (workspaceId, owners) ->
            add(ProjectValidationIssue(ProjectValidationCode.DuplicateWorkspaceId, "Workspace $workspaceId is owned by roots ${owners.joinToString { it.id.value }}."))
        }
        val entitiesByRoot = project.roots.mapValues { (_, root) -> root.workspace.canonical?.entities.orEmpty().map { it.ref.id }.toSet() }
        project.roots.forEach { (rootId, root) ->
            val canonical = root.workspace.canonical ?: return@forEach
            CanonicalWorkspaceValidator.validate(canonical).forEach { issue ->
                val crossTarget = issue.relationId?.let { relationId -> canonical.relations.firstOrNull { it.id == relationId }?.target?.id }
                    ?.let { target -> entitiesByRoot.any { (otherRoot, ids) -> otherRoot != rootId && target in ids } }
                    ?: false
                add(
                    ProjectValidationIssue(
                        if (crossTarget) ProjectValidationCode.CrossRootRelation else ProjectValidationCode.InvalidWorkspace,
                        issue.message,
                        rootId,
                    ),
                )
            }
        }
    }

    private fun validateReference(project: ProjectDocument, reference: ProjectReference): ProjectValidationIssue? {
        val sourceWorkspace = when (val source = reference.source) {
            is ProjectReferenceSource.Root -> project.roots[source.rootId]?.workspace
                ?: return issue(reference, ProjectValidationCode.ReferenceSourceMissing, "Reference source root ${source.rootId.value} is missing.")
            is ProjectReferenceSource.Definition -> {
                val definition = project.definitions[source.definitionId]
                    ?: return issue(reference, ProjectValidationCode.ReferenceSourceMissing, "Reference source definition ${source.definitionId.value} is missing.")
                definition.workspace
            }
        }
        reference.source.let { source ->
            val entityId = when (source) {
                is ProjectReferenceSource.Root -> source.entityId
                is ProjectReferenceSource.Definition -> source.entityId
            }
            if (entityId != null && sourceWorkspace?.canonical?.entity(entityId) == null) {
                return issue(reference, ProjectValidationCode.ReferenceSourceEntityMissing, "Reference source entity ${entityId.value} is missing.")
            }
        }
        val targetExists = when (val target = reference.target) {
            is ProjectReferenceTarget.Definition -> target.definitionId in project.definitions
            is ProjectReferenceTarget.Resource -> target.resourceId in project.resources
            is ProjectReferenceTarget.Variable -> target.variableId in project.variables
            is ProjectReferenceTarget.External -> target.providerReference.isNotBlank()
        }
        if (!targetExists) return issue(reference, ProjectValidationCode.ReferenceTargetMissing, "Reference target ${reference.target.targetId} is missing.")
        val kindMatches = when (reference.target) {
            is ProjectReferenceTarget.Definition -> reference.kind == ProjectReferenceKind.Call
            is ProjectReferenceTarget.Resource -> reference.kind == ProjectReferenceKind.Resource
            is ProjectReferenceTarget.Variable -> reference.kind == ProjectReferenceKind.Variable
            is ProjectReferenceTarget.External -> reference.kind == ProjectReferenceKind.Provider
        }
        return if (kindMatches) null else issue(reference, ProjectValidationCode.ReferenceKindMismatch, "Reference ${reference.id.value} kind does not match its target.")
    }

    private fun issue(reference: ProjectReference, code: ProjectValidationCode, message: String) =
        ProjectValidationIssue(code, message, referenceId = reference.id)
}
