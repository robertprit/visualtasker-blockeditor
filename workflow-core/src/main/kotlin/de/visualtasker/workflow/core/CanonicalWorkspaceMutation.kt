package de.visualtasker.workflow.core

sealed interface CanonicalMutation {
    data class CreateEntity(val entity: SemanticEntity) : CanonicalMutation
    data class DeleteEntity(val entityId: SemanticEntityId) : CanonicalMutation
    data class CreateRelation(val relation: SemanticRelation) : CanonicalMutation
    data class RemoveRelation(val relationId: SemanticRelationId) : CanonicalMutation
    data class SetRelationOrder(val relationId: SemanticRelationId, val order: Int) : CanonicalMutation
}

sealed interface CanonicalMutationResult {
    data class Applied(val document: WorkspaceDocument) : CanonicalMutationResult
    data class Rejected(
        val document: WorkspaceDocument,
        val issues: List<CanonicalValidationIssue>,
    ) : CanonicalMutationResult
}

data class CanonicalMigrationResult(
    val document: WorkspaceDocument,
    val diagnostics: List<CanonicalDiagnostic>,
    val issues: List<CanonicalValidationIssue>,
) {
    val isValid: Boolean get() = diagnostics.none { it.severity == CanonicalDiagnosticSeverity.Error } && issues.isEmpty()
}

object CanonicalWorkspaceMigration {
    fun toCurrent(document: WorkspaceDocument): CanonicalMigrationResult {
        val canonicalization = if (document.canonical == null) {
            LegacyWorkspaceCanonicalizer.canonicalize(document)
        } else {
            CanonicalizationResult(document.canonical, emptyList())
        }
        val canonical = canonicalization.document.copy(
            id = document.id,
            revision = document.version,
            entities = canonicalization.document.entities.sortedBy { it.ref.id.value },
            relations = canonicalization.document.relations.sortedBy { it.id.value },
        )
        val candidate = document.copy(canonical = canonical)
        val projection = CanonicalCompatibilityProjection.project(candidate)
        return CanonicalMigrationResult(
            document = if (projection.isValid) projection.document else candidate,
            diagnostics = canonicalization.diagnostics,
            issues = projection.issues,
        )
    }
}

/** Atomic local mutation of canonical identities, relations and semantic order. */
object CanonicalWorkspaceMutator {
    fun apply(
        document: WorkspaceDocument,
        mutations: List<CanonicalMutation>,
    ): CanonicalMutationResult {
        if (mutations.isEmpty()) return CanonicalMutationResult.Applied(document)
        val baseline = if (document.canonical == null) {
            val migration = CanonicalWorkspaceMigration.toCurrent(document)
            if (!migration.isValid) {
                return CanonicalMutationResult.Rejected(
                    document,
                    migration.issues + migration.diagnostics
                        .filter { it.severity == CanonicalDiagnosticSeverity.Error }
                        .map { CanonicalValidationIssue(it.code, it.message) },
                )
            }
            migration.document
        } else {
            val canonicalIssues = buildList {
                addAll(CanonicalWorkspaceValidator.validate(document.canonical))
                if (document.canonical.id != document.id) {
                    add(CanonicalValidationIssue("canonical.workspace.mismatch", "Canonical workspace ${document.canonical.id} does not match ${document.id}."))
                }
                if (document.canonical.revision != document.version) {
                    add(CanonicalValidationIssue("canonical.revision.mismatch", "Canonical revision ${document.canonical.revision} does not match ${document.version}."))
                }
            }
            if (canonicalIssues.isNotEmpty()) return CanonicalMutationResult.Rejected(document, canonicalIssues)
            document
        }
        val canonical = baseline.canonical ?: return CanonicalMutationResult.Rejected(
            document,
            listOf(CanonicalValidationIssue("canonical.missing", "Canonical workspace state is missing.")),
        )
        val entities = canonical.entities.associateByTo(linkedMapOf()) { it.ref.id }
        val relations = canonical.relations.associateByTo(linkedMapOf(), SemanticRelation::id)

        mutations.forEach { mutation ->
            when (mutation) {
                is CanonicalMutation.CreateEntity -> {
                    if (entities.putIfAbsent(mutation.entity.ref.id, mutation.entity) != null) {
                        return reject(document, "mutation.entity.exists", "Entity ${mutation.entity.ref.id.value} already exists.")
                    }
                }
                is CanonicalMutation.DeleteEntity -> {
                    if (entities.remove(mutation.entityId) == null) {
                        return reject(document, "mutation.entity.missing", "Entity ${mutation.entityId.value} does not exist.")
                    }
                    relations.entries.removeAll { (_, relation) ->
                        relation.source.id == mutation.entityId || relation.target.id == mutation.entityId
                    }
                }
                is CanonicalMutation.CreateRelation -> {
                    if (relations.putIfAbsent(mutation.relation.id, mutation.relation) != null) {
                        return reject(document, "mutation.relation.exists", "Relation ${mutation.relation.id.value} already exists.")
                    }
                }
                is CanonicalMutation.RemoveRelation -> {
                    if (relations.remove(mutation.relationId) == null) {
                        return reject(document, "mutation.relation.missing", "Relation ${mutation.relationId.value} does not exist.")
                    }
                }
                is CanonicalMutation.SetRelationOrder -> {
                    if (mutation.order < 0) {
                        return reject(document, "mutation.order.invalid", "Relation order must be non-negative.")
                    }
                    val relation = relations[mutation.relationId]
                        ?: return reject(document, "mutation.relation.missing", "Relation ${mutation.relationId.value} does not exist.")
                    relations[mutation.relationId] = relation.copy(order = mutation.order)
                }
            }
        }

        normalizeRootRelations(entities, relations)

        val nextRevision = baseline.version + 1
        val nextCanonical = canonical.copy(
            revision = nextRevision,
            entities = entities.values.sortedBy { it.ref.id.value },
            relations = relations.values.sortedBy { it.id.value },
        )
        val validation = CanonicalWorkspaceValidator.validate(nextCanonical)
        if (validation.isNotEmpty()) return CanonicalMutationResult.Rejected(document, validation)
        val projected = CanonicalCompatibilityProjection.project(
            baseline.copy(version = nextRevision, canonical = nextCanonical),
        )
        return if (projected.isValid) {
            CanonicalMutationResult.Applied(
                projected.document.copy(
                    rootPositions = document.rootPositions.filterKeys { it in projected.document.rootBlocks },
                ),
            )
        } else {
            CanonicalMutationResult.Rejected(document, projected.issues)
        }
    }

