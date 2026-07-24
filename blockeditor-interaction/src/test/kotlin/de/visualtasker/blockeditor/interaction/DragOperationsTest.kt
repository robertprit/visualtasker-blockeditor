package de.visualtasker.blockeditor.interaction

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.Offset2
import de.visualtasker.blockeditor.domain.Rect
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.WorkspaceReducer
import de.visualtasker.blockeditor.domain.withRootOffset
import de.visualtasker.blockeditor.layout.LayoutEngine
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry
import de.visualtasker.blockeditor.registry.asFactory
import de.visualtasker.blockeditor.registry.createNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DragOperationsTest {
    private val factory = DefaultBlockRegistry.asFactory()
    private val layoutEngine = LayoutEngine(DefaultBlockRegistry)

    @Test
    fun blockTouchZoneSplitsBlockIntoGroupLabelAndSingleAreas() {
        val bounds = Rect(x = 90f, y = 40f, width = 300f, height = 80f)

        assertEquals(BlockTouchZone.LeftGroup, DragOperations.detectTouchZone(bounds, Offset2(100f, 64f)))
        assertEquals(BlockTouchZone.CenterLabel, DragOperations.detectTouchZone(bounds, Offset2(240f, 64f)))
        assertEquals(BlockTouchZone.RightSingle, DragOperations.detectTouchZone(bounds, Offset2(380f, 64f)))
        assertEquals(DragPullMode.StackBelow, DragOperations.detectPullMode(bounds, Offset2(100f, 64f)))
        assertEquals(DragPullMode.Single, DragOperations.detectPullMode(bounds, Offset2(380f, 64f)))
    }

    @Test
    fun dragMove_doesNotChangeDocumentVersion() {
        val blockId = BlockId("root")
        val block = DefaultBlockRegistry.getDefinition("event.start")!!
            .createNode(blockId)
            .withRootOffset(100f, 100f)

        var document = WorkspaceReducer.reduce(
            WorkspaceDocument(id = "drag-test"),
            de.visualtasker.blockeditor.domain.WorkspaceAction.InstantiateBlock("event.start", 100f, 100f),
            factory,
        )
        document = document.copy(blocks = mapOf(blockId to block))
        val versionBefore = document.version
        val layoutCache = layoutEngine.build(document)

        var transient = DragOperations.beginDrag(
            document = document,
            layoutCache = layoutCache,
            blockId = blockId,
            pointer = Offset2(120f, 120f),
            viewport = ViewportState(),
        )
        assertNotNull(transient.dragSession)

        repeat(5) { step ->
            val (updatedTransient, sameDocument) = DragOperations.updateDrag(
                transient = transient,
                pointer = Offset2(120f + step * 10f, 120f + step * 8f),
                layoutCache = layoutCache,
                document = document,
            )
            transient = updatedTransient
            assertEquals("Drag move must not mutate document", versionBefore, sameDocument.version)
            assertEquals("Drag move must not mutate document", document, sameDocument)
        }
    }
}
