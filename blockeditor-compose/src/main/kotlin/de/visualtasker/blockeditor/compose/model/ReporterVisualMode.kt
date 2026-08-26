package de.visualtasker.blockeditor.compose.model

import de.visualtasker.blockeditor.domain.BlockNode
import de.visualtasker.blockeditor.registry.BlockDefinition
import de.visualtasker.blockeditor.registry.BlockTypes

const val REPORTER_VISUAL_MODE_METADATA_KEY = "reporterVisualMode"
const val REPORTER_ASSET_DIR = "reporter"

enum class ReporterVisualMode {
    COMPACT,
    DETAILED,
    ;

    companion object {
        fun fromMetadata(raw: String?): ReporterVisualMode =
            if (raw.equals(DETAILED.name, ignoreCase = true)) DETAILED else COMPACT
    }
}

enum class ReporterFamily {
    BOOLEAN,
    STRING,
    NUMBER,
    ANY,
    CUSTOM,
    OPERATOR_BOOL,
    OPERATOR_ANY,
    OPERATOR_CUSTOM,
    OPERATOR_NUM,
    OPERATOR_STRING,
}

fun reporterVisualModeFor(block: BlockNode): ReporterVisualMode =
    ReporterVisualMode.fromMetadata(block.metadata[REPORTER_VISUAL_MODE_METADATA_KEY])

fun blockWithReporterVisualMode(
    block: BlockNode,
    mode: ReporterVisualMode,
): BlockNode = block.copy(
    metadata = block.metadata + (REPORTER_VISUAL_MODE_METADATA_KEY to mode.name),
)

fun resolveReporterFamily(
    blockType: String,
    definition: BlockDefinition?,
): ReporterFamily? {
    if (definition?.isReporter != true) return null
    if (blockType == BlockTypes.LOGIC_BOOLEAN || blockType == BlockTypes.LITERAL_BOOLEAN) {
        return ReporterFamily.BOOLEAN
    }
    val normalizedOutput = definition.outputType?.trim()?.lowercase().orEmpty()
    if (definition.inputsInline) {
        return when {
            normalizedOutput == "boolean" -> ReporterFamily.OPERATOR_BOOL
            normalizedOutput == "number" -> ReporterFamily.OPERATOR_NUM
            normalizedOutput == "text" || normalizedOutput == "string" -> ReporterFamily.OPERATOR_STRING
            normalizedOutput == "any" -> ReporterFamily.OPERATOR_ANY
            else -> ReporterFamily.OPERATOR_CUSTOM
        }
    }
    return when {
        normalizedOutput == "number" -> ReporterFamily.NUMBER
        normalizedOutput == "text" || normalizedOutput == "string" -> ReporterFamily.STRING
        normalizedOutput == "any" -> ReporterFamily.ANY
        normalizedOutput == "boolean" -> ReporterFamily.CUSTOM
        blockType.startsWith(BlockTypes.CUSTOM_PREFIX) -> ReporterFamily.CUSTOM
        else -> ReporterFamily.CUSTOM
    }
}

fun reporterTemplateAsset(
    family: ReporterFamily,
    mode: ReporterVisualMode,
    boolValue: Boolean = false,
): String {
    val file = when (family) {
        ReporterFamily.BOOLEAN ->
            if (boolValue) "reporter-bool-small-true.svg" else "reporter-bool-small.false.svg"
        ReporterFamily.STRING ->
            if (mode == ReporterVisualMode.COMPACT) "reporter-string-small.svg" else "Reporter-Operator-String.svg"
        ReporterFamily.NUMBER ->
            if (mode == ReporterVisualMode.COMPACT) "reporter-num.small.svg" else "Reporter-Operator-Num.svg"
        ReporterFamily.ANY ->
            if (mode == ReporterVisualMode.COMPACT) "reporter-any.small.svg" else "Reporter-Operator-Any.svg"
        ReporterFamily.CUSTOM ->
            if (mode == ReporterVisualMode.COMPACT) "reporter-custom.small.svg" else "Reporter-Operator-Custom.svg"
        ReporterFamily.OPERATOR_BOOL ->
            if (mode == ReporterVisualMode.COMPACT) "reporter-bool-small.false.svg" else "Reporter-Operator-Custom.svg"
        ReporterFamily.OPERATOR_ANY ->
            if (mode == ReporterVisualMode.COMPACT) "reporter-any.small.svg" else "Reporter-Operator-Any.svg"
        ReporterFamily.OPERATOR_CUSTOM ->
            if (mode == ReporterVisualMode.COMPACT) "reporter-custom.small.svg" else "Reporter-Operator-Custom.svg"
        ReporterFamily.OPERATOR_NUM ->
            if (mode == ReporterVisualMode.COMPACT) "reporter-num.small.svg" else "Reporter-Operator-Num.svg"
        ReporterFamily.OPERATOR_STRING ->
            if (mode == ReporterVisualMode.COMPACT) "reporter-string-small.svg" else "Reporter-Operator-String.svg"
    }
    return "$REPORTER_ASSET_DIR/$file"
}
