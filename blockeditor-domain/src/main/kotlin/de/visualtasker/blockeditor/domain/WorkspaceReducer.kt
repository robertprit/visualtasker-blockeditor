package de.visualtasker.blockeditor.domain

import java.util.UUID

fun interface BlockFactory {
    fun create(definitionId: String, id: BlockId): BlockNode?
}

fun newBlockId(): BlockId = BlockId(UUID.randomUUID().toString())

object WorkspaceReducer {
    fun reduce(
        document: WorkspaceDocument,
        action: WorkspaceAction,
        factory: BlockFactory = BlockFactory { _, _ -> null },
    ): WorkspaceDocument = when (action) {
        is WorkspaceAction.InstantiateBlock -> instantiateBlock(document, action, factory)
        is WorkspaceAction.DeleteBlock -> deleteBlock(document, action.blockId)
        is WorkspaceAction.MoveRoot -> moveRoot(document, action.blockId, action.x, action.y)
        is WorkspaceAction.Connect -> connect(document, action.source, action.target)
        is WorkspaceAction.Disconnect -> disconnect(document, action.connection)
        is WorkspaceAction.DetachBlock -> detachBlock(document, action.blockId)
        is WorkspaceAction.Collapse -> setCollapsed(document, action.blockId, true)
        is WorkspaceAction.Expand -> setCollapsed(document, action.blockId, false)
        is WorkspaceAction.UpdateField -> updateField(document, action.blockId, action.key, action.value)
        is WorkspaceAction.CreateVariable -> createVariable(document, action.variable)
        is WorkspaceAction.DeleteVariable -> deleteVariable(document, action.variableId)
    }

    private fun bump(document: WorkspaceDocument): WorkspaceDocument =
        WorkspaceGraph.topLevelRoots(document).let { roots ->
            document.copy(
            version = document.version + 1,
                rootBlocks = roots,
                rootPositions = document.rootPositions.filterKeys { it in roots },
            )
        }

    private fun instantiateBlock(
        document: WorkspaceDocument,
        action: WorkspaceAction.InstantiateBlock,
        factory: BlockFactory,
    ): WorkspaceDocument {
        val id = newBlockId()
        val block = factory.create(action.definitionId, id) ?: return document
        val withBlock = document.copy(blocks = document.blocks + (id to block))
        return bump(
            withBlock.copy(
                rootBlocks = WorkspaceGraph.pruneRootBlocks(
                    withBlock,
                    document.rootBlocks + id,
                ),
                rootPositions = withBlock.rootPositions + (id to WorkspacePoint(action.x, action.y)),
            ),
        )
    }

    private fun deleteBlock(document: WorkspaceDocument, blockId: BlockId): WorkspaceDocument {
        val toRemove = WorkspaceGraph.descendants(document, blockId) + blockId
        var updated = document
        toRemove.forEach { id ->
            updated = detachBlockInternal(updated, id)
        }
        return bump(
            updated.copy(
                blocks = updated.blocks - toRemove,
                rootBlocks = updated.rootBlocks.filter { it !in toRemove },
                rootPositions = updated.rootPositions - toRemove,
            ),
        )
    }

    fun detachBlock(document: WorkspaceDocument, blockId: BlockId): WorkspaceDocument =
        bump(detachBlockInternal(document, blockId))

    /** Löst nur eingehende Verbindungen (Previous / Statement-Slot), behält Next-Kette und Kinder. */
    fun liftBlock(document: WorkspaceDocument, blockId: BlockId): WorkspaceDocument =
        bump(liftBlockInternal(document, blockId))

    /**
     * Löst den Drag-Root von der Kette darüber bzw. aus dem Statement-Slot,
     * behält aber die Next-Kette, wenn der Partner-Block mitgezogen wird.
     */
    fun liftDragGroup(
        document: WorkspaceDocument,
        blockId: BlockId,
        includedBlocks: Set<BlockId>,
    ): WorkspaceDocument = bump(liftDragGroupInternal(document, blockId, includedBlocks))

