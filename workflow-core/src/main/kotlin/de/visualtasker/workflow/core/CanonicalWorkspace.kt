package de.visualtasker.workflow.core

import kotlinx.serialization.Serializable

@Serializable
enum class SemanticIdentityOrigin {
    Native,
    LegacyBlock,
    LegacyVariable,
    LegacyDerived,
}

@Serializable
data class SemanticEntity(
    val ref: SemanticEntityRef,
    val origin: SemanticIdentityOrigin,
    val legacySourceId: String? = null,
)

/**
 * Read-only canonical authoring model. It deliberately contains no connector, geometry or editor state.
 * M2-4 does not make this a second mutable store beside [WorkspaceDocument].
 */
@Serializable
data class CanonicalWorkspaceDocument(
    val id: String,
    val revision: Long,
    val entities: List<SemanticEntity>,
    val relations: List<SemanticRelation>,
) {
    init {
        require(id.isNotBlank()) { "Canonical workspace ID must not be blank." }
    }

    fun entity(id: SemanticEntityId): SemanticEntity? = entities.firstOrNull { it.ref.id == id }
}

enum class CanonicalDiagnosticSeverity {
    Info,
    Warning,
    Error,
}

data class CanonicalDiagnostic(
    val severity: CanonicalDiagnosticSeverity,
    val code: String,
    val message: String,
    val legacyBlockId: BlockId? = null,
    val legacyConnectionId: ConnectionId? = null,
)

data class CanonicalizationResult(
    val document: CanonicalWorkspaceDocument,
    val diagnostics: List<CanonicalDiagnostic>,
) {
    val isValid: Boolean get() = diagnostics.none { it.severity == CanonicalDiagnosticSeverity.Error }
}

data class CanonicalValidationIssue(
    val code: String,
    val message: String,
    val entityId: SemanticEntityId? = null,
    val relationId: SemanticRelationId? = null,
)

object CanonicalWorkspaceValidator {
    fun validate(document: CanonicalWorkspaceDocument): List<CanonicalValidationIssue> = buildList {
        val entitiesById = document.entities.groupBy { it.ref.id }
        entitiesById.filterValues { it.size > 1 }.keys.forEach { id ->
            add(CanonicalValidationIssue("duplicate.entity", "Duplicate semantic entity ${id.value}.", entityId = id))
        }
        val relationsById = document.relations.groupBy(SemanticRelation::id)
        relationsById.filterValues { it.size > 1 }.keys.forEach { id ->
            add(CanonicalValidationIssue("duplicate.relation", "Duplicate semantic relation ${id.value}.", relationId = id))
        }
        val entityIds = entitiesById.keys
        document.relations.forEach { relation ->
            if (relation.source.id !in entityIds) {
                add(CanonicalValidationIssue("relation.source.missing", "Missing source ${relation.source.id.value}.", relationId = relation.id))
            }
            if (relation.target.id !in entityIds) {
                add(CanonicalValidationIssue("relation.target.missing", "Missing target ${relation.target.id.value}.", relationId = relation.id))
            }
            val source = entitiesById[relation.source.id]?.singleOrNull()?.ref
            val target = entitiesById[relation.target.id]?.singleOrNull()?.ref
            if (source != null && source.kind != relation.source.kind) {
                add(CanonicalValidationIssue("relation.source.kind", "Source kind does not match entity ${source.id.value}.", relationId = relation.id))
            }
            if (target != null && target.kind != relation.target.kind) {
                add(CanonicalValidationIssue("relation.target.kind", "Target kind does not match entity ${target.id.value}.", relationId = relation.id))
            }
            validateCompatibility(relation)?.let(::add)
        }
        document.relations
            .filter { it.kind == SemanticRelationKind.Sequence }
            .groupBy { it.source.id }
            .filterValues { it.size > 1 }
            .forEach { (sourceId, relations) ->
                add(
                    CanonicalValidationIssue(
                        code = "sequence.outgoing.duplicate",
                        message = "Statement ${sourceId.value} has ${relations.size} outgoing sequence relations.",
                        entityId = sourceId,
                    ),
                )
            }
        document.relations
            .filter { it.kind == SemanticRelationKind.Sequence }
            .groupBy { it.target.id }
            .filterValues { it.size > 1 }
            .forEach { (targetId, relations) ->
                add(
                    CanonicalValidationIssue(
                        code = "sequence.incoming.duplicate",
                        message = "Statement ${targetId.value} has ${relations.size} incoming sequence relations.",
                        entityId = targetId,
                    ),
                )
            }
        document.relations
            .filter {
                it.kind == SemanticRelationKind.Containment &&
                    it.role.kind in setOf(SemanticRelationRoleKind.Root, SemanticRelationRoleKind.Branch)
            }
            .filter { it.order != null }
            .groupBy { Triple(it.source.id, it.role.kind, it.order) }
            .filterValues { it.size > 1 }
            .forEach { (_, relations) ->
                add(
                    CanonicalValidationIssue(
                        code = "containment.order.duplicate",
                        message = "Containment order ${relations.first().order} is duplicated for ${relations.first().source.id.value}.",
                        relationId = relations.first().id,
                    ),
                )
            }
        document.relations
            .groupBy { relation ->
                listOf(
                    relation.kind.name,
                    relation.source.id.value,
                    relation.target.id.value,
                    relation.role.kind.name,
                    relation.role.name.orEmpty(),
                    relation.role.branchRole?.name.orEmpty(),
                ).joinToString("|")
            }
            .filterValues { it.size > 1 }
            .forEach { (_, relations) ->
                add(
                    CanonicalValidationIssue(
                        code = "relation.semantic.duplicate",
                        message = "Duplicate semantic relation facts ${relations.joinToString { it.id.value }}.",
                        relationId = relations.first().id,
                    ),
                )
            }
    }

