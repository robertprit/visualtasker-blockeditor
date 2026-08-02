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
    DUMMY,
    END_FIELD,
}

@Serializable
enum class BlockDesignFieldType {
    LABEL,
    TEXT_INPUT,
    NUMERIC_INPUT,
    DROPDOWN_LIST,
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
data class BlockDesignBlueprint(
    val label: String,
    val category: String = BlockCategories.CUSTOM,
    val hasPrevious: Boolean = true,
    val hasNext: Boolean = true,
    val isReporter: Boolean = false,
    val outputType: String? = null,
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
        val generatedValueInputs = blueprint.inputs
            .filter { it.kind == BlockDesignInputKind.VALUE }
            .map { ValueInputDefinition(it.name, it.label, setOf(it.connectionType)) }
        val generatedStatementInputs = blueprint.inputs
            .filter { it.kind == BlockDesignInputKind.STATEMENT }
            .map { StatementInputDefinition(it.name, it.label) }
        return BlockDefinition(
            id = blueprint.type.ifBlank { id },
            label = blueprint.label.trim(),
            category = blueprint.category,
            hasPrevious = blueprint.hasPrevious,
            hasNext = blueprint.hasNext,
            outputType = blueprint.outputType,
            fields = blueprint.fields.ifEmpty { blueprint.infoFields.map { it.toFieldDefinition() } },
            valueInputs = blueprint.valueInputs.ifEmpty { generatedValueInputs },
            statementInputs = blueprint.statementInputs.ifEmpty { generatedStatementInputs },
            isReporter = blueprint.isReporter,
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
        inputs = listOf(
            BlockDesignInputDefinition(BlockDesignInputKind.VALUE, "image", "image", "Image", required = true),
            BlockDesignInputDefinition(BlockDesignInputKind.VALUE, "threshold", "threshold", "Number"),
            BlockDesignInputDefinition(BlockDesignInputKind.VALUE, "timeout", "timeout", "Duration"),
            BlockDesignInputDefinition(BlockDesignInputKind.VALUE, "retry", "retry", "Number"),
            BlockDesignInputDefinition(BlockDesignInputKind.VALUE, "region", "region", "Region"),
        ),
        infoFields = listOf(
            BlockDesignFieldBlueprint(
                name = "imagePath",
                label = "imagePath",
                fieldType = BlockDesignFieldType.IMAGE_CAROUSEL,
                valueType = BlockDesignValueType.IMAGE,
                required = true,
                allowedSources = listOf(ParameterSourceKind.FILE, ParameterSourceKind.VARIABLE, ParameterSourceKind.REPORTER),
            ),
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
            BlockDesignFieldBlueprint(
                name = "timeoutMs",
                label = "timeoutMs",
                fieldType = BlockDesignFieldType.DURATION,
                valueType = BlockDesignValueType.DURATION,
                defaultValue = "3000",
                required = true,
                min = 0.0,
            ),
            BlockDesignFieldBlueprint(
                name = "retryCount",
                label = "retryCount",
                fieldType = BlockDesignFieldType.RETRY_COUNT,
                valueType = BlockDesignValueType.NUMBER,
                defaultValue = "1",
                min = 0.0,
            ),
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
            BlockDesignFieldBlueprint(
                name = "regionSource",
                label = "regionSource",
                fieldType = BlockDesignFieldType.DROPDOWN_LIST,
                valueType = BlockDesignValueType.STRING,
                defaultValue = "manual",
                options = listOf(
                    FieldOption("manual", "manual"),
                    FieldOption("reporter", "reporter"),
                    FieldOption("variable", "variable"),
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
        val parameterNames = blueprint.infoFields
            .map { it.name }
            .ifEmpty { blueprint.fields.map { it.key } }
            .joinToString(" ")
        return listOf(blueprint.label, parameterNames)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    fun generatorPreview(blueprint: BlockDesignBlueprint): String =
        blueprint.generatorTemplate.ifBlank {
            val parameters = blueprint.infoFields
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
        BlockDesignFieldType.LABEL,
        BlockDesignFieldType.TEXT_INPUT,
        -> FieldKind.TEXT
        BlockDesignFieldType.NUMERIC_INPUT,
        BlockDesignFieldType.SLIDER,
        -> FieldKind.NUMBER
        BlockDesignFieldType.DROPDOWN_LIST -> FieldKind.CHOICE
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
