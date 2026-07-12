package de.visualtasker.blockeditor.validation

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.ConnectionKind
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.WorkspaceGraph
import de.visualtasker.blockeditor.domain.allConnections
import de.visualtasker.blockeditor.registry.BlockRegistry
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry

object Validator {
    fun validate(
        document: WorkspaceDocument,
        registry: BlockRegistry = DefaultBlockRegistry,
    ): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        document.blocks.forEach { (blockId, block) ->
            val definition = registry.getDefinition(block.type)
            if (definition == null) {
                errors += UnknownBlockType(blockId, block.type)
                return@forEach
            }

            definition.valueInputs.forEach { inputDef ->
                val input = block.valueInputs.find { it.name == inputDef.name }
                if (input == null) {
                    errors += MissingRequiredInput(blockId, inputDef.name)
                    return@forEach
                }
                val connected = input.connection.connectedTo
                if (connected == null) {
                    errors += MissingRequiredInput(blockId, inputDef.name)
                } else {
                    val (valueBlockId, outputConn) = WorkspaceGraph.findConnection(document, connected)
                        ?: run {
                            errors += DisconnectedChain(blockId)
                            return@forEach
                        }
                    val valueBlock = document.blocks[valueBlockId]
                    val outputType = outputConn.provides ?: valueBlock?.let { registry.getDefinition(it.type)?.outputType }
                    if (outputType != null && inputDef.accepts.isNotEmpty() && outputType !in inputDef.accepts) {
                        errors += TypeMismatch(blockId, inputDef.name, inputDef.accepts, outputType)
                    }
                }
            }

            block.allConnections().forEach { connection ->
                val partnerId = connection.connectedTo ?: return@forEach
                val partner = WorkspaceGraph.findConnection(document, partnerId)
                if (partner == null) {
                    errors += InvalidConnection(connection.id, partnerId, "partner connection missing")
                }
            }

            block.previous?.connectedTo?.let { partnerId ->
                val partner = WorkspaceGraph.findConnection(document, partnerId)
                if (partner?.second?.kind != ConnectionKind.Next &&
                    partner?.second?.kind != ConnectionKind.StatementInput
                ) {
                    errors += DisconnectedChain(blockId)
                }
            }
            block.next?.connectedTo?.let { partnerId ->
                val partner = WorkspaceGraph.findConnection(document, partnerId)
                if (partner?.second?.kind != ConnectionKind.Previous) {
                    errors += DisconnectedChain(blockId)
                }
            }
        }

        val reachable = mutableSetOf<BlockId>()
        document.rootBlocks.forEach { rootId ->
            collectReachable(document, rootId, reachable)
        }
        document.blocks.keys.filter { it !in reachable }.forEach { orphanId ->
            errors += OrphanBlock(orphanId)
        }

        document.blocks.keys.forEach { blockId ->
            if (hasCycle(document, blockId)) {
                errors += CycleDetected(blockId)
            }
        }

        return ValidationResult(errors.distinctBy { it.message })
    }

    private fun collectReachable(
        document: WorkspaceDocument,
        blockId: BlockId,
        out: MutableSet<BlockId>,
    ) {
        if (!out.add(blockId)) return
        val block = document.blocks[blockId] ?: return

        WorkspaceGraph.chainFrom(document, blockId).forEach { chainId ->
            out.add(chainId)
            out += WorkspaceGraph.descendants(document, chainId)
        }

        block.valueInputs.forEach { input ->
            input.connection.connectedTo?.let { connId ->
                val (valueId, _) = WorkspaceGraph.findConnection(document, connId) ?: return@let
                out.add(valueId)
                out += WorkspaceGraph.descendants(document, valueId)
            }
        }

        block.statementInputs.forEach { slot ->
            WorkspaceGraph.statementStack(document, blockId, slot.name).forEach { childId ->
                collectReachable(document, childId, out)
            }
        }
    }

    private fun hasCycle(document: WorkspaceDocument, startId: BlockId): Boolean {
        val visiting = mutableSetOf<BlockId>()
        val visited = mutableSetOf<BlockId>()

        fun dfs(blockId: BlockId): Boolean {
            if (blockId in visiting) return true
            if (blockId in visited) return false
            visiting += blockId
            val block = document.blocks[blockId] ?: return false

            block.next?.connectedTo?.let { connId ->
                WorkspaceGraph.findConnection(document, connId)?.first?.let { nextId ->
                    if (dfs(nextId)) return true
                }
            }

            block.statementInputs.forEach { slot ->
                WorkspaceGraph.statementStack(document, blockId, slot.name).forEach { childId ->
                    if (dfs(childId)) return true
                }
            }

            block.valueInputs.forEach { input ->
                input.connection.connectedTo?.let { connId ->
                    WorkspaceGraph.findConnection(document, connId)?.first?.let { valueId ->
                        if (dfs(valueId)) return true
                    }
                }
            }

            visiting -= blockId
            visited += blockId
            return false
        }

        return dfs(startId)
    }
}
