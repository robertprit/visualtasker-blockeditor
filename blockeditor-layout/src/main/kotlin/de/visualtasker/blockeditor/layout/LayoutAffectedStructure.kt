package de.visualtasker.blockeditor.layout

import de.visualtasker.blockeditor.domain.Rect
import de.visualtasker.workflow.core.BlockId
import de.visualtasker.workflow.core.BlockNode
import de.visualtasker.workflow.core.WorkspaceDocument
import de.visualtasker.workflow.core.WorkspaceGraph
import de.visualtasker.workflow.core.rootOffset

internal fun LayoutEngine.translateAffectedStructure(
    previousDocument: WorkspaceDocument,
    document: WorkspaceDocument,
    previousCache: LayoutCache,
): LayoutCache? {
    if (previousCache.documentVersion != previousDocument.version) return null
    return translatePositionOnly(previousDocument, document, previousCache)
        ?: translateRigidChainRelink(previousDocument, document, previousCache)
}

private fun translatePositionOnly(
    previousDocument: WorkspaceDocument,
    document: WorkspaceDocument,
    previousCache: LayoutCache,
): LayoutCache? {
    if (previousDocument.id != document.id) return null
    if (previousDocument.blocks != document.blocks) return null
    if (previousDocument.rootBlocks != document.rootBlocks) return null
    if (previousDocument.variables != document.variables) return null

    val previousRoots = WorkspaceGraph.topLevelRoots(previousDocument).toSet()
    val currentRoots = WorkspaceGraph.topLevelRoots(document).toSet()
    if (previousRoots != currentRoots) return null

    val translations = previousRoots.mapNotNull { rootId ->
        val before = previousDocument.rootOffset(rootId) ?: return@mapNotNull null
        val after = document.rootOffset(rootId) ?: return@mapNotNull null
        RootTranslation(rootId, after.x - before.x, after.y - before.y)
            .takeUnless { it.dx == 0f && it.dy == 0f }
    }
    if (translations.isEmpty()) return null
    return translateClosures(
        document = document,
        previousCache = previousCache,
        translations = translations,
        dirtyBlocks = translations.size,
        path = LayoutUpdatePath.ROOT_TRANSLATION,
    )
}

private fun translateRigidChainRelink(
    previousDocument: WorkspaceDocument,
    document: WorkspaceDocument,
    previousCache: LayoutCache,
): LayoutCache? {
    if (previousDocument.id != document.id || previousDocument.variables != document.variables) return null
    if (previousDocument.blocks.keys != document.blocks.keys) return null
    val changedIds = previousDocument.blocks.keys.filter { blockId ->
        previousDocument.blocks[blockId] != document.blocks[blockId]
    }
    if (changedIds.size != 2) return null
    if (changedIds.any { blockId ->
            previousDocument.blocks.getValue(blockId).withoutConnectionLinks() !=
                document.blocks.getValue(blockId).withoutConnectionLinks()
        }
    ) return null

    val beforeRoots = WorkspaceGraph.topLevelRoots(previousDocument)
    val afterRoots = WorkspaceGraph.topLevelRoots(document)
    val removedRoots = beforeRoots.toSet() - afterRoots.toSet()
    val addedRoots = afterRoots.toSet() - beforeRoots.toSet()

    val translation = when {
        removedRoots.size == 1 && addedRoots.isEmpty() -> {
            val movedRoot = removedRoots.single()
            val predecessor = WorkspaceGraph.previousChain(document, movedRoot) ?: return null
            if (WorkspaceGraph.nextChain(previousDocument, predecessor) != null) return null
            if (beforeRoots.lastOrNull() != movedRoot) return null
            val sourceRoot = topLevelRootContaining(previousDocument, predecessor) ?: return null
            if (beforeRoots.getOrNull(beforeRoots.lastIndex - 1) != sourceRoot) return null
            val predecessorBounds = previousCache.placedLayoutTree.blocks[predecessor]?.bounds ?: return null
            val movedBounds = previousCache.placedLayoutTree.blocks[movedRoot]?.bounds ?: return null
            RootTranslation(
                rootId = movedRoot,
                dx = predecessorBounds.x - movedBounds.x,
                dy = predecessorBounds.bottom + LayoutConstants.STACK_VERTICAL_GAP - movedBounds.y,
            )
        }

        addedRoots.size == 1 && removedRoots.isEmpty() -> {
            val movedRoot = addedRoots.single()
            val predecessor = WorkspaceGraph.previousChain(previousDocument, movedRoot) ?: return null
            if (WorkspaceGraph.nextChain(document, predecessor) != null) return null
            if (afterRoots.lastOrNull() != movedRoot) return null
            val sourceRoot = topLevelRootContaining(previousDocument, predecessor) ?: return null
            if (afterRoots.getOrNull(afterRoots.lastIndex - 1) != sourceRoot) return null
            val target = document.rootOffset(movedRoot) ?: return null
            val movedBounds = previousCache.placedLayoutTree.blocks[movedRoot]?.bounds ?: return null
            RootTranslation(movedRoot, target.x - movedBounds.x, target.y - movedBounds.y)
        }

        else -> return null
    }

    val beforeClosure = WorkspaceGraph.descendants(previousDocument, translation.rootId) + translation.rootId
    val afterClosure = WorkspaceGraph.descendants(document, translation.rootId) + translation.rootId
    if (beforeClosure != afterClosure) return null
    return translateClosures(
        document = document,
        previousCache = previousCache,
        translations = listOf(translation),
        dirtyBlocks = changedIds.size,
        path = LayoutUpdatePath.RIGID_CHAIN_RELINK,
    )
}

