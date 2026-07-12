package de.visualtasker.blockeditor.interaction

import de.visualtasker.blockeditor.domain.WorkspaceAction
import de.visualtasker.blockeditor.domain.WorkspaceGraph
import de.visualtasker.blockeditor.domain.WorkspaceReducer
import de.visualtasker.blockeditor.domain.withRootOffset
import de.visualtasker.blockeditor.layout.LayoutEngine
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry
import de.visualtasker.blockeditor.registry.SampleWorkspaceFactory
import de.visualtasker.blockeditor.registry.asFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EndDragTest {
    private val layoutEngine = LayoutEngine()

    @Test
    fun endDrag_withoutSnap_keepsChainDescendantsVisible() {
        val document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val clickId = chain[1]
        val repeatId = chain[2]
        val layoutCache = layoutEngine.build(document)

        val begin = DragOperations.beginDrag(
            document,
            layoutCache,
            clickId,
            de.visualtasker.blockeditor.domain.Offset2(0f, 0f),
            ViewportState(),
        )
        val session = begin.dragSession!!
        val moved = session.copy(dragOffset = de.visualtasker.blockeditor.domain.Offset2(10f, 5f))
        val transient = begin.copy(dragSession = moved)

        val (result, _) = DragOperations.endDrag(transient, document)

        val visibleIds = layoutEngine.build(result).flatIndex.visibleBlocks.map { it.blockId }.toSet()

        assertEquals(repeatId, WorkspaceGraph.nextChain(result, clickId))
        assertTrue(chain.drop(1).all { it in visibleIds })
    }

    @Test
    fun endDrag_snapInsertsRepeatBetweenStartAndClick() {
        val document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val startId = chain[0]
        val clickId = chain[1]
        val repeatId = chain[2]

        val included = setOf(repeatId)
        val layoutDoc = DragLayoutPreview.layoutDocument(document, repeatId, included)
        val snapDoc = DragLayoutPreview.snapDocument(document, repeatId, included)
        val staticLayout = layoutEngine.build(layoutDoc)
        val begin = DragOperations.beginDrag(
            document = document,
            layoutCache = staticLayout,
            blockId = repeatId,
            pointer = de.visualtasker.blockeditor.domain.Offset2(0f, 0f),
            ViewportState(),
            pullMode = DragPullMode.Single,
        )
        val repeatPrevious = document.blocks[repeatId]!!.previous!!.id
        val startNext = document.blocks[startId]!!.next!!.id
        val repeatAnchor = staticLayout.flatIndex.connectionAnchors.first { it.connectionId == repeatPrevious }
        val startAnchor = staticLayout.flatIndex.connectionAnchors.first { it.connectionId == startNext }
        val dragOffset = de.visualtasker.blockeditor.domain.Offset2(
            x = startAnchor.x - repeatAnchor.x,
            y = startAnchor.y - repeatAnchor.y + 8f,
        )
        val snapEngine = SnapEngine()
        val candidate = snapEngine.findSnapCandidate(
            staticLayout.flatIndex,
            begin.dragSession!!.copy(dragOffset = dragOffset),
            snapDoc,
        )
        assertNotNull(candidate)

        val transient = begin.copy(
            dragSession = begin.dragSession!!.copy(dragOffset = dragOffset),
            activeSnapCandidate = candidate,
        )
        val (result, _) = DragOperations.endDrag(transient, document)

        assertEquals(repeatId, WorkspaceGraph.nextChain(result, startId))
        assertEquals(clickId, WorkspaceGraph.nextChain(result, repeatId))
    }

    @Test
    fun endDrag_snapConnectsStartClickToPreviouslyDetachedClick() {
        val document = workspaceWithTwoClicksAtStart()
        val startId = document.rootBlocks.first()
        val click1 = WorkspaceGraph.nextChain(document, startId)!!
        val click2 = WorkspaceGraph.nextChain(document, click1)!!
        val layoutCache = layoutEngine.build(document)

        val detachBegin = DragOperations.beginDrag(
            document = document,
            layoutCache = layoutCache,
            blockId = click2,
            pointer = de.visualtasker.blockeditor.domain.Offset2(0f, 0f),
            ViewportState(),
            pullMode = DragPullMode.Single,
        )
        val detachedSession = detachBegin.dragSession!!.copy(
            dragOffset = de.visualtasker.blockeditor.domain.Offset2(120f, 180f),
        )
        val (detachedDoc, _) = DragOperations.endDrag(
            detachBegin.copy(dragSession = detachedSession),
            document,
        )

        assertEquals(click1, WorkspaceGraph.nextChain(detachedDoc, startId))
        assertNull(WorkspaceGraph.nextChain(detachedDoc, click1))

        val preLayout = layoutEngine.build(detachedDoc)
        val click1Bounds = preLayout.flatIndex.visibleBlocks.first { it.blockId == click1 }.bounds
        val included = setOf(click1)
        var layoutDoc = DragLayoutPreview.layoutDocument(detachedDoc, click1, included)
        layoutDoc = layoutDoc.copy(
            blocks = layoutDoc.blocks + (
                click1 to layoutDoc.blocks[click1]!!.withRootOffset(click1Bounds.x, click1Bounds.y)
                ),
        )
        val staticLayout = layoutEngine.build(layoutDoc)
        val snapDoc = DragLayoutPreview.snapDocument(detachedDoc, click1, included)

        val click1Next = detachedDoc.blocks[click1]!!.next!!.id
        val click2Prev = detachedDoc.blocks[click2]!!.previous!!.id
        val sourceAnchor = staticLayout.flatIndex.connectionAnchors
            .first { it.connectionId == click1Next }
        val targetAnchor = staticLayout.flatIndex.connectionAnchors
            .first { it.connectionId == click2Prev }
        val dragOffset = de.visualtasker.blockeditor.domain.Offset2(
            x = targetAnchor.x - sourceAnchor.x,
            y = targetAnchor.y - sourceAnchor.y + 8f,
        )

        val connectBegin = DragOperations.beginDrag(
            document = detachedDoc,
            layoutCache = preLayout,
            blockId = click1,
            pointer = de.visualtasker.blockeditor.domain.Offset2(0f, 0f),
            ViewportState(),
            pullMode = DragPullMode.Single,
        )
        val rootLayout = staticLayout.flatIndex.visibleBlocks.first { it.blockId == click1 }
        val connectSession = connectBegin.dragSession!!.copy(
            originalAnchors = staticLayout.flatIndex.connectionAnchors.filter { it.ownerBlockId == click1 },
            originalLayoutPosition = de.visualtasker.blockeditor.domain.Offset2(
                rootLayout.bounds.x,
                rootLayout.bounds.y,
            ),
            dragOffset = dragOffset,
        )
        val candidate = SnapEngine().findSnapCandidate(
            staticLayout.flatIndex,
            connectSession,
            snapDoc,
        )
        assertNotNull(candidate)

        val (result, _) = DragOperations.endDrag(
            connectBegin.copy(
                dragSession = connectSession,
                activeSnapCandidate = candidate,
            ),
            detachedDoc,
        )

        assertNull(WorkspaceGraph.nextChain(result, startId))
        assertEquals(click2, WorkspaceGraph.nextChain(result, click1))
        val resultLayout = layoutEngine.build(result)
        val click1Y = resultLayout.flatIndex.visibleBlocks.first { it.blockId == click1 }.bounds.y
        val startY = resultLayout.flatIndex.visibleBlocks.first { it.blockId == startId }.bounds.y
        assertTrue(click1Y > startY + 40f)
    }

    private fun workspaceWithTwoClicksAtStart(): de.visualtasker.blockeditor.domain.WorkspaceDocument {
        val factory = DefaultBlockRegistry.asFactory()
        var document = SampleWorkspaceFactory.create()
        val startId = document.rootBlocks.first()
        document = WorkspaceReducer.reduce(
            document,
            WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_CLICK_TEXT, 40f, 120f),
            factory,
        )
        val click1 = document.rootBlocks.last()
        document = WorkspaceReducer.reduce(
            document,
            WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_CLICK_TEXT, 40f, 200f),
            factory,
        )
        val click2 = document.rootBlocks.last()
        val start = document.blocks[startId]!!
        val firstClick = document.blocks[click1]!!
        val secondClick = document.blocks[click2]!!
        document = WorkspaceReducer.reduce(
            document,
            WorkspaceAction.Connect(start.next!!.id, firstClick.previous!!.id),
        )
        document = WorkspaceReducer.reduce(
            document,
            WorkspaceAction.Connect(firstClick.next!!.id, secondClick.previous!!.id),
        )
        return document
    }
}
