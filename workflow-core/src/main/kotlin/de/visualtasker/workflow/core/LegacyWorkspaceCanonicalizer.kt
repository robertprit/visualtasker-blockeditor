package de.visualtasker.workflow.core

/**
 * Deterministic schema-1 compatibility reader. The result is read-only; legacy endpoints remain the input,
 * never a second independently mutable truth beside the canonical result.
 */
object LegacyWorkspaceCanonicalizer {
    fun canonicalize(legacy: WorkspaceDocument): CanonicalizationResult {
        val diagnostics = mutableListOf<CanonicalDiagnostic>()
        val entities = linkedMapOf<SemanticEntityId, SemanticEntity>()
        val relations = linkedMapOf<SemanticRelationId, SemanticRelation>()
        val workflow = ref("workflow:${legacy.id}", SemanticEntityKind.Workflow)
        entities[workflow.id] = SemanticEntity(workflow, SemanticIdentityOrigin.LegacyDerived, legacy.id)

        val blockRefs = legacy.blocks.entries.sortedBy { it.key.value }.associate { (blockId, block) ->
            val kind = if (block.output != null) SemanticEntityKind.Expression else SemanticEntityKind.Statement
            val entityRef = ref("block:${blockId.value}", kind)
            entities[entityRef.id] = SemanticEntity(entityRef, SemanticIdentityOrigin.LegacyBlock, blockId.value)
            blockId to entityRef
        }
        val variableRefs = legacy.variables.variables.entries.sortedBy { it.key }.associate { (registryId, definition) ->
            val stableId = definition.id.ifBlank { registryId }
            val entityRef = ref("variable:$stableId", SemanticEntityKind.Variable)
            entities[entityRef.id] = SemanticEntity(entityRef, SemanticIdentityOrigin.LegacyVariable, stableId)
            stableId to entityRef
        }

        val branchRefs = mutableMapOf<Pair<BlockId, String>, SemanticEntityRef>()
        legacy.blocks.entries.sortedBy { it.key.value }.forEach { (blockId, block) ->
            val owner = blockRefs.getValue(blockId)
            block.statementInputs.forEachIndexed { order, input ->
                val branchRole = input.name.toBranchRole()
                if (branchRole == null) {
                    diagnostics += CanonicalDiagnostic(
                        severity = CanonicalDiagnosticSeverity.Warning,
                        code = "legacy.branch.role.unsupported",
                        message = "Statement slot '${input.name}' has no M2-3 branch role.",
                        legacyBlockId = blockId,
                        legacyConnectionId = input.connection.id,
                    )
                    return@forEachIndexed
                }
                val branch = ref("branch:${blockId.value}:${input.name}", SemanticEntityKind.Branch)
                branchRefs[blockId to input.name] = branch
                entities[branch.id] = SemanticEntity(
                    ref = branch,
                    origin = SemanticIdentityOrigin.LegacyDerived,
                    legacySourceId = "${blockId.value}:${input.name}",
                )
                putRelation(
                    relations,
                    relation(
                        id = "containment:branch:${owner.id.value}:${branch.id.value}",
                        kind = SemanticRelationKind.Containment,
                        source = owner,
                        target = branch,
                        role = SemanticRelationRole(SemanticRelationRoleKind.Branch, branchRole = branchRole),
                        order = order,
                    ),
                    diagnostics,
                )
            }
        }

        legacy.rootBlocks.forEachIndexed { order, blockId ->
            val target = blockRefs[blockId]
            if (target == null) {
                diagnostics += CanonicalDiagnostic(
                    CanonicalDiagnosticSeverity.Error,
                    "legacy.root.missing",
                    "Root ${blockId.value} does not exist.",
                    legacyBlockId = blockId,
                )
            } else {
                putRelation(
                    relations,
                    relation(
                        id = "containment:root:${workflow.id.value}:${target.id.value}",
                        kind = SemanticRelationKind.Containment,
                        source = workflow,
                        target = target,
                        role = SemanticRelationRole(SemanticRelationRoleKind.Root),
                        order = order,
                    ),
                    diagnostics,
                )
            }
        }

        val connectionOwners = linkedMapOf<ConnectionId, Pair<BlockId, Connection>>()
        legacy.blocks.entries.sortedBy { it.key.value }.forEach { (blockId, block) ->
            block.allConnections().forEach { connection ->
                val previous = connectionOwners.putIfAbsent(connection.id, blockId to connection)
                if (previous != null) {
                    diagnostics += CanonicalDiagnostic(
                        CanonicalDiagnosticSeverity.Error,
                        "legacy.connection.id.duplicate",
                        "Connection ID ${connection.id.value} is owned by multiple endpoints.",
                        legacyBlockId = blockId,
                        legacyConnectionId = connection.id,
                    )
                }
            }
        }

        val processedPairs = mutableSetOf<String>()
        connectionOwners.entries.sortedBy { it.key.value }.forEach { (connectionId, owned) ->
            val (ownerId, connection) = owned
            val partnerId = connection.connectedTo ?: return@forEach
            val partnerOwned = connectionOwners[partnerId]
            if (partnerOwned == null) {
                diagnostics += CanonicalDiagnostic(
                    CanonicalDiagnosticSeverity.Error,
                    "legacy.connection.partner.missing",
                    "Connection ${connectionId.value} points to missing ${partnerId.value}.",
                    legacyBlockId = ownerId,
                    legacyConnectionId = connectionId,
                )
                return@forEach
            }
            val partner = partnerOwned.second
            if (partner.connectedTo != null && partner.connectedTo != connectionId) {
                diagnostics += CanonicalDiagnostic(
                    CanonicalDiagnosticSeverity.Error,
                    "legacy.connection.conflict",
                    "Connection ${connectionId.value} points to ${partnerId.value}, which points to ${partner.connectedTo.value}.",
                    legacyBlockId = ownerId,
                    legacyConnectionId = connectionId,
                )
                return@forEach
            }
            if (partner.connectedTo == null) {
                diagnostics += CanonicalDiagnostic(
                    CanonicalDiagnosticSeverity.Warning,
                    "legacy.connection.one-sided",
                    "Connection ${connectionId.value} points to ${partnerId.value} without reciprocal endpoint.",
                    legacyBlockId = ownerId,
                    legacyConnectionId = connectionId,
                )
            }
            val pairKey = listOf(connectionId.value, partnerId.value).sorted().joinToString("|")
            if (!processedPairs.add(pairKey)) return@forEach
            deriveRelation(
                legacy = legacy,
                firstOwner = ownerId,
                first = connection,
                secondOwner = partnerOwned.first,
                second = partner,
                blockRefs = blockRefs,
                branchRefs = branchRefs,
                relations = relations,
                diagnostics = diagnostics,
            )
        }

        legacy.blocks.entries.sortedBy { it.key.value }.forEach { (blockId, block) ->
            val source = blockRefs.getValue(blockId)
            val variableId = block.variableIdentity(legacy) ?: return@forEach
            val target = variableRefs[variableId]
            if (target == null) {
                diagnostics += CanonicalDiagnostic(
                    CanonicalDiagnosticSeverity.Warning,
                    "legacy.variable.reference.missing",
                    "Block ${blockId.value} references unknown variable '$variableId'.",
                    legacyBlockId = blockId,
                )
                return@forEach
            }
            putRelation(
                relations,
                relation(
                    id = "reference:variable:${source.id.value}:${target.id.value}",
                    kind = SemanticRelationKind.Reference,
                    source = source,
                    target = target,
                    role = SemanticRelationRole(SemanticRelationRoleKind.Variable),
                ),
                diagnostics,
            )
        }

        val document = CanonicalWorkspaceDocument(
            id = legacy.id,
            revision = legacy.version,
            entities = entities.values.sortedBy { it.ref.id.value },
            relations = relations.values.sortedBy { it.id.value },
        )
        CanonicalWorkspaceValidator.validate(document).forEach { issue ->
            diagnostics += CanonicalDiagnostic(
                CanonicalDiagnosticSeverity.Error,
                "canonical.${issue.code}",
                issue.message,
            )
        }
        return CanonicalizationResult(document, diagnostics.distinctBy { it.code to it.message })
    }

