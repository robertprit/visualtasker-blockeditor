package de.visualtasker.blockeditor.interaction

import de.visualtasker.blockeditor.layout.LayoutEngine
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.SampleWorkspaceFactory
import org.junit.Assert.assertNull
import org.junit.Test

class OccupiedSnapTest {
    private val layoutEngine = LayoutEngine()
    private val snapEngine = SnapEngine()

    @Test
    fun findSnapCandidate_skipsOccupiedStatementSlotWhenDraggedBlockHasTail() {
        val document = SampleWorkspaceFactory.createWithStatementSlot()
        val layout = layoutEngine.build(document).flatIndex
        val waitId = document.blocks.entries.first { it.value.type == BlockTypes.ACTION_WAIT }.key
        val waitPrevious = document.blocks[waitId]!!.previous!!.id
        val repeatStmt = document.blocks.entries.first { it.value.type == BlockTypes.CONTROL_REPEAT }.key
            .let { id -> document.blocks[id]!!.statementInputs.first().connection.id }
        val waitAnchor = layout.connectionAnchors.first { it.connectionId == waitPrevious }
        val stmtAnchor = layout.connectionAnchors.first { it.connectionId == repeatStmt }

        val dragOffset = de.visualtasker.blockeditor.domain.Offset2(
            x = stmtAnchor.x - waitAnchor.x,
            y = stmtAnchor.y - waitAnchor.y + 8f,
        )
        val dragSession = DragSession(
            rootBlockId = waitId,
            includedBlocks = setOf(waitId),
            pullMode = DragPullMode.Single,
            startPointer = de.visualtasker.blockeditor.domain.Offset2(0f, 0f),
            currentPointer = de.visualtasker.blockeditor.domain.Offset2(0f, 0f),
            originalLayoutPosition = de.visualtasker.blockeditor.domain.Offset2(40f, 120f),
            dragOffset = dragOffset,
            originalAnchors = layout.connectionAnchors.filter { it.ownerBlockId == waitId },
        )

        val candidate = snapEngine.findSnapCandidate(layout, dragSession, document)
        assertNull(candidate)
    }
}