    /** Löst einen Reporter von einem Value-Input und macht ihn zu einem Root-Block. */
    fun liftFromValuePlug(document: WorkspaceDocument, blockId: BlockId): WorkspaceDocument {
        val block = document.blocks[blockId] ?: return document
        val output = block.output ?: return document
        if (output.connectedTo == null) return document
        var blocks = document.blocks.toMutableMap()
        blocks = disconnectAt(blocks, output.id)
        val roots = WorkspaceGraph.pruneRootBlocks(
            document.copy(blocks = blocks),
            if (blockId in document.rootBlocks) document.rootBlocks else document.rootBlocks + blockId,
        )
        return bump(document.copy(blocks = blocks, rootBlocks = roots))
    }

    private fun liftDragGroupInternal(
        document: WorkspaceDocument,
        blockId: BlockId,
        includedBlocks: Set<BlockId>,
        preserveRootNextChain: Boolean = false,
    ): WorkspaceDocument {
        var blocks = document.blocks.toMutableMap()
        if (blocks[blockId] == null) return document

        val chainAboveId = WorkspaceGraph.previousChain(document, blockId)
        val chainBelowId = WorkspaceGraph.nextChain(document, blockId)
        val keepNextChain = chainBelowId != null &&
            (chainBelowId in includedBlocks || (preserveRootNextChain && chainAboveId == null))
        val promotedBelowRoots = mutableListOf<BlockId>()

        blocks[blockId]?.previous?.connectedTo?.let {
            blocks = disconnectAt(blocks, blocks[blockId]!!.previous!!.id)
        }

        if (chainAboveId != null && chainBelowId != null && chainBelowId !in includedBlocks) {
            val above = blocks[chainAboveId]!!
            val below = blocks[chainBelowId]!!
            blocks = link(blocks, chainAboveId, above.next!!.id, below.previous!!.id)
            blocks = link(blocks, chainBelowId, below.previous!!.id, above.next!!.id)
        }

        if (chainAboveId != null && !keepNextChain) {
            blocks[blockId]?.next?.connectedTo?.let {
                blocks = disconnectAt(blocks, blocks[blockId]!!.next!!.id)
            }
        }
        if (chainAboveId == null && chainBelowId != null && !keepNextChain) {
            blocks[blockId]?.next?.connectedTo?.let {
                blocks = disconnectAt(blocks, blocks[blockId]!!.next!!.id)
            }
            promotedBelowRoots += chainBelowId
        }

        WorkspaceGraph.slotContaining(document, blockId)?.let { (parentId, slotName) ->
            if (WorkspaceGraph.statementStackHead(document, parentId, slotName) == blockId) {
                val parent = blocks[parentId] ?: return@let
                val slot = parent.statementInputs.find { it.name == slotName } ?: return@let
                blocks = disconnectAt(blocks, slot.connection.id)
            }
        }

        val lifted = document.copy(blocks = blocks)
        val roots = WorkspaceGraph.pruneRootBlocks(
            lifted,
            document.rootBlocks + blockId + promotedBelowRoots,
        )
        return lifted.copy(rootBlocks = roots)
    }

    private fun liftBlockInternal(document: WorkspaceDocument, blockId: BlockId): WorkspaceDocument =
        liftDragGroupInternal(document, blockId, setOf(blockId), preserveRootNextChain = true)