    private fun deriveRelation(
        legacy: WorkspaceDocument,
        firstOwner: BlockId,
        first: Connection,
        secondOwner: BlockId,
        second: Connection,
        blockRefs: Map<BlockId, SemanticEntityRef>,
        branchRefs: Map<Pair<BlockId, String>, SemanticEntityRef>,
        relations: MutableMap<SemanticRelationId, SemanticRelation>,
        diagnostics: MutableList<CanonicalDiagnostic>,
    ) {
        val pair = setOf(first.kind, second.kind)
        val relation = when (pair) {
            setOf(ConnectionKind.Next, ConnectionKind.Previous) -> {
                val sourceId = if (first.kind == ConnectionKind.Next) firstOwner else secondOwner
                val targetId = if (first.kind == ConnectionKind.Previous) firstOwner else secondOwner
                relation(
                    id = "sequence:${sourceId.value}:${targetId.value}",
                    kind = SemanticRelationKind.Sequence,
                    source = blockRefs.getValue(sourceId),
                    target = blockRefs.getValue(targetId),
                    role = SemanticRelationRole(SemanticRelationRoleKind.Next),
                )
            }

            setOf(ConnectionKind.Output, ConnectionKind.ValueInput) -> {
                val inputOwner = if (first.kind == ConnectionKind.ValueInput) firstOwner else secondOwner
                val input = if (first.kind == ConnectionKind.ValueInput) first else second
                val outputOwner = if (first.kind == ConnectionKind.Output) firstOwner else secondOwner
                val inputName = input.slotName ?: legacy.blocks[inputOwner]
                    ?.valueInputs
                    ?.firstOrNull { it.connection.id == input.id }
                    ?.name
                    ?: input.id.value
                val branch = conditionBranch(inputOwner, inputName, branchRefs)
                val source = branch ?: blockRefs.getValue(inputOwner)
                val (role, order) = inputRole(legacy.blocks.getValue(inputOwner), inputName, branch != null)
                relation(
                    id = "expression:${source.id.value}:${inputName}:${blockRefs.getValue(outputOwner).id.value}",
                    kind = SemanticRelationKind.Expression,
                    source = source,
                    target = blockRefs.getValue(outputOwner),
                    role = role,
                    order = order,
                )
            }

            setOf(ConnectionKind.StatementInput, ConnectionKind.Previous) -> {
                val inputOwner = if (first.kind == ConnectionKind.StatementInput) firstOwner else secondOwner
                val input = if (first.kind == ConnectionKind.StatementInput) first else second
                val bodyOwner = if (first.kind == ConnectionKind.Previous) firstOwner else secondOwner
                val slotName = input.slotName ?: legacy.blocks[inputOwner]
                    ?.statementInputs
                    ?.firstOrNull { it.connection.id == input.id }
                    ?.name
                    ?: input.id.value
                val branch = branchRefs[inputOwner to slotName]
                if (branch == null) {
                    diagnostics += CanonicalDiagnostic(
                        CanonicalDiagnosticSeverity.Error,
                        "legacy.statement.branch.missing",
                        "Statement input '$slotName' has no canonical branch entity.",
                        legacyBlockId = inputOwner,
                        legacyConnectionId = input.id,
                    )
                    null
                } else {
                    relation(
                        id = "containment:body:${branch.id.value}:${blockRefs.getValue(bodyOwner).id.value}",
                        kind = SemanticRelationKind.Containment,
                        source = branch,
                        target = blockRefs.getValue(bodyOwner),
                        role = SemanticRelationRole(SemanticRelationRoleKind.Body),
                        order = 0,
                    )
                }
            }

            else -> {
                diagnostics += CanonicalDiagnostic(
                    CanonicalDiagnosticSeverity.Error,
                    "legacy.connection.kind.incompatible",
                    "Cannot canonicalize ${first.kind} ↔ ${second.kind}.",
                    legacyBlockId = firstOwner,
                    legacyConnectionId = first.id,
                )
                null
            }
        }
        relation?.let { putRelation(relations, it, diagnostics) }
    }

