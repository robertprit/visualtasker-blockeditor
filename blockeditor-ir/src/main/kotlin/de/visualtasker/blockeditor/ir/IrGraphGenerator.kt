package de.visualtasker.blockeditor.ir

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.BlockNode
import de.visualtasker.blockeditor.domain.ConnectionKind
import de.visualtasker.blockeditor.domain.FieldValue
import de.visualtasker.blockeditor.domain.NormalizedOperator
import de.visualtasker.blockeditor.domain.OperatorNormalization
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.WorkspaceGraph
import de.visualtasker.blockeditor.domain.asString
import de.visualtasker.blockeditor.registry.BlockRegistry
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.CommandCatalogEntry
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry
import de.visualtasker.blockeditor.registry.VisualTaskerCommandCatalog

class IrGraphGenerator(
    private val registry: BlockRegistry = DefaultBlockRegistry,
) {
    fun generate(document: WorkspaceDocument): IrGraph {
        val diagnostics = mutableListOf<IrGraphDiagnostic>()
        val nodes = linkedMapOf<IrGraphNodeId, IrGraphNode>()
        val edges = linkedMapOf<IrGraphEdgeId, IrGraphEdge>()
        val scopes = linkedMapOf<String, IrGraphScope>()
        val branches = linkedMapOf<String, IrGraphBranch>()
        val visited = mutableSetOf<BlockId>()
        val entryNodeIds = mutableListOf<IrGraphNodeId>()

        document.rootBlocks.forEachIndexed { index, rootId ->
            val root = document.blocks[rootId] ?: return@forEachIndexed
            if (root.type == BlockTypes.EVENT_START) {
                val scopeId = "script:${root.id.value}"
                registerScope(
                    scopes = scopes,
                    id = scopeId,
                    kind = IrGraphScopeKind.SCRIPT,
                    parentId = null,
                    label = "Script ${root.id.value}",
                    source = sourceRef(document, root.id),
                )
                val scopePath = listOf(scopeId)
                entryNodeIds += rootId.irNodeId()
                walkBlock(
                    document = document,
                    blockId = rootId,
                    scopePath = scopePath,
                    scopeId = scopeId,
                    nodes = nodes,
                    edges = edges,
                    scopes = scopes,
                    branches = branches,
                    diagnostics = diagnostics,
                    visited = visited,
                )
            } else {
                val scopeId = "orphan-root:$index"
                registerScope(
                    scopes = scopes,
                    id = scopeId,
                    kind = IrGraphScopeKind.ORPHAN_ROOT,
                    parentId = null,
                    label = "Orphan Root $index",
                    source = sourceRef(document, root.id),
                )
                val scopePath = listOf(scopeId)
                walkBlock(
                    document = document,
                    blockId = rootId,
                    scopePath = scopePath,
                    scopeId = scopeId,
                    nodes = nodes,
                    edges = edges,
                    scopes = scopes,
                    branches = branches,
                    diagnostics = diagnostics,
                    visited = visited,
                )
            }
        }

        document.blocks.keys
            .filterNot { it in visited }
            .sortedBy { it.value }
            .forEach { blockId ->
                val scopeId = "unreachable"
                registerScope(
                    scopes = scopes,
                    id = scopeId,
                    kind = IrGraphScopeKind.UNREACHABLE,
                    parentId = null,
                    label = "Unreachable",
                    source = sourceRef(document, blockId),
                )
                walkBlock(
                    document = document,
                    blockId = blockId,
                    scopePath = listOf(scopeId),
                    scopeId = scopeId,
                    nodes = nodes,
                    edges = edges,
                    scopes = scopes,
                    branches = branches,
                    diagnostics = diagnostics,
                    visited = visited,
                )
            }

        return IrGraph(
            id = "ir:${document.id}",
            sourceRevision = document.version.toString(),
            entryNodeIds = entryNodeIds,
            nodes = nodes.values.toList(),
            edges = edges.values.toList(),
            diagnostics = diagnostics,
            scopes = scopes.values.toList(),
            branches = branches.values.toList(),
            facets = buildFacets(document, nodes.values.toList(), branches.values.toList()),
        )
    }

    private fun walkBlock(
        document: WorkspaceDocument,
        blockId: BlockId,
        scopePath: List<String>,
        scopeId: String,
        nodes: MutableMap<IrGraphNodeId, IrGraphNode>,
        edges: MutableMap<IrGraphEdgeId, IrGraphEdge>,
        scopes: MutableMap<String, IrGraphScope>,
        branches: MutableMap<String, IrGraphBranch>,
        diagnostics: MutableList<IrGraphDiagnostic>,
        visited: MutableSet<BlockId>,
    ) {
        val block = document.blocks[blockId] ?: return
        val node = irNode(document, block, scopePath, diagnostics)
        nodes[node.id] = node
        if (!visited.add(blockId)) return

        block.valueInputs.forEach { input ->
            val connected = input.connection.connectedTo ?: return@forEach
            val (valueBlockId, connection) = WorkspaceGraph.findConnection(document, connected) ?: return@forEach
            if (connection.kind != ConnectionKind.Output) return@forEach
            val valueScopeId = "$scopeId/value:${blockId.value}:${input.name}"
            registerScope(
                scopes = scopes,
                id = valueScopeId,
                kind = IrGraphScopeKind.VALUE,
                parentId = scopeId,
                label = input.name,
                source = sourceRef(document, blockId, input.name),
            )
            walkBlock(
                document = document,
                blockId = valueBlockId,
                scopePath = scopePath + "value:${input.name}" + valueScopeId,
                scopeId = valueScopeId,
                nodes = nodes,
                edges = edges,
                scopes = scopes,
                branches = branches,
                diagnostics = diagnostics,
                visited = visited,
            )
            putEdge(
                edges = edges,
                source = valueBlockId.irNodeId(),
                target = blockId.irNodeId(),
                kind = if (input.name.endsWith("CONDITION")) IrGraphEdgeKind.CONDITION else IrGraphEdgeKind.DATA_FLOW,
                label = input.name,
                sourceRef = sourceRef(document, blockId, input.name),
            )
        }

        block.statementInputs.forEachIndexed { branchIndex, input ->
            val head = WorkspaceGraph.statementStackHead(document, blockId, input.name) ?: return@forEachIndexed
            val kind = edgeKindForStatementSlot(input.name)
            val role = branchRoleForStatementSlot(input.name)
            val branchRef = IrGraphBranchRef(
                id = "branch:${blockId.value}:${role.name.lowercase()}:$branchIndex:${input.name}",
                ownerBlockId = blockId.value,
                role = role,
                index = branchIndex,
                slotName = input.name,
            )
            registerScope(
                scopes = scopes,
                id = branchRef.id,
                kind = IrGraphScopeKind.BRANCH,
                parentId = scopeId,
                label = branchLabel(role, branchIndex),
                source = sourceRef(document, blockId, input.name, branchRef),
            )
            branches[branchRef.id] = IrGraphBranch(
                id = branchRef.id,
                ownerNodeId = blockId.irNodeId(),
                role = role,
                index = branchIndex,
                slotName = input.name,
                scopeId = branchRef.id,
                conditionNodeId = conditionNodeForBranch(document, block, role),
                bodyEntryNodeId = head.irNodeId(),
                source = sourceRef(document, blockId, input.name, branchRef),
            )
            walkBlock(
                document = document,
                blockId = head,
                scopePath = scopePath + "branch:${input.name}" + branchRef.id,
                scopeId = branchRef.id,
                nodes = nodes,
                edges = edges,
                scopes = scopes,
                branches = branches,
                diagnostics = diagnostics,
                visited = visited,
            )
            putEdge(
                edges = edges,
                source = blockId.irNodeId(),
                target = head.irNodeId(),
                kind = kind,
                label = input.name,
                sourceRef = sourceRef(document, blockId, input.name, branchRef),
            )
        }

        WorkspaceGraph.nextChain(document, blockId)?.let { nextId ->
            walkBlock(
                document = document,
                blockId = nextId,
                scopePath = scopePath,
                scopeId = scopeId,
                nodes = nodes,
                edges = edges,
                scopes = scopes,
                branches = branches,
                diagnostics = diagnostics,
                visited = visited,
            )
            putEdge(
                edges = edges,
                source = blockId.irNodeId(),
                target = nextId.irNodeId(),
                kind = if (block.type == BlockTypes.CONTROL_REPEAT || block.type == BlockTypes.CONTROL_WHILE) {
                    IrGraphEdgeKind.LOOP_EXIT
                } else {
                    IrGraphEdgeKind.SEQUENCE
                },
                label = null,
                sourceRef = sourceRef(document, blockId, "next"),
            )
        }
    }

    private fun irNode(
        document: WorkspaceDocument,
        block: BlockNode,
        scopePath: List<String>,
        diagnostics: MutableList<IrGraphDiagnostic>,
    ): IrGraphNode {
        val unsupported = registry.getDefinition(block.type) == null &&
            !block.type.startsWith(BlockTypes.VARIABLE_REPORTER_PREFIX)
        if (unsupported) {
            diagnostics += IrGraphDiagnostic(
                code = "UNKNOWN_BLOCK_TYPE",
                message = "Unknown block type: ${block.type}",
                source = sourceRef(document, block.id),
            )
        }
        val command = VisualTaskerCommandCatalog.findByBlockType(block.type)
        return IrGraphNode(
            id = block.id.irNodeId(),
            kind = nodeKind(block),
            label = nodeLabel(block),
            scopePath = scopePath,
            source = sourceRef(document, block.id),
            properties = buildMap {
                put("blockType", block.type)
                put("blockId", block.id.value)
                put("scopeId", scopePath.lastOrNull().orEmpty())
                putCommandProperties(command)
                if (block.collapsed) put("collapsed", "true")
                block.metadata["emscript.source.line"]?.let { put("sourceLine", it) }
                block.metadata["emscript.source.column"]?.let { put("sourceColumn", it) }
            },
        )
    }

    private fun MutableMap<String, String>.putCommandProperties(command: CommandCatalogEntry?) {
        command ?: return
        put("commandId", command.id)
        put("commandName", command.canonicalName)
        put("commandKind", command.kind.name)
        put("commandPluginOwner", command.pluginOwner)
        put("commandCapabilities", command.capabilities.joinToString(",") { it.name })
    }

    private fun nodeKind(block: BlockNode): IrGraphNodeKind = when (block.type) {
        BlockTypes.EVENT_START -> IrGraphNodeKind.SCRIPT_ENTRY
        BlockTypes.ACTION_CLICK_TEXT,
        BlockTypes.ACTION_WAIT,
        BlockTypes.DEBUG_LOG,
        BlockTypes.FEEDBACK_BEEP,
        BlockTypes.FEEDBACK_VIBRATE -> IrGraphNodeKind.ACTION
        BlockTypes.VARIABLE_SET,
        BlockTypes.LOGIC_OPERATE -> IrGraphNodeKind.ASSIGNMENT
        BlockTypes.CONTROL_IF,
        BlockTypes.CONTROL_IF_ELSE,
        BlockTypes.CONTROL_IF_ELSEIF_ELSE,
        BlockTypes.LOGIC_COMPARE,
        BlockTypes.LOGIC_BOOLEAN,
        BlockTypes.LOGIC_AND,
        BlockTypes.LOGIC_OR -> IrGraphNodeKind.DECISION
        BlockTypes.CONTROL_REPEAT,
        BlockTypes.CONTROL_WHILE -> IrGraphNodeKind.LOOP
        BlockTypes.LITERAL_NUMBER,
        BlockTypes.LITERAL_STRING,
        BlockTypes.LITERAL_BOOLEAN,
        BlockTypes.VARIABLE_GET,
        BlockTypes.VARIABLE_REPORTER,
        BlockTypes.VARIABLE_VALUE,
        BlockTypes.VARIABLES_GET -> IrGraphNodeKind.VALUE
        else -> if (block.type.startsWith(BlockTypes.VARIABLE_REPORTER_PREFIX)) IrGraphNodeKind.VALUE else IrGraphNodeKind.UNKNOWN
    }

    private fun nodeLabel(block: BlockNode): String = when (block.type) {
        BlockTypes.EVENT_START -> "START"
        BlockTypes.ACTION_CLICK_TEXT -> "CLICK \"${block.fieldText("text")}\""
        BlockTypes.ACTION_WAIT -> "WAIT ${block.fieldNumber("ms").toLong()}ms"
        BlockTypes.DEBUG_LOG -> "LOG \"${block.fieldText("message")}\""
        BlockTypes.FEEDBACK_BEEP -> {
            val frequency = block.fieldNumber("frequency").toLong()
            val duration = block.fieldNumber("durationMs").toLong()
            val volume = block.fieldNumber("volume").toLong()
            "BEEP ${frequency}Hz ${duration}ms ${volume}%"
        }
        BlockTypes.FEEDBACK_VIBRATE -> "VIBRATE ${block.fieldText("pattern").ifBlank { "80" }}"
        BlockTypes.CONTROL_REPEAT -> "REPEAT ${block.fieldNumber("times").toLong()}x"
        BlockTypes.CONTROL_WHILE -> "WHILE"
        BlockTypes.CONTROL_IF,
        BlockTypes.CONTROL_IF_ELSE,
        BlockTypes.CONTROL_IF_ELSEIF_ELSE -> "IF"
        BlockTypes.LOGIC_COMPARE -> "COMPARE ${operatorSymbol(block)}"
        BlockTypes.LOGIC_OPERATE -> operatorLabel(block)
        BlockTypes.LITERAL_NUMBER -> "NUM ${block.fieldNumber("value")}"
        BlockTypes.LITERAL_STRING -> "STR \"${block.fieldText("value")}\""
        BlockTypes.LITERAL_BOOLEAN,
        BlockTypes.LOGIC_BOOLEAN -> "BOOL ${block.fieldBool("value").toString().uppercase()}"
        BlockTypes.LOGIC_AND -> "AND"
        BlockTypes.LOGIC_OR -> "OR"
        BlockTypes.VARIABLE_SET -> "${block.fieldText("assignmentKind").ifBlank { "SET" }} ${block.fieldText("variable")}"
        else -> if (block.type.startsWith(BlockTypes.VARIABLE_REPORTER_PREFIX)) {
            block.fieldText("variableLabel").ifBlank { block.fieldText("variable") }
        } else {
            block.type
        }
    }

    private fun putEdge(
        edges: MutableMap<IrGraphEdgeId, IrGraphEdge>,
        source: IrGraphNodeId,
        target: IrGraphNodeId,
        kind: IrGraphEdgeKind,
        label: String?,
        sourceRef: IrGraphSourceRef,
    ) {
        val id = IrGraphEdgeId("edge:${source.value}|${target.value}|${kind.name}|${label.orEmpty()}")
        edges.putIfAbsent(
            id,
            IrGraphEdge(
                id = id,
                sourceNodeId = source,
                targetNodeId = target,
                kind = kind,
                label = label,
                source = sourceRef,
            ),
        )
    }

    private fun edgeKindForStatementSlot(slotName: String): IrGraphEdgeKind = when (slotName) {
        BlockTypes.SLOT_THEN -> IrGraphEdgeKind.TRUE_BRANCH
        BlockTypes.SLOT_ELSE -> IrGraphEdgeKind.FALSE_BRANCH
        BlockTypes.SLOT_ELIF -> IrGraphEdgeKind.ELSE_IF_BRANCH
        BlockTypes.SLOT_DO,
        BlockTypes.SLOT_BODY -> IrGraphEdgeKind.LOOP_BODY
        else -> IrGraphEdgeKind.SEQUENCE
    }

    private fun branchRoleForStatementSlot(slotName: String): IrGraphBranchRole = when (slotName) {
        BlockTypes.SLOT_THEN -> IrGraphBranchRole.THEN
        BlockTypes.SLOT_ELSE -> IrGraphBranchRole.ELSE
        BlockTypes.SLOT_ELIF -> IrGraphBranchRole.ELSE_IF
        BlockTypes.SLOT_DO,
        BlockTypes.SLOT_BODY -> IrGraphBranchRole.LOOP_BODY
        else -> IrGraphBranchRole.THEN
    }

    private fun branchLabel(role: IrGraphBranchRole, index: Int): String = when (role) {
        IrGraphBranchRole.THEN -> "Then"
        IrGraphBranchRole.ELSE_IF -> "Else If ${index + 1}"
        IrGraphBranchRole.ELSE -> "Else"
        IrGraphBranchRole.LOOP_BODY -> "Loop Body"
    }

    private fun conditionNodeForBranch(
        document: WorkspaceDocument,
        block: BlockNode,
        role: IrGraphBranchRole,
    ): IrGraphNodeId? {
        val inputName = when (role) {
            IrGraphBranchRole.ELSE_IF -> "ELIF_CONDITION"
            IrGraphBranchRole.THEN,
            IrGraphBranchRole.ELSE,
            IrGraphBranchRole.LOOP_BODY -> "CONDITION"
        }
        val connected = block.valueInputs.firstOrNull { it.name == inputName }?.connection?.connectedTo ?: return null
        val (conditionBlockId, connection) = WorkspaceGraph.findConnection(document, connected) ?: return null
        return conditionBlockId.irNodeId().takeIf { connection.kind == ConnectionKind.Output }
    }

    private fun registerScope(
        scopes: MutableMap<String, IrGraphScope>,
        id: String,
        kind: IrGraphScopeKind,
        parentId: String?,
        label: String,
        source: IrGraphSourceRef,
    ) {
        scopes.putIfAbsent(
            id,
            IrGraphScope(
                id = id,
                kind = kind,
                parentId = parentId,
                label = label,
                source = source,
            ),
        )
    }

    private fun buildFacets(
        document: WorkspaceDocument,
        nodes: List<IrGraphNode>,
        branches: List<IrGraphBranch>,
    ): List<IrGraphFacet> = buildList {
        branches.forEach { branch ->
            add(
                IrGraphFacet(
                    id = "facet:${branch.id}",
                    kind = IrGraphFacetKind.BRANCH_REGION,
                    label = branchLabel(branch.role, branch.index),
                    scopeId = branch.scopeId,
                    ownerNodeId = branch.ownerNodeId,
                    nodeIds = nodes.filter { branch.scopeId in it.scopePath }.map { it.id },
                    source = branch.source,
                    properties = mapOf(
                        "role" to branch.role.name,
                        "slotName" to branch.slotName,
                        "index" to branch.index.toString(),
                    ),
                )
            )
        }
        nodes.filter { it.properties["collapsed"] == "true" }.forEach { node ->
            add(
                IrGraphFacet(
                    id = "facet:collapse:${node.id.value}",
                    kind = IrGraphFacetKind.COLLAPSE_GROUP,
                    label = node.label,
                    scopeId = node.properties["scopeId"],
                    ownerNodeId = node.id,
                    nodeIds = listOf(node.id),
                    source = node.source,
                    properties = mapOf("collapsed" to "true"),
                )
            )
        }
        val variableNodes = nodes.filter { it.properties["blockType"] == BlockTypes.VARIABLE_SET }
        if (variableNodes.size >= 2) {
            add(
                IrGraphFacet(
                    id = "facet:variables:${document.id}",
                    kind = IrGraphFacetKind.VARIABLE_BULK,
                    label = "Variables",
                    scopeId = variableNodes.firstOrNull()?.properties?.get("scopeId"),
                    ownerNodeId = null,
                    nodeIds = variableNodes.map { it.id },
                    source = IrGraphSourceRef(workspaceId = document.id, workspaceVersion = document.version),
                )
            )
        }
    }

    private fun sourceRef(
        document: WorkspaceDocument,
        blockId: BlockId,
        slotName: String? = null,
        branchRef: IrGraphBranchRef? = null,
    ): IrGraphSourceRef {
        val block = document.blocks[blockId]
        return IrGraphSourceRef(
            workspaceId = document.id,
            workspaceVersion = document.version,
            blockId = blockId.value,
            slotName = slotName,
            sourceLine = block?.metadata?.get("emscript.source.line")?.toIntOrNull(),
            sourceColumn = block?.metadata?.get("emscript.source.column")?.toIntOrNull(),
            branch = branchRef,
        )
    }

    private fun BlockId.irNodeId(): IrGraphNodeId = IrGraphNodeId("block:${value}")

    private fun operatorSymbol(block: BlockNode): String =
        when (val normalized = OperatorNormalization.normalize(block.fieldText("operator"))) {
            is NormalizedOperator.Compare -> normalized.value.symbol
            is NormalizedOperator.Arithmetic -> normalized.value.symbol
            null -> "?"
        }

    private fun operatorLabel(block: BlockNode): String =
        when (val normalized = OperatorNormalization.normalize(block.fieldText("operator"))) {
            is NormalizedOperator.Arithmetic -> normalized.value.name
            is NormalizedOperator.Compare -> "COMPARE ${normalized.value.symbol}"
            null -> "OPERATE ?"
        }

    private fun BlockNode.fieldText(key: String): String =
        fields[key]?.asString() ?: registry.getDefinition(type)?.fields?.find { it.key == key }?.defaultValue ?: ""

    private fun BlockNode.fieldNumber(key: String): Double =
        when (val value = fields[key]) {
            is FieldValue.Number -> value.value
            is FieldValue.Text -> value.value.toDoubleOrNull() ?: 0.0
            else -> registry.getDefinition(type)?.fields?.find { it.key == key }?.defaultValue?.toDoubleOrNull() ?: 0.0
        }

    private fun BlockNode.fieldBool(key: String): Boolean =
        when (val value = fields[key]) {
            is FieldValue.Bool -> value.value
            is FieldValue.Text -> value.value.equals("true", ignoreCase = true)
            else -> registry.getDefinition(type)?.fields?.find { it.key == key }?.defaultValue?.equals("true", ignoreCase = true) == true
        }
}
