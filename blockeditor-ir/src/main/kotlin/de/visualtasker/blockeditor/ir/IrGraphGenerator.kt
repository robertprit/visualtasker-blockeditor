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
                putEditorProperties(block, document)
                if (block.collapsed) put("collapsed", "true")
                block.metadata["emscript.source.line"]?.let { put("sourceLine", it) }
                block.metadata["emscript.source.column"]?.let { put("sourceColumn", it) }
                block.metadata
                    .filterKeys { it.startsWith("emscript.groupFacet.") }
                    .forEach { (key, value) -> put(key, value) }
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

    private fun MutableMap<String, String>.putEditorProperties(
        block: BlockNode,
        document: WorkspaceDocument,
    ) {
        put("inputPorts", inputPorts(block).joinToString("|") { it.encoded() })
        put("outputPorts", outputPorts(block).joinToString("|") { it.encoded() })
        put("branchCount", block.ifBranchCount().toString())
        variableId(block, document)?.let { put("variableId", it) }
        block.fieldTextOrNull("variableLabel", "variableName", "label", "variable")?.let { put("variableLabel", it) }
        block.fieldTextOrNull("operator", "compare", "op", "operation", "COMPARE_OP")?.let { put("operator", it) }
        block.fieldNumberOrNull("value")?.let { put("literalNumber", it.toString()) }
        block.fieldTextOrNull("value")?.let { put("literalString", it) }
        block.fieldBoolOrNull("value")?.let { put("literalBoolean", it.toString()) }
        block.fieldNumberOrNull("ms")?.let { put("waitMs", it.toString()) }
        block.fieldNumberOrNull("frequency")?.let { put("frequency", it.toString()) }
        block.fieldNumberOrNull("durationMs")?.let { put("durationMs", it.toString()) }
        block.fieldNumberOrNull("volume")?.let { put("volume", it.toString()) }
        block.fieldTextOrNull("pattern")?.let { put("pattern", it) }
        block.fieldTextOrNull("message")?.let { put("message", it) }
        block.fieldTextOrNull("text")?.let { put("text", it) }
        block.fieldTextOrNull("command")?.let { put("command", it) }
        block.fieldTextOrNull("args")?.let { put("args", it) }
    }

    private data class IrPortProperty(
        val name: String,
        val label: String,
        val kind: IrGraphEdgeKind,
    ) {
        fun encoded(): String = listOf(name, label, kind.name).joinToString("~") { it.replace("~", "%7E").replace("|", "%7C") }
    }

    private fun inputPorts(block: BlockNode): List<IrPortProperty> = buildList {
        if (block.previous != null) add(IrPortProperty("previous", "Previous", IrGraphEdgeKind.SEQUENCE))
        block.valueInputs.forEach { input ->
            add(
                IrPortProperty(
                    name = input.name,
                    label = input.name,
                    kind = if (input.name.endsWith("CONDITION")) IrGraphEdgeKind.CONDITION else IrGraphEdgeKind.DATA_FLOW,
                )
            )
        }
    }

    private fun outputPorts(block: BlockNode): List<IrPortProperty> = buildList {
        if (block.next != null) {
            add(
                IrPortProperty(
                    name = "next",
                    label = "Next",
                    kind = if (block.type == BlockTypes.CONTROL_REPEAT || block.type == BlockTypes.CONTROL_WHILE) {
                        IrGraphEdgeKind.LOOP_EXIT
                    } else {
                        IrGraphEdgeKind.SEQUENCE
                    },
                )
            )
        }
        block.statementInputs.forEach { input ->
            add(
                IrPortProperty(
                    name = input.name,
                    label = input.name,
                    kind = edgeKindForStatementSlot(input.name),
                )
            )
        }
        if (block.output != null) add(IrPortProperty("output", "Output", IrGraphEdgeKind.DATA_FLOW))
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
        else -> when {
            block.type.startsWith(BlockTypes.VARIABLE_REPORTER_PREFIX) -> IrGraphNodeKind.VALUE
            block.type.startsWith(BlockTypes.EMSCRIPT_COMMAND_PREFIX) -> IrGraphNodeKind.ACTION
            else -> IrGraphNodeKind.UNKNOWN
        }
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
        else -> when {
            block.type.startsWith(BlockTypes.VARIABLE_REPORTER_PREFIX) ->
                block.fieldText("variableLabel").ifBlank { block.fieldText("variable") }
            block.type.startsWith(BlockTypes.EMSCRIPT_COMMAND_PREFIX) ->
                block.fieldText("command").ifBlank { block.type.removePrefix(BlockTypes.EMSCRIPT_COMMAND_PREFIX) }
            else -> block.type
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
        nodes.forEach { owner ->
            addAll(editorGroupFacets(owner, nodes))
        }
    }

    private fun editorGroupFacets(
        owner: IrGraphNode,
        nodes: List<IrGraphNode>,
    ): List<IrGraphFacet> {
        val count = owner.properties["emscript.groupFacet.count"]?.toIntOrNull() ?: return emptyList()
        return (0 until count).mapNotNull { index ->
            val prefix = "emscript.groupFacet.$index"
            val id = owner.properties["$prefix.id"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val kind = owner.properties["$prefix.kind"]?.toIrFacetKind()
            val groupNodes = nodesForEditorGroupFacet(kind, owner, nodes)
            IrGraphFacet(
                id = "facet:emscript:$id",
                kind = kind ?: IrGraphFacetKind.COMMENT_MARKER,
                label = owner.properties["$prefix.label"]?.takeIf { it.isNotBlank() } ?: id,
                scopeId = owner.properties["scopeId"],
                ownerNodeId = owner.id,
                nodeIds = groupNodes,
                source = owner.source.copy(
                    sourceLine = owner.properties["$prefix.startLine"]?.toIntOrNull(),
                ),
                properties = buildMap {
                    put("editorFacetId", id)
                    put("editorFacetKind", owner.properties["$prefix.kind"].orEmpty())
                    owner.properties["$prefix.startLine"]?.let { put("startLine", it) }
                    owner.properties["$prefix.endLine"]?.let { put("endLine", it) }
                },
            )
        }
    }

    private fun nodesForEditorGroupFacet(
        kind: IrGraphFacetKind?,
        owner: IrGraphNode,
        nodes: List<IrGraphNode>,
    ): List<IrGraphNodeId> {
        val scopeId = owner.properties["scopeId"].orEmpty()
        val scoped = nodes.filter { it.id != owner.id && it.properties["scopeId"] == scopeId }
        return when (kind) {
            IrGraphFacetKind.VARIABLE_BULK -> scoped
                .filter { it.properties["blockType"] == BlockTypes.VARIABLE_SET }
                .map { it.id }
            IrGraphFacetKind.FUNCTION_REGION,
            IrGraphFacetKind.COLLAPSE_GROUP,
            IrGraphFacetKind.BRANCH_REGION,
            IrGraphFacetKind.COMMENT_MARKER,
            null -> scoped.map { it.id }
        }.ifEmpty { listOf(owner.id) }
    }

    private fun String.toIrFacetKind(): IrGraphFacetKind? =
        when (lowercase()) {
            "branch-region", "branch" -> IrGraphFacetKind.BRANCH_REGION
            "collapse-group", "collapse" -> IrGraphFacetKind.COLLAPSE_GROUP
            "variable-bulk", "variables", "vars" -> IrGraphFacetKind.VARIABLE_BULK
            "function-region", "function" -> IrGraphFacetKind.FUNCTION_REGION
            "comment-marker", "comment", "region", "loop-region" -> IrGraphFacetKind.COMMENT_MARKER
            else -> null
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

    private fun variableId(block: BlockNode, document: WorkspaceDocument): String? {
        val explicit = block.fieldTextOrNull("variableId", "varId", "variable_id")
        if (!explicit.isNullOrBlank()) return explicit
        val prefixed = block.type
            .takeIf { it.startsWith(BlockTypes.VARIABLE_REPORTER_PREFIX) }
            ?.removePrefix(BlockTypes.VARIABLE_REPORTER_PREFIX)
            ?.takeIf { it.isNotBlank() }
        if (!prefixed.isNullOrBlank()) return prefixed
        val legacy = block.fieldTextOrNull("variable")
        return legacy?.takeIf { it in document.variables.variables }
    }

    private fun BlockNode.ifBranchCount(): Int {
        val explicit = metadata["if.branchCount"]?.toIntOrNull()
        if (explicit != null) return explicit.coerceIn(1, 8)
        return statementInputs.count { input ->
            input.name == BlockTypes.SLOT_THEN ||
                input.name == BlockTypes.SLOT_ELSE ||
                input.name == BlockTypes.SLOT_ELIF ||
                input.name.startsWith("ELIF_")
        }.coerceAtLeast(1)
    }

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

    private fun BlockNode.fieldTextOrNull(vararg keys: String): String? {
        keys.forEach { key ->
            val value = fields[key]
            if (value is FieldValue.Text && value.value.isNotBlank()) return value.value
        }
        return null
    }

    private fun BlockNode.fieldNumberOrNull(key: String): Double? = when (val value = fields[key]) {
        is FieldValue.Number -> value.value
        is FieldValue.Text -> value.value.toDoubleOrNull()
        else -> null
    }

    private fun BlockNode.fieldBoolOrNull(key: String): Boolean? = when (val value = fields[key]) {
        is FieldValue.Bool -> value.value
        is FieldValue.Text -> value.value.toBooleanStrictOrNull()
        else -> null
    }

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
