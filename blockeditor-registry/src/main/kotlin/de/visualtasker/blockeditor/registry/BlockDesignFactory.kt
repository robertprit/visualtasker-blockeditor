package de.visualtasker.blockeditor.registry

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.UUID

@Serializable
enum class BlockDesignInputKind {
    STATEMENT,
    VALUE,
    END_ROW,
}

@Serializable
enum class BlockDesignFieldType {
    TEXT,
    TEXT_INPUT,
    NUMBER_INPUT,
    DROPDOWN,
    RADIO_BUTTONS,
    CHECKBOX,
    VARIABLE,
    IMAGE,
    IMAGE_CAROUSEL,
    SLIDER,
    SWITCH,
    REGION_EDITOR,
    FILE_PATH,
    COLOR_PICKER,
    DURATION,
    RETRY_COUNT,
    THRESHOLD,
}

@Serializable
enum class BlockDesignValueType {
    ANY,
    BOOL,
    STRING,
    NUMBER,
    ARRAY,
    OBJECT,
    COLOR_HEX,
    COLOR_RGBA,
    REGION,
    IMAGE,
    DURATION,
    CUSTOM,
}

@Serializable
data class CustomConnectionTypeDefinition(
    val name: String,
    val description: String = "",
)

@Serializable
enum class BlockDesignPortHandleKind {
    PREVIOUS,
    NEXT,
    VALUE_INPUT,
    STATEMENT_BRANCH,
    OUTPUT,
    CUSTOM,
}

@Serializable
data class BlockDesignPortHandle(
    val name: String,
    val kind: BlockDesignPortHandleKind,
    val x: Float,
    val y: Float,
)

@Serializable
data class BlockDesignInputDefinition(
    val kind: BlockDesignInputKind,
    val name: String,
    val label: String = name,
    val connectionType: String = "Any",
    val required: Boolean = false,
    val defaultValue: String = "",
    val portX: Float? = null,
    val portY: Float? = null,
)

@Serializable
data class BlockDesignFieldBlueprint(
    val name: String,
    val label: String = name,
    val fieldType: BlockDesignFieldType = BlockDesignFieldType.TEXT_INPUT,
    val valueType: BlockDesignValueType = BlockDesignValueType.STRING,
    val defaultValue: String = "",
    val required: Boolean = false,
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val options: List<FieldOption> = emptyList(),
    val allowedSources: List<ParameterSourceKind> = listOf(ParameterSourceKind.MANUAL),
)

@Serializable
enum class BlockDesignElementKind {
    INPUT,
    FIELD,
}

@Serializable
data class BlockDesignElement(
    val kind: BlockDesignElementKind,
    val input: BlockDesignInputDefinition? = null,
    val field: BlockDesignFieldBlueprint? = null,
) {
    init {
        require((kind == BlockDesignElementKind.INPUT) == (input != null)) {
            "INPUT element requires input and no field."
        }
        require((kind == BlockDesignElementKind.FIELD) == (field != null)) {
            "FIELD element requires field and no input."
        }
    }

    companion object {
        fun input(input: BlockDesignInputDefinition): BlockDesignElement =
            BlockDesignElement(BlockDesignElementKind.INPUT, input = input)

        fun field(field: BlockDesignFieldBlueprint): BlockDesignElement =
            BlockDesignElement(BlockDesignElementKind.FIELD, field = field)
    }
}

@Serializable
data class BlockDesignBlueprint(
    val label: String,
    val category: String = BlockCategories.CUSTOM,
    val hasPrevious: Boolean = true,
    val hasNext: Boolean = true,
    val isReporter: Boolean = false,
    val outputType: String? = null,
    val inputsInline: Boolean = false,
    val fields: List<FieldDefinition> = emptyList(),
    val valueInputs: List<ValueInputDefinition> = emptyList(),
    val statementInputs: List<StatementInputDefinition> = emptyList(),
    val type: String = "",
    val color: String = "blue",
    val icon: String? = null,
    val description: String = "",
    val customConnectionTypes: List<CustomConnectionTypeDefinition> = emptyList(),
    val inputs: List<BlockDesignInputDefinition> = emptyList(),
    val infoFields: List<BlockDesignFieldBlueprint> = emptyList(),
    val elements: List<BlockDesignElement> = emptyList(),
    val generatorTemplate: String = "",
    val svgPath: String? = null,
    val portHandles: List<BlockDesignPortHandle> = emptyList(),
)

