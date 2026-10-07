package de.visualtasker.workflow.core

import de.visualtasker.emscript.contract.CoreTypes
import de.visualtasker.emscript.contract.LanguageTypeCompatibility
import de.visualtasker.emscript.contract.LanguageTypeRef
import java.util.UUID

/** Schema-2 reducer. It changes canonical relations first and rebuilds legacy connector fields afterwards. */
internal object CanonicalWorkspaceReducer {
    fun reduce(document: WorkspaceDocument, action: WorkspaceAction, factory: BlockFactory): WorkspaceDocument {
        val migration = CanonicalWorkspaceMigration.toCurrent(document)
        if (!migration.isValid) return document
        val current = migration.document
        val reduced = when (action) {
            is WorkspaceAction.InstantiateBlock -> instantiate(current, action, factory)
            is WorkspaceAction.CreateBlock -> createBlock(current, action.block, action.x, action.y)
            is WorkspaceAction.ReplaceBlockShape -> replaceBlockShape(current, action.block)
            is WorkspaceAction.DeleteBlock -> delete(current, action.blockId)
            is WorkspaceAction.DeleteBlockPreservingChain -> deletePreservingChain(current, action.blockId)
            is WorkspaceAction.DeleteBlocks -> deleteExact(current, action.blockIds)
            is WorkspaceAction.MoveRoot -> moveRoot(current, action)
            is WorkspaceAction.Connect -> connect(current, action.source, action.target)
            is WorkspaceAction.Disconnect -> disconnect(current, action.connection)
            is WorkspaceAction.DetachBlock -> detach(current, action.blockId)
            is WorkspaceAction.Collapse -> updateBlock(current, action.blockId) { it.copy(collapsed = true) }
            is WorkspaceAction.Expand -> updateBlock(current, action.blockId) { it.copy(collapsed = false) }
            is WorkspaceAction.UpdateField -> updateField(current, action)
            is WorkspaceAction.CreateVariable -> createVariable(current, action.variable)
            is WorkspaceAction.RenameVariable -> renameVariable(current, action.variableId, action.name)
            is WorkspaceAction.DeleteVariable -> deleteVariable(current, action.variableId)
        }
        return if (reduced == current && document.canonical == null) document else reduced
    }

    fun liftBlock(document: WorkspaceDocument, blockId: BlockId, includedBlocks: Set<BlockId>? = null): WorkspaceDocument {
        val current = CanonicalWorkspaceMigration.toCurrent(document).takeIf(CanonicalMigrationResult::isValid)?.document
            ?: return document
        val entity = current.entityForBlock(blockId) ?: return document
        val included = includedBlocks.orEmpty().mapNotNullTo(mutableSetOf()) { current.entityForBlock(it)?.ref?.id }
        val relations = current.canonical!!.relations
        val incoming = relations.filter { relation ->
            relation.target.id == entity.ref.id && relation.kind in setOf(
                SemanticRelationKind.Sequence,
                SemanticRelationKind.Expression,
            ) || relation.kind == SemanticRelationKind.Containment &&
                relation.role.kind == SemanticRelationRoleKind.Body && relation.target.id == entity.ref.id
        }
        val outgoingSequence = relations.firstOrNull {
            it.kind == SemanticRelationKind.Sequence && it.source.id == entity.ref.id
        }
        val previousSequence = incoming.firstOrNull { it.kind == SemanticRelationKind.Sequence }
        val operations = mutableListOf<CanonicalMutation>()
        incoming.forEach { operations += CanonicalMutation.RemoveRelation(it.id) }
        val keepNext = (includedBlocks == null && incoming.isEmpty()) || outgoingSequence?.target?.id in included
        if (outgoingSequence != null && !keepNext) operations += CanonicalMutation.RemoveRelation(outgoingSequence.id)
        if (previousSequence != null && outgoingSequence != null && !keepNext) {
            operations += CanonicalMutation.CreateRelation(
                nativeRelation(
                    kind = SemanticRelationKind.Sequence,
                    source = previousSequence.source,
                    target = outgoingSequence.target,
                    role = SemanticRelationRole(SemanticRelationRoleKind.Next),
                ),
            )
        }
        return current.applyCanonical(operations)
    }

    private fun instantiate(document: WorkspaceDocument, action: WorkspaceAction.InstantiateBlock, factory: BlockFactory): WorkspaceDocument {
        val id = action.blockId ?: newBlockId()
        if (id in document.blocks) return document
        val block = factory.create(action.definitionId, id)?.withoutLinks() ?: return document
        return createBlock(document, block, action.x, action.y)
    }

