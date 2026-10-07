package de.visualtasker.workflow.serialization

import de.visualtasker.workflow.core.BlockId
import de.visualtasker.workflow.core.BlockNode
import de.visualtasker.workflow.core.CanonicalCompatibilityProjection
import de.visualtasker.workflow.core.CanonicalWorkspaceDocument
import de.visualtasker.workflow.core.CanonicalWorkspaceMigration
import de.visualtasker.workflow.core.Connection
import de.visualtasker.workflow.core.ConnectionId
import de.visualtasker.workflow.core.ConnectionKind
import de.visualtasker.workflow.core.FieldValue
import de.visualtasker.workflow.core.StatementInput
import de.visualtasker.workflow.core.ValueInput
import de.visualtasker.workflow.core.VariableDefinition
import de.visualtasker.workflow.core.VariableRegistry
import de.visualtasker.workflow.core.VariableScope
import de.visualtasker.workflow.core.WorkspaceDocument
import de.visualtasker.workflow.core.WorkspacePoint
import de.visualtasker.workflow.core.rootOffset
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val WORKFLOW_SCHEMA_VERSION = 2
private const val LEGACY_WORKFLOW_SCHEMA_VERSION = 1

@Serializable
private data class WorkspaceDocumentDto(
    val schemaVersion: Int = WORKFLOW_SCHEMA_VERSION,
    val id: String,
    val version: Long = 0L,
    val blocks: List<BlockEntryDto> = emptyList(),
    val rootBlocks: List<String> = emptyList(),
    val rootPositions: List<RootPositionDto> = emptyList(),
    val variables: List<VariableEntryDto> = emptyList(),
    val canonical: CanonicalWorkspaceDocument? = null,
)

@Serializable
private data class BlockEntryDto(
    val id: String,
    val node: BlockNodeDto,
)

@Serializable
private data class BlockNodeDto(
    val type: String,
    val fields: List<FieldEntryDto> = emptyList(),
    val previous: ConnectionDto? = null,
    val next: ConnectionDto? = null,
    val output: ConnectionDto? = null,
    val valueInputs: List<ValueInputDto> = emptyList(),
    val statementInputs: List<StatementInputDto> = emptyList(),
    val collapsed: Boolean = false,
    val metadata: List<MetadataEntryDto> = emptyList(),
)

@Serializable
private data class FieldEntryDto(
    val key: String,
    val value: FieldValueDto,
)

@Serializable
private data class MetadataEntryDto(
    val key: String,
    val value: String,
)

@Serializable
private data class ConnectionDto(
    val id: String,
    val owner: String,
    val kind: String,
    val accepts: List<String> = emptyList(),
    val provides: String? = null,
    val connectedTo: String? = null,
    val slotName: String? = null,
)

@Serializable
private data class ValueInputDto(
    val name: String,
    val connection: ConnectionDto,
)

@Serializable
private data class StatementInputDto(
    val name: String,
    val connection: ConnectionDto,
)

@Serializable
private data class FieldValueDto(
    val type: String,
    val value: String,
)

@Serializable
private data class VariableEntryDto(
    val id: String,
    val definition: VariableDefinitionDto,
)

@Serializable
private data class RootPositionDto(
    val blockId: String,
    val x: Float,
    val y: Float,
)

@Serializable
private data class VariableDefinitionDto(
    val name: String,
    val type: String,
    val scope: String,
    val defaultValue: String? = null,
)

enum class WorkflowCompatibilitySeverity {
    INFO,
    WARNING,
    ERROR,
}

data class WorkflowCompatibilityDiagnostic(
    val severity: WorkflowCompatibilitySeverity,
    val code: String,
    val message: String,
    val blockId: String? = null,
)

data class WorkflowDefinitionShape(
    val valueInputs: Set<String>,
    val statementInputs: Set<String>,
)

fun interface WorkflowDefinitionResolver {
    fun resolve(type: String): WorkflowDefinitionShape?
}

