package de.visualtasker.workflow.semantics.validation

import de.visualtasker.workflow.core.BlockId
import de.visualtasker.workflow.core.Connection
import de.visualtasker.workflow.core.ConnectionId
import de.visualtasker.workflow.core.ConnectionKind
import de.visualtasker.workflow.core.WorkspaceDocument
import de.visualtasker.workflow.core.allConnections
import de.visualtasker.workflow.semantics.DefaultWorkflowSemanticDefinitions
import de.visualtasker.workflow.semantics.SemanticValueInputDefinition
import de.visualtasker.workflow.semantics.VisualTaskerCommandCatalog
import de.visualtasker.workflow.semantics.WorkflowElementTypes as BlockTypes
import de.visualtasker.workflow.semantics.WorkflowSemanticDefinitionProvider
import de.visualtasker.workflow.semantics.WorkspaceValueTypeSystem
import de.visualtasker.workflow.semantics.argumentAt
import de.visualtasker.workflow.semantics.workspaceInputNameAt
import de.visualtasker.emscript.contract.LanguageTypeRef

object Validator {
    fun validate(
        document: WorkspaceDocument,
        definitions: WorkflowSemanticDefinitionProvider = DefaultWorkflowSemanticDefinitions,
    ): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val graph = ValidationGraphIndex(document)

        document.blocks.forEach { (blockId, block) ->
            val definition = definitions.semanticDefinition(block.type)
            if (definition == null) {
                errors += UnknownBlockType(blockId, block.type)
                return@forEach
            }

            val catalogEntry = VisualTaskerCommandCatalog.findByBlockType(block.type)
            val commandInputs = catalogEntry?.let { entry ->
                (0 until block.valueInputs.size).mapNotNull { index ->
                    val name = entry.workspaceInputNameAt(index) ?: return@mapNotNull null
                    if (block.valueInputs.none { it.name == name }) return@mapNotNull null
                    val argument = entry.argumentAt(index) ?: return@mapNotNull null
                    SemanticValueInputDefinition(
                        name = name,
                        accepts = argument.acceptedTypes,
                        required = false,
                    )
                }
            }.orEmpty()
            (definition.valueInputs + commandInputs)
                .distinctBy(SemanticValueInputDefinition::name)
                .forEach { inputDef ->
                val input = block.valueInputs.find { it.name == inputDef.name }
                if (input == null) {
                    if (inputDef.required) {
                        errors += MissingRequiredInput(blockId, inputDef.name)
                    }
                    return@forEach
                }
                val connected = input.connection.connectedTo
                if (connected == null) {
                    if (inputDef.required) {
                        errors += MissingRequiredInput(blockId, inputDef.name)
                    }
                } else {
                    val (valueBlockId, _) = graph.findConnection(connected)
                        ?: run {
                            errors += DisconnectedChain(blockId)
                            return@forEach
                        }
                    val actualType = WorkspaceValueTypeSystem.expressionType(document, valueBlockId, definitions)
                    val expectedTypes = WorkspaceValueTypeSystem.expectedInputTypes(
                        document = document,
                        block = block,
                        inputName = inputDef.name,
                        declaredAccepts = inputDef.accepts,
                    )
                    if (!WorkspaceValueTypeSystem.isCompatible(actualType, expectedTypes)) {
                        val nullableToNonNull = actualType is LanguageTypeRef.Nullable &&
                            expectedTypes.none { it is LanguageTypeRef.Nullable }
                        if (block.type == BlockTypes.VARIABLE_SET) {
                            val variableId = WorkspaceValueTypeSystem.variableId(block).orEmpty()
                            errors += AssignmentTypeMismatch(
                                blockId = blockId,
                                variableId = variableId,
                                expectedType = expectedTypes.singleOrNull()
                                    ?.let(WorkspaceValueTypeSystem::workspaceName)
                                    ?: expectedTypes.joinToString { WorkspaceValueTypeSystem.workspaceName(it).orEmpty() },
                                actualType = WorkspaceValueTypeSystem.workspaceName(actualType),
                                code = if (nullableToNonNull) {
                                    "NULLABLE_TO_NONNULL_ASSIGNMENT"
                                } else {
                                    "EMSCRIPT_ASSIGNMENT_TYPE_MISMATCH"
                                },
                            )
                        } else {
                            val nullableCode = when {
                                !nullableToNonNull -> "EMSCRIPT_ARGUMENT_TYPE_MISMATCH"
                                block.type.startsWith("control.") -> "NULLABLE_VALUE_IN_NONNULL_CONTEXT"
                                else -> "NULLABLE_ARGUMENT_TO_NONNULL_PARAMETER"
                            }
                            errors += TypeMismatch(
                                blockId = blockId,
                                inputName = inputDef.name,
                                expected = expectedTypes.mapNotNullTo(linkedSetOf(), WorkspaceValueTypeSystem::workspaceName),
                                actual = WorkspaceValueTypeSystem.workspaceName(actualType),
                                commandId = catalogEntry?.id ?: block.type,
                                code = nullableCode,
                            )
                        }
                    }
                }
            }

            block.allConnections().forEach { connection ->
                val partnerId = connection.connectedTo ?: return@forEach
                val partner = graph.findConnection(partnerId)
                if (partner == null) {
                    errors += InvalidConnection(connection.id, partnerId, "partner connection missing")
                }
            }

            block.previous?.connectedTo?.let { partnerId ->
                val partner = graph.findConnection(partnerId)
                if (partner?.second?.kind != ConnectionKind.Next &&
                    partner?.second?.kind != ConnectionKind.StatementInput
                ) {
                    errors += DisconnectedChain(blockId)
                }
            }
            block.next?.connectedTo?.let { partnerId ->
                val partner = graph.findConnection(partnerId)
                if (partner?.second?.kind != ConnectionKind.Previous) {
                    errors += DisconnectedChain(blockId)
                }
            }
        }