    private fun conditionBranch(
        owner: BlockId,
        inputName: String,
        branches: Map<Pair<BlockId, String>, SemanticEntityRef>,
    ): SemanticEntityRef? {
        val slot = when {
            inputName == "CONDITION" -> listOf("THEN", "BODY").firstOrNull { owner to it in branches }
            inputName == "ELIF_CONDITION" -> "ELIF"
            inputName.startsWith("ELIF_CONDITION_") -> "ELIF_${inputName.removePrefix("ELIF_CONDITION_")}"
            else -> null
        }
        return slot?.let { branches[owner to it] }
    }

    private fun inputRole(
        owner: BlockNode,
        inputName: String,
        isCondition: Boolean,
    ): Pair<SemanticRelationRole, Int?> {
        if (isCondition) return SemanticRelationRole(SemanticRelationRoleKind.Condition) to null
        val order = owner.valueInputs.indexOfFirst { it.name == inputName }.takeIf { it >= 0 }
        return when (inputName.uppercase()) {
            "VALUE" -> SemanticRelationRole(SemanticRelationRoleKind.Value) to null
            "A", "B", "C", "LEFT", "RIGHT", "INPUT1", "INPUT2" ->
                SemanticRelationRole(SemanticRelationRoleKind.Operand) to order
            else -> SemanticRelationRole(SemanticRelationRoleKind.Argument, name = inputName) to order
        }
    }

