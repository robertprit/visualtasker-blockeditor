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
        propertySchema: SemanticPropertySchema = CanonicalSemanticPropertySchema,
    ): WorkspaceDocument = WorkspaceActionOperationAdapter.reduce(
        document = document,
        actions = listOf(action),
        factory = factory,
        propertySchema = propertySchema,
    )

    /** Applies one logical editor command as one atomic canonical transaction. */
    fun reduceAll(
        document: WorkspaceDocument,
        actions: List<WorkspaceAction>,
        factory: BlockFactory = BlockFactory { _, _ -> null },
        propertySchema: SemanticPropertySchema = CanonicalSemanticPropertySchema,
    ): WorkspaceDocument = WorkspaceActionOperationAdapter.reduce(
        document = document,
        actions = actions,
        factory = factory,
        propertySchema = propertySchema,
        requireEveryActionToApply = true,
    )

    fun detachBlock(document: WorkspaceDocument, blockId: BlockId): WorkspaceDocument =
        reduce(document, WorkspaceAction.DetachBlock(blockId))

    fun liftBlock(document: WorkspaceDocument, blockId: BlockId): WorkspaceDocument = applyLiftCandidate(
        document,
        CanonicalWorkspaceReducer.liftBlock(document, blockId),
    )

    fun liftDragGroup(
        document: WorkspaceDocument,
        blockId: BlockId,
        includedBlocks: Set<BlockId>,
    ): WorkspaceDocument = applyLiftCandidate(
        document,
        CanonicalWorkspaceReducer.liftBlock(document, blockId, includedBlocks),
    )

    fun liftFromValuePlug(document: WorkspaceDocument, blockId: BlockId): WorkspaceDocument = applyLiftCandidate(
        document,
        CanonicalWorkspaceReducer.liftBlock(document, blockId),
    )

    private fun applyLiftCandidate(document: WorkspaceDocument, candidate: WorkspaceDocument): WorkspaceDocument {
        if (candidate == document) return document
        return WorkspaceActionOperationAdapter.publishCandidate(
            document = document,
            candidate = candidate,
            propertySchema = CanonicalSemanticPropertySchema,
        )
    }
}
