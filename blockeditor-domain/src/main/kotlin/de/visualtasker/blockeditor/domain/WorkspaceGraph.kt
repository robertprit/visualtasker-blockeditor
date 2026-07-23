package de.visualtasker.blockeditor.domain

object WorkspaceGraph {
    fun connectionOwner(document: WorkspaceDocument, connectionId: ConnectionId): BlockId? =
        document.blocks.entries.firstOrNull { (_, block) ->
            block.allConnections().any { it.id == connectionId }
        }?.key

    fun findConnection(document: WorkspaceDocument, connectionId: ConnectionId): Pair<BlockId, Connection>? {
        document.blocks.forEach { (blockId, block) ->
            block.allConnections().forEach { connection ->
                if (connection.id == connectionId) return blockId to connection
            }
        }
        return null
    }

    fun nextChain(document: WorkspaceDocument, blockId: BlockId): BlockId? {
        val nextConn = document.blocks[blockId]?.next ?: return null
        val targetConnId = nextConn.connectedTo ?: return null
        val (targetBlockId, targetConn) = findConnection(document, targetConnId) ?: return null
        if (targetConn.kind == ConnectionKind.Previous) return targetBlockId
        return null
    }

    fun previousChain(document: WorkspaceDocument, blockId: BlockId): BlockId? {
        val prevConn = document.blocks[blockId]?.previous ?: return null
        val targetConnId = prevConn.connectedTo ?: return null
        val (targetBlockId, targetConn) = findConnection(document, targetConnId) ?: return null
        if (targetConn.kind == ConnectionKind.Next) return targetBlockId
        if (targetConn.kind == ConnectionKind.StatementInput) return null
        return null
    }

    fun chainFrom(document: WorkspaceDocument, headId: BlockId): List<BlockId> {
        val result = mutableListOf(headId)
        var current = headId
        while (true) {
            val next = nextChain(document, current) ?: break
            result += next
            current = next
        }
        return result
    }

    fun statementStackHead(document: WorkspaceDocument, parentId: BlockId, slotName: String): BlockId? {
        val parent = document.blocks[parentId] ?: return null
        val slot = parent.statementInputs.find { it.name == slotName } ?: return null
        val headConnId = slot.connection.connectedTo ?: return null
        val (blockId, conn) = findConnection(document, headConnId) ?: return null
        if (conn.kind == ConnectionKind.Previous) return blockId
        return null
    }

    fun statementStack(document: WorkspaceDocument, parentId: BlockId, slotName: String): List<BlockId> {
        val head = statementStackHead(document, parentId, slotName) ?: return emptyList()
        return chainFrom(document, head)
    }

    fun slotContaining(document: WorkspaceDocument, childId: BlockId): Pair<BlockId, String>? {
        document.blocks.forEach { (parentId, parent) ->
            parent.statementInputs.forEach { slot ->
                if (childId in statementStack(document, parentId, slot.name)) {
                    return parentId to slot.name
                }
            }
        }
        return null
    }

    /** Block, dessen Output an einem Value-Input eines anderen Blocks hängt. */
    fun valueInputParent(document: WorkspaceDocument, childId: BlockId): Pair<BlockId, String>? {
        val child = document.blocks[childId] ?: return null
        val outputTarget = child.output?.connectedTo ?: return null
        val (parentId, connection) = findConnection(document, outputTarget) ?: return null
        if (connection.kind != ConnectionKind.ValueInput) return null
        return parentId to (connection.slotName ?: connection.id.value)
    }

    fun isValuePlugged(document: WorkspaceDocument, blockId: BlockId): Boolean =
        valueInputParent(document, blockId) != null

    /** Reporter/Blöcke, die über Value-Inputs an einen gezogenen Vorfahren hängen. */
    fun isAttachedToDraggedAncestor(
        document: WorkspaceDocument,
        blockId: BlockId,
        draggedBlocks: Set<BlockId>,
    ): Boolean {
        var current: BlockId? = blockId
        while (current != null) {
            val parent = valueInputParent(document, current)?.first ?: return false
            if (parent in draggedBlocks) return true
            current = parent
        }
        return false
    }