private fun topLevelRootContaining(document: WorkspaceDocument, blockId: BlockId): BlockId? =
    WorkspaceGraph.topLevelRoots(document).firstOrNull { rootId ->
        blockId == rootId || blockId in WorkspaceGraph.descendants(document, rootId)
    }

private fun BlockNode.withoutConnectionLinks(): BlockNode = copy(
    previous = previous?.copy(connectedTo = null),
    next = next?.copy(connectedTo = null),
    output = output?.copy(connectedTo = null),
    valueInputs = valueInputs.map { input ->
        input.copy(connection = input.connection.copy(connectedTo = null))
    },
    statementInputs = statementInputs.map { input ->
        input.copy(connection = input.connection.copy(connectedTo = null))
    },
)

private fun translateClosures(
    document: WorkspaceDocument,
    previousCache: LayoutCache,
    translations: List<RootTranslation>,
    dirtyBlocks: Int,
    path: LayoutUpdatePath,
): LayoutCache? {
    val translationByBlock = mutableMapOf<BlockId, RootTranslation>()
    translations.forEach { translation ->
        val closure = WorkspaceGraph.descendants(document, translation.rootId) + translation.rootId
        closure.forEach { blockId ->
            if (translationByBlock.put(blockId, translation) != null) return null
        }
    }

    fun Rect.translateFor(blockId: BlockId): Rect {
        val translation = translationByBlock[blockId] ?: return this
        return copy(x = x + translation.dx, y = y + translation.dy)
    }

    val visibleBlocks = previousCache.flatIndex.visibleBlocks.map { layout ->
        layout.copy(
            bounds = layout.bounds.translateFor(layout.blockId),
            subtreeBounds = layout.subtreeBounds.translateFor(layout.blockId),
        )
    }
    val hitPrimitives = previousCache.flatIndex.hitPrimitives.map { primitive ->
        primitive.copy(bounds = primitive.bounds.translateFor(primitive.blockId))
    }
    val connectionAnchors = previousCache.flatIndex.connectionAnchors.map { anchor ->
        val translation = translationByBlock[anchor.ownerBlockId]
        if (translation == null) anchor else anchor.copy(
            x = anchor.x + translation.dx,
            y = anchor.y + translation.dy,
        )
    }
    val statementSlots = previousCache.flatIndex.statementSlots.map { slot ->
        slot.copy(bounds = slot.bounds.translateFor(slot.blockId))
    }
    val branchSections = previousCache.flatIndex.branchSections.map { section ->
        section.copy(bounds = section.bounds.translateFor(section.blockId))
    }
    val inlineReporterLayouts = previousCache.flatIndex.inlineReporterLayouts.map { inline ->
        inline.copy(
            leftSlot = inline.leftSlot.translateFor(inline.blockId),
            operatorBounds = inline.operatorBounds.translateFor(inline.blockId),
            rightSlot = inline.rightSlot.translateFor(inline.blockId),
        )
    }

    val hitIndex = SpatialIndex<HitPrimitive>()
    hitPrimitives.forEach { primitive -> hitIndex.insert(primitive, primitive.bounds) }
    val anchorIndex = SpatialIndex<ConnectionAnchor>()
    connectionAnchors.forEach { anchor ->
        val diameter = anchor.radius * 2f
        anchorIndex.insert(
            anchor,
            Rect(anchor.x - anchor.radius, anchor.y - anchor.radius, diameter, diameter),
        )
    }
    val flatIndex = FlatLayoutIndex(
        visibleBlocks = visibleBlocks,
        hitPrimitives = hitPrimitives,
        connectionAnchors = connectionAnchors,
        statementSlots = statementSlots,
        branchSections = branchSections,
        inlineReporterLayouts = inlineReporterLayouts,
        hitIndex = hitIndex,
        anchorIndex = anchorIndex,
    )
    return LayoutCache(
        documentVersion = document.version,
        flatIndex = flatIndex,
        measuredLayoutTree = previousCache.measuredLayoutTree.copy(documentVersion = document.version),
        placedLayoutTree = LayoutPlacePass.fromFlatIndex(document, flatIndex),
        updateDiagnostics = LayoutUpdateDiagnostics(
            path = path,
            totalBlocks = document.blocks.size,
            dirtyBlocks = dirtyBlocks,
            measuredBlocks = 0,
            reusedLayoutBlocks = document.blocks.size,
            repositionedBlocks = translationByBlock.size,
            fullBuilds = 0,
            localLayoutOperations = translations.size,
        ),
    )
}

private data class RootTranslation(
    val rootId: BlockId,
    val dx: Float,
    val dy: Float,
)
