package de.visualtasker.workflow.core

/**
 * Compatibility boundary for editor-facing [WorkspaceAction] commands.
 *
 * The legacy reducer is used only to prepare a validated candidate. Publication, revision changes and rollback are
 * owned exclusively by [WorkspaceOperationExecutor]. Presentation-only candidates are published without advancing
 * the canonical revision.
 */
internal object WorkspaceActionOperationAdapter {
    fun reduce(
        document: WorkspaceDocument,
        actions: List<WorkspaceAction>,
        factory: BlockFactory,
        propertySchema: SemanticPropertySchema,
        requireEveryActionToApply: Boolean = false,
    ): WorkspaceDocument {
        if (actions.isEmpty()) return document
        val migration = CanonicalWorkspaceMigration.toCurrent(document)
        if (!migration.isValid) return document
        val baseline = migration.document
        var candidate = baseline
        actions.forEach { action ->
            val next = CanonicalWorkspaceReducer.reduce(candidate, action, factory, propertySchema)
            if (requireEveryActionToApply && next == candidate) return document
            candidate = next
        }
        if (candidate == baseline) return document

        return publishCandidate(baseline, candidate, actions, propertySchema, document)
    }

    fun publishCandidate(
        document: WorkspaceDocument,
        candidate: WorkspaceDocument,
        propertySchema: SemanticPropertySchema,
    ): WorkspaceDocument {
        val migration = CanonicalWorkspaceMigration.toCurrent(document)
        if (!migration.isValid || candidate == migration.document) return document
        return publishCandidate(migration.document, candidate, emptyList(), propertySchema, document)
    }

    private fun publishCandidate(
        baseline: WorkspaceDocument,
        candidate: WorkspaceDocument,
        actions: List<WorkspaceAction>,
        propertySchema: SemanticPropertySchema,
        fallback: WorkspaceDocument,
    ): WorkspaceDocument {
        val operations = planOperations(baseline, candidate, actions)
        if (operations.isEmpty()) return candidate.withRevisionFrom(baseline)

        val staged = candidate.withRevisionFrom(baseline)
        val transactionSeed = buildString {
            append(baseline.version).append('|')
            operations.forEach { append(it::class.simpleName).append(':').append(it.operationId.value).append('|') }
        }
        val transaction = WorkspaceTransaction(
            transactionId = WorkspaceOperationId("editor-action-${transactionSeed.hashCode().toUInt()}"),
            operations = operations,
            expectedRevision = baseline.version,
        )
        return when (
            val result = WorkspaceOperationExecutor.executeWithStagedPayload(
                document = baseline,
                transaction = transaction,
                stagedPayload = staged,
                propertySchema = propertySchema,
            )
        ) {
            is WorkspaceOperationResult.Failure -> fallback
            is WorkspaceOperationResult.Success -> result.document.copy(
                rootPositions = candidate.rootPositions.filterKeys { it in result.document.rootBlocks },
            )
        }
    }

    private fun planOperations(
        baseline: WorkspaceDocument,
        candidate: WorkspaceDocument,
        actions: List<WorkspaceAction>,
    ): List<WorkspaceOperation> {
        val before = requireNotNull(baseline.canonical)
        val after = requireNotNull(candidate.canonical)
        val beforeEntities = before.entities.associateBy { it.ref.id }
        val afterEntities = after.entities.associateBy { it.ref.id }
        val beforeRelations = before.relations.associateBy(SemanticRelation::id)
        val afterRelations = after.relations.associateBy(SemanticRelation::id)
        var index = 0
        fun id(kind: String, stableId: String) = WorkspaceOperationId(
            "editor:${baseline.version}:${index++}:$kind:${stableId.hashCode().toUInt()}",
        )

        return buildList {
            beforeRelations.values
                .filter { previous -> afterRelations[previous.id]?.sameStructure(previous) != true }
                .sortedBy { it.id.value }
                .forEach { relation ->
                    add(WorkspaceOperation.RemoveRelation(id("remove-relation", relation.id.value), relation.id))
                }
            beforeEntities.values
                .filter { it.ref.id !in afterEntities }
                .sortedBy { it.ref.id.value }
                .forEach { entity ->
                    add(WorkspaceOperation.DeleteEntity(id("delete-entity", entity.ref.id.value), entity.ref.id))
                }
            afterEntities.values
                .filter { it.ref.id !in beforeEntities }
                .sortedBy { it.ref.id.value }
                .forEach { entity ->
                    add(WorkspaceOperation.CreateEntity(id("create-entity", entity.ref.id.value), entity))
                }
            afterRelations.values
                .filter { desired -> beforeRelations[desired.id]?.sameStructure(desired) != true }
                .sortedBy { it.id.value }
                .forEach { relation ->
                    add(WorkspaceOperation.CreateRelation(id("create-relation", relation.id.value), relation))
                }
            afterRelations.values
                .filter { desired ->
                    val previous = beforeRelations[desired.id]
                    previous != null && previous.sameStructure(desired) && previous.order != desired.order && desired.order != null
                }
                .sortedBy { it.id.value }
                .forEach { relation ->
                    add(WorkspaceOperation.SetOrder(id("set-order", relation.id.value), relation.id, requireNotNull(relation.order)))
                }

            actions.forEach { action ->
                when (action) {
                    is WorkspaceAction.UpdateField -> {
                        val entityId = candidate.entityIdForBlock(action.blockId) ?: return@forEach
                        if (entityId !in beforeEntities) return@forEach
                        add(
                            WorkspaceOperation.SetProperty(
                                id("set-property", "${entityId.value}:${action.key}"),
                                entityId,
                                SemanticPropertyId(action.key),
                                action.value.toWorkspacePropertyValue(),
                            ),
                        )
                    }
                    is WorkspaceAction.RenameVariable -> add(
                        WorkspaceOperation.SetProperty(
                            id("set-property", "variable:${action.variableId}:name"),
                            SemanticEntityId("variable:${action.variableId}"),
                            SemanticPropertyId("name"),
                            WorkspacePropertyValue.Text(action.name.trim()),
                        ),
                    )
                    is WorkspaceAction.ReplaceBlockShape -> {
                        val entityId = candidate.entityIdForBlock(action.block.id) ?: return@forEach
                        add(WorkspaceOperation.ReplaceEntityPayload(id("replace-payload", entityId.value), entityId))
                    }
                    else -> Unit
                }
            }
        }.distinctBy(WorkspaceOperation::operationId)
    }

    private fun WorkspaceDocument.withRevisionFrom(baseline: WorkspaceDocument): WorkspaceDocument = copy(
        version = baseline.version,
        canonical = requireNotNull(canonical).copy(revision = baseline.version),
    )

    private fun WorkspaceDocument.entityIdForBlock(blockId: BlockId): SemanticEntityId? = canonical?.entities
        ?.firstOrNull { entity ->
            entity.ref.kind in setOf(SemanticEntityKind.Statement, SemanticEntityKind.Expression) &&
                entity.legacySourceId == blockId.value
        }
        ?.ref
        ?.id

    private fun SemanticRelation.sameStructure(other: SemanticRelation): Boolean =
        kind == other.kind && source == other.source && target == other.target && role == other.role

    private fun FieldValue.toWorkspacePropertyValue(): WorkspacePropertyValue = when (this) {
        is FieldValue.Text -> WorkspacePropertyValue.Text(value)
        is FieldValue.Number -> WorkspacePropertyValue.Number(value)
        is FieldValue.Bool -> WorkspacePropertyValue.Bool(value)
    }
}
