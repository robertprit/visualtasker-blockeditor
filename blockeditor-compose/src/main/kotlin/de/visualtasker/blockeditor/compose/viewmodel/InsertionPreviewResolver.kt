package de.visualtasker.blockeditor.compose.viewmodel

import de.visualtasker.blockeditor.interaction.SnapCandidate
import de.visualtasker.blockeditor.layout.LayoutCache
import de.visualtasker.blockeditor.layout.LayoutEngine
import de.visualtasker.workflow.core.BlockId
import de.visualtasker.workflow.core.WorkspaceAction
import de.visualtasker.workflow.core.WorkspaceReducer

internal fun resolveInsertionPreview(
    render: DragRenderState,
    candidate: SnapCandidate?,
    layoutEngine: LayoutEngine,
): DragRenderState {
    if (candidate == null) {
        return render.copy(
            snapCandidate = null,
            staticLayoutCache = render.baseStaticLayoutCache,
            previewAffectedBlockIds = emptySet(),
            insertionPreviewDocument = null,
        )
    }

    val previewDocument = WorkspaceReducer.reduce(
        render.previewDocument,
        WorkspaceAction.Connect(candidate.sourceConnectionId, candidate.targetConnectionId),
    )
    if (previewDocument == render.previewDocument) {
        return render.copy(
            snapCandidate = candidate,
            staticLayoutCache = render.baseStaticLayoutCache,
            previewAffectedBlockIds = emptySet(),
            insertionPreviewDocument = null,
        )
    }

    val previewLayout = layoutEngine.build(previewDocument)
    return render.copy(
        snapCandidate = candidate,
        staticLayoutCache = previewLayout,
        previewAffectedBlockIds = changedEnvironmentBlocks(
            before = render.baseStaticLayoutCache,
            after = previewLayout,
            draggedIds = render.session.includedBlocks,
        ),
        insertionPreviewDocument = previewDocument,
    )
}

private fun changedEnvironmentBlocks(
    before: LayoutCache,
    after: LayoutCache,
    draggedIds: Set<BlockId>,
): Set<BlockId> {
    val beforeById = before.flatIndex.visibleBlocks.associateBy { it.blockId }
    return after.flatIndex.visibleBlocks
        .asSequence()
        .filter { it.blockId !in draggedIds }
        .filter { placed -> beforeById[placed.blockId]?.bounds != placed.bounds }
        .mapTo(linkedSetOf()) { it.blockId }
}
