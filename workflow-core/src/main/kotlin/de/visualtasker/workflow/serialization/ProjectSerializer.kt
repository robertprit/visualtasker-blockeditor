package de.visualtasker.workflow.serialization

import de.visualtasker.workflow.core.DefinitionId
import de.visualtasker.workflow.core.PROJECT_SCHEMA_VERSION
import de.visualtasker.workflow.core.ProjectDefinition
import de.visualtasker.workflow.core.ProjectDefinitionKind
import de.visualtasker.workflow.core.ProjectDocument
import de.visualtasker.workflow.core.ProjectId
import de.visualtasker.workflow.core.ProjectManifest
import de.visualtasker.workflow.core.ProjectReference
import de.visualtasker.workflow.core.ProjectReferenceId
import de.visualtasker.workflow.core.ProjectReferenceKind
import de.visualtasker.workflow.core.ProjectReferenceSource
import de.visualtasker.workflow.core.ProjectReferenceTarget
import de.visualtasker.workflow.core.ProjectResource
import de.visualtasker.workflow.core.ProjectResourceScope
import de.visualtasker.workflow.core.ProjectResourceType
import de.visualtasker.workflow.core.ProjectRevision
import de.visualtasker.workflow.core.ProjectValidator
import de.visualtasker.workflow.core.ProjectVariable
import de.visualtasker.workflow.core.ProjectWorkflowRoot
import de.visualtasker.workflow.core.ResourceId
import de.visualtasker.workflow.core.SemanticEntityId
import de.visualtasker.workflow.core.VariableDefinition
import de.visualtasker.workflow.core.VariableScope
import de.visualtasker.workflow.core.WorkflowRootId
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull

@Serializable
private data class ProjectDocumentDto(
    val projectSchemaVersion: Int = PROJECT_SCHEMA_VERSION,
    val id: String,
    val revision: Long,
    val manifest: ProjectManifestDto,
    val roots: List<ProjectRootDto>,
    val definitions: List<ProjectDefinitionDto> = emptyList(),
    val variables: List<ProjectVariableDto> = emptyList(),
    val resources: List<ProjectResourceDto> = emptyList(),
    val references: List<ProjectReferenceDto> = emptyList(),
    val metadata: List<ProjectMetadataDto> = emptyList(),
)

@Serializable private data class ProjectManifestDto(val name: String, val activeRootId: String? = null, val metadata: List<ProjectMetadataDto> = emptyList())
@Serializable private data class ProjectRootDto(val id: String, val name: String, val workspaceJson: String, val metadata: List<ProjectMetadataDto> = emptyList())
@Serializable private data class ProjectDefinitionDto(val id: String, val name: String, val kind: String, val workspaceJson: String? = null, val metadata: List<ProjectMetadataDto> = emptyList())
@Serializable private data class ProjectVariableDto(val id: String, val name: String, val type: String, val scope: String, val defaultValue: String? = null, val providerBinding: String? = null, val metadata: List<ProjectMetadataDto> = emptyList())
@Serializable private data class ProjectResourceDto(val id: String, val name: String, val type: String, val scope: String, val location: String, val contentHash: String? = null, val metadata: List<ProjectMetadataDto> = emptyList())
@Serializable private data class ProjectReferenceDto(val id: String, val sourceType: String, val sourceOwnerId: String, val sourceEntityId: String? = null, val targetType: String, val targetId: String, val kind: String, val metadata: List<ProjectMetadataDto> = emptyList())
@Serializable private data class ProjectMetadataDto(val key: String, val value: String)

enum class ProjectCompatibilitySeverity { INFO, WARNING, ERROR }

data class ProjectCompatibilityDiagnostic(
    val severity: ProjectCompatibilitySeverity,
    val code: String,
    val message: String,
)

sealed interface ProjectDecodeResult {
    val diagnostics: List<ProjectCompatibilityDiagnostic>

    data class Decoded(
        val project: ProjectDocument,
        val migratedFromWorkspace: Boolean,
        override val diagnostics: List<ProjectCompatibilityDiagnostic> = emptyList(),
    ) : ProjectDecodeResult

    data class UnsupportedSchema(
        val version: Int?,
        override val diagnostics: List<ProjectCompatibilityDiagnostic>,
    ) : ProjectDecodeResult

    data class Malformed(
        val reason: String,
        override val diagnostics: List<ProjectCompatibilityDiagnostic>,
    ) : ProjectDecodeResult
}

class ProjectSerializationException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

