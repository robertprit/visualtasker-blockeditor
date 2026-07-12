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
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val WORKSPACE_SCHEMA_VERSION = 1

@Serializable
private data class WorkspaceDocumentDto(
    val schemaVersion: Int = WORKSPACE_SCHEMA_VERSION,
    val id: String,
    val version: Long = 0L,
    val blocks: List<BlockEntryDto> = emptyList(),
    val rootBlocks: List<String> = emptyList(),
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

    private fun WorkspaceDocument.toDto(): WorkspaceDocumentDto = WorkspaceDocumentDto(
        schemaVersion = WORKSPACE_SCHEMA_VERSION,
        id = id,
        version = version,
        blocks = blocks.entries
            .sortedBy { it.key.value }
            .map { BlockEntryDto(it.key.value, it.value.toDto(it.key.value)) },
        rootBlocks = rootBlocks.map { it.value },
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
            .sortedBy { it.name }
            .map { ValueInputDto(it.name, it.connection.toDto()) },
        statementInputs = statementInputs
            .sortedBy { it.name }
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

    private fun WorkspaceDocumentDto.toDomain(): WorkspaceDocument = WorkspaceDocument(
        id = id,
        version = version,
        blocks = blocks.associate { BlockId(it.id) to it.node.toDomain(BlockId(it.id)) },
        rootBlocks = rootBlocks.map { BlockId(it) },
        variables = VariableRegistry(
            variables = variables.associate { it.id to it.definition.toDomain(it.id) },
        ),
    )

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
