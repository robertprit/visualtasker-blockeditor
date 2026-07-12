package de.visualtasker.blockeditor.interaction

import de.visualtasker.blockeditor.domain.WorkspaceGraph
import de.visualtasker.blockeditor.layout.LayoutEngine
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry
import de.visualtasker.blockeditor.registry.SampleWorkspaceFactory
import de.visualtasker.blockeditor.registry.asFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NestRepeatTest {
    private val layoutEngine = LayoutEngine()
    private val snapEngine = SnapEngine()

    @Test
    fun connect_insertsRepeatIntoRepeatDoSlot() {
        val factory = DefaultBlockRegistry.asFactory()
        var document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val outerRepeat = chain[2]

        document = de.visualtasker.blockeditor.domain.WorkspaceReducer.reduce(
            document,
            de.visualtasker.blockeditor.domain.WorkspaceAction.InstantiateBlock(
                BlockTypes.CONTROL_REPEAT,
                200f,
                200f,
            ),
            factory,
        )
        val innerRepeat = document.rootBlocks.last { it !in chain }
        val outer = document.blocks[outerRepeat]!!

        document = de.visualtasker.blockeditor.domain.WorkspaceReducer.reduce(
            document,
            de.visualtasker.blockeditor.domain.WorkspaceAction.Connect(
                outer.statementInputs.first().connection.id,
                document.blocks[innerRepeat]!!.previous!!.id,
            ),
        )

        assertEquals(
            listOf(innerRepeat),
            WorkspaceGraph.statementStack(document, outerRepeat, BlockTypes.SLOT_DO),
        )
    }

    @Test
    fun snap_findsRepeatDoSlot_forFloatingInnerRepeat() {
        val factory = DefaultBlockRegistry.asFactory()
        var document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val outerRepeat = chain[2]

        document = de.visualtasker.blockeditor.domain.WorkspaceReducer.reduce(
            document,
            de.visualtasker.blockeditor.domain.WorkspaceAction.InstantiateBlock(
                BlockTypes.CONTROL_REPEAT,
                200f,
                200f,
            ),
            factory,
        )
        val innerRepeat = document.rootBlocks.last { it !in chain }

        val layout = layoutEngine.build(document)
        val outerStmt = document.blocks[outerRepeat]!!.statementInputs.first().connection.id
        val innerPrev = document.blocks[innerRepeat]!!.previous!!.id
        val stmtAnchor = layout.flatIndex.connectionAnchors.first { it.connectionId == outerStmt }
        val prevAnchor = layout.flatIndex.connectionAnchors.first { it.connectionId == innerPrev }

        val dragOffset = de.visualtasker.blockeditor.domain.Offset2(
            x = stmtAnchor.x - prevAnchor.x,
            y = stmtAnchor.y - prevAnchor.y + 6f,
        )
        val begin = DragOperations.beginDrag(
            document = document,
            layoutCache = layout,
            blockId = innerRepeat,
            pointer = de.visualtasker.blockeditor.domain.Offset2(0f, 0f),
            ViewportState(),
        )
        val candidate = snapEngine.findSnapCandidate(
            layout.flatIndex,
            begin.dragSession!!.copy(dragOffset = dragOffset),
            document,
        )

        assertNotNull(candidate)
        assertEquals(outerStmt, candidate!!.targetConnectionId)
        assertEquals(innerPrev, candidate.sourceConnectionId)
    }

    @Test
    fun endDrag_insertsRepeatFromChainIntoOuterRepeatDoSlot() {
        val document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val outerRepeat = chain[2]
        val innerRepeat = chain[4]

        val layoutDoc = DragLayoutPreview.layoutDocument(document, innerRepeat, setOf(innerRepeat))
        val snapDoc = DragLayoutPreview.snapDocument(document, innerRepeat, setOf(innerRepeat))
        val layout = layoutEngine.build(layoutDoc)

        val outerStmt = document.blocks[outerRepeat]!!.statementInputs.first().connection.id
        val innerPrev = document.blocks[innerRepeat]!!.previous!!.id
        val stmtAnchor = layout.flatIndex.connectionAnchors.first { it.connectionId == outerStmt }
        val prevAnchor = layout.flatIndex.connectionAnchors.first { it.connectionId == innerPrev }

        val dragOffset = de.visualtasker.blockeditor.domain.Offset2(
            x = stmtAnchor.x - prevAnchor.x,
            y = stmtAnchor.y - prevAnchor.y + 6f,
        )
        val begin = DragOperations.beginDrag(
            document = document,
            layoutCache = layout,
            blockId = innerRepeat,
            pointer = de.visualtasker.blockeditor.domain.Offset2(0f, 0f),
            ViewportState(),
            pullMode = DragPullMode.Single,
        )
        val candidate = snapEngine.findSnapCandidate(
            layout.flatIndex,
            begin.dragSession!!.copy(dragOffset = dragOffset),
            snapDoc,
        )
        assertNotNull(candidate)

        val transient = begin.copy(
            dragSession = begin.dragSession!!.copy(dragOffset = dragOffset),
            activeSnapCandidate = candidate,
        )
        val (result, _) = DragOperations.endDrag(transient, document)

        assertEquals(
            listOf(innerRepeat),
            WorkspaceGraph.statementStack(result, outerRepeat, BlockTypes.SLOT_DO),
        )
        assertEquals(chain[5], WorkspaceGraph.nextChain(result, chain[3]))
    }

    @Test
    fun endDrag_rightDragInsertsLowerRepeatWithClicksBelowIntoUpperRepeat() {
        val document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val upperRepeat = chain[2]
        val lowerRepeat = chain[4]

        val layoutDoc = DragLayoutPreview.layoutDocument(document, lowerRepeat, setOf(lowerRepeat))
        val snapDoc = DragLayoutPreview.snapDocument(document, lowerRepeat, setOf(lowerRepeat))
        val layout = layoutEngine.build(layoutDoc)

        val outerStmt = document.blocks[upperRepeat]!!.statementInputs.first().connection.id
        val innerPrev = document.blocks[lowerRepeat]!!.previous!!.id
        val stmtAnchor = layout.flatIndex.connectionAnchors.first { it.connectionId == outerStmt }
        val prevAnchor = layout.flatIndex.connectionAnchors.first { it.connectionId == innerPrev }
        val dragOffset = de.visualtasker.blockeditor.domain.Offset2(
            x = stmtAnchor.x - prevAnchor.x,
            y = stmtAnchor.y - prevAnchor.y + 6f,
        )

        val begin = DragOperations.beginDrag(
            document = document,
            layoutCache = layout,
            blockId = lowerRepeat,
            pointer = de.visualtasker.blockeditor.domain.Offset2(0f, 0f),
            ViewportState(),
            pullMode = DragPullMode.Single,
        )
        assertEquals(1, begin.dragSession!!.includedBlocks.size)

        val candidate = snapEngine.findSnapCandidate(
            layout.flatIndex,
            begin.dragSession!!.copy(dragOffset = dragOffset),
            snapDoc,
        )
        assertNotNull(candidate)
        assertEquals(outerStmt, candidate!!.targetConnectionId)

        val transient = begin.copy(
            dragSession = begin.dragSession!!.copy(dragOffset = dragOffset),
            activeSnapCandidate = candidate,
        )
        val (result, _) = DragOperations.endDrag(transient, document)

        assertEquals(
            listOf(lowerRepeat),
            WorkspaceGraph.statementStack(result, upperRepeat, BlockTypes.SLOT_DO),
        )
        assertEquals(chain[5], WorkspaceGraph.nextChain(result, chain[3]))
        assertEquals(chain[6], WorkspaceGraph.nextChain(result, chain[5]))
    }

    @Test
    fun snap_prefersDoSlotOverChainNext_whenNestedRepeat() {
        val document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val outerRepeat = chain[2]
        val innerRepeat = chain[4]

        val layout = layoutEngine.build(document)
        val outerStmt = document.blocks[outerRepeat]!!.statementInputs.first().connection.id
        val innerPrev = document.blocks[innerRepeat]!!.previous!!.id
        val stmtAnchor = layout.flatIndex.connectionAnchors.first { it.connectionId == outerStmt }
        val prevAnchor = layout.flatIndex.connectionAnchors.first { it.connectionId == innerPrev }

        val dragOffset = de.visualtasker.blockeditor.domain.Offset2(
            x = stmtAnchor.x - prevAnchor.x,
            y = stmtAnchor.y - prevAnchor.y + 6f,
        )
        val begin = DragOperations.beginDrag(
            document = document,
            layoutCache = layout,
            blockId = innerRepeat,
            pointer = de.visualtasker.blockeditor.domain.Offset2(0f, 0f),
            ViewportState(),
        )
        val candidate = snapEngine.findSnapCandidate(
            layout.flatIndex,
            begin.dragSession!!.copy(dragOffset = dragOffset),
            DragLayoutPreview.snapDocument(document, innerRepeat, setOf(innerRepeat)),
        )

        assertNotNull(candidate)
        assertEquals(outerStmt, candidate!!.targetConnectionId)
        assertEquals(innerPrev, candidate.sourceConnectionId)
    }
}
