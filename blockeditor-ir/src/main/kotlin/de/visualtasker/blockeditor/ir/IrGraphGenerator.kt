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
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry

class IrGraphGenerator(
    private val registry: BlockRegistry = DefaultBlockRegistry,
) {
    fun generate(document: WorkspaceDocument): IrGraph {
        val diagnostics = mutableListOf<IrGraphDiagnostic>()
        val nodes = linkedMapOf<IrGraphNodeId, IrGraphNode>()
        val edges = linkedMapOf<IrGraphEdgeId, IrGraphEdge>()
        val visited = mutableSetOf<BlockId>()
        val entryNodeIds = mutableListOf<IrGraphNodeId>()

        document.rootBlocks.forEachIndexed { index, rootId ->
            val root = document.blocks[rootId] ?: return@forEachIndexed
            if (root.type == BlockTypes.EVENT_START) {
                val scopePath = listOf("script:${root.id.value}")
                entryNodeIds += rootId.irNodeId()
                walkBlock(
                    document = document,
                    blockId = rootId,
                    scopePath = scopePath,
                    nodes = nodes,
                    edges = edges,
                    diagnostics = diagnostics,
                    visited = visited,
                )
            } else {
                val scopePath = listOf("orphan-root:$index")
                walkBlock(
                    document = document,
                    blockId = rootId,
                    scopePath = scopePath,
                    nodes = nodes,
                    edges = edges,
                    diagnostics = diagnostics,
                    visited = visited,
                )
            }
        }

        document.blocks.keys
            .filterNot { it in visited }
            .sortedBy { it.value }
            .forEach { blockId ->
                walkBlock(
                    document = document,
                    blockId = blockId,
                    scopePath = listOf("unreachable"),
                    nodes = nodes,
                    edges = edges,
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
        )
    }

    private fun walkBlock(
        document: WorkspaceDocument,
        blockId: BlockId,
        scopePath: List<String>,
        nodes: MutableMap<IrGraphNodeId, IrGraphNode>,
        edges: MutableMap<IrGraphEdgeId, IrGraphEdge>,
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
            walkBlock(document, valueBlockId, scopePath + "value:${input.name}", nodes, edges, diagnostics, visited)
            putEdge(
                edges = edges,
                source = valueBlockId.irNodeId(),
                target = blockId.irNodeId(),
                kind = if (input.name.endsWith("CONDITION")) IrGraphEdgeKind.CONDITION else IrGraphEdgeKind.DATA_FLOW,
                label = input.name,
                sourceRef = sourceRef(document, blockId, input.name),
            )
        }

        block.statementInputs.forEach { input ->
            val head = WorkspaceGraph.statementStackHead(document, blockId, input.name) ?: return@forEach
            val kind = edgeKindForStatementSlot(input.name)
            walkBlock(document, head, scopePath + "branch:${input.name}", nodes, edges, diagnostics, visited)
            putEdge(
                edges = edges,
                source = blockId.irNodeId(),
                target = head.irNodeId(),
                kind = kind,
                label = input.name,
                sourceRef = sourceRef(document, blockId, input.name),
            )
        }

        WorkspaceGraph.nextChain(document, blockId)?.let { nextId ->
            walkBlock(document, nextId, scopePath, nodes, edges, diagnostics, visited)
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
        return IrGraphNode(
            id = block.id.irNodeId(),
            kind = nodeKind(block),
            label = nodeLabel(block),
            scopePath = scopePath,
            source = sourceRef(document, block.id),
            properties = buildMap {
                put("blockType", block.type)
                put("blockId", block.id.value)
                if (block.collapsed) put("collapsed", "true")
            },
        )
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

    private fun sourceRef(document: WorkspaceDocument, blockId: BlockId, slotName: String? = null): IrGraphSourceRef =
        IrGraphSourceRef(
            workspaceId = document.id,
            workspaceVersion = document.version,
            blockId = blockId.value,
            slotName = slotName,
        )

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
