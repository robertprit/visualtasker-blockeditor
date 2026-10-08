package de.visualtasker.workflow.semantics

import de.visualtasker.emscript.contract.CoreTypes
import de.visualtasker.emscript.contract.LanguageTypeCompatibility
import de.visualtasker.emscript.contract.LanguageTypeRef
import de.visualtasker.workflow.core.BlockId
import de.visualtasker.workflow.core.CanonicalSemanticPropertySchema
import de.visualtasker.workflow.core.SemanticEntityId
import de.visualtasker.workflow.core.SemanticEntityKind
import de.visualtasker.workflow.core.SemanticPropertyConstraint
import de.visualtasker.workflow.core.SemanticPropertyDefinition
import de.visualtasker.workflow.core.SemanticPropertyId
import de.visualtasker.workflow.core.SemanticPropertySchema
import de.visualtasker.workflow.core.SemanticPropertySemantics
import de.visualtasker.workflow.core.WorkspaceDocument

/** Catalog-backed property discovery. Command arguments remain the semantic owner. */
object VisualTaskerSemanticPropertySchema : SemanticPropertySchema {
    override fun propertiesFor(
        document: WorkspaceDocument,
        entityId: SemanticEntityId,
    ): List<SemanticPropertyDefinition> {
        val entity = document.canonical?.entity(entityId) ?: return emptyList()
        val fallback = CanonicalSemanticPropertySchema.propertiesFor(document, entityId)
        if (entity.ref.kind !in setOf(SemanticEntityKind.Statement, SemanticEntityKind.Expression)) return fallback
        val block = entity.legacySourceId?.let(::BlockId)?.let(document.blocks::get) ?: return fallback
        val command = VisualTaskerCommandCatalog.findByBlockType(block.type) ?: return fallback
        return buildMap<SemanticPropertyId, SemanticPropertyDefinition> {
            fallback.forEach { put(it.propertyId, it) }
            command.semanticPropertyDefinitions(entity.ref.kind).forEach { put(it.propertyId, it) }
        }.values.sortedBy { it.propertyId.value }
    }
}

fun CommandCatalogEntry.semanticPropertyDefinitions(
    ownerKind: SemanticEntityKind,
): List<SemanticPropertyDefinition> = arguments.flatMap { argument ->
    val structural = argument.type == CommandArgumentType.STATEMENT_BODY
    val value = SemanticPropertyDefinition(
        propertyId = SemanticPropertyId(argument.name),
        ownerKind = ownerKind,
        valueType = argument.languageType(),
        nullable = !argument.required && argument.defaultValue == null,
        writable = !structural,
        semantics = if (structural) SemanticPropertySemantics.Structural else SemanticPropertySemantics.Scalar,
        constraints = argument.constraints(),
        commandId = id,
    )
    if (structural) {
        listOf(value)
    } else {
        listOf(
            value,
            SemanticPropertyDefinition(
                propertyId = SemanticPropertyId("${argument.name}.source"),
                ownerKind = ownerKind,
                valueType = CoreTypes.STRING.ref,
                nullable = true,
                commandId = id,
            ),
        )
    }
}

private fun CommandArgument.languageType(): LanguageTypeRef = when (type) {
    CommandArgumentType.BOOLEAN -> CoreTypes.BOOL.ref
    CommandArgumentType.NUMBER,
    CommandArgumentType.DURATION_MS,
    CommandArgumentType.FREQUENCY_HZ,
    CommandArgumentType.PERCENT,
    -> CoreTypes.NUMBER.ref
    CommandArgumentType.ANY,
    CommandArgumentType.STATEMENT_BODY,
    -> acceptedTypes.singleOrNull()
        ?.let(LanguageTypeCompatibility::fromWorkspaceName)
        ?: CoreTypes.ANY.ref
    CommandArgumentType.TEXT,
    CommandArgumentType.VARIABLE_REF,
    CommandArgumentType.IMAGE_TEMPLATE,
    CommandArgumentType.REGION,
    -> acceptedTypes.singleOrNull()
        ?.let(LanguageTypeCompatibility::fromWorkspaceName)
        ?: CoreTypes.STRING.ref
}

private fun CommandArgument.constraints(): List<SemanticPropertyConstraint> = buildList {
    if (required && defaultValue == null && type in setOf(
            CommandArgumentType.TEXT,
            CommandArgumentType.VARIABLE_REF,
            CommandArgumentType.IMAGE_TEMPLATE,
            CommandArgumentType.REGION,
        )
    ) {
        add(SemanticPropertyConstraint.NonBlank)
    }
    when (type) {
        CommandArgumentType.DURATION_MS,
        CommandArgumentType.FREQUENCY_HZ,
        -> add(SemanticPropertyConstraint.NumberRange(minimum = 0.0))
        CommandArgumentType.PERCENT -> add(SemanticPropertyConstraint.NumberRange(minimum = 0.0, maximum = 1.0))
        else -> Unit
    }
}