    private fun createBlock(document: WorkspaceDocument, source: BlockNode, x: Float, y: Float): WorkspaceDocument {
        val id = source.id
        if (id in document.blocks) return document
        val block = source.withoutLinks()
        val entity = SemanticEntity(
            ref = SemanticEntityRef(
                SemanticEntityId("block:${id.value}"),
                if (block.output != null) SemanticEntityKind.Expression else SemanticEntityKind.Statement,
            ),
            origin = SemanticIdentityOrigin.Native,
            legacySourceId = id.value,
        )
        val staged = document.copy(
            blocks = document.blocks + (id to block),
            rootPositions = document.rootPositions + (id to WorkspacePoint(x, y)),
        )
        val operations = mutableListOf<CanonicalMutation>(CanonicalMutation.CreateEntity(entity))
        block.statementInputs.forEachIndexed { order, input ->
            val branchRole = input.name.toBranchRole() ?: return@forEachIndexed
            val branch = SemanticEntity(
                ref = SemanticEntityRef(SemanticEntityId("branch:${id.value}:${input.name}"), SemanticEntityKind.Branch),
                origin = SemanticIdentityOrigin.Native,
                legacySourceId = "${id.value}:${input.name}",
            )
            operations += CanonicalMutation.CreateEntity(branch)
            operations += CanonicalMutation.CreateRelation(
                SemanticRelation(
                    id = SemanticRelationId("native:branch:${id.value}:${input.name}"),
                    kind = SemanticRelationKind.Containment,
                    source = entity.ref,
                    target = branch.ref,
                    role = SemanticRelationRole(SemanticRelationRoleKind.Branch, branchRole = branchRole),
                    order = order,
                ),
            )
        }
        desiredVariableReference(document, entity.ref, block)?.let { relation ->
            operations += CanonicalMutation.CreateRelation(relation)
        }
        return staged.applyCanonical(operations, fallback = document)
    }

    private fun replaceBlockShape(document: WorkspaceDocument, replacementSource: BlockNode): WorkspaceDocument {
        val blockId = replacementSource.id
        val currentBlock = document.blocks[blockId] ?: return document
        val owner = document.entityForBlock(blockId)?.ref ?: return document
        val replacement = replacementSource.withoutLinks()
        val canonical = document.canonical!!
        val desiredBranches = replacement.statementInputs.mapNotNull { input ->
            input.name.toBranchRole()?.let { input.name to it }
        }.toMap()
        val existingBranchRelations = canonical.relations.filter {
            it.kind == SemanticRelationKind.Containment &&
                it.role.kind == SemanticRelationRoleKind.Branch && it.source.id == owner.id
        }
        val existingBySlot = existingBranchRelations.mapNotNull { relation ->
            canonical.entity(relation.target.id)?.legacySourceId
                ?.removePrefix("${blockId.value}:")
                ?.takeIf(String::isNotBlank)
                ?.let { it to relation }
        }.toMap()
        val operations = mutableListOf<CanonicalMutation>()

        existingBySlot.filterKeys { it !in desiredBranches }.values.forEach { relation ->
            operations += CanonicalMutation.DeleteEntity(relation.target.id)
        }
        desiredBranches.forEach { (slotName, branchRole) ->
            val desiredOrder = replacement.statementInputs.indexOfFirst { it.name == slotName }
            val existing = existingBySlot[slotName]
            if (existing == null) {
                val branch = SemanticEntity(
                    ref = SemanticEntityRef(
                        SemanticEntityId("branch:${blockId.value}:$slotName"),
                        SemanticEntityKind.Branch,
                    ),
                    origin = SemanticIdentityOrigin.Native,
                    legacySourceId = "${blockId.value}:$slotName",
                )
                operations += CanonicalMutation.CreateEntity(branch)
                operations += CanonicalMutation.CreateRelation(
                    SemanticRelation(
                        id = SemanticRelationId("native:branch:${blockId.value}:$slotName"),
                        kind = SemanticRelationKind.Containment,
                        source = owner,
                        target = branch.ref,
                        role = SemanticRelationRole(SemanticRelationRoleKind.Branch, branchRole = branchRole),
                        order = desiredOrder,
                    ),
                )
            } else if (existing.order != desiredOrder) {
                operations += CanonicalMutation.SetRelationOrder(existing.id, desiredOrder)
            }
        }

        canonical.relations.filter { relation ->
            when {
                relation.kind == SemanticRelationKind.Sequence && relation.source.id == owner.id -> replacement.next == null
                relation.kind == SemanticRelationKind.Sequence && relation.target.id == owner.id -> replacement.previous == null
                relation.kind == SemanticRelationKind.Expression && relation.target.id == owner.id -> replacement.output == null
                relation.kind == SemanticRelationKind.Expression && relation.source.id == owner.id ->
                    !replacement.supportsExpression(relation)
                else -> false
            }
        }.forEach { operations += CanonicalMutation.RemoveRelation(it.id) }

        val staged = document.copy(blocks = document.blocks + (blockId to replacement))
        return if (operations.isEmpty()) {
            publishPayload(staged, staged.blocks, fallback = document)
        } else {
            staged.applyCanonical(operations, fallback = document)
        }.takeIf { it.blocks[blockId] != currentBlock } ?: document
    }