    private fun String.toBranchRole(): SemanticBranchRole? = when {
        this == "THEN" -> SemanticBranchRole.Then
        this == "ELSE" -> SemanticBranchRole.Else
        this == "ELIF" || startsWith("ELIF_") -> SemanticBranchRole.ElseIf
        this == "DO" || this == "BODY" -> SemanticBranchRole.LoopBody
        else -> null
    }

    private fun BlockNode.variableIdentity(document: WorkspaceDocument): String? {
        val explicit = listOf("variableId", "varId", "variable_id")
            .firstNotNullOfOrNull { key -> (fields[key] as? FieldValue.Text)?.value?.takeIf(String::isNotBlank) }
        if (explicit != null) return explicit
        val label = listOf("variable", "variableLabel", "name")
            .firstNotNullOfOrNull { key -> (fields[key] as? FieldValue.Text)?.value?.takeIf(String::isNotBlank) }
            ?: return null
        return document.variables.variables.values.firstOrNull { it.name == label }?.id
    }

    private fun putRelation(
        target: MutableMap<SemanticRelationId, SemanticRelation>,
        relation: SemanticRelation,
        diagnostics: MutableList<CanonicalDiagnostic>,
    ) {
        val previous = target.putIfAbsent(relation.id, relation)
        if (previous != null && previous != relation) {
            diagnostics += CanonicalDiagnostic(
                CanonicalDiagnosticSeverity.Error,
                "canonical.relation.id.collision",
                "Relation ID ${relation.id.value} maps to different facts.",
            )
        }
    }

    private fun relation(
        id: String,
        kind: SemanticRelationKind,
        source: SemanticEntityRef,
        target: SemanticEntityRef,
        role: SemanticRelationRole,
        order: Int? = null,
    ): SemanticRelation = SemanticRelation(
        id = SemanticRelationId("legacy:$id"),
        kind = kind,
        source = source,
        target = target,
        role = role,
        order = order,
    )

    private fun ref(id: String, kind: SemanticEntityKind): SemanticEntityRef =
        SemanticEntityRef(SemanticEntityId(id), kind)
}
