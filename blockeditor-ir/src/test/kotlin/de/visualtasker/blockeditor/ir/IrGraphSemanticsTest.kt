package de.visualtasker.blockeditor.ir

import org.junit.Assert.assertTrue
import org.junit.Test

class IrGraphSemanticsTest {
    @Test
    fun validateSemanticsReportsMissingSourceMappingDuplicatePortsAndBranchGaps() {
        val source = IrGraphSourceRef(workspaceId = "semantic", workspaceVersion = 1, blockId = "owner")
        val value = IrGraphNode(
            id = IrGraphNodeId("block:value"),
            kind = IrGraphNodeKind.VALUE,
            label = "VALUE",
            scopePath = listOf("scope:main"),
            source = source.copy(blockId = "value"),
            properties = mapOf("outputPorts" to "output~Output~DATA_FLOW|output~Output~DATA_FLOW"),
        )
        val owner = IrGraphNode(
            id = IrGraphNodeId("block:owner"),
            kind = IrGraphNodeKind.DECISION,
            label = "IF",
            scopePath = listOf("scope:missing"),
            source = source,
            properties = mapOf("inputPorts" to "CONDITION~Condition~CONDITION|CONDITION~Condition~CONDITION"),
        )
        val branch = IrGraphBranch(
            id = "branch:owner:then",
            ownerNodeId = owner.id,
            role = IrGraphBranchRole.THEN,
            index = 0,
            slotName = "THEN",
            scopeId = "scope:then",
            conditionNodeId = value.id,
            bodyEntryNodeId = IrGraphNodeId("block:body"),
            source = source.copy(slotName = "THEN"),
        )
        val graph = IrGraph(
            id = "semantic",
            sourceRevision = "1",
            entryNodeIds = listOf(owner.id),
            nodes = listOf(value, owner),
            edges = emptyList(),
            scopes = listOf(
                IrGraphScope(
                    id = "scope:main",
                    kind = IrGraphScopeKind.SCRIPT,
                    parentId = null,
                    label = "Main",
                    source = source,
                ),
                IrGraphScope(
                    id = "scope:then",
                    kind = IrGraphScopeKind.BRANCH,
                    parentId = "scope:main",
                    label = "Then",
                    source = source.copy(slotName = "THEN"),
                ),
            ),
            branches = listOf(branch),
        )

        val codes = graph.validateSemantics().map { it.code }.toSet()

        assertTrue(IrGraphSemanticCode.NODE_MISSING_SCOPE.name in codes)
        assertTrue(IrGraphSemanticCode.DUPLICATE_INPUT_PORT.name in codes)
        assertTrue(IrGraphSemanticCode.DUPLICATE_OUTPUT_PORT.name in codes)
        assertTrue(IrGraphSemanticCode.BRANCH_BODY_EDGE_MISSING.name in codes)
        assertTrue(IrGraphSemanticCode.BRANCH_CONDITION_EDGE_MISSING.name in codes)
    }
}