object BlockDesignFactory {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun create(blueprint: BlockDesignBlueprint, id: String = nextId(blueprint.label)): BlockDefinition {
        require(blueprint.label.isNotBlank()) { "Block label required" }
        val designElements = blueprint.effectiveElements()
        val generatedValueInputs = designElements.mapNotNull { it.input }
            .filter { it.kind == BlockDesignInputKind.VALUE }
            .map { ValueInputDefinition(it.name, it.label, setOf(it.connectionType)) }
        val generatedStatementInputs = designElements.mapNotNull { it.input }
            .filter { it.kind == BlockDesignInputKind.STATEMENT }
            .map { StatementInputDefinition(it.name, it.label) }
        val generatedFields = designElements.mapNotNull { it.field }.map { it.toFieldDefinition() }
        val metadata = blueprint.rowLayoutMetadata(designElements)
        return BlockDefinition(
            id = blueprint.type.ifBlank { id },
            label = blueprint.label.trim(),
            category = blueprint.category,
            hasPrevious = blueprint.hasPrevious,
            hasNext = blueprint.hasNext,
            outputType = blueprint.outputType,
            fields = blueprint.fields.ifEmpty { generatedFields },
            valueInputs = blueprint.valueInputs.ifEmpty { generatedValueInputs },
            statementInputs = blueprint.statementInputs.ifEmpty { generatedStatementInputs },
            isReporter = blueprint.isReporter,
            inputsInline = blueprint.inputsInline,
            metadata = metadata,
            svgPath = blueprint.svgPath,
        )
    }

    fun quickStatementBlock(
        label: String,
        category: String = BlockCategories.CUSTOM,
        fieldLabel: String = "value",
        defaultValue: String = "",
    ): BlockDefinition = create(
        BlockDesignBlueprint(
            label = label,
            category = category,
            fields = listOf(
                FieldDefinition(
                    key = "payload",
                    label = fieldLabel,
                    defaultValue = defaultValue,
                ),
            ),
        ),
    )

    fun findTemplateBlueprint(): BlockDesignBlueprint = BlockDesignBlueprint(
        type = "vision.findTemplate",
        label = "FIND_TEMPLATE",
        category = "Vision",
        color = "blue",
        description = "Workspace-only Template-Suchblock fuer Vision-Importpfade.",
        hasPrevious = true,
        hasNext = true,
        customConnectionTypes = listOf(
            CustomConnectionTypeDefinition("TemplateImage"),
            CustomConnectionTypeDefinition("ScreenRegion"),
        ),
        elements = listOf(
            BlockDesignElement.input(BlockDesignInputDefinition(BlockDesignInputKind.VALUE, "image", "image", "Image", required = true)),
            BlockDesignElement.field(
                BlockDesignFieldBlueprint(
                    name = "imagePath",
                    label = "imagePath",
                    fieldType = BlockDesignFieldType.IMAGE_CAROUSEL,
                    valueType = BlockDesignValueType.IMAGE,
                    required = true,
                    allowedSources = listOf(ParameterSourceKind.FILE, ParameterSourceKind.VARIABLE, ParameterSourceKind.REPORTER),
                ),
            ),
            BlockDesignElement.input(BlockDesignInputDefinition(BlockDesignInputKind.VALUE, "thresholdInput", "threshold", "Number")),
            BlockDesignElement.field(
                BlockDesignFieldBlueprint(
                    name = "threshold",
                    label = "threshold",
                    fieldType = BlockDesignFieldType.THRESHOLD,
                    valueType = BlockDesignValueType.NUMBER,
                    defaultValue = "0.85",
                    required = true,
                    min = 0.0,
                    max = 1.0,
                    step = 0.01,
                    allowedSources = listOf(ParameterSourceKind.MANUAL, ParameterSourceKind.REPORTER, ParameterSourceKind.VARIABLE),
                ),
            ),
            BlockDesignElement.input(BlockDesignInputDefinition(BlockDesignInputKind.END_ROW, "row1")),
            BlockDesignElement.input(BlockDesignInputDefinition(BlockDesignInputKind.VALUE, "timeoutInput", "timeout", "Duration")),
            BlockDesignElement.field(
                BlockDesignFieldBlueprint(
                    name = "timeoutMs",
                    label = "timeoutMs",
                    fieldType = BlockDesignFieldType.DURATION,
                    valueType = BlockDesignValueType.DURATION,
                    defaultValue = "3000",
                    required = true,
                    min = 0.0,
                ),
            ),
            BlockDesignElement.input(BlockDesignInputDefinition(BlockDesignInputKind.VALUE, "retryInput", "retry", "Number")),
            BlockDesignElement.field(
                BlockDesignFieldBlueprint(
                    name = "retryCount",
                    label = "retryCount",
                    fieldType = BlockDesignFieldType.RETRY_COUNT,
                    valueType = BlockDesignValueType.NUMBER,
                    defaultValue = "1",
                    min = 0.0,
                ),
            ),
            BlockDesignElement.input(BlockDesignInputDefinition(BlockDesignInputKind.END_ROW, "row2")),
            BlockDesignElement.input(BlockDesignInputDefinition(BlockDesignInputKind.VALUE, "regionInput", "region", "Region")),
            BlockDesignElement.field(
                BlockDesignFieldBlueprint(
                    name = "searchRegion",
                    label = "searchRegion",
                    fieldType = BlockDesignFieldType.REGION_EDITOR,
                    valueType = BlockDesignValueType.REGION,
                    allowedSources = listOf(
                        ParameterSourceKind.REGION_MANUAL,
                        ParameterSourceKind.REGION_REPORTER,
                        ParameterSourceKind.VARIABLE,
                    ),
                ),
            ),
            BlockDesignElement.field(
                BlockDesignFieldBlueprint(
                    name = "regionSource",
                    label = "regionSource",
                    fieldType = BlockDesignFieldType.DROPDOWN,
                    valueType = BlockDesignValueType.STRING,
                    defaultValue = "manual",
                    options = listOf(
                        FieldOption("manual", "manual"),
                        FieldOption("reporter", "reporter"),
                        FieldOption("variable", "variable"),
                    ),
                ),
            ),
        ),
        generatorTemplate = "FIND_TEMPLATE image=${'$'}{image} threshold=${'$'}{threshold} " +
            "timeout=${'$'}{timeoutMs} retry=${'$'}{retryCount} region=${'$'}{searchRegion}",
    )

