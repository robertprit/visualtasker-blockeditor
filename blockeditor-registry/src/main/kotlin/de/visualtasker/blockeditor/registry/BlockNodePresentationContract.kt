package de.visualtasker.blockeditor.registry

enum class SemanticPropertyCategory {
    INPUT,
    OUTPUT,
    STRUCTURE,
    CONFIG,
    PRESENTATION,
}

enum class SemanticProjectionSurface {
    BLOCK,
    NODE,
    INSPECTOR,
}

data class SemanticPropertyRef(
    val id: String,
    val ownerId: String,
    val key: String,
    val category: SemanticPropertyCategory,
)

object BlockNodePresentationContract {
    private val presentationFieldKeys = setOf(
        "displayLabel",
        "displayMode",
        "label",
        "icon",
        "color",
        "note",
        "comment",
        "collapsed",
        "breakpoint",
    )

    private val configFieldKeys = setOf(
        "active",
        "enabled",
        "disabled",
        "mode",
    )

    fun fieldCategory(field: FieldDefinition): SemanticPropertyCategory {
        val key = field.key
        return when {
            key in presentationFieldKeys -> SemanticPropertyCategory.PRESENTATION
            key in configFieldKeys -> SemanticPropertyCategory.CONFIG
            else -> SemanticPropertyCategory.INPUT
        }
    }

    fun fieldIsReporterSlotCandidate(field: FieldDefinition): Boolean {
        if (fieldCategory(field) != SemanticPropertyCategory.INPUT) return false
        return ParameterSourceKind.REPORTER in field.sourceOptions ||
            ParameterSourceKind.REGION_REPORTER in field.sourceOptions ||
            ParameterSourceKind.VARIABLE in field.sourceOptions
    }

    fun fieldProperty(ownerId: String, fieldKey: String, category: SemanticPropertyCategory): SemanticPropertyRef =
        SemanticPropertyRef(
            id = fieldPropertyId(ownerId, fieldKey),
            ownerId = ownerId,
            key = fieldKey,
            category = category,
        )

    fun semanticPropertiesForDefinition(
        ownerId: String,
        definition: BlockDefinition,
    ): List<SemanticPropertyRef> {
        val fields = definition.fields.map { field ->
            fieldProperty(
                ownerId = ownerId,
                fieldKey = field.key,
                category = fieldCategory(field),
            )
        }
        val inputs = definition.valueInputs.map { input ->
            SemanticPropertyRef(
                id = valueInputPropertyId(ownerId, input.name),
                ownerId = ownerId,
                key = input.name,
                category = SemanticPropertyCategory.INPUT,
            )
        }
        val statements = definition.statementInputs.map { input ->
            SemanticPropertyRef(
                id = statementPropertyId(ownerId, input.name),
                ownerId = ownerId,
                key = input.name,
                category = SemanticPropertyCategory.STRUCTURE,
            )
        }
        val output = definition.outputType?.let {
            SemanticPropertyRef(
                id = outputPropertyId(ownerId),
                ownerId = ownerId,
                key = "output",
                category = SemanticPropertyCategory.OUTPUT,
            )
        }
        return fields + inputs + statements + listOfNotNull(output)
    }

    fun fieldPropertyId(ownerId: String, fieldKey: String): String =
        "semantic:$ownerId:field:$fieldKey"

    fun valueInputPropertyId(ownerId: String, inputName: String): String =
        "semantic:$ownerId:input:$inputName"

    fun outputPropertyId(ownerId: String): String =
        "semantic:$ownerId:output"

    fun statementPropertyId(ownerId: String, inputName: String): String =
        "semantic:$ownerId:structure:$inputName"
}