    private fun detachBlockInternal(document: WorkspaceDocument, blockId: BlockId): WorkspaceDocument {
        var blocks = document.blocks.toMutableMap()
        val block = blocks[blockId] ?: return document

        block.previous?.connectedTo?.let { blocks = disconnectAt(blocks, block.previous!!.id) }
        block.next?.connectedTo?.let { blocks = disconnectAt(blocks, block.next!!.id) }
        block.output?.connectedTo?.let { blocks = disconnectAt(blocks, block.output!!.id) }
        block.valueInputs.forEach { input ->
            input.connection.connectedTo?.let { blocks = disconnectAt(blocks, input.connection.id) }
        }
        block.statementInputs.forEach { input ->
            input.connection.connectedTo?.let { blocks = disconnectAt(blocks, input.connection.id) }
        }

        WorkspaceGraph.slotContaining(document, blockId)?.let { (parentId, _) ->
            val parent = blocks[parentId] ?: return@let
            val slot = parent.statementInputs.find { slot ->
                blockId in WorkspaceGraph.statementStack(document, parentId, slot.name)
            } ?: return@let
            if (WorkspaceGraph.statementStackHead(document, parentId, slot.name) == blockId) {
                blocks = disconnectAt(blocks, slot.connection.id)
            } else {
                val prev = WorkspaceGraph.previousChain(document, blockId)
                val next = WorkspaceGraph.nextChain(document, blockId)
                if (prev != null && next != null) {
                    val prevBlock = blocks[prev]!!
                    val nextBlock = blocks[next]!!
                    blocks = link(blocks, prev, prevBlock.next!!.id, nextBlock.previous!!.id)
                    blocks = link(blocks, next, nextBlock.previous!!.id, prevBlock.next!!.id)
                }
                block.previous?.connectedTo?.let { blocks = disconnectAt(blocks, block.previous!!.id) }
                block.next?.connectedTo?.let { blocks = disconnectAt(blocks, block.next!!.id) }
            }
        }

        blocks[blockId] = block.copy(
            previous = block.previous?.copy(connectedTo = null),
            next = block.next?.copy(connectedTo = null),
            output = block.output?.copy(connectedTo = null),
            valueInputs = block.valueInputs.map { it.copy(connection = it.connection.copy(connectedTo = null)) },
            statementInputs = block.statementInputs.map { it.copy(connection = it.connection.copy(connectedTo = null)) },
        )

        val detached = document.copy(blocks = blocks)
        val roots = WorkspaceGraph.pruneRootBlocks(
            detached,
            if (blockId in document.rootBlocks) document.rootBlocks else document.rootBlocks + blockId,
        )
        return detached.copy(rootBlocks = roots)
    }

    private fun connect(document: WorkspaceDocument, source: ConnectionId, target: ConnectionId): WorkspaceDocument {
        val (sourceBlockId, sourceConn) = WorkspaceGraph.findConnection(document, source) ?: return document
        val (targetBlockId, targetConn) = WorkspaceGraph.findConnection(document, target) ?: return document
        if (sourceBlockId == targetBlockId) return document
        val intoStatementSlot =
            sourceConn.kind == ConnectionKind.StatementInput && targetConn.kind == ConnectionKind.Previous
        if (WorkspaceGraph.isDescendantOf(document, targetBlockId, sourceBlockId)) return document
        if (!intoStatementSlot && WorkspaceGraph.isDescendantOf(document, sourceBlockId, targetBlockId)) return document
        if (!canConnect(sourceConn, targetConn)) return document

        var blocks = document.blocks.toMutableMap()
        var roots = document.rootBlocks.toMutableList()

        when {
            sourceConn.kind == ConnectionKind.Next && targetConn.kind == ConnectionKind.Previous -> {
                val formerPartnerId = partnerBlockId(document, sourceConn.connectedTo, ConnectionKind.Previous)
                sourceConn.connectedTo?.let { blocks = disconnectAt(blocks, source) }
                targetConn.connectedTo?.let { blocks = disconnectAt(blocks, target) }
                blocks = link(blocks, sourceBlockId, source, target)
                blocks = link(blocks, targetBlockId, target, source)
                roots.remove(targetBlockId)
                blocks = reattachFormerBelowIfPossible(blocks, roots, targetBlockId, formerPartnerId)
            }
            sourceConn.kind == ConnectionKind.Previous && targetConn.kind == ConnectionKind.Next ->
                return connect(document, target, source)
            sourceConn.kind == ConnectionKind.Output && targetConn.kind == ConnectionKind.ValueInput -> {
                if (!typesCompatible(sourceConn, targetConn)) return document
                if (targetConn.connectedTo != null && targetConn.connectedTo != source) return document
                targetConn.connectedTo?.let { blocks = disconnectAt(blocks, target) }
                sourceConn.connectedTo?.let { blocks = disconnectAt(blocks, source) }
                blocks = link(blocks, sourceBlockId, source, target)
                blocks = link(blocks, targetBlockId, target, source)
                roots.remove(sourceBlockId)
            }
            sourceConn.kind == ConnectionKind.StatementInput && targetConn.kind == ConnectionKind.Previous -> {
                val formerStackHeadId = partnerBlockId(document, sourceConn.connectedTo, ConnectionKind.Previous)
                val formerStackHeadPrevId = formerStackHeadId?.let { document.blocks[it]?.previous?.id }
                sourceConn.connectedTo?.let { blocks = disconnectAt(blocks, source) }
                targetConn.connectedTo?.let { blocks = disconnectAt(blocks, target) }
                blocks = link(blocks, sourceBlockId, source, target)
                blocks = link(blocks, targetBlockId, target, source)
                roots.remove(targetBlockId)
                blocks = reattachFormerIntoStatementStack(
                    blocks,
                    roots,
                    targetBlockId,
                    formerStackHeadId,
                    formerStackHeadPrevId,
                )
            }
            sourceConn.kind == ConnectionKind.Previous && targetConn.kind == ConnectionKind.StatementInput ->
                return connect(document, target, source)
            else -> return document
        }

        val linkedDocument = document.copy(
            blocks = blocks,
            rootBlocks = WorkspaceGraph.pruneRootBlocks(document.copy(blocks = blocks), roots),
        )
        return bump(linkedDocument)
    }

