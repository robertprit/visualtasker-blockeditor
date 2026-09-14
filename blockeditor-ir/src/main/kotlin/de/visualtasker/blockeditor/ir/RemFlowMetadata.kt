package de.visualtasker.blockeditor.ir

import de.visualtasker.blockeditor.registry.CommandCatalogEntry

enum class RemFlowDirectiveKind {
    REGION,
    VARIABLE_BULK,
    EXPRESSION_CAPSULE,
    FLOW_BREAK,
    OFF_PAGE_OUT,
    OFF_PAGE_IN,
    GROUP,
    LAYOUT_HINT,
}

data class RemFlowDirective(
    val kind: RemFlowDirectiveKind,
    val attributes: Map<String, String>,
) {
    fun properties(): Map<String, String> = buildMap {
        put(PROPERTY_KIND, kind.name)
        put(PROPERTY_DIRECTIVE, "true")
        attributes.forEach { (key, value) ->
            put("$PROPERTY_PREFIX$key", value)
        }
    }

    companion object {
        const val PROPERTY_KIND = "remFlowKind"
        const val PROPERTY_DIRECTIVE = "remFlowDirective"
        const val PROPERTY_PREFIX = "remFlow."

        fun parse(entry: CommandCatalogEntry?, rawArguments: String): RemFlowDirective? {
            val kind = when (entry?.id) {
                "rem.region" -> RemFlowDirectiveKind.REGION
                "rem.variableBulk" -> RemFlowDirectiveKind.VARIABLE_BULK
                "rem.expressionCapsule" -> RemFlowDirectiveKind.EXPRESSION_CAPSULE
                "rem.flowBreak" -> RemFlowDirectiveKind.FLOW_BREAK
                "rem.offPageOut" -> RemFlowDirectiveKind.OFF_PAGE_OUT
                "rem.offPageIn" -> RemFlowDirectiveKind.OFF_PAGE_IN
                "rem.group" -> RemFlowDirectiveKind.GROUP
                "rem.layoutHint" -> RemFlowDirectiveKind.LAYOUT_HINT
                else -> return null
            }
            val arguments = splitTopLevelArguments(rawArguments)
            val attributes = entry.arguments
                .mapIndexedNotNull { index, argument ->
                    val raw = arguments.getOrNull(index) ?: argument.defaultValue ?: return@mapIndexedNotNull null
                    argument.name to raw.normalizedMetadataValue()
                }
                .toMap(linkedMapOf())
            return RemFlowDirective(kind, attributes)
        }
    }
}

internal fun splitTopLevelArguments(value: String): List<String> {
    if (value.isBlank()) return emptyList()
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var quote: Char? = null
    var escaped = false
    var depth = 0
    value.forEach { char ->
        when {
            escaped -> {
                current.append(char)
                escaped = false
            }
            char == '\\' && quote != null -> {
                current.append(char)
                escaped = true
            }
            quote != null -> {
                current.append(char)
                if (char == quote) quote = null
            }
            char == '"' || char == '\'' -> {
                quote = char
                current.append(char)
            }
            char == '(' || char == '[' || char == '{' -> {
                depth += 1
                current.append(char)
            }
            char == ')' || char == ']' || char == '}' -> {
                depth = (depth - 1).coerceAtLeast(0)
                current.append(char)
            }
            char == ',' && depth == 0 -> {
                result += current.toString().trim()
                current.clear()
            }
            else -> current.append(char)
        }
    }
    if (current.isNotEmpty() || result.isNotEmpty()) result += current.toString().trim()
    return result
}

private fun String.normalizedMetadataValue(): String {
    val trimmed = trim()
    if (trimmed.length < 2) return trimmed
    val quote = trimmed.first()
    if ((quote != '"' && quote != '\'') || trimmed.last() != quote) return trimmed
    return trimmed.substring(1, trimmed.lastIndex)
        .replace("\\$quote", quote.toString())
        .replace("\\n", "\n")
        .replace("\\t", "\t")
        .replace("\\\\", "\\")
}
