package de.visualtasker.blockeditor.interaction

import de.visualtasker.blockeditor.layout.LayoutEngine
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.SampleWorkspaceFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DragSubtreeTest {
    private val layoutEngine = LayoutEngine()

    @Test
    fun rightDrag_pullsSingleBlockFromStatementSlot() {
        val document = SampleWorkspaceFactory.createWithStatementSlot()
        val clickId = document.blocks.entries.first { it.value.type == BlockTypes.ACTION_CLICK_TEXT }.key

        val begin = DragOperations.beginDrag(
            document = document,
            layoutCache = layoutEngine.build(document),
            blockId = clickId,
            pointer = de.visualtasker.blockeditor.domain.Offset2(0f, 0f),
            ViewportState(),
            pullMode = DragPullMode.Single,
        )

        assertEquals(1, begin.dragSession!!.includedBlocks.size)
    }

    @Test
    fun rightDrag_pullsSingleBlock_notChainBelow() {
        val document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val clickBetweenRepeats = chain[3]
        val repeatBelow = chain[4]

        val begin = DragOperations.beginDrag(
            document = document,
            layoutCache = layoutEngine.build(document),
            blockId = clickBetweenRepeats,
            pointer = de.visualtasker.blockeditor.domain.Offset2(0f, 0f),
            ViewportState(),
            pullMode = DragPullMode.Single,
        )

        val included = begin.dragSession!!.includedBlocks
        assertEquals(1, included.size)
        assertTrue(clickBetweenRepeats in included)
        assertFalse(repeatBelow in included)
    }

    @Test
    fun leftDrag_includesFullScriptBelowStart() {
        val document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val startId = chain[0]

        val begin = DragOperations.beginDrag(
            document = document,
            layoutCache = layoutEngine.build(document),
            blockId = startId,
            pointer = de.visualtasker.blockeditor.domain.Offset2(0f, 0f),
            ViewportState(),
            pullMode = DragPullMode.StackBelow,
        )

        val included = begin.dragSession!!.includedBlocks
        assertTrue(chain.drop(1).all { it in included })
    }

    @Test
    fun leftDrag_onMiddleRepeat_includesChainBelow() {
        val document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val repeatId = chain[2]

        val begin = DragOperations.beginDrag(
            document = document,
            layoutCache = layoutEngine.build(document),
            blockId = repeatId,
            pointer = de.visualtasker.blockeditor.domain.Offset2(0f, 0f),
            ViewportState(),
            pullMode = DragPullMode.StackBelow,
        )

        val included = begin.dragSession!!.includedBlocks
        assertTrue(chain[3] in included)
        assertTrue(chain[4] in included)
    }

    @Test
    fun rightDrag_onLowerRepeat_excludesClicksBelow() {
        val document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val lowerRepeat = chain[4]

        val begin = DragOperations.beginDrag(
            document = document,
            layoutCache = layoutEngine.build(document),
            blockId = lowerRepeat,
            pointer = de.visualtasker.blockeditor.domain.Offset2(0f, 0f),
            ViewportState(),
            pullMode = DragPullMode.Single,
        )

        val included = begin.dragSession!!.includedBlocks
        assertEquals(1, included.size)
        assertTrue(lowerRepeat in included)
        assertFalse(chain[5] in included)
        assertFalse(chain[6] in included)
    }

    @Test
    fun detectPullMode_usesPointerSideOfBlock() {
        val bounds = de.visualtasker.blockeditor.domain.Rect(100f, 50f, 200f, 44f)
        assertEquals(
            DragPullMode.StackBelow,
            DragOperations.detectPullMode(bounds, de.visualtasker.blockeditor.domain.Offset2(150f, 70f)),
        )
        assertEquals(
            DragPullMode.Single,
            DragOperations.detectPullMode(bounds, de.visualtasker.blockeditor.domain.Offset2(250f, 70f)),
        )
    }
}
