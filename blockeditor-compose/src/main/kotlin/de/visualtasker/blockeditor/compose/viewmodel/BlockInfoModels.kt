package de.visualtasker.blockeditor.compose.viewmodel

import de.visualtasker.blockeditor.domain.BlockNode
import de.visualtasker.blockeditor.domain.FieldValue
import de.visualtasker.blockeditor.domain.asString
import de.visualtasker.blockeditor.registry.FieldDefinition
import de.visualtasker.blockeditor.registry.FieldKind
import de.visualtasker.blockeditor.registry.FieldOption
import de.visualtasker.blockeditor.registry.ParameterSourceKind

data class BlockInfoField(
    val key: String,
    val label: String,
    val kind: FieldKind,
    val value: String,
    val options: List<FieldOption> = emptyList(),
    val required: Boolean = false,
    val source: ParameterSourceKind = ParameterSourceKind.MANUAL,
    val sourceOptions: List<ParameterSourceKind> = listOf(ParameterSourceKind.MANUAL),
    val diagnostic: String? = null,
    val reporterAllowed: Boolean = false,
    val variableAllowed: Boolean = false,
)

internal val CommonBlockInfoFields = listOf(
    FieldDefinition(
        key = "active",
        label = "Aktiv",
        kind = FieldKind.BOOLEAN,
        defaultValue = "true",
    ),
    FieldDefinition(
        key = "displayMode",
        label = "Darstellung",
        kind = FieldKind.CHOICE,
        defaultValue = "compact",
        options = listOf(
            FieldOption("compact", "Kompakt"),
            FieldOption("detailed", "Detailliert"),
        ),
    ),
)

internal fun parameterSourceFieldKey(fieldKey: String): String = "$fieldKey.source"

internal fun BlockNode.infoValue(field: FieldDefinition): String =
    fields[field.key]?.asString() ?: field.defaultValue

internal fun BlockNode.infoSource(field: FieldDefinition): ParameterSourceKind {
    val raw = fields[parameterSourceFieldKey(field.key)]?.asString()
    return raw?.let { value ->
        ParameterSourceKind.entries.firstOrNull { it.name == value }
    }?.takeIf { it in field.sourceOptions } ?: field.defaultSource
}

internal fun FieldDefinition.parseInfoValue(rawValue: String): FieldValue? = when (kind) {
    FieldKind.NUMBER,
    FieldKind.TIMEOUT_MS,
    FieldKind.RETRY_COUNT,
    FieldKind.THRESHOLD,
    -> rawValue.toDoubleOrNull()?.let { FieldValue.Number(it) } ?: FieldValue.Number(0.0)
    FieldKind.BOOLEAN -> FieldValue.Bool(rawValue.equals("true", ignoreCase = true))
    FieldKind.CHOICE -> {
        if (options.none { it.value == rawValue }) null else FieldValue.Text(rawValue)
    }
    FieldKind.TEXT,
    FieldKind.VARIABLE_REF,
    FieldKind.FILE_PATH,
    FieldKind.IMAGE_TEMPLATE,
    FieldKind.REGION,
    -> FieldValue.Text(rawValue)
}

internal fun FieldDefinition.toBlockInfoField(block: BlockNode): BlockInfoField {
    val value = block.infoValue(this)
    val source = block.infoSource(this)
    return BlockInfoField(
        key = key,
        label = label.ifEmpty { key },
        kind = kind,
        value = value,
        options = options,
        required = required,
        source = source,
        sourceOptions = sourceOptions,
        diagnostic = diagnoseParameter(value, source),
        reporterAllowed = ParameterSourceKind.REPORTER in sourceOptions ||
            ParameterSourceKind.REGION_REPORTER in sourceOptions,
        variableAllowed = ParameterSourceKind.VARIABLE in sourceOptions,
    )
}

private fun FieldDefinition.diagnoseParameter(
    value: String,
    source: ParameterSourceKind,
): String? {
    if (source !in sourceOptions) {
        return "Quelle ${source.label()} ist für diesen Parameter nicht erlaubt."
    }
    if (source == ParameterSourceKind.REPORTER && ParameterSourceKind.REPORTER !in sourceOptions) {
        return "Reporter ist für diesen Parameter nicht erlaubt."
    }
    if (source == ParameterSourceKind.VARIABLE && ParameterSourceKind.VARIABLE !in sourceOptions) {
        return "Variable ist für diesen Parameter nicht erlaubt."
    }
    val manualValueRequired = source !in setOf(
        ParameterSourceKind.REPORTER,
        ParameterSourceKind.REGION_REPORTER,
        ParameterSourceKind.VARIABLE,
    )
    if (required && manualValueRequired && value.isBlank()) {
        return when (kind) {
            FieldKind.FILE_PATH,
            FieldKind.IMAGE_TEMPLATE,
            -> "Datei oder Template fehlt."
            FieldKind.REGION -> "Region fehlt."
            else -> "Pflichtwert fehlt."
        }
    }
    val number = when (kind) {
        FieldKind.NUMBER,
        FieldKind.TIMEOUT_MS,
        FieldKind.RETRY_COUNT,
        FieldKind.THRESHOLD,
        -> value.toDoubleOrNull()
        else -> null
    }
    if (kind in setOf(FieldKind.NUMBER, FieldKind.TIMEOUT_MS, FieldKind.RETRY_COUNT, FieldKind.THRESHOLD) &&
        number == null
    ) {
        return "Zahl erwartet."
    }
    val minimum = minValue
    val maximum = maxValue
    if (number != null && minimum != null && number < minimum) {
        return "Wert muss mindestens ${minimum.formatLimit()} sein."
    }
    if (number != null && maximum != null && number > maximum) {
        return "Wert darf höchstens ${maximum.formatLimit()} sein."
    }
    if (kind == FieldKind.REGION && value.isNotBlank() && !isValidRegion(value)) {
        return "Region ungültig. Erwartet x,y,width,height."
    }
    return null
}

internal fun ParameterSourceKind.label(): String = when (this) {
    ParameterSourceKind.MANUAL -> "Manuell"
    ParameterSourceKind.REPORTER -> "Reporter"
    ParameterSourceKind.VARIABLE -> "Variable"
    ParameterSourceKind.PRESET -> "Preset"
    ParameterSourceKind.FILE -> "Datei"
    ParameterSourceKind.REGION_MANUAL -> "Region manuell"
    ParameterSourceKind.REGION_REPORTER -> "Region per Reporter"
}

private fun Double.formatLimit(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()

private fun isValidRegion(value: String): Boolean {
    val parts = value.split(',', ';').map { it.trim() }
    if (parts.size != 4) return false
    val numbers = parts.map { it.toDoubleOrNull() ?: return false }
    return numbers[2] > 0.0 && numbers[3] > 0.0
}