    private fun partnerBlockId(
        document: WorkspaceDocument,
        partnerConnId: ConnectionId?,
        expectedKind: ConnectionKind,
    ): BlockId? {
        partnerConnId ?: return null
        val (blockId, conn) = WorkspaceGraph.findConnection(document, partnerConnId) ?: return null
        return if (conn.kind == expectedKind) blockId else null
    }

    private fun reattachFormerBelowIfPossible(
        blocks: MutableMap<BlockId, BlockNode>,
        roots: MutableList<BlockId>,
        insertedBlockId: BlockId,
        formerPartnerId: BlockId?,
    ): MutableMap<BlockId, BlockNode> {
        if (formerPartnerId == null || formerPartnerId == insertedBlockId) return blocks
        val inserted = blocks[insertedBlockId] ?: return blocks
        val partner = blocks[formerPartnerId] ?: return blocks
        val insertedNext = inserted.next ?: return blocks
        val partnerPrev = partner.previous ?: return blocks
        if (inserted.next?.connectedTo != null) return blocks
        var updated = link(blocks, insertedBlockId, insertedNext.id, partnerPrev.id)
        updated = link(updated, formerPartnerId, partnerPrev.id, insertedNext.id)
        roots.remove(formerPartnerId)
        return updated
    }

    private fun reattachFormerIntoStatementStack(
        blocks: MutableMap<BlockId, BlockNode>,
        roots: MutableList<BlockId>,
        insertedBlockId: BlockId,
        formerStackHeadId: BlockId?,
        formerStackHeadPrevId: ConnectionId?,
    ): MutableMap<BlockId, BlockNode> {
        if (formerStackHeadId == null || formerStackHeadId == insertedBlockId) return blocks
        val formerPrevId = formerStackHeadPrevId ?: return blocks
        val inserted = blocks[insertedBlockId] ?: return blocks
        var updated = blocks
        inserted.next?.connectedTo?.let { updated = disconnectAt(updated, inserted.next!!.id) }
        val insertedNext = inserted.next ?: return updated
        updated = link(updated, insertedBlockId, insertedNext.id, formerPrevId)
        updated = link(updated, formerStackHeadId, formerPrevId, insertedNext.id)
        roots.remove(formerStackHeadId)
        return updated
    }

