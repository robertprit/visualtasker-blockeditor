package de.visualtasker.blockeditor.interaction

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.ConnectionKind
import de.visualtasker.blockeditor.domain.Offset2
import de.visualtasker.blockeditor.domain.Rect
import de.visualtasker.blockeditor.domain.WorkspaceAction
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.WorkspaceGraph
import de.visualtasker.blockeditor.domain.WorkspaceReducer
import de.visualtasker.blockeditor.layout.LayoutCache

private const val SCRIPT_START_TYPE = "event.start"

object DragOperations {
    fun beginDrag(
        document: WorkspaceDocument,
        layoutCache: LayoutCache,
        blockId: BlockId,
        pointer: Offset2,
        viewport: ViewportState,
        pullMode: DragPullMode? = null,
    ): TransientEditorState {
        val workspacePointer = viewport.localToWorkspace(pointer)
        val layoutBounds = layoutCache.flatIndex.visibleBlocks.find { it.blockId == blockId }?.bounds
        val block = document.blocks[blockId]
        val resolvedPullMode = when {
            block?.type == SCRIPT_START_TYPE -> DragPullMode.StackBelow
            pullMode != null -> pullMode
            else -> detectPullMode(layoutBounds, workspacePointer)
        }
        val included = collectDragSubtree(document, blockId, resolvedPullMode)
        val layoutPosition = if (layoutBounds != null) {
            Offset2(layoutBounds.x, layoutBounds.y)
        } else {
            workspacePointer
        }
        val anchors = layoutCache.flatIndex.connectionAnchors.filter { it.ownerBlockId == blockId }

        return TransientEditorState(
            viewport = viewport,
            dragSession = DragSession(
                rootBlockId = blockId,
                includedBlocks = included,
                pullMode = resolvedPullMode,
                startPointer = workspacePointer,
                currentPointer = workspacePointer,
                originalLayoutPosition = layoutPosition,
                dragOffset = Offset2(0f, 0f),
                originalAnchors = anchors,
            ),
        )
    }

    fun detectPullMode(blockBounds: Rect?, workspacePointer: Offset2): DragPullMode {
        if (blockBounds == null) return DragPullMode.StackBelow
        val midX = blockBounds.x + blockBounds.width / 2f
        return if (workspacePointer.x < midX) DragPullMode.StackBelow else DragPullMode.Single
    }

    fun updateDrag(
        transient: TransientEditorState,
        pointer: Offset2,
        snapEngine: SnapEngine = SnapEngine(),
        layoutCache: LayoutCache,
        document: WorkspaceDocument,
    ): Pair<TransientEditorState, WorkspaceDocument> {
        val session = transient.dragSession ?: return transient to document
        val workspacePointer = transient.viewport.localToWorkspace(pointer)
        val dragOffset = Offset2(
            x = workspacePointer.x - session.startPointer.x,
            y = workspacePointer.y - session.startPointer.y,
        )
        val updatedSession = session.copy(
            currentPointer = workspacePointer,
            dragOffset = dragOffset,
        )
        val candidate = snapEngine.findSnapCandidate(
            layout = layoutCache.flatIndex,
            dragSession = updatedSession,
            document = document,
            currentCandidate = transient.activeSnapCandidate,
        )
        return transient.copy(
            dragSession = updatedSession,
            activeSnapCandidate = candidate,
        ) to document
    }

