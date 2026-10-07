package de.visualtasker.workflow.core

data class CanonicalCompatibilityProjectionResult(
    val document: WorkspaceDocument,
    val issues: List<CanonicalValidationIssue>,
) {
    val isValid: Boolean get() = issues.isEmpty()
}

/**
 * Disposable schema-1-shaped view for existing editors. Semantic ownership remains in
 * [WorkspaceDocument.canonical]; connector endpoints and root membership are rebuilt from it.
 */
object CanonicalCompatibilityProjection {
    fun project(document: WorkspaceDocument): CanonicalCompatibilityProjectionResult {
        val canonical = document.canonical ?: return CanonicalCompatibilityProjectionResult(
            document = document,
            issues = listOf(CanonicalValidationIssue("canonical.missing", "Canonical workspace state is missing.")),
        )
        val issues = CanonicalWorkspaceValidator.validate(canonical).toMutableList()
        if (canonical.id != document.id) {
            issues += CanonicalValidationIssue(
                code = "canonical.workspace.mismatch",
                message = "Canonical workspace ${canonical.id} does not match ${document.id}.",
            )
        }
        if (canonical.revision != document.version) {
            issues += CanonicalValidationIssue(
                code = "canonical.revision.mismatch",
                message = "Canonical revision ${canonical.revision} does not match ${document.version}.",
            )
        }
        if (issues.isNotEmpty()) return CanonicalCompatibilityProjectionResult(document, issues)

        val blockEntities = canonical.entities
            .filter { it.ref.kind in setOf(SemanticEntityKind.Statement, SemanticEntityKind.Expression) }
            .associateBy { it.ref.id }
        val blocksByEntity = blockEntities.mapNotNull { (entityId, entity) ->
            entity.blockIdOrNull()?.takeIf(document.blocks::containsKey)?.let { entityId to it }
        }.toMap()
        val cleanBlocks = document.blocks.mapValues { (_, block) -> block.withoutSemanticConnections() }.toMutableMap()
        val rootRelations = mutableListOf<SemanticRelation>()

        fun connection(blockId: BlockId, connectionId: ConnectionId): Connection? =
            cleanBlocks[blockId]?.allConnections()?.firstOrNull { it.id == connectionId }

        fun link(firstBlockId: BlockId, firstId: ConnectionId, secondBlockId: BlockId, secondId: ConnectionId) {
            val first = connection(firstBlockId, firstId)
            val second = connection(secondBlockId, secondId)
            if (first == null || second == null) {
                issues += CanonicalValidationIssue(
                    code = "compatibility.connection.missing",
                    message = "Cannot project ${firstId.value} to ${secondId.value}; endpoint shape is missing.",
                )
                return
            }
            cleanBlocks[firstBlockId] = cleanBlocks.getValue(firstBlockId)
                .withConnectionUpdated(first.id) { it.copy(connectedTo = second.id) }
            cleanBlocks[secondBlockId] = cleanBlocks.getValue(secondBlockId)
                .withConnectionUpdated(second.id) { it.copy(connectedTo = first.id) }
        }

        canonical.relations.sortedBy { it.id.value }.forEach { relation ->
            when (relation.kind) {
                SemanticRelationKind.Sequence -> {
                    val sourceId = blocksByEntity[relation.source.id]
                    val targetId = blocksByEntity[relation.target.id]
                    val source = sourceId?.let(cleanBlocks::get)
                    val target = targetId?.let(cleanBlocks::get)
                    if (sourceId == null || targetId == null || source?.next == null || target?.previous == null) {
                        issues += projectionIssue(relation, "Sequence endpoint shape is missing.")
                    } else {
                        link(sourceId, source.next.id, targetId, target.previous.id)
                    }
                }

                SemanticRelationKind.Expression -> {
                    val targetId = blocksByEntity[relation.target.id]
                    val target = targetId?.let(cleanBlocks::get)
                    val inputOwner = relation.expressionInputOwner(canonical, blocksByEntity)
                    val owner = inputOwner?.first?.let(cleanBlocks::get)
                    val input = owner?.valueInputFor(relation, inputOwner.second)
                    if (targetId == null || target?.output == null || inputOwner == null || input == null) {
                        issues += projectionIssue(relation, "Expression endpoint shape is missing.")
                    } else {
                        link(targetId, target.output.id, inputOwner.first, input.connection.id)
                    }
                }

                SemanticRelationKind.Containment -> when (relation.role.kind) {
                    SemanticRelationRoleKind.Root -> rootRelations += relation
                    SemanticRelationRoleKind.Branch -> Unit
                    SemanticRelationRoleKind.Body -> {
                        val branch = canonical.entity(relation.source.id)
                        val ownerAndSlot = branch?.branchOwnerAndSlot(canonical, blocksByEntity)
                        val targetId = blocksByEntity[relation.target.id]
                        val target = targetId?.let(cleanBlocks::get)
                        val slot = ownerAndSlot?.let { (ownerId, slotName) ->
                            cleanBlocks[ownerId]?.statementInputs?.firstOrNull { it.name == slotName }
                        }
                        if (ownerAndSlot == null || targetId == null || target?.previous == null || slot == null) {
                            issues += projectionIssue(relation, "Containment body endpoint shape is missing.")
                        } else {
                            link(ownerAndSlot.first, slot.connection.id, targetId, target.previous.id)
                        }
                    }
                    else -> Unit
                }

                SemanticRelationKind.Reference -> Unit
            }
        }

        if (issues.isNotEmpty()) return CanonicalCompatibilityProjectionResult(document, issues.distinct())
        val roots = rootRelations
            .sortedWith(compareBy<SemanticRelation> { it.order ?: Int.MAX_VALUE }.thenBy { it.id.value })
            .mapNotNull { blocksByEntity[it.target.id] }
            .distinct()
        return CanonicalCompatibilityProjectionResult(
            document = document.copy(
                blocks = cleanBlocks,
                rootBlocks = roots,
                rootPositions = document.rootPositions.filterKeys { it in roots },
            ),
            issues = emptyList(),
        )
    }

