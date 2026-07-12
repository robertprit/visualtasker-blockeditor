package de.visualtasker.blockeditor.registry

import java.util.Locale
import java.util.UUID

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
)

object BlockDesignFactory {
    fun create(blueprint: BlockDesignBlueprint, id: String = nextId(blueprint.label)): BlockDefinition {
        require(blueprint.label.isNotBlank()) { "Block label required" }
        return BlockDefinition(
            id = id,
            label = blueprint.label.trim(),
            category = blueprint.category,
            hasPrevious = blueprint.hasPrevious,
            hasNext = blueprint.hasNext,
            outputType = blueprint.outputType,
            fields = blueprint.fields,
            valueInputs = blueprint.valueInputs,
            statementInputs = blueprint.statementInputs,
            isReporter = blueprint.isReporter,
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

    private fun nextId(label: String): String {
        val slug = label.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "block" }
        return "${BlockTypes.CUSTOM_PREFIX}${slug}_${UUID.randomUUID().toString().take(6)}"
    }
}