    private fun delete(document: WorkspaceDocument, blockId: BlockId): WorkspaceDocument {
        if (blockId !in document.blocks) return document
        val toRemove = WorkspaceGraph.descendants(document, blockId) + blockId
        return deleteExact(document, toRemove)
    }

    private fun deletePreservingChain(document: WorkspaceDocument, blockId: BlockId): WorkspaceDocument {
        val entity = document.entityForBlock(blockId)?.ref ?: return document
        val toRemove = document.ownedNestedBlocks(blockId) + blockId
        val removedEntityIds = toRemove.flatMapTo(mutableSetOf()) { document.semanticEntityIdsForBlock(it) }
        val relations = document.canonical!!.relations
        val incomingSequence = relations.firstOrNull {
            it.kind == SemanticRelationKind.Sequence && it.target.id == entity.id
        }
        val incomingBody = relations.firstOrNull {
            it.kind == SemanticRelationKind.Containment &&
                it.role.kind == SemanticRelationRoleKind.Body && it.target.id == entity.id
        }
        val outgoingSequence = relations.firstOrNull {
            it.kind == SemanticRelationKind.Sequence && it.source.id == entity.id && it.target.id !in removedEntityIds
        }
        val operations = removedEntityIds.map<SemanticEntityId, CanonicalMutation>(CanonicalMutation::DeleteEntity).toMutableList()
        if (outgoingSequence != null) {
            val replacement = when {
                incomingSequence != null -> nativeRelation(
                    SemanticRelationKind.Sequence,
                    incomingSequence.source,
                    outgoingSequence.target,
                    SemanticRelationRole(SemanticRelationRoleKind.Next),
                )
                incomingBody != null -> nativeRelation(
                    SemanticRelationKind.Containment,
                    incomingBody.source,
                    outgoingSequence.target,
                    SemanticRelationRole(SemanticRelationRoleKind.Body),
                    incomingBody.order,
                )
                else -> null
            }
            replacement?.let { operations += CanonicalMutation.CreateRelation(it) }
        }
        val staged = document.copy(
            blocks = document.blocks - toRemove,
            rootPositions = document.rootPositions - toRemove,
        )
        return staged.applyCanonical(operations, fallback = document)
    }

    private fun deleteExact(document: WorkspaceDocument, requested: Set<BlockId>): WorkspaceDocument {
        val toRemove = requested.filterTo(mutableSetOf()) { it in document.blocks }
        if (toRemove.isEmpty()) return document
        val entities = toRemove.flatMapTo(mutableSetOf()) { document.semanticEntityIdsForBlock(it) }
        val staged = document.copy(
            blocks = document.blocks - toRemove,
            rootPositions = document.rootPositions - toRemove,
        )
        return staged.applyCanonical(entities.map(CanonicalMutation::DeleteEntity), fallback = document)
    }

    private fun moveRoot(document: WorkspaceDocument, action: WorkspaceAction.MoveRoot): WorkspaceDocument {
        if (action.blockId !in document.blocks) return document
        val nextVersion = document.version + 1
        val canonical = document.canonical!!.copy(revision = nextVersion)
        val candidate = document.copy(version = nextVersion, canonical = canonical)
            .withRootOffset(action.blockId, action.x, action.y)
        val projection = CanonicalCompatibilityProjection.project(candidate)
        return if (projection.isValid) projection.document else document
    }

    private fun connect(document: WorkspaceDocument, sourceId: ConnectionId, targetId: ConnectionId): WorkspaceDocument {
        val (sourceBlockId, source) = WorkspaceGraph.findConnection(document, sourceId) ?: return document
        val (targetBlockId, target) = WorkspaceGraph.findConnection(document, targetId) ?: return document
        if (sourceBlockId == targetBlockId || !canConnect(source, target)) return document
        return when (source.kind to target.kind) {
            ConnectionKind.Previous to ConnectionKind.Next,
            ConnectionKind.Previous to ConnectionKind.StatementInput -> connect(document, targetId, sourceId)
            ConnectionKind.Next to ConnectionKind.Previous -> connectSequence(document, sourceBlockId, targetBlockId)
            ConnectionKind.Output to ConnectionKind.ValueInput -> connectExpression(document, sourceBlockId, targetBlockId, target)
            ConnectionKind.StatementInput to ConnectionKind.Previous -> connectBody(document, sourceBlockId, source, targetBlockId)
            else -> document
        }
    }

