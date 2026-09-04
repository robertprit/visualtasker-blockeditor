package de.visualtasker.blockeditor.ir

enum class IrGraphSemanticCode {
    NODE_MISSING_SOURCE_BLOCK,
    EDGE_MISSING_SOURCE_BLOCK,
    BRANCH_MISSING_SOURCE_BLOCK,
    FACET_MISSING_SOURCE_BLOCK,
    NODE_MISSING_SCOPE,
    DUPLICATE_NODE_SOURCE_BLOCK,
    DUPLICATE_BRANCH_SLOT,
    BRANCH_INDEX_OUT_OF_ORDER,
    BRANCH_CONDITION_EDGE_MISSING,
    BRANCH_BODY_EDGE_MISSING,
    DUPLICATE_INPUT_PORT,
    DUPLICATE_OUTPUT_PORT,
}

fun IrGraph.validateSemantics(): List<IrGraphDiagnostic> {
    val diagnostics = mutableListOf<IrGraphDiagnostic>()
    val fallbackSource = IrGraphSourceRef(workspaceId = id, workspaceVersion = sourceRevision.toLongOrNull() ?: 0L)
    val nodeIds = nodes.map { it.id }.toSet()
    val nodeById = nodes.associateBy { it.id }
    val edgeKeys = edges.map { Triple(it.sourceNodeId, it.targetNodeId, it.kind) }.toSet()
    val scopeIds = scopes.map { it.id }.toSet()

    fun add(code: IrGraphSemanticCode, message: String, source: IrGraphSourceRef = fallbackSource) {
        diagnostics += IrGraphDiagnostic(code = code.name, message = message, source = source)
    }

    nodes.forEach { node ->
        if (node.source.blockId.isNullOrBlank() && node.kind != IrGraphNodeKind.UNKNOWN) {
            add(code = IrGraphSemanticCode.NODE_MISSING_SOURCE_BLOCK, message = "IR node has no source block: ${node.id.value}", source = node.source)
        }
        if (node.scopePath.isEmpty() || node.scopePath.last() !in scopeIds) {
            add(code = IrGraphSemanticCode.NODE_MISSING_SCOPE, message = "IR node has no known active scope: ${node.id.value}", source = node.source)
        }
        validatePorts(node, "inputPorts", IrGraphSemanticCode.DUPLICATE_INPUT_PORT, ::add)
        validatePorts(node, "outputPorts", IrGraphSemanticCode.DUPLICATE_OUTPUT_PORT, ::add)
    }

    nodes
        .mapNotNull { node -> node.source.blockId?.let { it to node } }
        .groupBy({ it.first }, { it.second })
        .filterValues { it.size > 1 }
        .forEach { (blockId, blockNodes) ->
            add(
                code = IrGraphSemanticCode.DUPLICATE_NODE_SOURCE_BLOCK,
                message = "Multiple IR nodes reference source block $blockId: ${blockNodes.joinToString { it.id.value }}",
                source = blockNodes.first().source,
            )
        }

    edges.forEach { edge ->
        if (edge.source.blockId.isNullOrBlank()) {
            add(code = IrGraphSemanticCode.EDGE_MISSING_SOURCE_BLOCK, message = "IR edge has no source block: ${edge.id.value}", source = edge.source)
        }
    }

    branches.forEach { branch ->
        if (branch.source.blockId.isNullOrBlank()) {
            add(code = IrGraphSemanticCode.BRANCH_MISSING_SOURCE_BLOCK, message = "IR branch has no source block: ${branch.id}", source = branch.source)
        }
        if (branch.bodyEntryNodeId != null && Triple(branch.ownerNodeId, branch.bodyEntryNodeId, edgeKindForBranch(branch.role)) !in edgeKeys) {
            add(
                code = IrGraphSemanticCode.BRANCH_BODY_EDGE_MISSING,
                message = "IR branch ${branch.id} has a body entry without matching branch edge.",
                source = branch.source,
            )
        }
        if (branch.conditionNodeId != null && Triple(branch.conditionNodeId, branch.ownerNodeId, IrGraphEdgeKind.CONDITION) !in edgeKeys) {
            add(
                code = IrGraphSemanticCode.BRANCH_CONDITION_EDGE_MISSING,
                message = "IR branch ${branch.id} has a condition node without matching condition edge.",
                source = branch.source,
            )
        }
    }

    branches
        .groupBy { it.ownerNodeId to it.slotName }
        .filterValues { it.size > 1 }
        .forEach { (key, duplicateBranches) ->
            add(
                code = IrGraphSemanticCode.DUPLICATE_BRANCH_SLOT,
                message = "IR node ${key.first.value} has duplicate branch slot ${key.second}.",
                source = duplicateBranches.first().source,
            )
        }

    branches
        .groupBy { it.ownerNodeId }
        .forEach { (ownerNodeId, ownerBranches) ->
            val expected = ownerBranches.sortedWith(compareBy<IrGraphBranch> { branchRoleOrder(it.role) }.thenBy { it.index })
            if (ownerBranches != expected) {
                add(
                    code = IrGraphSemanticCode.BRANCH_INDEX_OUT_OF_ORDER,
                    message = "IR node ${ownerNodeId.value} branches are not in deterministic display order.",
                    source = nodeById[ownerNodeId]?.source ?: ownerBranches.first().source,
                )
            }
        }

    facets.forEach { facet ->
        if (facet.source.blockId.isNullOrBlank() && facet.ownerNodeId != null) {
            add(code = IrGraphSemanticCode.FACET_MISSING_SOURCE_BLOCK, message = "IR facet has no source block: ${facet.id}", source = facet.source)
        }
        facet.nodeIds.filterNot { it in nodeIds }.forEach {
            add(code = IrGraphSemanticCode.FACET_MISSING_SOURCE_BLOCK, message = "IR facet ${facet.id} references missing node ${it.value}.", source = facet.source)
        }
    }

    return diagnostics
}

private fun validatePorts(
    node: IrGraphNode,
    propertyKey: String,
    code: IrGraphSemanticCode,
    addDiagnostic: (IrGraphSemanticCode, String, IrGraphSourceRef) -> Unit,
) {
    val encoded = node.properties[propertyKey].orEmpty()
    if (encoded.isBlank()) return
    encoded
        .split("|")
        .mapNotNull { it.split("~").firstOrNull()?.replace("%7C", "|")?.replace("%7E", "~") }
        .filter { it.isNotBlank() }
        .groupBy { it }
        .filterValues { it.size > 1 }
        .keys
        .forEach { portName ->
            addDiagnostic(code, "IR node ${node.id.value} has duplicate $propertyKey port $portName.", node.source)
        }
}

private fun edgeKindForBranch(role: IrGraphBranchRole): IrGraphEdgeKind =
    when (role) {
        IrGraphBranchRole.THEN -> IrGraphEdgeKind.TRUE_BRANCH
        IrGraphBranchRole.ELSE_IF -> IrGraphEdgeKind.ELSE_IF_BRANCH
        IrGraphBranchRole.ELSE -> IrGraphEdgeKind.FALSE_BRANCH
        IrGraphBranchRole.LOOP_BODY -> IrGraphEdgeKind.LOOP_BODY
    }

private fun branchRoleOrder(role: IrGraphBranchRole): Int =
    when (role) {
        IrGraphBranchRole.THEN -> 0
        IrGraphBranchRole.ELSE_IF -> 1
        IrGraphBranchRole.ELSE -> 2
        IrGraphBranchRole.LOOP_BODY -> 3
    }