sealed interface WorkflowDecodeResult {
    val diagnostics: List<WorkflowCompatibilityDiagnostic>

    data class Decoded(
        val document: WorkspaceDocument,
        override val diagnostics: List<WorkflowCompatibilityDiagnostic> = emptyList(),
    ) : WorkflowDecodeResult

    data class UnsupportedSchema(
        val version: Int?,
        override val diagnostics: List<WorkflowCompatibilityDiagnostic>,
    ) : WorkflowDecodeResult

    data class Malformed(
        val reason: String,
        override val diagnostics: List<WorkflowCompatibilityDiagnostic>,
    ) : WorkflowDecodeResult
}

class WorkflowSerializationException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

object WorkflowSerializer {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = false
        encodeDefaults = true
        explicitNulls = false
    }

    fun serialize(document: WorkspaceDocument): String {
        val normalized = WorkflowDocumentNormalizer.normalize(document)
        val migration = CanonicalWorkspaceMigration.toCurrent(normalized)
        if (!migration.isValid) {
            val details = (migration.issues.map { it.message } + migration.diagnostics.map { it.message })
                .distinct()
                .joinToString("; ")
            throw WorkflowSerializationException("Cannot serialize invalid canonical workspace: $details")
        }
        return json.encodeToString(migration.document.toDto())
    }

    fun decode(
        raw: String,
        definitionResolver: WorkflowDefinitionResolver? = null,
    ): WorkflowDecodeResult {
        if (raw.isBlank()) {
            return WorkflowDecodeResult.Malformed(
                reason = "Workspace document is blank.",
                diagnostics = listOf(
                    WorkflowCompatibilityDiagnostic(
                        severity = WorkflowCompatibilitySeverity.ERROR,
                        code = "workspace.blank",
                        message = "Workspace document is blank.",
                    ),
                ),
            )
        }
        val schemaVersion = rawSchemaVersion(raw)
        if (schemaVersion != null && schemaVersion > WORKFLOW_SCHEMA_VERSION) {
            return WorkflowDecodeResult.UnsupportedSchema(
                version = schemaVersion,
                diagnostics = listOf(
                    WorkflowCompatibilityDiagnostic(
                        severity = WorkflowCompatibilitySeverity.ERROR,
                        code = "workspace.schema.unsupported",
                        message = "Unsupported workspace schema version $schemaVersion; expected $WORKFLOW_SCHEMA_VERSION.",
                    ),
                ),
            )
        }
        val document = try {
            deserialize(raw)
        } catch (error: WorkflowSerializationException) {
            return WorkflowDecodeResult.Malformed(
                reason = error.message ?: "Malformed workspace JSON.",
                diagnostics = listOf(
                    WorkflowCompatibilityDiagnostic(
                        severity = WorkflowCompatibilitySeverity.ERROR,
                        code = "workspace.malformed",
                        message = error.message ?: "Malformed workspace JSON.",
                    ),
                ),
            )
        }
        val diagnostics = buildList {
            if (schemaVersion == null) {
                add(
                    WorkflowCompatibilityDiagnostic(
                        severity = WorkflowCompatibilitySeverity.INFO,
                        code = "workspace.schema.migrated",
                        message = "Workspace without schemaVersion was loaded as schema $WORKFLOW_SCHEMA_VERSION.",
                    ),
                )
            }
            if (definitionResolver != null) {
                addAll(document.compatibilityDiagnostics(definitionResolver))
            }
        }
        return WorkflowDecodeResult.Decoded(document, diagnostics)
    }

    fun deserialize(raw: String): WorkspaceDocument {
        if (raw.isBlank()) {
            throw WorkflowSerializationException("Workspace document is blank.")
        }
        val dto = try {
            json.decodeFromString<WorkspaceDocumentDto>(raw)
        } catch (error: SerializationException) {
            throw WorkflowSerializationException("Malformed workspace JSON.", error)
        } catch (error: IllegalArgumentException) {
            throw WorkflowSerializationException("Malformed workspace JSON.", error)
        }
        val actualSchemaVersion = rawSchemaVersion(raw) ?: LEGACY_WORKFLOW_SCHEMA_VERSION
        if (actualSchemaVersion !in LEGACY_WORKFLOW_SCHEMA_VERSION..WORKFLOW_SCHEMA_VERSION) {
            throw WorkflowSerializationException(
                "Unsupported workspace schema version $actualSchemaVersion; " +
                    "expected $LEGACY_WORKFLOW_SCHEMA_VERSION..$WORKFLOW_SCHEMA_VERSION.",
            )
        }
        val decoded = WorkflowDocumentNormalizer.normalize(dto.toDomain())
        val migrated = when (actualSchemaVersion) {
            LEGACY_WORKFLOW_SCHEMA_VERSION -> CanonicalWorkspaceMigration.toCurrent(decoded)
            WORKFLOW_SCHEMA_VERSION -> {
                if (decoded.canonical == null) {
                    throw WorkflowSerializationException("Schema $WORKFLOW_SCHEMA_VERSION document has no canonical state.")
                }
                val projection = CanonicalCompatibilityProjection.project(decoded)
                if (!projection.isValid) {
                    throw WorkflowSerializationException(
                        "Invalid schema $WORKFLOW_SCHEMA_VERSION canonical state: " +
                            projection.issues.joinToString("; ") { it.message },
                    )
                }
                de.visualtasker.workflow.core.CanonicalMigrationResult(
                    document = projection.document,
                    diagnostics = emptyList(),
                    issues = emptyList(),
                )
            }
            else -> error("schema checked above")
        }
        if (!migrated.isValid) {
            throw WorkflowSerializationException(
                "Cannot migrate workspace schema $actualSchemaVersion: " +
                    (migrated.issues.map { it.message } + migrated.diagnostics.map { it.message })
                        .distinct()
                        .joinToString("; "),
            )
        }
        return migrated.document
    }

    private fun rawSchemaVersion(raw: String): Int? = runCatching {
        val element = json.parseToJsonElement(raw)
        (element.jsonObject["schemaVersion"] ?: return@runCatching null)
            .jsonPrimitive
            .intOrNull
    }.getOrNull()

    private fun WorkspaceDocument.compatibilityDiagnostics(
        definitionResolver: WorkflowDefinitionResolver,
    ): List<WorkflowCompatibilityDiagnostic> = buildList {
        blocks.values.forEach { block ->
            val definition = definitionResolver.resolve(block.type)
            if (definition == null) {
                add(
                    WorkflowCompatibilityDiagnostic(
                        severity = WorkflowCompatibilitySeverity.ERROR,
                        code = "block.type.missing-definition",
                        message = "Block ${block.id.value} references unavailable block definition '${block.type}'.",
                        blockId = block.id.value,
                    ),
                )
                return@forEach
            }
            val definitionValueInputs = definition.valueInputs
            val blockValueInputs = block.valueInputs.map { it.name }.toSet()
            val definitionStatementInputs = definition.statementInputs
            val blockStatementInputs = block.statementInputs.map { it.name }.toSet()
            if (!blockValueInputs.containsAll(definitionValueInputs)) {
                add(
                    WorkflowCompatibilityDiagnostic(
                        severity = WorkflowCompatibilitySeverity.ERROR,
                        code = "block.shape.missing-value-input",
                        message = "Block ${block.id.value} is missing value inputs ${(definitionValueInputs - blockValueInputs).sorted()}.",
                        blockId = block.id.value,
                    ),
                )
            }
            if (!blockStatementInputs.containsAll(definitionStatementInputs)) {
                add(
                    WorkflowCompatibilityDiagnostic(
                        severity = WorkflowCompatibilitySeverity.ERROR,
                        code = "block.shape.missing-statement-input",
                        message = "Block ${block.id.value} is missing statement inputs ${(definitionStatementInputs - blockStatementInputs).sorted()}.",
                        blockId = block.id.value,
                    ),
                )
            }
            val extraValueInputs = blockValueInputs - definitionValueInputs
            val extraStatementInputs = blockStatementInputs - definitionStatementInputs
            if (extraValueInputs.isNotEmpty() || extraStatementInputs.isNotEmpty()) {
                add(
                    WorkflowCompatibilityDiagnostic(
                        severity = WorkflowCompatibilitySeverity.WARNING,
                        code = "block.shape.extra-inputs",
                        message = "Block ${block.id.value} carries plugin/dynamic inputs value=${extraValueInputs.sorted()} statement=${extraStatementInputs.sorted()}.",
                        blockId = block.id.value,
                    ),
                )
            }
        }
        rootBlocks.filter { it !in blocks }.forEach { rootId ->
            add(
                WorkflowCompatibilityDiagnostic(
                    severity = WorkflowCompatibilitySeverity.ERROR,
                    code = "workspace.root.missing-block",
                    message = "Root block ${rootId.value} is not present in blocks.",
                    blockId = rootId.value,
                ),
            )
        }
    }

    private fun WorkspaceDocument.toDto(): WorkspaceDocumentDto = WorkspaceDocumentDto(
        schemaVersion = WORKFLOW_SCHEMA_VERSION,
        id = id,
        version = version,
        blocks = blocks.entries
            .sortedBy { it.key.value }
            .map { BlockEntryDto(it.key.value, it.value.toDto(it.key.value, includeLinks = false)) },
        rootBlocks = emptyList(),
        rootPositions = rootPositions.entries
            .sortedBy { it.key.value }
            .map { RootPositionDto(it.key.value, it.value.x, it.value.y) },
        variables = variables.variables.entries
            .sortedBy { it.key }
            .map { VariableEntryDto(it.key, it.value.toDto()) },
        canonical = canonical,
    )

    private fun BlockNode.toDto(blockId: String, includeLinks: Boolean): BlockNodeDto = BlockNodeDto(
        type = type,
        fields = fields.entries
            .sortedBy { it.key }
            .map { FieldEntryDto(it.key, it.value.toDto()) },
        previous = previous?.toDto(includeLinks),
        next = next?.toDto(includeLinks),
        output = output?.toDto(includeLinks),
        valueInputs = valueInputs
            .map { ValueInputDto(it.name, it.connection.toDto(includeLinks)) },
        statementInputs = statementInputs
            .map { StatementInputDto(it.name, it.connection.toDto(includeLinks)) },
        collapsed = collapsed,
        metadata = metadata.entries
            .sortedBy { it.key }
            .map { MetadataEntryDto(it.key, it.value) },
    )

    private fun Connection.toDto(includeLinks: Boolean): ConnectionDto = ConnectionDto(
        id = id.value,
        owner = owner.value,
        kind = kind.name,
        accepts = accepts.sorted(),
        provides = provides,
        connectedTo = connectedTo?.value.takeIf { includeLinks },
        slotName = slotName,
    )

    private fun FieldValue.toDto(): FieldValueDto = when (this) {
        is FieldValue.Text -> FieldValueDto("text", value)
        is FieldValue.Number -> FieldValueDto("number", value.toString())
        is FieldValue.Bool -> FieldValueDto("boolean", value.toString())
    }

    private fun VariableDefinition.toDto(): VariableDefinitionDto = VariableDefinitionDto(
        name = name,
        type = type,
        scope = scope.name,
        defaultValue = defaultValue,
    )

    private fun WorkspaceDocumentDto.toDomain(): WorkspaceDocument {
        val domainBlocks = blocks.associate { BlockId(it.id) to it.node.toDomain(BlockId(it.id)) }
        val domainRoots = rootBlocks.map { BlockId(it) }
        val legacyPositions = domainRoots.mapNotNull { blockId ->
            domainBlocks[blockId]?.rootOffset()?.let { blockId to WorkspacePoint(it.x, it.y) }
        }.toMap()
        val explicitPositions = rootPositions.mapNotNull { position ->
            if (position.x.isFinite() && position.y.isFinite()) {
                BlockId(position.blockId) to WorkspacePoint(position.x, position.y)
            } else {
                null
            }
        }.toMap()
        return WorkspaceDocument(
            id = id,
            version = version,
            blocks = domainBlocks,
            rootBlocks = domainRoots,
            rootPositions = legacyPositions + explicitPositions,
            variables = VariableRegistry(
                variables = variables.associate { it.id to it.definition.toDomain(it.id) },
            ),
            canonical = canonical,
        )
    }

    private fun BlockNodeDto.toDomain(blockId: BlockId): BlockNode = BlockNode(
        id = blockId,
        type = type,
        fields = fields.associate { it.key to it.value.toDomain() },
        previous = previous?.toDomain(),
        next = next?.toDomain(),
        output = output?.toDomain(),
        valueInputs = valueInputs.map { ValueInput(it.name, it.connection.toDomain()) },
        statementInputs = statementInputs.map { StatementInput(it.name, it.connection.toDomain()) },
        collapsed = collapsed,
        metadata = metadata.associate { it.key to it.value },
    )

    private fun ConnectionDto.toDomain(): Connection = Connection(
        id = ConnectionId(id),
        owner = BlockId(owner),
        kind = ConnectionKind.valueOf(kind),
        accepts = accepts.toSet(),
        provides = provides,
        connectedTo = connectedTo?.let { ConnectionId(it) },
        slotName = slotName,
    )

    private fun FieldValueDto.toDomain(): FieldValue = when (type) {
        "number" -> FieldValue.Number(value.toDoubleOrNull() ?: 0.0)
        "boolean" -> FieldValue.Bool(value.equals("true", ignoreCase = true))
        else -> FieldValue.Text(value)
    }

    private fun VariableDefinitionDto.toDomain(id: String): VariableDefinition = VariableDefinition(
        id = id,
        name = name,
        type = type,
        scope = VariableScope.valueOf(scope),
        defaultValue = defaultValue,
    )
}