    private fun connectSequence(document: WorkspaceDocument, sourceId: BlockId, targetId: BlockId): WorkspaceDocument {
        val source = document.entityForBlock(sourceId)?.ref ?: return document
        val target = document.entityForBlock(targetId)?.ref ?: return document
        if (source.kind != SemanticEntityKind.Statement || target.kind != SemanticEntityKind.Statement) return document
        val relations = document.canonical!!.relations
        if (document.isSemanticDescendant(target.id, source.id)) return document
        val oldAfterSource = relations.firstOrNull { it.kind == SemanticRelationKind.Sequence && it.source.id == source.id }
        val oldBeforeTarget = relations.firstOrNull { it.kind == SemanticRelationKind.Sequence && it.target.id == target.id }
            ?: relations.firstOrNull {
                it.kind == SemanticRelationKind.Containment &&
                    it.role.kind == SemanticRelationRoleKind.Body && it.target.id == target.id
            }
        val operations = mutableListOf<CanonicalMutation>()
        listOfNotNull(oldAfterSource, oldBeforeTarget).distinctBy { it.id }.forEach {
            operations += CanonicalMutation.RemoveRelation(it.id)
        }
        operations += CanonicalMutation.CreateRelation(
            nativeRelation(SemanticRelationKind.Sequence, source, target, SemanticRelationRole(SemanticRelationRoleKind.Next)),
        )
        val formerTarget = oldAfterSource?.target
        val targetHasOutgoing = relations.any { it.kind == SemanticRelationKind.Sequence && it.source.id == target.id }
        if (formerTarget != null && formerTarget.id != target.id && !targetHasOutgoing) {
            operations += CanonicalMutation.CreateRelation(
                nativeRelation(SemanticRelationKind.Sequence, target, formerTarget, SemanticRelationRole(SemanticRelationRoleKind.Next)),
            )
        }
        return document.applyCanonical(operations)
    }

    private fun connectExpression(
        document: WorkspaceDocument,
        reporterId: BlockId,
        inputOwnerId: BlockId,
        input: Connection,
    ): WorkspaceDocument {
        val reporter = document.entityForBlock(reporterId)?.ref ?: return document
        val owner = document.entityForBlock(inputOwnerId)?.ref ?: return document
        if (reporter.kind != SemanticEntityKind.Expression) return document
        val inputName = input.slotName ?: document.blocks[inputOwnerId]?.valueInputs
            ?.firstOrNull { it.connection.id == input.id }?.name ?: return document
        val branch = document.conditionBranch(owner, inputName)
        val source = branch ?: owner
        val ownerBlock = document.blocks.getValue(inputOwnerId)
        val isCondition = branch != null || inputName == "CONDITION" || inputName.startsWith("ELIF_CONDITION")
        val role = when {
            isCondition -> SemanticRelationRole(SemanticRelationRoleKind.Condition)
            inputName == "VALUE" -> SemanticRelationRole(SemanticRelationRoleKind.Value)
            inputName.uppercase() in setOf("A", "B", "C", "LEFT", "RIGHT", "INPUT1", "INPUT2") ->
                SemanticRelationRole(SemanticRelationRoleKind.Operand)
            else -> SemanticRelationRole(SemanticRelationRoleKind.Argument, name = inputName)
        }
        val order = ownerBlock.valueInputs.indexOfFirst { it.name == inputName }.takeIf { it >= 0 && role.kind in setOf(SemanticRelationRoleKind.Operand, SemanticRelationRoleKind.Argument) }
        val existingForInput = document.expressionForInput(source.id, role, order)
        val existingForReporter = document.canonical!!.relations.firstOrNull {
            it.kind == SemanticRelationKind.Expression && it.target.id == reporter.id
        }
        if (existingForInput != null && existingForInput.target.id != reporter.id) return document
        if (!typesCompatible(document, reporterId, inputOwnerId, input)) return document
        val operations = listOfNotNull(existingForInput, existingForReporter)
            .distinctBy { it.id }
            .map<SemanticRelation, CanonicalMutation> { CanonicalMutation.RemoveRelation(it.id) }
            .toMutableList()
        operations += CanonicalMutation.CreateRelation(
            nativeRelation(SemanticRelationKind.Expression, source, reporter, role, order),
        )
        return document.applyCanonical(operations)
    }

