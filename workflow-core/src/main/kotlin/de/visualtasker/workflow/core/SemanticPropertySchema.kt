package de.visualtasker.workflow.core

import de.visualtasker.emscript.contract.CoreTypes
import de.visualtasker.emscript.contract.LanguageTypeCompatibility
import de.visualtasker.emscript.contract.LanguageTypeRef

enum class SemanticPropertySemantics {
    Scalar,
    Structural,
}

sealed interface SemanticPropertyConstraint {
    data object NonBlank : SemanticPropertyConstraint

    data class NumberRange(
        val minimum: Double? = null,
        val maximum: Double? = null,
    ) : SemanticPropertyConstraint

    data class AllowedText(val values: Set<String>) : SemanticPropertyConstraint
}

data class SemanticPropertyDefinition(
    val propertyId: SemanticPropertyId,
    val ownerKind: SemanticEntityKind,
    val valueType: LanguageTypeRef,
    val nullable: Boolean = false,
    val writable: Boolean = true,
    val semantics: SemanticPropertySemantics = SemanticPropertySemantics.Scalar,
    val constraints: List<SemanticPropertyConstraint> = emptyList(),
    val commandId: String? = null,
)

enum class SemanticPropertyValidationCode {
    ENTITY_NOT_FOUND,
    PROPERTY_NOT_FOUND,
    PROPERTY_NOT_WRITABLE,
    STRUCTURAL_PROPERTY,
    INVALID_TYPE,
    NULL_NOT_ALLOWED,
    CONSTRAINT_VIOLATION,
}

data class SemanticPropertyValidationIssue(
    val code: SemanticPropertyValidationCode,
    val message: String,
)

interface SemanticPropertySchema {
    fun propertiesFor(document: WorkspaceDocument, entityId: SemanticEntityId): List<SemanticPropertyDefinition>

    fun propertyFor(
        document: WorkspaceDocument,
        entityId: SemanticEntityId,
        propertyId: SemanticPropertyId,
    ): SemanticPropertyDefinition? = propertiesFor(document, entityId).firstOrNull { it.propertyId == propertyId }

    fun validate(
        document: WorkspaceDocument,
        entityId: SemanticEntityId,
        propertyId: SemanticPropertyId,
        value: WorkspacePropertyValue,
    ): SemanticPropertyValidationIssue? {
        val entity = document.canonical?.entity(entityId) ?: return SemanticPropertyValidationIssue(
            SemanticPropertyValidationCode.ENTITY_NOT_FOUND,
            "Entity ${entityId.value} does not exist.",
        )
        val definition = propertyFor(document, entityId, propertyId) ?: return SemanticPropertyValidationIssue(
            SemanticPropertyValidationCode.PROPERTY_NOT_FOUND,
            "Property '${propertyId.value}' does not exist on ${entity.ref.kind} ${entityId.value}.",
        )
        if (definition.semantics == SemanticPropertySemantics.Structural) {
            return SemanticPropertyValidationIssue(
                SemanticPropertyValidationCode.STRUCTURAL_PROPERTY,
                "Property '${propertyId.value}' is structural and requires a structural WorkspaceOperation.",
            )
        }
        if (!definition.writable) {
            return SemanticPropertyValidationIssue(
                SemanticPropertyValidationCode.PROPERTY_NOT_WRITABLE,
                "Property '${propertyId.value}' is read-only.",
            )
        }
        if (value == WorkspacePropertyValue.Null) {
            return if (definition.nullable) null else SemanticPropertyValidationIssue(
                SemanticPropertyValidationCode.NULL_NOT_ALLOWED,
                "Property '${propertyId.value}' is not nullable.",
            )
        }
        val actualType = value.languageType()
        if (!LanguageTypeCompatibility.isAssignable(actualType, definition.valueType)) {
            return SemanticPropertyValidationIssue(
                SemanticPropertyValidationCode.INVALID_TYPE,
                "Property '${propertyId.value}' expects ${LanguageTypeCompatibility.workspaceName(definition.valueType)}, " +
                    "not ${LanguageTypeCompatibility.workspaceName(actualType)}.",
            )
        }
        return definition.constraints.firstNotNullOfOrNull { it.validate(propertyId, value) }
    }
}

/** Core fallback for variables and payloads whose definitions are not owned by a command catalog. */
object CanonicalSemanticPropertySchema : SemanticPropertySchema {
    override fun propertiesFor(document: WorkspaceDocument, entityId: SemanticEntityId): List<SemanticPropertyDefinition> {
        val entity = document.canonical?.entity(entityId) ?: return emptyList()
        return when (entity.ref.kind) {
            SemanticEntityKind.Statement,
            SemanticEntityKind.Expression,
            -> blockProperties(document, entity)

            SemanticEntityKind.Variable -> variableProperties()
            else -> structuralProperties(entity.ref.kind)
        }
    }

