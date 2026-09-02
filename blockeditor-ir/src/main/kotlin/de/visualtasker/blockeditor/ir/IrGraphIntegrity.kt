package de.visualtasker.blockeditor.ir

enum class IrGraphIntegrityCode {
    DUPLICATE_NODE_ID,
    DUPLICATE_EDGE_ID,
    UNKNOWN_EDGE_SOURCE,
    UNKNOWN_EDGE_TARGET,
    UNKNOWN_ENTRY_NODE,
    UNKNOWN_BRANCH_OWNER,
    UNKNOWN_BRANCH_CONDITION,
    UNKNOWN_BRANCH_BODY,
    UNKNOWN_BRANCH_SCOPE,
    UNKNOWN_FACET_OWNER,
    UNKNOWN_FACET_NODE,
    UNKNOWN_FACET_SCOPE,
    UNKNOWN_SCOPE_PARENT,
}

fun IrGraph.validateIntegrity(): List<IrGraphDiagnostic> {
    val fallbackSource = IrGraphSourceRef(workspaceId = id, workspaceVersion = sourceRevision.toLongOrNull() ?: 0L)
    val nodeIds = nodes.map { it.id }.toSet()
    val edgeIds = edges.map { it.id }.toSet()
    val scopeIds = scopes.map { it.id }.toSet()
    val diagnostics = mutableListOf<IrGraphDiagnostic>()

    fun add(code: IrGraphIntegrityCode, message: String, source: IrGraphSourceRef = fallbackSource) {
        diagnostics += IrGraphDiagnostic(code = code.name, message = message, source = source)
    }

    nodes.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { id ->
        add(IrGraphIntegrityCode.DUPLICATE_NODE_ID, "Duplicate IR node id: ${id.value}")
    }
    edges.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { id ->
        add(IrGraphIntegrityCode.DUPLICATE_EDGE_ID, "Duplicate IR edge id: ${id.value}")
    }
    entryNodeIds.filterNot { it in nodeIds }.forEach { id ->
        add(IrGraphIntegrityCode.UNKNOWN_ENTRY_NODE, "Entry node does not exist: ${id.value}")
    }
    edges.forEach { edge ->
        if (edge.id !in edgeIds) add(IrGraphIntegrityCode.DUPLICATE_EDGE_ID, "Invalid IR edge id: ${edge.id.value}", edge.source)
        if (edge.sourceNodeId !in nodeIds) {
            add(IrGraphIntegrityCode.UNKNOWN_EDGE_SOURCE, "Edge source does not exist: ${edge.sourceNodeId.value}", edge.source)
        }
        if (edge.targetNodeId !in nodeIds) {
            add(IrGraphIntegrityCode.UNKNOWN_EDGE_TARGET, "Edge target does not exist: ${edge.targetNodeId.value}", edge.source)
        }
    }
    scopes.forEach { scope ->
        val parentId = scope.parentId
        if (parentId != null && parentId !in scopeIds) {
            add(IrGraphIntegrityCode.UNKNOWN_SCOPE_PARENT, "Scope parent does not exist: $parentId", scope.source)
        }
    }
    branches.forEach { branch ->
        if (branch.ownerNodeId !in nodeIds) {
            add(IrGraphIntegrityCode.UNKNOWN_BRANCH_OWNER, "Branch owner does not exist: ${branch.ownerNodeId.value}", branch.source)
        }
        val conditionNodeId = branch.conditionNodeId
        if (conditionNodeId != null && conditionNodeId !in nodeIds) {
            add(IrGraphIntegrityCode.UNKNOWN_BRANCH_CONDITION, "Branch condition does not exist: ${conditionNodeId.value}", branch.source)
        }
        val bodyEntryNodeId = branch.bodyEntryNodeId
        if (bodyEntryNodeId != null && bodyEntryNodeId !in nodeIds) {
            add(IrGraphIntegrityCode.UNKNOWN_BRANCH_BODY, "Branch body entry does not exist: ${bodyEntryNodeId.value}", branch.source)
        }
        if (branch.scopeId !in scopeIds) {
            add(IrGraphIntegrityCode.UNKNOWN_BRANCH_SCOPE, "Branch scope does not exist: ${branch.scopeId}", branch.source)
        }
    }
    facets.forEach { facet ->
        val ownerNodeId = facet.ownerNodeId
        if (ownerNodeId != null && ownerNodeId !in nodeIds) {
            add(IrGraphIntegrityCode.UNKNOWN_FACET_OWNER, "Facet owner does not exist: ${ownerNodeId.value}", facet.source)
        }
        val scopeId = facet.scopeId
        if (scopeId != null && scopeId !in scopeIds) {
            add(IrGraphIntegrityCode.UNKNOWN_FACET_SCOPE, "Facet scope does not exist: $scopeId", facet.source)
        }
        facet.nodeIds.filterNot { it in nodeIds }.forEach { nodeId ->
            add(IrGraphIntegrityCode.UNKNOWN_FACET_NODE, "Facet node does not exist: ${nodeId.value}", facet.source)
        }
    }
    return diagnostics
}