    fun toJson(blueprint: BlockDesignBlueprint): String = json.encodeToString(blueprint)

    fun fromJson(raw: String): BlockDesignBlueprint {
        if (raw.isBlank()) {
            throw IllegalArgumentException("Block design JSON is blank.")
        }
        return try {
            json.decodeFromString(raw)
        } catch (error: SerializationException) {
            throw IllegalArgumentException("Malformed block design JSON.", error)
        }
    }

    fun previewLabel(blueprint: BlockDesignBlueprint): String {
        val parameterNames = blueprint.effectiveElements().mapNotNull { it.field }
            .map { it.name }
            .ifEmpty { blueprint.fields.map { it.key } }
            .joinToString(" ")
        return listOf(blueprint.label, parameterNames)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    fun generatorPreview(blueprint: BlockDesignBlueprint): String =
        blueprint.generatorTemplate.ifBlank {
            val parameters = blueprint.effectiveElements().mapNotNull { it.field }
                .map { "${it.name}=${'$'}{${it.name}}" }
                .ifEmpty { blueprint.fields.map { "${it.key}=${'$'}{${it.key}}" } }
                .joinToString(" ")
            listOf(blueprint.label, parameters)
                .filter { it.isNotBlank() }
                .joinToString(" ")
        }

    private fun nextId(label: String): String {
        val slug = label.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "block" }
        return "${BlockTypes.CUSTOM_PREFIX}${slug}_${UUID.randomUUID().toString().take(6)}"
    }
}