    private fun blockProperties(
        document: WorkspaceDocument,
        entity: SemanticEntity,
    ): List<SemanticPropertyDefinition> {
        val block = entity.legacySourceId?.let(::BlockId)?.let(document.blocks::get) ?: return emptyList()
        val declared = block.fields.map { (key, value) ->
            SemanticPropertyDefinition(
                propertyId = SemanticPropertyId(key),
                ownerKind = entity.ref.kind,
                valueType = value.languageType(),
                nullable = key == "note" || key.endsWith(".source"),
                constraints = if (key == "command") {
                    listOf(SemanticPropertyConstraint.NonBlank)
                } else {
                    emptyList()
                },
            )
        }
        val compatibility = listOf(
            textProperty(entity.ref.kind, "note", nullable = true),
            textProperty(entity.ref.kind, "variableId"),
            textProperty(entity.ref.kind, "variableLabel"),
            textProperty(entity.ref.kind, "command"),
            textProperty(entity.ref.kind, "args"),
            textProperty(entity.ref.kind, "displayLabel"),
            boolProperty(entity.ref.kind, "active"),
        )
        return (declared + compatibility + structuralProperties(entity.ref.kind)).distinctBy { it.propertyId }
    }

    private fun variableProperties(): List<SemanticPropertyDefinition> = listOf(
        textProperty(SemanticEntityKind.Variable, "name", constraints = listOf(SemanticPropertyConstraint.NonBlank)),
        textProperty(SemanticEntityKind.Variable, "type", constraints = listOf(SemanticPropertyConstraint.NonBlank)),
        textProperty(
            SemanticEntityKind.Variable,
            "scope",
            constraints = listOf(SemanticPropertyConstraint.AllowedText(VariableScope.entries.mapTo(linkedSetOf()) { it.name })),
        ),
        anyProperty(SemanticEntityKind.Variable, "defaultValue", nullable = true),
        anyProperty(SemanticEntityKind.Variable, "initialValue", nullable = true),
        textProperty(SemanticEntityKind.Variable, "variableId", writable = false),
    ) + structuralProperties(SemanticEntityKind.Variable)

    private fun structuralProperties(kind: SemanticEntityKind): List<SemanticPropertyDefinition> = listOf(
        textProperty(kind, "entityId", writable = false),
        SemanticPropertyDefinition(
            propertyId = SemanticPropertyId("relationOrder"),
            ownerKind = kind,
            valueType = CoreTypes.NUMBER.ref,
            writable = false,
            semantics = SemanticPropertySemantics.Structural,
        ),
        SemanticPropertyDefinition(
            propertyId = SemanticPropertyId("branchStructure"),
            ownerKind = kind,
            valueType = CoreTypes.ANY.ref,
            writable = false,
            semantics = SemanticPropertySemantics.Structural,
        ),
    )

    private fun textProperty(
        owner: SemanticEntityKind,
        id: String,
        nullable: Boolean = false,
        writable: Boolean = true,
        constraints: List<SemanticPropertyConstraint> = emptyList(),
    ) = SemanticPropertyDefinition(
        propertyId = SemanticPropertyId(id),
        ownerKind = owner,
        valueType = CoreTypes.STRING.ref,
        nullable = nullable,
        writable = writable,
        constraints = constraints,
    )

    private fun boolProperty(owner: SemanticEntityKind, id: String) = SemanticPropertyDefinition(
        propertyId = SemanticPropertyId(id),
        ownerKind = owner,
        valueType = CoreTypes.BOOL.ref,
    )

    private fun anyProperty(owner: SemanticEntityKind, id: String, nullable: Boolean) = SemanticPropertyDefinition(
        propertyId = SemanticPropertyId(id),
        ownerKind = owner,
        valueType = CoreTypes.ANY.ref,
        nullable = nullable,
    )
}

private fun WorkspacePropertyValue.languageType(): LanguageTypeRef = when (this) {
    is WorkspacePropertyValue.Text -> CoreTypes.STRING.ref
    is WorkspacePropertyValue.Number -> CoreTypes.NUMBER.ref
    is WorkspacePropertyValue.Bool -> CoreTypes.BOOL.ref
    WorkspacePropertyValue.Null -> CoreTypes.ANY.ref
}

private fun FieldValue.languageType(): LanguageTypeRef = when (this) {
    is FieldValue.Text -> CoreTypes.STRING.ref
    is FieldValue.Number -> CoreTypes.NUMBER.ref
    is FieldValue.Bool -> CoreTypes.BOOL.ref
}

private fun SemanticPropertyConstraint.validate(
    propertyId: SemanticPropertyId,
    value: WorkspacePropertyValue,
): SemanticPropertyValidationIssue? = when (this) {
    SemanticPropertyConstraint.NonBlank -> if ((value as? WorkspacePropertyValue.Text)?.value?.isBlank() == true) {
        SemanticPropertyValidationIssue(
            SemanticPropertyValidationCode.CONSTRAINT_VIOLATION,
            "Property '${propertyId.value}' must not be blank.",
        )
    } else {
        null
    }

    is SemanticPropertyConstraint.NumberRange -> {
        val number = (value as? WorkspacePropertyValue.Number)?.value ?: return null
        if ((minimum != null && number < minimum) || (maximum != null && number > maximum)) {
            SemanticPropertyValidationIssue(
                SemanticPropertyValidationCode.CONSTRAINT_VIOLATION,
                "Property '${propertyId.value}' is outside its supported range.",
            )
        } else {
            null
        }
    }

    is SemanticPropertyConstraint.AllowedText -> {
        val text = (value as? WorkspacePropertyValue.Text)?.value ?: return null
        if (values.none { it.equals(text, ignoreCase = true) }) {
            SemanticPropertyValidationIssue(
                SemanticPropertyValidationCode.CONSTRAINT_VIOLATION,
                "Property '${propertyId.value}' does not accept '$text'.",
            )
        } else {
            null
        }
    }
}
