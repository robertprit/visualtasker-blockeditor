package de.visualtasker.blockeditor.registry

import de.visualtasker.blockeditor.domain.VariableDefinition

object VariableReporterFactory {
    fun reporterId(variableId: String): String = "${BlockTypes.VARIABLE_REPORTER_PREFIX}$variableId"

    fun create(variable: VariableDefinition): BlockDefinition = BlockDefinition(
        id = reporterId(variable.id),
        label = variable.name,
        category = BlockCategories.VARIABLE,
        hasPrevious = false,
        hasNext = false,
        outputType = variable.type.ifBlank { "Any" },
        isReporter = true,
        fields = listOf(
            FieldDefinition("variable", "var", defaultValue = variable.name),
        ),
    )
}