    fun expandDragClosure(
        document: WorkspaceDocument,
        seeds: Set<BlockId>,
    ): Set<BlockId> {
        val result = seeds.toMutableSet()
        var changed = true
        while (changed) {
            changed = false
            for (id in result.toList()) {
                val block = document.blocks[id] ?: continue
                block.valueInputs.forEach { input ->
                    val connected = input.connection.connectedTo ?: return@forEach
                    val (childId, conn) = findConnection(document, connected) ?: return@forEach
                    if (conn.kind == ConnectionKind.Output && result.add(childId)) {
                        changed = true
                    }
                }
            }
            for (childId in document.blocks.keys) {
                val parent = valueInputParent(document, childId)?.first ?: continue
                if (parent in result && result.add(childId)) {
                    changed = true
                }
            }
        }
        return result
    }

    fun descendants(document: WorkspaceDocument, blockId: BlockId): Set<BlockId> {
        val result = mutableSetOf<BlockId>()
        fun walk(id: BlockId) {
            val block = document.blocks[id] ?: return
            block.statementInputs.forEach { slot ->
                statementStack(document, id, slot.name).forEach { childId ->
                    if (result.add(childId)) walk(childId)
                }
            }
            block.valueInputs.forEach { input ->
                val connected = input.connection.connectedTo ?: return@forEach
                val (valueBlockId, conn) = findConnection(document, connected) ?: return@forEach
                if (conn.kind == ConnectionKind.Output && result.add(valueBlockId)) {
                    walk(valueBlockId)
                }
            }
            var current = id
            while (true) {
                val next = nextChain(document, current) ?: break
                if (result.add(next)) walk(next)
                current = next
            }
        }
        walk(blockId)
        result.remove(blockId)
        return result
    }

    fun isDescendantOf(document: WorkspaceDocument, nodeId: BlockId, ancestorId: BlockId): Boolean {
        if (nodeId == ancestorId) return true
        return nodeId in descendants(document, ancestorId)
    }

    fun isInStatementStack(document: WorkspaceDocument, blockId: BlockId): Boolean =
        slotContaining(document, blockId) != null

    fun isRootBlock(document: WorkspaceDocument, blockId: BlockId): Boolean =
        blockId in document.rootBlocks

    /**
     * Root-Einträge, die nicht bereits über die Next-Kette eines anderen Roots erreichbar sind.
     * Verhindert doppeltes Layout, wenn [WorkspaceDocument.rootBlocks] veraltete Einträge enthält.
     */
    fun topLevelRoots(document: WorkspaceDocument): List<BlockId> =
        pruneRootBlocks(
            document,
            document.rootBlocks.filter { !isValuePlugged(document, it) },
        )

    fun pruneRootBlocks(document: WorkspaceDocument, roots: List<BlockId>): List<BlockId> =
        sortRootBlocks(
            document,
            roots.distinct().filter { candidate ->
                !isValuePlugged(document, candidate) &&
                    roots.none { other ->
                        other != candidate && candidate in chainFrom(document, other).drop(1)
                    }
            },
        )

    /**
     * Stabile Root-Reihenfolge: Script Start zuerst, danach nach Y/X, dann nach ID.
     * Verhindert Layout-Sprünge durch unsortierte [WorkspaceDocument.rootBlocks].
     */
    fun sortRootBlocks(document: WorkspaceDocument, roots: List<BlockId>): List<BlockId> =
        roots.sortedWith(
            compareBy<BlockId> { id ->
                if (document.blocks[id]?.type == SCRIPT_START_TYPE) 0 else 1
            }.thenBy { id ->
                document.rootOffset(id)?.y ?: Float.MAX_VALUE
            }.thenBy { id ->
                document.rootOffset(id)?.x ?: Float.MAX_VALUE
            }.thenBy { id ->
                id.value
            },
        )

    private const val SCRIPT_START_TYPE = "event.start"
}