    private fun connectBody(
        document: WorkspaceDocument,
        ownerId: BlockId,
        input: Connection,
        targetId: BlockId,
    ): WorkspaceDocument {
        val owner = document.entityForBlock(ownerId)?.ref ?: return document
        val target = document.entityForBlock(targetId)?.ref ?: return document
        val slotName = input.slotName ?: document.blocks[ownerId]?.statementInputs
            ?.firstOrNull { it.connection.id == input.id }?.name ?: return document
        val branch = document.branchFor(owner, slotName) ?: return document
        val relations = document.canonical!!.relations
        val formerBody = relations.firstOrNull {
            it.kind == SemanticRelationKind.Containment && it.role.kind == SemanticRelationRoleKind.Body && it.source.id == branch.id
        }
        val formerIncoming = relations.firstOrNull {
            (it.kind == SemanticRelationKind.Sequence || it.kind == SemanticRelationKind.Expression) && it.target.id == target.id ||
                it.kind == SemanticRelationKind.Containment && it.role.kind == SemanticRelationRoleKind.Body && it.target.id == target.id
        }
        val operations = listOfNotNull(formerBody, formerIncoming)
            .distinctBy { it.id }
            .map<SemanticRelation, CanonicalMutation> { CanonicalMutation.RemoveRelation(it.id) }
            .toMutableList()
        operations += CanonicalMutation.CreateRelation(
            nativeRelation(
                SemanticRelationKind.Containment,
                branch,
                target,
                SemanticRelationRole(SemanticRelationRoleKind.Body),
                0,
            ),
        )
        val formerHead = formerBody?.target
        val targetHasOutgoing = relations.any { it.kind == SemanticRelationKind.Sequence && it.source.id == target.id }
        if (formerHead != null && formerHead.id != target.id && !targetHasOutgoing) {
            operations += CanonicalMutation.CreateRelation(
                nativeRelation(SemanticRelationKind.Sequence, target, formerHead, SemanticRelationRole(SemanticRelationRoleKind.Next)),
            )
        }
        return document.applyCanonical(operations)
    }

    private fun disconnect(document: WorkspaceDocument, connectionId: ConnectionId): WorkspaceDocument {
        val (_, connection) = WorkspaceGraph.findConnection(document, connectionId) ?: return document
        val partner = connection.connectedTo ?: return document
        val relation = document.canonical!!.relations.firstOrNull { relation ->
            document.relationConnectionIds(relation)?.let { (first, second) ->
                connectionId in setOf(first, second) && partner in setOf(first, second)
            } == true
        } ?: return document
        return document.applyCanonical(listOf(CanonicalMutation.RemoveRelation(relation.id)))
    }

    private fun detach(document: WorkspaceDocument, blockId: BlockId): WorkspaceDocument {
        val entity = document.entityForBlock(blockId)?.ref ?: return document
        val operations = document.canonical!!.relations
            .filter { it.source.id == entity.id || it.target.id == entity.id }
            .map { CanonicalMutation.RemoveRelation(it.id) }
        return document.applyCanonical(operations)
    }

    private fun updateBlock(document: WorkspaceDocument, blockId: BlockId, transform: (BlockNode) -> BlockNode): WorkspaceDocument {
        val block = document.blocks[blockId] ?: return document
        val updated = transform(block)
        if (updated == block) return document
        val owner = document.entityForBlock(blockId)?.ref ?: return document
        val existing = document.canonical!!.relations.filter {
            it.kind == SemanticRelationKind.Reference &&
                it.role.kind == SemanticRelationRoleKind.Variable &&
                it.source.id == owner.id
        }
        val desired = desiredVariableReference(document, owner, updated)
        val operations = buildList {
            existing.filter { relation -> desired == null || relation.target.id != desired.target.id }
                .forEach { add(CanonicalMutation.RemoveRelation(it.id)) }
            if (desired != null && existing.none { it.target.id == desired.target.id }) {
                add(CanonicalMutation.CreateRelation(desired))
            }
        }
        val staged = document.copy(blocks = document.blocks + (blockId to updated))
        return if (operations.isEmpty()) {
            publishPayload(staged, staged.blocks, fallback = document)
        } else {
            staged.applyCanonical(operations, fallback = document)
        }
    }

    private fun updateField(document: WorkspaceDocument, action: WorkspaceAction.UpdateField): WorkspaceDocument {
        val target = document.entityForBlock(action.blockId)?.ref?.id ?: return document
        val operation = WorkspaceOperation.SetProperty(
            operationId = WorkspaceOperationId("editor:set-property:${document.version}:${action.blockId.value}:${action.key}"),
            target = target,
            property = SemanticPropertyId(action.key),
            value = action.value.toWorkspacePropertyValue(),
        )
        return when (val result = WorkspaceOperationExecutor.execute(document, operation)) {
            is WorkspaceOperationResult.Success -> result.document
            is WorkspaceOperationResult.Failure -> document
        }
    }

    private fun desiredVariableReference(
        document: WorkspaceDocument,
        owner: SemanticEntityRef,
        block: BlockNode,
    ): SemanticRelation? {
        val variableId = listOf("variableId", "varId", "variable_id")
            .firstNotNullOfOrNull { key ->
                (block.fields[key] as? FieldValue.Text)?.value?.takeIf(String::isNotBlank)
            }
            ?: return null
        val target = document.canonical?.entity(SemanticEntityId("variable:$variableId"))?.ref
            ?: return null
        return nativeRelation(
            kind = SemanticRelationKind.Reference,
            source = owner,
            target = target,
            role = SemanticRelationRole(SemanticRelationRoleKind.Variable),
        )
    }