    private fun reject(document: WorkspaceDocument, code: String, message: String): CanonicalMutationResult.Rejected =
        CanonicalMutationResult.Rejected(document, listOf(CanonicalValidationIssue(code, message)))

    private fun normalizeRootRelations(
        entities: Map<SemanticEntityId, SemanticEntity>,
        relations: MutableMap<SemanticRelationId, SemanticRelation>,
    ) {
        val workflow = entities.values.singleOrNull { it.ref.kind == SemanticEntityKind.Workflow }?.ref ?: return
        val structurallyOwned = relations.values.mapNotNullTo(mutableSetOf()) { relation ->
            when {
                relation.kind == SemanticRelationKind.Sequence -> relation.target.id
                relation.kind == SemanticRelationKind.Expression -> relation.target.id
                relation.kind == SemanticRelationKind.Containment && relation.role.kind == SemanticRelationRoleKind.Body -> relation.target.id
                else -> null
            }
        }
        val rootCandidates = entities.values
            .filter { it.ref.kind in setOf(SemanticEntityKind.Statement, SemanticEntityKind.Expression) }
            .filter { it.ref.id !in structurallyOwned }
            .map { it.ref }
            .sortedBy { it.id.value }
        val existingRoots = relations.values
            .filter { it.kind == SemanticRelationKind.Containment && it.role.kind == SemanticRelationRoleKind.Root }
            .sortedWith(compareBy<SemanticRelation> { it.order ?: Int.MAX_VALUE }.thenBy { it.id.value })
        val candidateIds = rootCandidates.mapTo(mutableSetOf(), SemanticEntityRef::id)
        relations.entries.removeAll { (_, relation) ->
            relation.kind == SemanticRelationKind.Containment &&
                relation.role.kind == SemanticRelationRoleKind.Root &&
                relation.target.id !in candidateIds
        }
        val retained = existingRoots.filter { it.target.id in candidateIds }
        val retainedIds = retained.mapTo(mutableSetOf()) { it.target.id }
        val ordered = retained.map { it.target } + rootCandidates.filter { it.id !in retainedIds }
        ordered.forEachIndexed { order, target ->
            val current = relations.values.firstOrNull {
                it.kind == SemanticRelationKind.Containment &&
                    it.role.kind == SemanticRelationRoleKind.Root &&
                    it.target.id == target.id
            }
            if (current != null) {
                relations[current.id] = current.copy(order = order)
            } else {
                val id = SemanticRelationId("native:root:${target.id.value}")
                relations[id] = SemanticRelation(
                    id = id,
                    kind = SemanticRelationKind.Containment,
                    source = workflow,
                    target = target,
                    role = SemanticRelationRole(SemanticRelationRoleKind.Root),
                    order = order,
                )
            }
        }
    }
}
