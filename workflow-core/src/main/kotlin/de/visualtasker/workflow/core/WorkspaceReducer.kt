package de.visualtasker.workflow.core

import java.util.UUID

fun interface BlockFactory {
    fun create(definitionId: String, id: BlockId): BlockNode?
}

fun newBlockId(): BlockId = BlockId(UUID.randomUUID().toString())

/** Public editor facade. All semantic writes are owned by [CanonicalWorkspaceReducer]. */
object WorkspaceReducer {
    fun reduce(
        document: WorkspaceDocument,
        action: WorkspaceAction,
        factory: BlockFactory = BlockFactory { _, _ -> null },
    ): WorkspaceDocument = CanonicalWorkspaceReducer.reduce(document, action, factory)

    fun detachBlock(document: WorkspaceDocument, blockId: BlockId): WorkspaceDocument =
        CanonicalWorkspaceReducer.reduce(
            document,
            WorkspaceAction.DetachBlock(blockId),
            BlockFactory { _, _ -> null },
        )

    fun liftBlock(document: WorkspaceDocument, blockId: BlockId): WorkspaceDocument =
        CanonicalWorkspaceReducer.liftBlock(document, blockId)

    fun liftDragGroup(
        document: WorkspaceDocument,
        blockId: BlockId,
        includedBlocks: Set<BlockId>,
    ): WorkspaceDocument = CanonicalWorkspaceReducer.liftBlock(document, blockId, includedBlocks)

    fun liftFromValuePlug(document: WorkspaceDocument, blockId: BlockId): WorkspaceDocument =
        CanonicalWorkspaceReducer.liftBlock(document, blockId)
}