private fun BlockDesignFieldBlueprint.toFieldDefinition(): FieldDefinition {
    val kind = when (fieldType) {
        BlockDesignFieldType.TEXT,
        BlockDesignFieldType.TEXT_INPUT,
        -> FieldKind.TEXT
        BlockDesignFieldType.NUMBER_INPUT,
        BlockDesignFieldType.SLIDER,
        -> FieldKind.NUMBER
        BlockDesignFieldType.DROPDOWN,
        BlockDesignFieldType.RADIO_BUTTONS,
        -> FieldKind.CHOICE
        BlockDesignFieldType.CHECKBOX,
        BlockDesignFieldType.SWITCH,
        -> FieldKind.BOOLEAN
        BlockDesignFieldType.VARIABLE -> FieldKind.VARIABLE_REF
        BlockDesignFieldType.IMAGE,
        BlockDesignFieldType.IMAGE_CAROUSEL,
        -> FieldKind.IMAGE_TEMPLATE
        BlockDesignFieldType.REGION_EDITOR -> FieldKind.REGION
        BlockDesignFieldType.FILE_PATH -> FieldKind.FILE_PATH
        BlockDesignFieldType.COLOR_PICKER -> FieldKind.TEXT
        BlockDesignFieldType.DURATION -> FieldKind.TIMEOUT_MS
        BlockDesignFieldType.RETRY_COUNT -> FieldKind.RETRY_COUNT
        BlockDesignFieldType.THRESHOLD -> FieldKind.THRESHOLD
    }
    return FieldDefinition(
        key = name,
        label = label,
        kind = kind,
        defaultValue = defaultValue,
        options = if (kind == FieldKind.CHOICE) options else emptyList(),
        required = required,
        sourceOptions = allowedSources.ifEmpty { listOf(ParameterSourceKind.MANUAL) },
        defaultSource = allowedSources.firstOrNull() ?: ParameterSourceKind.MANUAL,
        minValue = min,
        maxValue = max,
    )
}

fun BlockDesignBlueprint.effectiveElements(): List<BlockDesignElement> =
    elements.ifEmpty {
        inputs.map { BlockDesignElement.input(it) } + infoFields.map { BlockDesignElement.field(it) }
    }

private fun BlockDesignBlueprint.rowLayoutMetadata(elements: List<BlockDesignElement>): Map<String, String> {
    var row = 0
    var column = 0
    var statementIndex = 0
    var firstStatementRow: Int? = null
    var maxUsedRow = 0
    var maxRowColumns = 0
    val inputRows = mutableMapOf<String, Int>()
    val inputColumns = mutableMapOf<String, Int>()
    val fieldRows = mutableMapOf<String, Int>()
    val fieldColumns = mutableMapOf<String, Int>()
    val fieldTypes = mutableMapOf<String, String>()
    elements.forEach { element ->
        val input = element.input
        val field = element.field
        if (input != null) {
            if (input.kind == BlockDesignInputKind.END_ROW) {
                maxRowColumns = maxOf(maxRowColumns, column)
                if (column > 0) {
                    maxUsedRow = maxOf(maxUsedRow, row)
                    row += 1
                    column = 0
                }
                return@forEach
            }
            if (input.kind == BlockDesignInputKind.STATEMENT) {
                maxRowColumns = maxOf(maxRowColumns, column)
                row += if (column == 0) 0 else 1
                inputRows[input.name] = row
                inputColumns[input.name] = 0
                statementIndex += 1
                firstStatementRow = firstStatementRow ?: row
                maxUsedRow = maxOf(maxUsedRow, row)
                row += 1
                column = 0
                maxRowColumns = maxOf(maxRowColumns, 1)
                return@forEach
            }
            inputRows[input.name] = row
            inputColumns[input.name] = column
            maxUsedRow = maxOf(maxUsedRow, row)
            column += 1
            return@forEach
        }
        if (field != null) {
            fieldRows[field.name] = row
            fieldColumns[field.name] = column
            fieldTypes[field.name] = field.fieldType.name
            maxUsedRow = maxOf(maxUsedRow, row)
            column += 1
        } else {
            return@forEach
        }
    }
    maxRowColumns = maxOf(maxRowColumns, column)
    return buildMap {
        put("custom.layout.designer", "true")
        put("custom.layout.rowCount", (maxUsedRow + 1).toString())
        put("custom.layout.headerRows", (firstStatementRow ?: (maxUsedRow + 1)).coerceAtLeast(1).toString())
        put("custom.layout.maxRowColumns", maxRowColumns.coerceAtLeast(1).toString())
        put("custom.layout.statementCount", statementIndex.toString())
        inputRows.forEach { (name, index) -> put("custom.layout.input.$name.row", index.toString()) }
        inputColumns.forEach { (name, index) -> put("custom.layout.input.$name.column", index.toString()) }
        fieldRows.forEach { (name, index) -> put("custom.layout.field.$name.row", index.toString()) }
        fieldColumns.forEach { (name, index) -> put("custom.layout.field.$name.column", index.toString()) }
        fieldTypes.forEach { (name, type) -> put("custom.layout.field.$name.type", type) }
    }
}