    private fun validateCompatibility(relation: SemanticRelation): CanonicalValidationIssue? {
        val valid = when (relation.kind) {
            SemanticRelationKind.Sequence ->
                relation.source.kind == SemanticEntityKind.Statement &&
                    relation.target.kind == SemanticEntityKind.Statement &&
                    relation.role.kind == SemanticRelationRoleKind.Next

            SemanticRelationKind.Expression ->
                relation.source.kind in setOf(
                    SemanticEntityKind.Statement,
                    SemanticEntityKind.Expression,
                    SemanticEntityKind.Branch,
                ) && relation.target.kind == SemanticEntityKind.Expression &&
                    relation.role.kind in setOf(
                        SemanticRelationRoleKind.Value,
                        SemanticRelationRoleKind.Condition,
                        SemanticRelationRoleKind.Argument,
                        SemanticRelationRoleKind.Operand,
                    )

            SemanticRelationKind.Containment -> when (relation.role.kind) {
                SemanticRelationRoleKind.Root ->
                    relation.source.kind == SemanticEntityKind.Workflow &&
                        relation.target.kind in setOf(
                            SemanticEntityKind.Statement,
                            SemanticEntityKind.Expression,
                        ) && relation.order != null
                SemanticRelationRoleKind.Branch ->
                    relation.source.kind == SemanticEntityKind.Statement &&
                        relation.target.kind == SemanticEntityKind.Branch && relation.order != null
                SemanticRelationRoleKind.Body ->
                    relation.source.kind == SemanticEntityKind.Branch &&
                        relation.target.kind == SemanticEntityKind.Statement
                else -> false
            }

            SemanticRelationKind.Reference ->
                relation.source.kind in setOf(SemanticEntityKind.Statement, SemanticEntityKind.Expression) &&
                    relation.target.kind == SemanticEntityKind.Variable &&
                    relation.role.kind == SemanticRelationRoleKind.Variable
        }
        return if (valid) null else {
            CanonicalValidationIssue(
                code = "relation.incompatible",
                message = "${relation.kind} relation ${relation.id.value} has incompatible endpoints or role.",
                relationId = relation.id,
            )
        }
    }
}
