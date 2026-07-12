package de.visualtasker.blockeditor.interaction

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.ConnectionId
import de.visualtasker.blockeditor.domain.ConnectionKind
import de.visualtasker.blockeditor.domain.Offset2
import de.visualtasker.blockeditor.layout.ConnectionAnchor
import de.visualtasker.blockeditor.layout.FlatLayoutIndex
import de.visualtasker.blockeditor.layout.LayoutEngine
import de.visualtasker.blockeditor.layout.SpatialIndex
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.SampleWorkspaceFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapEngineTest {
    private val layoutEngine = LayoutEngine()
    private val snapEngine = SnapEngine()

    @Test
    fun findSnapCandidate_detectsNearbyCompatibleAnchor() {
        val document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val layout = layoutEngine.build(document).flatIndex
        val clickId = chain[3]
        val repeatId = chain[2]
        val clickPrevious = document.blocks[clickId]!!.previous!!.id
        val repeatNext = document.blocks[repeatId]!!.next!!.id
        val clickAnchor = layout.connectionAnchors.first { it.connectionId == clickPrevious }
        val repeatAnchor = layout.connectionAnchors.first { it.connectionId == repeatNext }

        val dragOffset = Offset2(
            x = repeatAnchor.x - clickAnchor.x,
            y = repeatAnchor.y - clickAnchor.y + 8f,
        )

        val dragSession = DragSession(
            rootBlockId = clickId,
            includedBlocks = setOf(clickId),
            pullMode = DragPullMode.Single,
            startPointer = Offset2(0f, 0f),
            currentPointer = Offset2(0f, 0f),
            originalLayoutPosition = Offset2(40f, 280f),
            dragOffset = dragOffset,
            originalAnchors = layout.connectionAnchors.filter { it.ownerBlockId == clickId },
        )

        val candidate = snapEngine.findSnapCandidate(layout, dragSession, document)
        assertNotNull(candidate)
        assert(candidate!!.targetConnectionId == repeatNext)
    }

    @Test
    fun findSnapCandidate_doesNotSnapSlotBlockToChainAboveParent() {
        val document = SampleWorkspaceFactory.createWithStatementSlot()
        val layout = layoutEngine.build(document).flatIndex
        val clickId = document.blocks.entries.first { it.value.type == BlockTypes.ACTION_CLICK_TEXT }.key
        val waitId = document.blocks.entries.first { it.value.type == BlockTypes.ACTION_WAIT }.key
        val repeatId = document.blocks.entries.first { it.value.type == BlockTypes.CONTROL_REPEAT }.key
        val clickPrevious = document.blocks[clickId]!!.previous!!.id
        val waitNext = document.blocks[waitId]!!.next!!.id
        val repeatNext = document.blocks[repeatId]!!.next!!.id
        val clickAnchor = layout.connectionAnchors.first { it.connectionId == clickPrevious }
        val waitAnchor = layout.connectionAnchors.first { it.connectionId == waitNext }
        val repeatNextAnchor = layout.connectionAnchors.first { it.connectionId == repeatNext }

        val dragOffset = Offset2(
            x = waitAnchor.x - clickAnchor.x,
            y = waitAnchor.y - clickAnchor.y + 8f,
        )

        val dragSession = DragSession(
            rootBlockId = clickId,
            includedBlocks = setOf(clickId),
            pullMode = DragPullMode.Single,
            startPointer = Offset2(0f, 0f),
            currentPointer = Offset2(0f, 0f),
            originalLayoutPosition = Offset2(72f, 320f),
            dragOffset = dragOffset,
            originalAnchors = layout.connectionAnchors.filter { it.ownerBlockId == clickId },
        )

        val candidate = snapEngine.findSnapCandidate(layout, dragSession, document)
        if (candidate != null) {
            assert(candidate.targetConnectionId != waitNext)
        }

        val dragToRepeat = Offset2(
            x = repeatNextAnchor.x - clickAnchor.x,
            y = repeatNextAnchor.y - clickAnchor.y + 8f,
        )
        val dragSessionRepeat = dragSession.copy(dragOffset = dragToRepeat)
        val repeatCandidate = snapEngine.findSnapCandidate(layout, dragSessionRepeat, document)
        assertNotNull(repeatCandidate)
    }

    @Test
    fun findSnapCandidate_ignoresDraggedBlockAnchorsAsTargets() {
        val document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val repeatId = chain[2]
        val layout = layoutEngine.build(document).flatIndex

        val dragSession = DragSession(
            rootBlockId = repeatId,
            includedBlocks = setOf(repeatId),
            pullMode = DragPullMode.Single,
            startPointer = Offset2(0f, 0f),
            currentPointer = Offset2(0f, 0f),
            originalLayoutPosition = Offset2(40f, 220f),
            dragOffset = Offset2(0f, 0f),
            originalAnchors = layout.connectionAnchors.filter { it.ownerBlockId == repeatId },
        )

        val candidate = snapEngine.findSnapCandidate(layout, dragSession, document)
        assertNull(candidate)
    }

    @Test
    fun findSnapCandidate_doesNotSkipDraggedSources() {
        val source = ConnectionAnchor(
            connectionId = ConnectionId("src"),
            ownerBlockId = BlockId("drag"),
            kind = ConnectionKind.Previous,
            type = null,
            x = 100f,
            y = 200f,
            radius = 8f,
            zIndex = 0,
        )
        val target = ConnectionAnchor(
            connectionId = ConnectionId("tgt"),
            ownerBlockId = BlockId("target"),
            kind = ConnectionKind.Next,
            type = null,
            x = 108f,
            y = 198f,
            radius = 8f,
            zIndex = 0,
        )
        val anchorIndex = SpatialIndex<ConnectionAnchor>()
        anchorIndex.insert(target, de.visualtasker.blockeditor.domain.Rect(100f, 190f, 16f, 16f))
        val layout = FlatLayoutIndex(
            visibleBlocks = emptyList(),
            hitPrimitives = emptyList(),
            connectionAnchors = listOf(source, target),
            statementSlots = emptyList(),
            branchSections = emptyList(),
            hitIndex = SpatialIndex(),
            anchorIndex = anchorIndex,
        )
        val dragSession = DragSession(
            rootBlockId = BlockId("drag"),
            includedBlocks = setOf(BlockId("drag")),
            pullMode = DragPullMode.Single,
            startPointer = Offset2(0f, 0f),
            currentPointer = Offset2(0f, 0f),
            originalLayoutPosition = Offset2(40f, 120f),
            dragOffset = Offset2(0f, -2f),
            originalAnchors = listOf(source),
        )

        val candidate = snapEngine.findSnapCandidate(layout, dragSession, SampleWorkspaceFactory.createDemo())
        assertNotNull(candidate)
        assert(candidate!!.targetConnectionId == target.connectionId)
    }

    @Test
    fun findSnapCandidate_repeatDragUsesRootAnchorNotSlotChild() {
        val document = SampleWorkspaceFactory.createWithStatementSlot()
        val repeatId = document.blocks.entries.first { it.value.type == BlockTypes.CONTROL_REPEAT }.key
        val startId = document.blocks.entries.first { it.value.type == BlockTypes.EVENT_START }.key
        val clickId = document.blocks.entries.first { it.value.type == BlockTypes.ACTION_CLICK_TEXT }.key

        val layoutDoc = DragLayoutPreview.layoutDocument(document, repeatId, setOf(repeatId))
        val snapDoc = DragLayoutPreview.snapDocument(document, repeatId, setOf(repeatId))
        val layout = layoutEngine.build(layoutDoc).flatIndex
        val repeatPrevious = snapDoc.blocks[repeatId]!!.previous!!.id
        val startNext = snapDoc.blocks[startId]!!.next!!.id
        val repeatAnchor = layout.connectionAnchors.first { it.connectionId == repeatPrevious }
        val startAnchor = layout.connectionAnchors.first { it.connectionId == startNext }

        val begin = DragOperations.beginDrag(
            document = document,
            layoutCache = layoutEngine.build(layoutDoc),
            blockId = repeatId,
            pointer = de.visualtasker.blockeditor.domain.Offset2(0f, 0f),
            ViewportState(),
        )
        val session = begin.dragSession!!
        assertTrue(session.originalAnchors.all { it.ownerBlockId == repeatId })
        assertTrue(clickId !in session.originalAnchors.map { it.ownerBlockId }.toSet())

        val dragOffset = Offset2(
            x = startAnchor.x - repeatAnchor.x,
            y = startAnchor.y - repeatAnchor.y + 8f,
        )
        val dragSession = session.copy(dragOffset = dragOffset)
        val candidate = snapEngine.findSnapCandidate(layout, dragSession, snapDoc)

        assertNotNull(candidate)
        assertEquals(repeatPrevious, candidate!!.sourceConnectionId)
        assertEquals(startNext, candidate.targetConnectionId)
    }
}