    fun endDrag(
        transient: TransientEditorState,
        document: WorkspaceDocument,
    ): Pair<WorkspaceDocument, TransientEditorState> {
        val session = transient.dragSession
            ?: return document to transient.copy(dragSession = null, activeSnapCandidate = null)

        val candidate = transient.activeSnapCandidate
        val dropX = session.originalLayoutPosition.x + session.dragOffset.x
        val dropY = session.originalLayoutPosition.y + session.dragOffset.y

        val newDocument = if (candidate != null) {
            var doc = document
            if (WorkspaceGraph.isValuePlugged(doc, session.rootBlockId)) {
                doc = WorkspaceReducer.liftFromValuePlug(doc, session.rootBlockId)
            }
            val connectedInChain = WorkspaceGraph.previousChain(doc, session.rootBlockId) != null ||
                WorkspaceGraph.nextChain(doc, session.rootBlockId) != null
            val inStatementSlot = WorkspaceGraph.slotContaining(doc, session.rootBlockId) != null
            if (connectedInChain || inStatementSlot) {
                doc = WorkspaceReducer.liftDragGroup(doc, session.rootBlockId, session.includedBlocks)
            }
            doc = WorkspaceReducer.reduce(
                doc,
                WorkspaceAction.Connect(candidate.sourceConnectionId, candidate.targetConnectionId),
            )
            val drop = dropPosition(session, candidate)
            val chainHead = chainHeadId(doc, session.rootBlockId)
            WorkspaceReducer.reduce(
                doc,
                WorkspaceAction.MoveRoot(chainHead, drop.x, drop.y),
            )
        } else {
            var doc = document
            if (WorkspaceGraph.isValuePlugged(doc, session.rootBlockId)) {
                doc = WorkspaceReducer.liftFromValuePlug(doc, session.rootBlockId)
            }
            val connectedInChain = WorkspaceGraph.previousChain(doc, session.rootBlockId) != null ||
                WorkspaceGraph.nextChain(doc, session.rootBlockId) != null
            val inStatementSlot = WorkspaceGraph.slotContaining(doc, session.rootBlockId) != null
            if (connectedInChain || inStatementSlot) {
                doc = WorkspaceReducer.liftDragGroup(doc, session.rootBlockId, session.includedBlocks)
            }
            WorkspaceReducer.reduce(
                doc,
                WorkspaceAction.MoveRoot(session.rootBlockId, dropX, dropY),
            )
        }

        return newDocument to transient.copy(
            dragSession = null,
            activeSnapCandidate = null,
        )
    }

    private fun dropPosition(session: DragSession, candidate: SnapCandidate?): Offset2 {
        val offset = candidate?.snapOffset ?: session.dragOffset
        return Offset2(
            x = session.originalLayoutPosition.x + offset.x,
            y = session.originalLayoutPosition.y + offset.y,
        )
    }

    private fun chainHeadId(document: WorkspaceDocument, blockId: BlockId): BlockId {
        var current = blockId
        while (true) {
            val previous = WorkspaceGraph.previousChain(document, current) ?: return current
            current = previous
        }
    }

    private fun collectDragSubtree(
        document: WorkspaceDocument,
        blockId: BlockId,
        pullMode: DragPullMode,
    ): Set<BlockId> {
        val result = mutableSetOf(blockId)
        addNestedDescendants(document, blockId, result)
        if (pullMode == DragPullMode.StackBelow) {
            var current = blockId
            while (true) {
                val next = WorkspaceGraph.nextChain(document, current) ?: break
                result += next
                addNestedDescendants(document, next, result)
                current = next
            }
        }
        return WorkspaceGraph.expandDragClosure(document, result)
    }

    private fun addNestedDescendants(
        document: WorkspaceDocument,
        blockId: BlockId,
        result: MutableSet<BlockId>,
    ) {
        val block = document.blocks[blockId] ?: return
        block.statementInputs.forEach { slot ->
            WorkspaceGraph.statementStack(document, blockId, slot.name).forEach { childId ->
                if (result.add(childId)) {
                    addNestedDescendants(document, childId, result)
                }
            }
        }
        block.valueInputs.forEach { input ->
            val connected = input.connection.connectedTo ?: return@forEach
            val (valueBlockId, conn) = WorkspaceGraph.findConnection(document, connected) ?: return@forEach
            if (conn.kind == ConnectionKind.Output && result.add(valueBlockId)) {
                addNestedDescendants(document, valueBlockId, result)
            }
        }
    }
}