    private fun createVariable(document: WorkspaceDocument, variable: VariableDefinition): WorkspaceDocument {
        if (variable.id in document.variables.variables || !variable.isValid()) return document
        val normalized = variable.copy(name = variable.name.trim(), type = variable.type.trim())
        if (document.hasVariableNameCollision(normalized.id, normalized.name, normalized.scope)) return document
        val staged = document.copy(variables = document.variables.copy(variables = document.variables.variables + (normalized.id to normalized)))
        val entity = SemanticEntity(
            SemanticEntityRef(SemanticEntityId("variable:${variable.id}"), SemanticEntityKind.Variable),
            SemanticIdentityOrigin.Native,
            variable.id,
        )
        return staged.applyCanonical(listOf(CanonicalMutation.CreateEntity(entity)), fallback = document)
    }

    private fun renameVariable(document: WorkspaceDocument, variableId: String, name: String): WorkspaceDocument {
        val variable = document.variables.variables[variableId] ?: return document
        val trimmed = name.trim()
        if (!trimmed.isValidVariableName() || document.hasVariableNameCollision(variableId, trimmed, variable.scope)) return document
        if (variable.name == trimmed) return document
        val variables = document.variables.variables + (variableId to variable.copy(name = trimmed))
        return publishPayload(document.copy(variables = document.variables.copy(variables = variables)), document.blocks, fallback = document)
    }

    private fun deleteVariable(document: WorkspaceDocument, variableId: String): WorkspaceDocument {
        if (variableId !in document.variables.variables) return document
        val entityId = SemanticEntityId("variable:$variableId")
        if (document.canonical!!.relations.any { it.kind == SemanticRelationKind.Reference && it.target.id == entityId }) return document
        val staged = document.copy(variables = document.variables.copy(variables = document.variables.variables - variableId))
        return staged.applyCanonical(listOf(CanonicalMutation.DeleteEntity(entityId)), fallback = document)
    }

    private fun publishPayload(
        document: WorkspaceDocument,
        blocks: Map<BlockId, BlockNode>,
        fallback: WorkspaceDocument = document,
    ): WorkspaceDocument {
        val nextVersion = document.version + 1
        val candidate = document.copy(
            version = nextVersion,
            blocks = blocks,
            canonical = document.canonical!!.copy(revision = nextVersion),
        )
        val projection = CanonicalCompatibilityProjection.project(candidate)
        return if (projection.isValid) projection.document else fallback
    }

    private fun WorkspaceDocument.applyCanonical(
        operations: List<CanonicalMutation>,
        fallback: WorkspaceDocument = this,
    ): WorkspaceDocument = when (val result = CanonicalWorkspaceMutator.apply(this, operations)) {
        is CanonicalMutationResult.Applied -> result.document
        is CanonicalMutationResult.Rejected -> fallback
    }

    private fun WorkspaceDocument.entityForBlock(blockId: BlockId): SemanticEntity? = canonical?.entities?.firstOrNull {
        it.ref.kind in setOf(SemanticEntityKind.Statement, SemanticEntityKind.Expression) && it.legacySourceId == blockId.value
    }

    private fun WorkspaceDocument.semanticEntityIdsForBlock(blockId: BlockId): Set<SemanticEntityId> {
        val prefix = "${blockId.value}:"
        return canonical!!.entities.mapNotNullTo(mutableSetOf()) { entity ->
            when {
                entity.ref.kind in setOf(SemanticEntityKind.Statement, SemanticEntityKind.Expression) &&
                    entity.legacySourceId == blockId.value -> entity.ref.id
                entity.ref.kind == SemanticEntityKind.Branch && entity.legacySourceId?.startsWith(prefix) == true -> entity.ref.id
                else -> null
            }
        }
    }

    private fun WorkspaceDocument.ownedNestedBlocks(blockId: BlockId): Set<BlockId> {
        val result = mutableSetOf<BlockId>()
        fun walk(id: BlockId) {
            val block = blocks[id] ?: return
            block.statementInputs.forEach { slot ->
                WorkspaceGraph.statementStack(this, id, slot.name).forEach { childId ->
                    if (result.add(childId)) walk(childId)
                }
            }
            block.valueInputs.forEach { input ->
                val connected = input.connection.connectedTo ?: return@forEach
                val (childId, connection) = WorkspaceGraph.findConnection(this, connected) ?: return@forEach
                if (connection.kind == ConnectionKind.Output && result.add(childId)) walk(childId)
            }
        }
        walk(blockId)
        return result
    }