    private fun disconnect(document: WorkspaceDocument, connectionId: ConnectionId): WorkspaceDocument {
        val blocks = disconnectAt(document.blocks.toMutableMap(), connectionId)
        return bump(document.copy(blocks = blocks))
    }

    private fun disconnectAt(blocks: MutableMap<BlockId, BlockNode>, connectionId: ConnectionId): MutableMap<BlockId, BlockNode> {
        val ownerEntry = blocks.entries.firstOrNull { (_, block) ->
            block.allConnections().any { it.id == connectionId }
        } ?: return blocks
        val connection = ownerEntry.value.allConnections().first { it.id == connectionId }
        val partnerId = connection.connectedTo ?: return blocks
        val partnerEntry = blocks.entries.firstOrNull { (_, block) ->
            block.allConnections().any { it.id == partnerId }
        } ?: return blocks

        blocks[ownerEntry.key] = ownerEntry.value.withConnectionUpdated(connectionId) { it.copy(connectedTo = null) }
        blocks[partnerEntry.key] = partnerEntry.value.withConnectionUpdated(partnerId) { it.copy(connectedTo = null) }
        return blocks
    }

    private fun link(
        blocks: MutableMap<BlockId, BlockNode>,
        blockId: BlockId,
        connectionId: ConnectionId,
        partner: ConnectionId,
    ): MutableMap<BlockId, BlockNode> {
        blocks[blockId] = blocks[blockId]!!.withConnectionUpdated(connectionId) { it.copy(connectedTo = partner) }
        return blocks
    }

    private fun canConnect(source: Connection, target: Connection): Boolean = when (source.kind to target.kind) {
        ConnectionKind.Next to ConnectionKind.Previous -> true
        ConnectionKind.Previous to ConnectionKind.Next -> true
        ConnectionKind.Output to ConnectionKind.ValueInput -> true
        ConnectionKind.StatementInput to ConnectionKind.Previous -> true
        ConnectionKind.Previous to ConnectionKind.StatementInput -> true
        else -> false
    }

    private fun typesCompatible(output: Connection, input: Connection): Boolean {
        val outputType = output.provides ?: output.accepts.firstOrNull() ?: return true
        if (input.accepts.isEmpty()) return true
        if (outputType == "Any" || "Any" in input.accepts) return true
        return outputType in input.accepts
    }

    private fun moveRoot(document: WorkspaceDocument, blockId: BlockId, x: Float, y: Float): WorkspaceDocument {
        if (blockId !in document.blocks) return document
        val moved = document.withRootOffset(blockId, x, y)
        val roots = WorkspaceGraph.pruneRootBlocks(
            moved,
            if (blockId in document.rootBlocks) document.rootBlocks else document.rootBlocks + blockId,
        )
        return bump(moved.copy(rootBlocks = roots))
    }

    private fun setCollapsed(document: WorkspaceDocument, blockId: BlockId, collapsed: Boolean): WorkspaceDocument {
        val block = document.blocks[blockId] ?: return document
        return bump(document.copy(blocks = document.blocks + (blockId to block.copy(collapsed = collapsed))))
    }

    private fun updateField(
        document: WorkspaceDocument,
        blockId: BlockId,
        key: String,
        value: FieldValue,
    ): WorkspaceDocument {
        val block = document.blocks[blockId] ?: return document
        return bump(document.copy(blocks = document.blocks + (blockId to block.copy(fields = block.fields + (key to value)))))
    }

    private fun createVariable(document: WorkspaceDocument, variable: VariableDefinition): WorkspaceDocument {
        if (variable.id in document.variables.variables) return document
        return bump(
            document.copy(
                variables = document.variables.copy(
                    variables = document.variables.variables + (variable.id to variable),
                ),
            ),
        )
    }

    private fun deleteVariable(document: WorkspaceDocument, variableId: String): WorkspaceDocument {
        if (variableId !in document.variables.variables) return document
        return bump(
            document.copy(
                variables = document.variables.copy(
                    variables = document.variables.variables - variableId,
                ),
            ),
        )
    }
}
