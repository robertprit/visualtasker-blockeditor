package de.visualtasker.workflow.core

sealed interface CanonicalMutation {
    data class CreateEntity(val entity: SemanticEntity) : CanonicalMutation
    data class DeleteEntity(val entityId: SemanticEntityId) : CanonicalMutation
    data class CreateRelation(val relation: SemanticRelation) : CanonicalMutation
    data class RemoveRelation(val relationId: SemanticRelationId) : CanonicalMutation
    data class SetRelationOrder(val relationId: SemanticRelationId, val order: Int) : CanonicalMutation
    data class SetProperty(
        val target: SemanticEntityId,
        val property: SemanticPropertyId,
        val value: WorkspacePropertyValue,
    ) : CanonicalMutation
}

sealed interface CanonicalMutationResult {
    data class Applied(val document: WorkspaceDocument) : CanonicalMutationResult
    data class Rejected(
        val document: WorkspaceDocument,
        val issues: List<CanonicalValidationIssue>,
        val failedMutationIndex: Int? = null,
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
        val blocks = baseline.blocks.toMutableMap()
        val variables = baseline.variables.variables.toMutableMap()

        mutations.forEachIndexed { mutationIndex, mutation ->
            when (mutation) {
                is CanonicalMutation.CreateEntity -> {
                    if (entities.putIfAbsent(mutation.entity.ref.id, mutation.entity) != null) {
                        return reject(document, "mutation.entity.exists", "Entity ${mutation.entity.ref.id.value} already exists.", mutationIndex)
                    }
                }
                is CanonicalMutation.DeleteEntity -> {
                    val removed = entities.remove(mutation.entityId)
                    if (removed == null) {
                        return reject(document, "mutation.entity.missing", "Entity ${mutation.entityId.value} does not exist.", mutationIndex)
                    }
                    when (removed.ref.kind) {
                        SemanticEntityKind.Statement,
                        SemanticEntityKind.Expression -> removed.legacySourceId?.let(::BlockId)?.let(blocks::remove)
                        SemanticEntityKind.Variable -> {
                            val variableId = removed.legacySourceId ?: removed.ref.id.value.removePrefix("variable:")
                            variables.remove(variableId)
                        }
                        else -> Unit
                    }
                    relations.entries.removeAll { (_, relation) ->
                        relation.source.id == mutation.entityId || relation.target.id == mutation.entityId
                    }
                }
                is CanonicalMutation.CreateRelation -> {
                    if (relations.putIfAbsent(mutation.relation.id, mutation.relation) != null) {
                        return reject(document, "mutation.relation.exists", "Relation ${mutation.relation.id.value} already exists.", mutationIndex)
                    }
                }
                is CanonicalMutation.RemoveRelation -> {
                    if (relations.remove(mutation.relationId) == null) {
                        return reject(document, "mutation.relation.missing", "Relation ${mutation.relationId.value} does not exist.", mutationIndex)
                    }
                }
                is CanonicalMutation.SetRelationOrder -> {
                    if (mutation.order < 0) {
                        return reject(document, "mutation.order.invalid", "Relation order must be non-negative.", mutationIndex)
                    }
                    val relation = relations[mutation.relationId]
                        ?: return reject(document, "mutation.relation.missing", "Relation ${mutation.relationId.value} does not exist.", mutationIndex)
                    relations[mutation.relationId] = relation.copy(order = mutation.order)
                }
                is CanonicalMutation.SetProperty -> {
                    setProperty(entities, relations, blocks, variables, mutation)?.let { issue ->
                        return CanonicalMutationResult.Rejected(document, listOf(issue), mutationIndex)
                    }
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
            baseline.copy(
                version = nextRevision,
                blocks = blocks,
                variables = VariableRegistry(variables),
                canonical = nextCanonical,
            ),
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

    private fun reject(
        document: WorkspaceDocument,
        code: String,
        message: String,
        mutationIndex: Int? = null,
    ): CanonicalMutationResult.Rejected =
        CanonicalMutationResult.Rejected(document, listOf(CanonicalValidationIssue(code, message)), mutationIndex)

    private fun setProperty(
        entities: Map<SemanticEntityId, SemanticEntity>,
        relations: MutableMap<SemanticRelationId, SemanticRelation>,
        blocks: MutableMap<BlockId, BlockNode>,
        variables: MutableMap<String, VariableDefinition>,
        mutation: CanonicalMutation.SetProperty,
    ): CanonicalValidationIssue? {
        val entity = entities[mutation.target] ?: return CanonicalValidationIssue(
            code = "property.entity.missing",
            message = "Entity ${mutation.target.value} does not exist.",
            entityId = mutation.target,
        )
        return when (entity.ref.kind) {
            SemanticEntityKind.Statement,
            SemanticEntityKind.Expression -> setBlockProperty(entity, entities, relations, blocks, mutation)
            SemanticEntityKind.Variable -> setVariableProperty(entity, variables, mutation)
            else -> CanonicalValidationIssue(
                code = "property.missing",
                message = "Entity ${mutation.target.value} has no mutable property '${mutation.property.value}'.",
                entityId = mutation.target,
            )
        }
    }

    private fun setBlockProperty(
        entity: SemanticEntity,
        entities: Map<SemanticEntityId, SemanticEntity>,
        relations: MutableMap<SemanticRelationId, SemanticRelation>,
        blocks: MutableMap<BlockId, BlockNode>,
        mutation: CanonicalMutation.SetProperty,
    ): CanonicalValidationIssue? {
        val blockId = entity.legacySourceId?.let(::BlockId) ?: return propertyMissing(mutation)
        val block = blocks[blockId] ?: return propertyMissing(mutation)
        val key = mutation.property.value
        val current = block.fields[key]
        val expected = current ?: missingPropertyPrototype(block, key) ?: return propertyMissing(mutation)
        val nextFields = when (val value = mutation.value) {
            WorkspacePropertyValue.Null -> {
                if (key != "note" && !key.endsWith(".source")) {
                    return invalidPropertyType(mutation, expected, value)
                }
                block.fields - key
            }
            else -> {
                val converted = value.toFieldValue()
                if (!expected.sameTypeAs(converted)) return invalidPropertyType(mutation, expected, value)
                block.fields + (key to converted)
            }
        }
        blocks[blockId] = block.copy(fields = nextFields)
        if (key == "variableId" || key == "varId" || key == "variable_id") {
            val referenceRelations = relations.values.filter {
                it.kind == SemanticRelationKind.Reference &&
                    it.role.kind == SemanticRelationRoleKind.Variable &&
                    it.source.id == entity.ref.id
            }
            val variableId = (nextFields[key] as? FieldValue.Text)?.value?.takeIf(String::isNotBlank)
            val desired = variableId?.let { entities[SemanticEntityId("variable:$it")] }
            if (variableId != null && desired == null) {
                return CanonicalValidationIssue(
                    code = "property.reference.missing",
                    message = "Variable '$variableId' does not exist.",
                    entityId = entity.ref.id,
                )
            }
            referenceRelations.forEach { relations.remove(it.id) }
            if (desired != null) {
                val relation = SemanticRelation(
                    id = SemanticRelationId("native:reference:${entity.ref.id.value}:${desired.ref.id.value}"),
                    kind = SemanticRelationKind.Reference,
                    source = entity.ref,
                    target = desired.ref,
                    role = SemanticRelationRole(SemanticRelationRoleKind.Variable),
                )
                relations[relation.id] = relation
            }
        }
        return null
    }

    private fun setVariableProperty(
        entity: SemanticEntity,
        variables: MutableMap<String, VariableDefinition>,
        mutation: CanonicalMutation.SetProperty,
    ): CanonicalValidationIssue? {
        val variableId = entity.legacySourceId ?: entity.ref.id.value.removePrefix("variable:")
        val variable = variables[variableId] ?: return propertyMissing(mutation)
        val updated = when (mutation.property.value) {
            "name" -> (mutation.value as? WorkspacePropertyValue.Text)?.let { variable.copy(name = it.value) }
            "type" -> (mutation.value as? WorkspacePropertyValue.Text)?.let { variable.copy(type = it.value) }
            "scope" -> (mutation.value as? WorkspacePropertyValue.Text)?.value
                ?.let { raw -> VariableScope.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } }
                ?.let { variable.copy(scope = it) }
            "defaultValue", "initialValue" -> when (val value = mutation.value) {
                WorkspacePropertyValue.Null -> variable.copy(defaultValue = null)
                is WorkspacePropertyValue.Text -> variable.copy(defaultValue = value.value)
                is WorkspacePropertyValue.Number -> variable.copy(defaultValue = value.value.toString())
                is WorkspacePropertyValue.Bool -> variable.copy(defaultValue = value.value.toString())
            }
            "variableId" -> if ((mutation.value as? WorkspacePropertyValue.Text)?.value == variableId) variable else null
            else -> return propertyMissing(mutation)
        } ?: return CanonicalValidationIssue(
            code = "property.type.invalid",
            message = "Property '${mutation.property.value}' does not accept ${mutation.value::class.simpleName}.",
            entityId = mutation.target,
        )
        if (updated.name.isBlank() || updated.type.isBlank()) {
            return CanonicalValidationIssue(
                code = "property.type.invalid",
                message = "Variable name and type must not be blank.",
                entityId = mutation.target,
            )
        }
        variables[variableId] = updated
        return null
    }

    private fun propertyMissing(mutation: CanonicalMutation.SetProperty): CanonicalValidationIssue =
        CanonicalValidationIssue(
            code = "property.missing",
            message = "Property '${mutation.property.value}' does not exist on ${mutation.target.value}.",
            entityId = mutation.target,
        )

    private fun invalidPropertyType(
        mutation: CanonicalMutation.SetProperty,
        expected: FieldValue,
        actual: WorkspacePropertyValue,
    ): CanonicalValidationIssue = CanonicalValidationIssue(
        code = if (actual == WorkspacePropertyValue.Null) "property.null.invalid" else "property.type.invalid",
        message = "Property '${mutation.property.value}' expects ${expected::class.simpleName}, not ${actual::class.simpleName}.",
        entityId = mutation.target,
    )

    private fun missingPropertyPrototype(block: BlockNode, key: String): FieldValue? = when {
        key == "active" -> FieldValue.Bool(false)
        key in setOf("note", "variableId", "variableLabel", "command", "args") -> FieldValue.Text("")
        key.endsWith(".source") && block.fields.containsKey(key.removeSuffix(".source")) -> FieldValue.Text("")
        else -> null
    }

    private fun WorkspacePropertyValue.toFieldValue(): FieldValue = when (this) {
        is WorkspacePropertyValue.Text -> FieldValue.Text(value)
        is WorkspacePropertyValue.Number -> FieldValue.Number(value)
        is WorkspacePropertyValue.Bool -> FieldValue.Bool(value)
        WorkspacePropertyValue.Null -> error("Null is handled before field conversion.")
    }

    private fun FieldValue.sameTypeAs(other: FieldValue): Boolean = when (this) {
        is FieldValue.Text -> other is FieldValue.Text
        is FieldValue.Number -> other is FieldValue.Number
        is FieldValue.Bool -> other is FieldValue.Bool
    }

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