    private fun WorkspaceDocument.branchFor(owner: SemanticEntityRef, slotName: String): SemanticEntityRef? {
        val relation = canonical!!.relations.firstOrNull {
            it.kind == SemanticRelationKind.Containment && it.role.kind == SemanticRelationRoleKind.Branch &&
                it.source.id == owner.id && canonical.entity(it.target.id)?.legacySourceId == "${owner.id.value.removePrefix("block:")}:$slotName"
        }
        return relation?.target
    }

    private fun WorkspaceDocument.conditionBranch(owner: SemanticEntityRef, inputName: String): SemanticEntityRef? {
        val slot = when {
            inputName == "CONDITION" -> listOf("THEN", "BODY", "DO").firstNotNullOfOrNull { branchFor(owner, it) }
            inputName == "ELIF_CONDITION" -> branchFor(owner, "ELIF")
            inputName.startsWith("ELIF_CONDITION_") -> branchFor(owner, "ELIF_${inputName.removePrefix("ELIF_CONDITION_")}")
            else -> null
        }
        return slot
    }

    private fun WorkspaceDocument.expressionForInput(
        sourceId: SemanticEntityId,
        role: SemanticRelationRole,
        order: Int?,
    ): SemanticRelation? = canonical!!.relations.firstOrNull {
        it.kind == SemanticRelationKind.Expression && it.source.id == sourceId &&
            it.role == role && it.order == order
    }

    private fun BlockNode.supportsExpression(relation: SemanticRelation): Boolean = when (relation.role.kind) {
        SemanticRelationRoleKind.Condition -> valueInputs.any {
            it.name == "CONDITION" || it.name == "ELIF_CONDITION" || it.name.startsWith("ELIF_CONDITION_")
        }
        SemanticRelationRoleKind.Value -> valueInputs.any { it.name == "VALUE" } || valueInputs.isNotEmpty()
        SemanticRelationRoleKind.Argument -> valueInputs.any { it.name == relation.role.name }
        SemanticRelationRoleKind.Operand -> relation.order?.let { it in valueInputs.indices } == true
        else -> false
    }

    private fun WorkspaceDocument.relationConnectionIds(relation: SemanticRelation): Pair<ConnectionId, ConnectionId>? {
        fun block(ref: SemanticEntityRef): BlockNode? = entityForSemantic(ref.id)?.let(blocks::get)
        return when (relation.kind) {
            SemanticRelationKind.Sequence -> block(relation.source)?.next?.id?.let { first ->
                block(relation.target)?.previous?.id?.let { first to it }
            }
            SemanticRelationKind.Expression -> {
                val target = block(relation.target)?.output?.id ?: return null
                val sourceEntity = canonical!!.entity(relation.source.id) ?: return null
                val ownerId = if (sourceEntity.ref.kind == SemanticEntityKind.Branch) {
                    sourceEntity.legacySourceId?.substringBeforeLast(":")?.let(::BlockId)
                } else {
                    entityForSemantic(sourceEntity.ref.id)
                } ?: return null
                val owner = blocks[ownerId] ?: return null
                val input = when (relation.role.kind) {
                    SemanticRelationRoleKind.Condition -> {
                        val slot = sourceEntity.legacySourceId?.removePrefix("${ownerId.value}:")
                        val name = when {
                            slot == "ELIF" -> "ELIF_CONDITION"
                            slot?.startsWith("ELIF_") == true -> "ELIF_CONDITION_${slot.removePrefix("ELIF_")}"
                            else -> "CONDITION"
                        }
                        owner.valueInputs.firstOrNull { it.name == name }
                    }
                    SemanticRelationRoleKind.Value -> owner.valueInputs.firstOrNull { it.name == "VALUE" } ?: owner.valueInputs.firstOrNull()
                    SemanticRelationRoleKind.Argument -> owner.valueInputs.firstOrNull { it.name == relation.role.name }
                    SemanticRelationRoleKind.Operand -> relation.order?.let(owner.valueInputs::getOrNull)
                    else -> null
                } ?: return null
                target to input.connection.id
            }
            SemanticRelationKind.Containment -> if (relation.role.kind == SemanticRelationRoleKind.Body) {
                val branch = canonical!!.entity(relation.source.id) ?: return null
                val ownerId = branch.legacySourceId?.substringBeforeLast(":")?.let(::BlockId) ?: return null
                val slotName = branch.legacySourceId.removePrefix("${ownerId.value}:")
                val slot = blocks[ownerId]?.statementInputs?.firstOrNull { it.name == slotName } ?: return null
                val target = block(relation.target)?.previous ?: return null
                slot.connection.id to target.id
            } else null
            SemanticRelationKind.Reference -> null
        }
    }

    private fun WorkspaceDocument.entityForSemantic(id: SemanticEntityId): BlockId? = canonical!!.entities
        .firstOrNull { it.ref.id == id }
        ?.legacySourceId
        ?.let(::BlockId)