object WorkflowDocumentNormalizer {
    private const val LEGACY_VARIABLE_GET = "variable.get"
    private const val LEGACY_BOOLEAN_REPORTER = "logic.boolean"
    private const val BOOLEAN_LITERAL = "literal.boolean"
    private const val VARIABLE_REPORTER_PREFIX = "variable.reporter."

    fun normalize(document: WorkspaceDocument): WorkspaceDocument {
        val normalizedBlocks = document.blocks.mapValues { (_, block) ->
            when (block.type) {
                LEGACY_VARIABLE_GET -> normalizeLegacyVariableReporter(document, block)
                LEGACY_BOOLEAN_REPORTER -> block.copy(type = BOOLEAN_LITERAL)
                else -> block
            }
        }
        return if (normalizedBlocks == document.blocks) document else document.copy(blocks = normalizedBlocks)
    }

    private fun normalizeLegacyVariableReporter(
        document: WorkspaceDocument,
        block: BlockNode,
    ): BlockNode {
        val variableId = block.fields["variableId"]?.asText()?.takeIf(String::isNotBlank)
            ?: block.fields["variable"]?.asText()?.takeIf { it in document.variables.variables }
            ?: return block
        val variable = document.variables.variables[variableId]
        val variableLabel = block.fields["variableLabel"]?.asText()?.takeIf(String::isNotBlank)
            ?: variable?.name
            ?: variableId
        val outputType = variable?.type?.ifBlank { null } ?: block.output?.provides ?: "Any"
        return block.copy(
            type = "$VARIABLE_REPORTER_PREFIX$variableId",
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

    private fun FieldValue.asText(): String = when (this) {
        is FieldValue.Text -> value
        is FieldValue.Number -> value.toString()
        is FieldValue.Bool -> value.toString()
    }
}