        val reachable = graph.reachableFrom(document.rootBlocks)
        document.blocks.keys.filter { it !in reachable }.forEach { orphanId ->
            errors += OrphanBlock(orphanId)
        }

        val cycleReachability = graph.cycleReachability()
        document.blocks.keys.forEach { blockId ->
            if (cycleReachability[blockId] == true) {
                errors += CycleDetected(blockId)
            }
        }

        return ValidationResult(errors.distinctBy { it.message })
    }

}

private class ValidationGraphIndex(document: WorkspaceDocument) {
    private val blocks = document.blocks
    private val connections = linkedMapOf<ConnectionId, Pair<BlockId, Connection>>()
    private val reachabilityEdges = linkedMapOf<BlockId, List<BlockId>>()
    private val cycleEdges = linkedMapOf<BlockId, List<BlockId>>()

    init {
        document.blocks.forEach { (blockId, block) ->
            block.allConnections().forEach { connection ->
                connections.putIfAbsent(connection.id, blockId to connection)
            }
        }
        document.blocks.forEach { (blockId, block) ->
            reachabilityEdges[blockId] = buildList {
                block.next.target(ConnectionKind.Previous)?.let(::add)
                block.statementInputs.forEach { input ->
                    input.connection.target(ConnectionKind.Previous)?.let(::add)
                }
                block.valueInputs.forEach { input ->
                    input.connection.target(ConnectionKind.Output)?.let(::add)
                }
            }.distinct()
            cycleEdges[blockId] = buildList {
                block.next.target()?.let(::add)
                block.statementInputs.forEach { input ->
                    input.connection.target(ConnectionKind.Previous)?.let(::add)
                }
                block.valueInputs.forEach { input ->
                    input.connection.target()?.let(::add)
                }
            }.distinct()
        }
    }

    fun findConnection(connectionId: ConnectionId): Pair<BlockId, Connection>? = connections[connectionId]

    fun reachableFrom(roots: List<BlockId>): Set<BlockId> {
        val reachable = linkedSetOf<BlockId>()
        val pending = ArrayDeque<BlockId>()
        roots.asReversed().forEach(pending::addLast)
        while (pending.isNotEmpty()) {
            val blockId = pending.removeLast()
            if (!reachable.add(blockId)) continue
            if (blockId !in blocks) continue
            reachabilityEdges[blockId].orEmpty().asReversed().forEach(pending::addLast)
        }
        return reachable
    }

    fun cycleReachability(): Map<BlockId, Boolean> {
        val colors = mutableMapOf<BlockId, DfsColor>()
        val reachesCycle = mutableMapOf<BlockId, Boolean>()

        fun visit(blockId: BlockId): Boolean {
            when (colors[blockId] ?: DfsColor.WHITE) {
                DfsColor.GRAY -> return true
                DfsColor.BLACK -> return reachesCycle[blockId] == true
                DfsColor.WHITE -> Unit
            }
            if (blockId !in blocks) return false

            colors[blockId] = DfsColor.GRAY
            var foundCycle = false
            cycleEdges[blockId].orEmpty().forEach { targetId ->
                if (visit(targetId)) foundCycle = true
            }
            colors[blockId] = DfsColor.BLACK
            reachesCycle[blockId] = foundCycle
            return foundCycle
        }

        blocks.keys.forEach(::visit)
        return reachesCycle
    }

    private fun Connection?.target(requiredKind: ConnectionKind? = null): BlockId? {
        val targetId = this?.connectedTo ?: return null
        val (targetBlockId, targetConnection) = connections[targetId] ?: return null
        return targetBlockId.takeIf { requiredKind == null || targetConnection.kind == requiredKind }
    }

    private enum class DfsColor {
        WHITE,
        GRAY,
        BLACK,
    }
}
