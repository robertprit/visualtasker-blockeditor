package de.visualtasker.blockeditor.ir

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.FieldValue
import de.visualtasker.blockeditor.domain.VariableDefinition
import de.visualtasker.blockeditor.domain.VariableScope
import de.visualtasker.blockeditor.domain.WorkspaceAction
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.WorkspaceReducer
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.CompositeBlockRegistry
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry
import de.visualtasker.blockeditor.registry.VariableReporterFactory
import de.visualtasker.blockeditor.registry.asFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IrGraphGeneratorTest {
    @Test
    fun generate_buildsStableNodesScopesBranchesAndDataFlow() {
        val document = referenceIfElseDocument()

        val graph = IrGraphGenerator(referenceRegistry()).generate(document)

        assertTrue(graph.diagnostics.joinToString { it.message }, graph.diagnostics.isEmpty())
        assertTrue(graph.validateIntegrity().joinToString { it.message }, graph.validateIntegrity().isEmpty())
        assertTrue(graph.validateSemantics().joinToString { it.message }, graph.validateSemantics().isEmpty())
        assertEquals(graph.nodes.size, graph.nodes.map { it.id }.distinct().size)
        assertEquals(graph.edges.size, graph.edges.map { it.id }.distinct().size)
        assertEquals(listOf(IrGraphNodeId("block:start")), graph.entryNodeIds)
        assertTrue(graph.nodes.any { it.id == IrGraphNodeId("block:if") && it.kind == IrGraphNodeKind.DECISION })
        assertTrue(graph.nodes.any { it.id == IrGraphNodeId("block:then") && "branch:THEN" in it.scopePath })
        assertTrue(graph.nodes.any { it.id == IrGraphNodeId("block:elif") && "branch:ELIF" in it.scopePath })
        assertTrue(graph.nodes.any { it.id == IrGraphNodeId("block:else") && "branch:ELSE" in it.scopePath })
        assertTrue(graph.scopes.any { it.id == "script:start" && it.kind == IrGraphScopeKind.SCRIPT })
        assertTrue(graph.branches.any { it.ownerNodeId == IrGraphNodeId("block:if") && it.role == IrGraphBranchRole.THEN && it.bodyEntryNodeId == IrGraphNodeId("block:then") })
        assertTrue(graph.branches.any { it.ownerNodeId == IrGraphNodeId("block:if") && it.role == IrGraphBranchRole.ELSE_IF })
        assertTrue(graph.branches.any { it.ownerNodeId == IrGraphNodeId("block:if") && it.role == IrGraphBranchRole.ELSE && it.bodyEntryNodeId == IrGraphNodeId("block:else") })
        assertTrue(graph.facets.any { it.kind == IrGraphFacetKind.BRANCH_REGION && IrGraphNodeId("block:elif") in it.nodeIds })
        assertTrue(graph.edges.any { it.sourceNodeId.value == "block:start" && it.targetNodeId.value == "block:if" && it.kind == IrGraphEdgeKind.SEQUENCE })
        assertTrue(graph.edges.any { it.sourceNodeId.value == "block:if" && it.targetNodeId.value == "block:then" && it.kind == IrGraphEdgeKind.TRUE_BRANCH })
        assertTrue(graph.edges.any { it.sourceNodeId.value == "block:if" && it.targetNodeId.value == "block:elif" && it.kind == IrGraphEdgeKind.ELSE_IF_BRANCH })
        assertTrue(graph.edges.any { it.sourceNodeId.value == "block:if" && it.targetNodeId.value == "block:else" && it.kind == IrGraphEdgeKind.FALSE_BRANCH })
        assertTrue(graph.edges.any { it.sourceNodeId.value == "block:compare" && it.targetNodeId.value == "block:if" && it.kind == IrGraphEdgeKind.CONDITION })
        assertTrue(graph.edges.any { it.sourceNodeId.value == "block:v1" && it.targetNodeId.value == "block:compare" && it.kind == IrGraphEdgeKind.DATA_FLOW && it.label == "LEFT" })
        assertTrue(graph.edges.any { it.source.blockId == "if" && it.source.slotName == "THEN" && it.source.branch?.role == IrGraphBranchRole.THEN })
        val waitNode = graph.nodes.single { it.id == IrGraphNodeId("block:then") }
        assertEquals("action.wait", waitNode.properties["commandId"])
        assertEquals("wait", waitNode.properties["commandName"])
        assertEquals("STATEMENT", waitNode.properties["commandKind"])
        assertTrue(waitNode.properties["commandCapabilities"]!!.contains("TIMING"))
        val compareNode = graph.nodes.single { it.id == IrGraphNodeId("block:compare") }
        assertEquals("logic.compare", compareNode.properties["commandId"])
        assertEquals("OPERATOR", compareNode.properties["commandKind"])
    }

    @Test
    fun validateIntegrity_reportsBrokenReferences() {
        val source = IrGraphSourceRef(workspaceId = "broken", workspaceVersion = 1)
        val graph = IrGraph(
            id = "broken",
            sourceRevision = "1",
            entryNodeIds = listOf(IrGraphNodeId("missing-entry")),
            nodes = listOf(
                IrGraphNode(
                    id = IrGraphNodeId("node:a"),
                    kind = IrGraphNodeKind.ACTION,
                    label = "A",
                    scopePath = listOf("scope:a"),
                    source = source,
                ),
            ),
            edges = listOf(
                IrGraphEdge(
                    id = IrGraphEdgeId("edge:broken"),
                    sourceNodeId = IrGraphNodeId("node:a"),
                    targetNodeId = IrGraphNodeId("node:missing"),
                    kind = IrGraphEdgeKind.SEQUENCE,
                    source = source,
                ),
            ),
            scopes = listOf(
                IrGraphScope(
                    id = "scope:a",
                    kind = IrGraphScopeKind.SCRIPT,
                    parentId = "scope:missing",
                    label = "A",
                    source = source,
                ),
            ),
            branches = listOf(
                IrGraphBranch(
                    id = "branch:broken",
                    ownerNodeId = IrGraphNodeId("node:missing-owner"),
                    role = IrGraphBranchRole.THEN,
                    index = 0,
                    slotName = "THEN",
                    scopeId = "scope:missing-branch",
                    conditionNodeId = IrGraphNodeId("node:missing-condition"),
                    bodyEntryNodeId = IrGraphNodeId("node:missing-body"),
                    source = source,
                ),
            ),
            facets = listOf(
                IrGraphFacet(
                    id = "facet:broken",
                    kind = IrGraphFacetKind.COLLAPSE_GROUP,
                    label = "Broken",
                    scopeId = "scope:missing-facet",
                    ownerNodeId = IrGraphNodeId("node:missing-owner"),
                    nodeIds = listOf(IrGraphNodeId("node:missing-facet-node")),
                    source = source,
                ),
            ),
        )

        val codes = graph.validateIntegrity().map { it.code }.toSet()

        assertTrue(IrGraphIntegrityCode.UNKNOWN_ENTRY_NODE.name in codes)
        assertTrue(IrGraphIntegrityCode.UNKNOWN_EDGE_TARGET.name in codes)
        assertTrue(IrGraphIntegrityCode.UNKNOWN_SCOPE_PARENT.name in codes)
        assertTrue(IrGraphIntegrityCode.UNKNOWN_BRANCH_OWNER.name in codes)
        assertTrue(IrGraphIntegrityCode.UNKNOWN_BRANCH_CONDITION.name in codes)
        assertTrue(IrGraphIntegrityCode.UNKNOWN_BRANCH_BODY.name in codes)
        assertTrue(IrGraphIntegrityCode.UNKNOWN_BRANCH_SCOPE.name in codes)
        assertTrue(IrGraphIntegrityCode.UNKNOWN_FACET_OWNER.name in codes)
        assertTrue(IrGraphIntegrityCode.UNKNOWN_FACET_SCOPE.name in codes)
        assertTrue(IrGraphIntegrityCode.UNKNOWN_FACET_NODE.name in codes)
    }

    private fun referenceIfElseDocument(): WorkspaceDocument {
        val registry = referenceRegistry()
        val factory = registry.asFactory()
        var document = WorkspaceDocument(id = "irgraph-reference")
        listOf(
            VariableDefinition("v1", "v1", "Number", VariableScope.Global),
            VariableDefinition("v2", "v2", "Number", VariableScope.Global),
        ).forEach { variable ->
            document = WorkspaceReducer.reduce(document, WorkspaceAction.CreateVariable(variable), factory)
        }

        fun instantiate(id: String, type: String): BlockId {
            val before = document.blocks.keys
            document = WorkspaceReducer.reduce(document, WorkspaceAction.InstantiateBlock(type, 64f, 64f), factory)
            val created = (document.blocks.keys - before).single()
            document = document.copy(
                blocks = document.blocks - created + (BlockId(id) to document.blocks.getValue(created).copy(id = BlockId(id))),
                rootBlocks = document.rootBlocks.map { if (it == created) BlockId(id) else it },
                rootPositions = document.rootPositions - created + (BlockId(id) to document.rootPositions.getValue(created)),
            )
            return BlockId(id)
        }

        val start = instantiate("start", BlockTypes.EVENT_START)
        val ifBlock = instantiate("if", BlockTypes.CONTROL_IF_ELSEIF_ELSE)
        val compare = instantiate("compare", BlockTypes.LOGIC_COMPARE)
        val v1 = instantiate("v1", VariableReporterFactory.reporterId("v1"))
        val v2 = instantiate("v2", VariableReporterFactory.reporterId("v2"))
        val thenBlock = instantiate("then", BlockTypes.ACTION_WAIT)
        val elifBlock = instantiate("elif", BlockTypes.ACTION_CLICK_TEXT)
        val elseBlock = instantiate("else", BlockTypes.DEBUG_LOG)

        fun connect(source: BlockId, sourceKey: String, target: BlockId, targetKey: String) {
            document = WorkspaceReducer.reduce(
                document,
                WorkspaceAction.Connect(
                    source = document.connectionId(source, sourceKey),
                    target = document.connectionId(target, targetKey),
                ),
                factory,
            )
        }

        document = WorkspaceReducer.reduce(document, WorkspaceAction.UpdateField(compare, "operator", FieldValue.Text("GREATER_OR_EQUAL")), factory)
        document = WorkspaceReducer.reduce(document, WorkspaceAction.UpdateField(v1, "variable", FieldValue.Text("v1")), factory)
        document = WorkspaceReducer.reduce(document, WorkspaceAction.UpdateField(v1, "variableLabel", FieldValue.Text("v1")), factory)
        document = WorkspaceReducer.reduce(document, WorkspaceAction.UpdateField(v2, "variable", FieldValue.Text("v2")), factory)
        document = WorkspaceReducer.reduce(document, WorkspaceAction.UpdateField(v2, "variableLabel", FieldValue.Text("v2")), factory)

        connect(start, "next", ifBlock, "previous")
        connect(compare, "output", ifBlock, "CONDITION")
        connect(v1, "output", compare, "LEFT")
        connect(v2, "output", compare, "RIGHT")
        connect(ifBlock, BlockTypes.SLOT_THEN, thenBlock, "previous")
        connect(ifBlock, BlockTypes.SLOT_ELIF, elifBlock, "previous")
        connect(ifBlock, BlockTypes.SLOT_ELSE, elseBlock, "previous")
        return document
    }

    private fun referenceRegistry(): CompositeBlockRegistry {
        val registry = CompositeBlockRegistry(DefaultBlockRegistry)
        registry.register(VariableReporterFactory.create(VariableDefinition("v1", "v1", "Number", VariableScope.Global)))
        registry.register(VariableReporterFactory.create(VariableDefinition("v2", "v2", "Number", VariableScope.Global)))
        return registry
    }

    private fun WorkspaceDocument.connectionId(
        blockId: BlockId,
        key: String,
    ): de.visualtasker.blockeditor.domain.ConnectionId {
        val block = blocks.getValue(blockId)
        return when (key) {
            "previous" -> block.previous!!.id
            "next" -> block.next!!.id
            "output" -> block.output!!.id
            else -> block.valueInputs.firstOrNull { it.name == key }?.connection?.id
                ?: block.statementInputs.first { it.name == key }.connection.id
        }
    }
}
