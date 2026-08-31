package de.visualtasker.blockeditor.serialization

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.BlockNode
import de.visualtasker.blockeditor.domain.Connection
import de.visualtasker.blockeditor.domain.ConnectionId
import de.visualtasker.blockeditor.domain.ConnectionKind
import de.visualtasker.blockeditor.domain.FieldValue
import de.visualtasker.blockeditor.domain.StatementInput
import de.visualtasker.blockeditor.domain.ValueInput
import de.visualtasker.blockeditor.domain.VariableDefinition
import de.visualtasker.blockeditor.domain.VariableRegistry
import de.visualtasker.blockeditor.domain.VariableScope
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.WorkspacePoint
import de.visualtasker.blockeditor.domain.rootOffset
import de.visualtasker.blockeditor.registry.BlockRegistry
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val WORKSPACE_SCHEMA_VERSION = 1

@Serializable
private data class WorkspaceDocumentDto(
    val schemaVersion: Int = WORKSPACE_SCHEMA_VERSION,
    val id: String,
    val version: Long = 0L,
    val blocks: List<BlockEntryDto> = emptyList(),
    val rootBlocks: List<String> = emptyList(),
    val rootPositions: List<RootPositionDto> = emptyList(),
    val variables: List<VariableEntryDto> = emptyList(),
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

object WorkspaceSerializer {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = false
        encodeDefaults = true
        explicitNulls = false
    }

    fun serialize(document: WorkspaceDocument): String =
        json.encodeToString(document.toDto())

    fun decode(
        raw: String,
        registry: BlockRegistry = DefaultBlockRegistry,
    ): WorkspaceDecodeResult {
        if (raw.isBlank()) {
            return WorkspaceDecodeResult.Malformed(
                reason = "Workspace document is blank.",
                diagnostics = listOf(
                    WorkspaceCompatibilityDiagnostic(
                        severity = WorkspaceCompatibilitySeverity.ERROR,
                        code = "workspace.blank",
                        message = "Workspace document is blank.",
                    ),
                ),
            )
        }
        val schemaVersion = rawSchemaVersion(raw)
        if (schemaVersion != null && schemaVersion > WORKSPACE_SCHEMA_VERSION) {
            return WorkspaceDecodeResult.UnsupportedSchema(
                version = schemaVersion,
                diagnostics = listOf(
                    WorkspaceCompatibilityDiagnostic(
                        severity = WorkspaceCompatibilitySeverity.ERROR,
                        code = "workspace.schema.unsupported",
                        message = "Unsupported workspace schema version $schemaVersion; expected $WORKSPACE_SCHEMA_VERSION.",
                    ),
                ),
            )
        }
        val document = try {
            deserialize(raw)
        } catch (error: WorkspaceSerializationException) {
            return WorkspaceDecodeResult.Malformed(
                reason = error.message ?: "Malformed workspace JSON.",
                diagnostics = listOf(
                    WorkspaceCompatibilityDiagnostic(
                        severity = WorkspaceCompatibilitySeverity.ERROR,
                        code = "workspace.malformed",
                        message = error.message ?: "Malformed workspace JSON.",
                    ),
                ),
            )
        }
        val diagnostics = buildList {
            if (schemaVersion == null) {
                add(
                    WorkspaceCompatibilityDiagnostic(
                        severity = WorkspaceCompatibilitySeverity.INFO,
                        code = "workspace.schema.migrated",
                        message = "Workspace without schemaVersion was loaded as schema $WORKSPACE_SCHEMA_VERSION.",
                    ),
                )
            }
            addAll(document.compatibilityDiagnostics(registry))
        }
        return WorkspaceDecodeResult.Decoded(document, diagnostics)
    }

    fun deserialize(raw: String): WorkspaceDocument {
        if (raw.isBlank()) {
            throw WorkspaceSerializationException("Workspace document is blank.")
        }
        val dto = try {
            json.decodeFromString<WorkspaceDocumentDto>(raw)
        } catch (error: SerializationException) {
            throw WorkspaceSerializationException("Malformed workspace JSON.", error)
        } catch (error: IllegalArgumentException) {
            throw WorkspaceSerializationException("Malformed workspace JSON.", error)
        }
        if (dto.schemaVersion != WORKSPACE_SCHEMA_VERSION) {
            throw WorkspaceSerializationException(
                "Unsupported workspace schema version ${dto.schemaVersion}; " +
                    "expected $WORKSPACE_SCHEMA_VERSION.",
            )
        }
        return dto.toDomain()
    }

    private fun rawSchemaVersion(raw: String): Int? = runCatching {
        val element = json.parseToJsonElement(raw)
        (element.jsonObject["schemaVersion"] ?: return@runCatching null)
            .jsonPrimitive
            .intOrNull
    }.getOrNull()

    private fun WorkspaceDocument.compatibilityDiagnostics(
        registry: BlockRegistry,
    ): List<WorkspaceCompatibilityDiagnostic> = buildList {
        blocks.values.forEach { block ->
            val definition = registry.getDefinition(block.type)
            if (definition == null) {
                add(
                    WorkspaceCompatibilityDiagnostic(
                        severity = WorkspaceCompatibilitySeverity.ERROR,
                        code = "block.type.missing-definition",
                        message = "Block ${block.id.value} references unavailable block definition '${block.type}'.",
                        blockId = block.id.value,
                    ),
                )
                return@forEach
            }
            val definitionValueInputs = definition.valueInputs.map { it.name }.toSet()
            val blockValueInputs = block.valueInputs.map { it.name }.toSet()
            val definitionStatementInputs = definition.statementInputs.map { it.name }.toSet()
            val blockStatementInputs = block.statementInputs.map { it.name }.toSet()
            if (!blockValueInputs.containsAll(definitionValueInputs)) {
                add(
                    WorkspaceCompatibilityDiagnostic(
                        severity = WorkspaceCompatibilitySeverity.ERROR,
                        code = "block.shape.missing-value-input",
                        message = "Block ${block.id.value} is missing value inputs ${(definitionValueInputs - blockValueInputs).sorted()}.",
                        blockId = block.id.value,
                    ),
                )
            }
            if (!blockStatementInputs.containsAll(definitionStatementInputs)) {
                add(
                    WorkspaceCompatibilityDiagnostic(
                        severity = WorkspaceCompatibilitySeverity.ERROR,
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
                    WorkspaceCompatibilityDiagnostic(
                        severity = WorkspaceCompatibilitySeverity.WARNING,
                        code = "block.shape.extra-inputs",
                        message = "Block ${block.id.value} carries plugin/dynamic inputs value=${extraValueInputs.sorted()} statement=${extraStatementInputs.sorted()}.",
                        blockId = block.id.value,
                    ),
                )
            }
        }
        rootBlocks.filter { it !in blocks }.forEach { rootId ->
            add(
                WorkspaceCompatibilityDiagnostic(
                    severity = WorkspaceCompatibilitySeverity.ERROR,
                    code = "workspace.root.missing-block",
                    message = "Root block ${rootId.value} is not present in blocks.",
                    blockId = rootId.value,
                ),
            )
        }
    }

    private fun WorkspaceDocument.toDto(): WorkspaceDocumentDto = WorkspaceDocumentDto(
        schemaVersion = WORKSPACE_SCHEMA_VERSION,
        id = id,
        version = version,
        blocks = blocks.entries
            .sortedBy { it.key.value }
            .map { BlockEntryDto(it.key.value, it.value.toDto(it.key.value)) },
        rootBlocks = rootBlocks.map { it.value },
        rootPositions = rootPositions.entries
            .sortedBy { it.key.value }
            .map { RootPositionDto(it.key.value, it.value.x, it.value.y) },
        variables = variables.variables.entries
            .sortedBy { it.key }
            .map { VariableEntryDto(it.key, it.value.toDto()) },
    )

    private fun BlockNode.toDto(blockId: String): BlockNodeDto = BlockNodeDto(
        type = type,
        fields = fields.entries
            .sortedBy { it.key }
            .map { FieldEntryDto(it.key, it.value.toDto()) },
        previous = previous?.toDto(),
        next = next?.toDto(),
        output = output?.toDto(),
        valueInputs = valueInputs
            .map { ValueInputDto(it.name, it.connection.toDto()) },
        statementInputs = statementInputs
            .map { StatementInputDto(it.name, it.connection.toDto()) },
        collapsed = collapsed,
        metadata = metadata.entries
            .sortedBy { it.key }
            .map { MetadataEntryDto(it.key, it.value) },
    )

    private fun Connection.toDto(): ConnectionDto = ConnectionDto(
        id = id.value,
        owner = owner.value,
        kind = kind.name,
        accepts = accepts.sorted(),
        provides = provides,
        connectedTo = connectedTo?.value,
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
            rootPositions = (legacyPositions + explicitPositions).filterKeys { it in domainRoots },
            variables = VariableRegistry(
                variables = variables.associate { it.id to it.definition.toDomain(it.id) },
            ),
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