object ProjectSerializer {
    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
    }

    fun serialize(project: ProjectDocument): String {
        val issues = ProjectValidator.validate(project)
        if (issues.isNotEmpty()) throw ProjectSerializationException("Cannot serialize invalid project: ${issues.joinToString("; ") { it.message }}")
        return json.encodeToString(project.toDto())
    }

    fun decode(raw: String): ProjectDecodeResult {
        if (raw.isBlank()) return malformed("Project document is blank.")
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrElse { return malformed("Malformed project JSON.") }
        if ("projectSchemaVersion" !in root) {
            return when (val workspace = WorkflowSerializer.decode(raw)) {
                is WorkflowDecodeResult.Decoded -> {
                    val migrated = ProjectDocument.fromLegacyWorkspace(workspace.document)
                    ProjectDecodeResult.Decoded(
                        migrated,
                        migratedFromWorkspace = true,
                        diagnostics = workspace.diagnostics.map {
                            ProjectCompatibilityDiagnostic(ProjectCompatibilitySeverity.INFO, it.code, it.message)
                        } + ProjectCompatibilityDiagnostic(
                            ProjectCompatibilitySeverity.INFO,
                            "project.workspace.migrated",
                            "Single-root workspace migrated deterministically to project ${migrated.id.value}.",
                        ),
                    )
                }
                is WorkflowDecodeResult.UnsupportedSchema -> ProjectDecodeResult.UnsupportedSchema(
                    workspace.version,
                    workspace.diagnostics.map { ProjectCompatibilityDiagnostic(ProjectCompatibilitySeverity.ERROR, it.code, it.message) },
                )
                is WorkflowDecodeResult.Malformed -> malformed(workspace.reason)
            }
        }
        val version = root["projectSchemaVersion"]?.jsonPrimitive?.intOrNull
        if (version != PROJECT_SCHEMA_VERSION) {
            return ProjectDecodeResult.UnsupportedSchema(
                version,
                listOf(ProjectCompatibilityDiagnostic(ProjectCompatibilitySeverity.ERROR, "project.schema.unsupported", "Unsupported project schema $version.")),
            )
        }
        val dto = try {
            json.decodeFromString<ProjectDocumentDto>(raw)
        } catch (error: SerializationException) {
            return malformed(error.message ?: "Malformed project JSON.")
        } catch (error: IllegalArgumentException) {
            return malformed(error.message ?: "Malformed project JSON.")
        }
        val project = runCatching { dto.toDomain() }.getOrElse { return malformed(it.message ?: "Malformed project values.") }
        val issues = ProjectValidator.validate(project)
        if (issues.isNotEmpty()) {
            return ProjectDecodeResult.Malformed(
                issues.joinToString("; ") { it.message },
                issues.map { ProjectCompatibilityDiagnostic(ProjectCompatibilitySeverity.ERROR, "project.${it.code.name}", it.message) },
            )
        }
        return ProjectDecodeResult.Decoded(project, migratedFromWorkspace = false)
    }

    fun deserialize(raw: String): ProjectDocument = when (val result = decode(raw)) {
        is ProjectDecodeResult.Decoded -> result.project
        is ProjectDecodeResult.UnsupportedSchema -> throw ProjectSerializationException(result.diagnostics.joinToString("; ") { it.message })
        is ProjectDecodeResult.Malformed -> throw ProjectSerializationException(result.reason)
    }

    private fun malformed(reason: String) = ProjectDecodeResult.Malformed(
        reason,
        listOf(ProjectCompatibilityDiagnostic(ProjectCompatibilitySeverity.ERROR, "project.malformed", reason)),
    )

    private fun ProjectDocument.toDto() = ProjectDocumentDto(
        id = id.value,
        revision = revision.value,
        manifest = ProjectManifestDto(manifest.name, manifest.activeRootId?.value, manifest.metadata.toDto()),
        roots = roots.values.sortedBy { it.id.value }.map { ProjectRootDto(it.id.value, it.name, WorkflowSerializer.serialize(it.workspace), it.metadata.toDto()) },
        definitions = definitions.values.sortedBy { it.id.value }.map { ProjectDefinitionDto(it.id.value, it.name, it.kind.name, it.workspace?.let(WorkflowSerializer::serialize), it.metadata.toDto()) },
        variables = variables.values.sortedBy { it.definition.id }.map {
            ProjectVariableDto(it.definition.id, it.definition.name, it.definition.type, it.definition.scope.name, it.definition.defaultValue, it.providerBinding, it.metadata.toDto())
        },
        resources = resources.values.sortedBy { it.id.value }.map { ProjectResourceDto(it.id.value, it.name, it.type.name, it.scope.name, it.location, it.contentHash, it.metadata.toDto()) },
        references = references.values.sortedBy { it.id.value }.map { it.toDto() },
        metadata = metadata.toDto(),
    )

    private fun ProjectReference.toDto(): ProjectReferenceDto {
        val (sourceType, sourceOwner, sourceEntity) = when (val value = source) {
            is ProjectReferenceSource.Root -> Triple("root", value.rootId.value, value.entityId?.value)
            is ProjectReferenceSource.Definition -> Triple("definition", value.definitionId.value, value.entityId?.value)
        }
        val (targetType, targetId) = when (val value = target) {
            is ProjectReferenceTarget.Definition -> "definition" to value.definitionId.value
            is ProjectReferenceTarget.Resource -> "resource" to value.resourceId.value
            is ProjectReferenceTarget.Variable -> "variable" to value.variableId
            is ProjectReferenceTarget.External -> "external" to value.providerReference
        }
        return ProjectReferenceDto(id.value, sourceType, sourceOwner, sourceEntity, targetType, targetId, kind.name, metadata.toDto())
    }

    private fun ProjectDocumentDto.toDomain(): ProjectDocument {
        roots.requireUniqueIds("root") { it.id }
        definitions.requireUniqueIds("definition") { it.id }
        variables.requireUniqueIds("variable") { it.id }
        resources.requireUniqueIds("resource") { it.id }
        references.requireUniqueIds("reference") { it.id }
        return ProjectDocument(
        schemaVersion = projectSchemaVersion,
        id = ProjectId(id),
        revision = ProjectRevision(revision),
        manifest = ProjectManifest(manifest.name, manifest.activeRootId?.let(::WorkflowRootId), manifest.metadata.toMap()),
        roots = roots.associate { root ->
            WorkflowRootId(root.id) to ProjectWorkflowRoot(WorkflowRootId(root.id), root.name, WorkflowSerializer.deserialize(root.workspaceJson), root.metadata.toMap())
        },
        definitions = definitions.associate { definition ->
            DefinitionId(definition.id) to ProjectDefinition(DefinitionId(definition.id), definition.name, ProjectDefinitionKind.valueOf(definition.kind), definition.workspaceJson?.let(WorkflowSerializer::deserialize), definition.metadata.toMap())
        },
        variables = variables.associate { variable ->
            variable.id to ProjectVariable(VariableDefinition(variable.id, variable.name, variable.type, VariableScope.valueOf(variable.scope), variable.defaultValue), variable.providerBinding, variable.metadata.toMap())
        },
        resources = resources.associate { resource ->
            ResourceId(resource.id) to ProjectResource(ResourceId(resource.id), resource.name, ProjectResourceType.valueOf(resource.type), ProjectResourceScope.valueOf(resource.scope), resource.location, resource.contentHash, resource.metadata.toMap())
        },
        references = references.associate { reference ->
            val id = ProjectReferenceId(reference.id)
            id to ProjectReference(
                id = id,
                source = when (reference.sourceType) {
                    "root" -> ProjectReferenceSource.Root(WorkflowRootId(reference.sourceOwnerId), reference.sourceEntityId?.let(::SemanticEntityId))
                    "definition" -> ProjectReferenceSource.Definition(DefinitionId(reference.sourceOwnerId), reference.sourceEntityId?.let(::SemanticEntityId))
                    else -> throw ProjectSerializationException("Unknown reference source ${reference.sourceType}.")
                },
                target = when (reference.targetType) {
                    "definition" -> ProjectReferenceTarget.Definition(DefinitionId(reference.targetId))
                    "resource" -> ProjectReferenceTarget.Resource(ResourceId(reference.targetId))
                    "variable" -> ProjectReferenceTarget.Variable(reference.targetId)
                    "external" -> ProjectReferenceTarget.External(reference.targetId)
                    else -> throw ProjectSerializationException("Unknown reference target ${reference.targetType}.")
                },
                kind = ProjectReferenceKind.valueOf(reference.kind),
                metadata = reference.metadata.toMap(),
            )
        },
        metadata = metadata.toMap(),
    )
    }

    private fun <T> List<T>.requireUniqueIds(kind: String, id: (T) -> String) {
        val duplicate = groupBy(id).entries.firstOrNull { it.value.size > 1 }?.key ?: return
        throw ProjectSerializationException("Duplicate project $kind ID $duplicate.")
    }

    private fun Map<String, String>.toDto() = entries.sortedBy { it.key }.map { ProjectMetadataDto(it.key, it.value) }
    private fun List<ProjectMetadataDto>.toMap() = associate { it.key to it.value }
}