    private fun projectionIssue(relation: SemanticRelation, message: String): CanonicalValidationIssue =
        CanonicalValidationIssue(
            code = "compatibility.relation.unprojectable",
            message = "$message Relation ${relation.id.value}.",
            relationId = relation.id,
        )

    private fun SemanticEntity.blockIdOrNull(): BlockId? {
        val source = legacySourceId?.takeIf(String::isNotBlank)
            ?: ref.id.value.removePrefix("block:").takeIf { it != ref.id.value }
        return source?.let(::BlockId)
    }

    private fun SemanticRelation.expressionInputOwner(
        canonical: CanonicalWorkspaceDocument,
        blocksByEntity: Map<SemanticEntityId, BlockId>,
    ): Pair<BlockId, String?>? {
        blocksByEntity[source.id]?.let { return it to null }
        val branch = canonical.entity(source.id) ?: return null
        return branch.branchOwnerAndSlot(canonical, blocksByEntity)
    }

    private fun SemanticEntity.branchOwnerAndSlot(
        canonical: CanonicalWorkspaceDocument,
        blocksByEntity: Map<SemanticEntityId, BlockId>,
    ): Pair<BlockId, String>? {
        if (ref.kind != SemanticEntityKind.Branch) return null
        val containment = canonical.relations.firstOrNull {
            it.kind == SemanticRelationKind.Containment &&
                it.role.kind == SemanticRelationRoleKind.Branch &&
                it.target.id == ref.id
        } ?: return null
        val ownerId = blocksByEntity[containment.source.id] ?: return null
        val slot = legacySourceId
            ?.removePrefix("${ownerId.value}:")
            ?.takeIf(String::isNotBlank)
            ?: containment.role.name
            ?: return null
        return ownerId to slot
    }

    private fun BlockNode.valueInputFor(relation: SemanticRelation, branchSlot: String?): ValueInput? {
        if (relation.role.kind == SemanticRelationRoleKind.Condition && branchSlot != null) {
            val name = when {
                branchSlot == "THEN" || branchSlot == "BODY" || branchSlot == "DO" -> "CONDITION"
                branchSlot == "ELIF" -> "ELIF_CONDITION"
                branchSlot.startsWith("ELIF_") -> "ELIF_CONDITION_${branchSlot.removePrefix("ELIF_")}"
                else -> "CONDITION"
            }
            return valueInputs.firstOrNull { it.name == name }
        }
        return when (relation.role.kind) {
            SemanticRelationRoleKind.Value -> valueInputs.firstOrNull { it.name == "VALUE" } ?: valueInputs.firstOrNull()
            SemanticRelationRoleKind.Argument -> valueInputs.firstOrNull { it.name == relation.role.name }
            SemanticRelationRoleKind.Operand -> relation.order?.let(valueInputs::getOrNull)
            SemanticRelationRoleKind.Condition -> valueInputs.firstOrNull { it.name == "CONDITION" }
            else -> null
        }
    }

    private fun BlockNode.withoutSemanticConnections(): BlockNode = copy(
        previous = previous?.copy(connectedTo = null),
        next = next?.copy(connectedTo = null),
        output = output?.copy(connectedTo = null),
        valueInputs = valueInputs.map { it.copy(connection = it.connection.copy(connectedTo = null)) },
        statementInputs = statementInputs.map { it.copy(connection = it.connection.copy(connectedTo = null)) },
    )
}
