package de.visualtasker.blockeditor.registry

import de.visualtasker.blockeditor.domain.BlockFactory
import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.BlockNode
import de.visualtasker.blockeditor.domain.Connection
import de.visualtasker.blockeditor.domain.ConnectionId
import de.visualtasker.blockeditor.domain.ConnectionKind
import de.visualtasker.blockeditor.domain.FieldValue
import de.visualtasker.blockeditor.domain.StatementInput
import de.visualtasker.blockeditor.domain.ValueInput

enum class FieldKind {
    TEXT,
    NUMBER,
    BOOLEAN,
    CHOICE,
    VARIABLE_REF,
    FILE_PATH,
    IMAGE_TEMPLATE,
    REGION,
    TIMEOUT_MS,
    RETRY_COUNT,
    THRESHOLD,
}

enum class ParameterSourceKind {
    MANUAL,
    REPORTER,
    VARIABLE,
    PRESET,
    FILE,
    REGION_MANUAL,
    REGION_REPORTER,
}

data class FieldOption(
    val value: String,
    val label: String,
)

data class FieldDefinition(
    val key: String,
    val label: String,
    val kind: FieldKind = FieldKind.TEXT,
    val defaultValue: String = "",
    val options: List<FieldOption> = emptyList(),
    val required: Boolean = false,
    val sourceOptions: List<ParameterSourceKind> = listOf(ParameterSourceKind.MANUAL),
    val defaultSource: ParameterSourceKind = sourceOptions.first(),
    val minValue: Double? = null,
    val maxValue: Double? = null,
) {
    init {
        if (kind == FieldKind.CHOICE) {
            require(options.isNotEmpty()) { "CHOICE field $key requires options" }
            require(options.map(FieldOption::value).distinct().size == options.size) {
                "CHOICE field $key requires unique option values"
            }
            require(defaultValue in options.map(FieldOption::value)) {
                "CHOICE field $key default must be an option value"
            }
        } else {
            require(options.isEmpty()) { "Only CHOICE fields may declare options" }
        }
        require(sourceOptions.isNotEmpty()) { "Field $key requires at least one source option" }
        require(defaultSource in sourceOptions) { "Field $key default source must be a source option" }
        if (minValue != null && maxValue != null) {
            require(minValue <= maxValue) { "Field $key minValue must be <= maxValue" }
        }
    }
}

data class ValueInputDefinition(
    val name: String,
    val label: String,
    val accepts: Set<String>,
)

data class StatementInputDefinition(
    val name: String,
    val label: String,
)

data class BlockDefinition(
    val id: String,
    val label: String,
    val category: String,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val outputType: String? = null,
    val fields: List<FieldDefinition> = emptyList(),
    val valueInputs: List<ValueInputDefinition> = emptyList(),
    val statementInputs: List<StatementInputDefinition> = emptyList(),
    val isReporter: Boolean = false,
    val inputsInline: Boolean = false,
    val paletteVisible: Boolean = true,
    val deprecated: Boolean = false,
    val paletteOrder: Int = 0,
)

interface BlockRegistry {
    fun getDefinition(id: String): BlockDefinition?
    fun allDefinitions(): List<BlockDefinition>
}

fun BlockDefinition.createNode(blockId: BlockId): BlockNode {
    val defaults = fields.associate { field ->
        field.key to when (field.kind) {
            FieldKind.NUMBER,
            FieldKind.TIMEOUT_MS,
            FieldKind.RETRY_COUNT,
            FieldKind.THRESHOLD,
            -> field.defaultValue.toDoubleOrNull()?.let { FieldValue.Number(it) }
                ?: FieldValue.Number(0.0)
            FieldKind.BOOLEAN -> FieldValue.Bool(field.defaultValue.equals("true", ignoreCase = true))
            FieldKind.TEXT,
            FieldKind.CHOICE,
            FieldKind.VARIABLE_REF,
            FieldKind.FILE_PATH,
            FieldKind.IMAGE_TEMPLATE,
            FieldKind.REGION,
            -> FieldValue.Text(field.defaultValue)
        }
    }
    return BlockNode(
        id = blockId,
        type = id,
        fields = defaults,
        previous = if (hasPrevious) {
            Connection(
                id = ConnectionId("${blockId.value}:previous"),
                owner = blockId,
                kind = ConnectionKind.Previous,
            )
        } else null,
        next = if (hasNext) {
            Connection(
                id = ConnectionId("${blockId.value}:next"),
                owner = blockId,
                kind = ConnectionKind.Next,
            )
        } else null,
        output = outputType?.let { type ->
            Connection(
                id = ConnectionId("${blockId.value}:output"),
                owner = blockId,
                kind = ConnectionKind.Output,
                provides = type,
                accepts = setOf(type),
            )
        },
        valueInputs = valueInputs.map { input ->
            ValueInput(
                name = input.name,
                connection = Connection(
                    id = ConnectionId("${blockId.value}:${input.name}"),
                    owner = blockId,
                    kind = ConnectionKind.ValueInput,
                    accepts = input.accepts,
                    slotName = input.name,
                ),
            )
        },
        statementInputs = statementInputs.map { input ->
            StatementInput(
                name = input.name,
                connection = Connection(
                    id = ConnectionId("${blockId.value}:${input.name}:stmt"),
                    owner = blockId,
                    kind = ConnectionKind.StatementInput,
                    slotName = input.name,
                ),
            )
        },
    )
}

fun BlockRegistry.asFactory(): BlockFactory = BlockFactory { definitionId, id ->
    getDefinition(definitionId)?.createNode(id)
}
