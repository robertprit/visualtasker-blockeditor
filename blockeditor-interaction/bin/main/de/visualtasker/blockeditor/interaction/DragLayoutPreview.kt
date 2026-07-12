package de.visualtasker.blockeditor.interaction

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.WorkspaceGraph
import de.visualtasker.blockeditor.domain.WorkspaceReducer

/**
 * Layout-Vorschau während Drag.
 * - [layoutDocument]: Kette oberhalb des gezogenen Subtrees wird geschlossen (kein Geister-Layout).
 * - [positionDocument]: Originalgraph für stabile Drag-Positionen.
 * - [snapDocument]: wie [layoutDocument] für freie Snap-Anker.
 */
object DragLayoutPreview {
    fun layoutDocument(
        document: WorkspaceDocument,
        rootBlockId: BlockId,
        includedBlocks: Set<BlockId>,
    ): WorkspaceDocument = detachedForDrag(document, rootBlockId, includedBlocks)

    fun positionDocument(document: WorkspaceDocument): WorkspaceDocument = document

    fun snapDocument(
        document: WorkspaceDocument,
        rootBlockId: BlockId,
        includedBlocks: Set<BlockId>,
    ): WorkspaceDocument = detachedForDrag(document, rootBlockId, includedBlocks)

    private fun detachedForDrag(
        document: WorkspaceDocument,
        rootBlockId: BlockId,
        includedBlocks: Set<BlockId>,
    ): WorkspaceDocument {
        var doc = document
        if (WorkspaceGraph.isValuePlugged(doc, rootBlockId)) {
            doc = WorkspaceReducer.liftFromValuePlug(doc, rootBlockId)
        }
        val inChain = WorkspaceGraph.previousChain(doc, rootBlockId) != null ||
            WorkspaceGraph.nextChain(doc, rootBlockId) != null
        val inSlot = WorkspaceGraph.slotContaining(doc, rootBlockId) != null
        return if (inChain || inSlot) {
            WorkspaceReducer.liftDragGroup(doc, rootBlockId, includedBlocks)
        } else {
            doc
        }
    }

    @Deprecated("Use layoutDocument", ReplaceWith("layoutDocument(document, rootBlockId, includedBlocks)"))
    fun staticDocument(document: WorkspaceDocument, rootBlockId: BlockId): WorkspaceDocument =
        layoutDocument(document, rootBlockId, setOf(rootBlockId))
}