    private fun WorkspaceDocument.isSemanticDescendant(candidate: SemanticEntityId, ancestor: SemanticEntityId): Boolean {
        if (candidate == ancestor) return true
        val adjacency = canonical!!.relations
            .filter {
                it.kind in setOf(SemanticRelationKind.Sequence, SemanticRelationKind.Expression) ||
                    it.kind == SemanticRelationKind.Containment && it.role.kind == SemanticRelationRoleKind.Body
            }
            .groupBy { it.source.id }
        val visited = mutableSetOf<SemanticEntityId>()
        fun walk(id: SemanticEntityId): Boolean {
            if (!visited.add(id)) return false
            return adjacency[id].orEmpty().any { it.target.id == candidate || walk(it.target.id) }
        }
        return walk(ancestor)
    }

    private fun canConnect(source: Connection, target: Connection): Boolean = when (source.kind to target.kind) {
        ConnectionKind.Next to ConnectionKind.Previous,
        ConnectionKind.Previous to ConnectionKind.Next,
        ConnectionKind.Output to ConnectionKind.ValueInput,
        ConnectionKind.StatementInput to ConnectionKind.Previous,
        ConnectionKind.Previous to ConnectionKind.StatementInput -> true
        else -> false
    }

    private fun typesCompatible(
        document: WorkspaceDocument,
        outputBlockId: BlockId,
        inputBlockId: BlockId,
        input: Connection,
    ): Boolean {
        val output = document.blocks.getValue(outputBlockId).output ?: return false
        val outputType = output.provides ?: output.accepts.firstOrNull() ?: return true
        val inputBlock = document.blocks.getValue(inputBlockId)
        val variableType = if (
            inputBlock.type == "variable.set" &&
            input.slotName.equals("value", ignoreCase = true)
        ) {
            val variableId = (inputBlock.fields["variableId"] as? FieldValue.Text)?.value
            variableId?.let { document.variables.variables[it]?.type }
        } else {
            null
        }
        val acceptedTypes = variableType?.let(::setOf) ?: input.accepts
        if (acceptedTypes.isEmpty()) return true
        if (outputType.equals("Any", ignoreCase = true)) return true
        if (acceptedTypes.any { it.equals("Any", ignoreCase = true) }) return !outputType.trim().endsWith('?')
        val actual = LanguageTypeCompatibility.fromWorkspaceName(outputType) ?: return false
        return acceptedTypes
            .mapNotNull(LanguageTypeCompatibility::fromWorkspaceName)
            .any { expected -> LanguageTypeCompatibility.isAssignable(actual, expected) }
    }

    private fun VariableDefinition.isValid(): Boolean =
        id.isNotBlank() && name.trim().isValidVariableName() && type.isValidVariableType()

    private fun WorkspaceDocument.hasVariableNameCollision(variableId: String, name: String, scope: VariableScope): Boolean =
        variables.variables.values.any { it.id != variableId && it.scope == scope && it.name == name }

    private fun String.isValidVariableName(): Boolean = matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))

    private fun String.isValidVariableType(): Boolean = runCatching {
        val typeRef = LanguageTypeCompatibility.fromWorkspaceName(this) ?: return@runCatching false
        typeRef != CoreTypes.VOID.ref && !(typeRef is LanguageTypeRef.Nullable && typeRef.baseType == CoreTypes.VOID.ref)
    }.getOrDefault(false)

    private fun BlockNode.withoutLinks(): BlockNode = copy(
        previous = previous?.copy(connectedTo = null),
        next = next?.copy(connectedTo = null),
        output = output?.copy(connectedTo = null),
        valueInputs = valueInputs.map { it.copy(connection = it.connection.copy(connectedTo = null)) },
        statementInputs = statementInputs.map { it.copy(connection = it.connection.copy(connectedTo = null)) },
    )

    private fun FieldValue.toWorkspacePropertyValue(): WorkspacePropertyValue = when (this) {
        is FieldValue.Text -> WorkspacePropertyValue.Text(value)
        is FieldValue.Number -> WorkspacePropertyValue.Number(value)
        is FieldValue.Bool -> WorkspacePropertyValue.Bool(value)
    }

    private fun String.toBranchRole(): SemanticBranchRole? = when {
        this == "THEN" -> SemanticBranchRole.Then
        this == "ELSE" -> SemanticBranchRole.Else
        this == "ELIF" || startsWith("ELIF_") -> SemanticBranchRole.ElseIf
        this == "DO" || this == "BODY" -> SemanticBranchRole.LoopBody
        else -> null
    }

    private fun nativeRelation(
        kind: SemanticRelationKind,
        source: SemanticEntityRef,
        target: SemanticEntityRef,
        role: SemanticRelationRole,
        order: Int? = null,
    ): SemanticRelation = SemanticRelation(
        id = SemanticRelationId("native:${UUID.randomUUID()}"),
        kind = kind,
        source = source,
        target = target,
        role = role,
        order = order,
    )
}
