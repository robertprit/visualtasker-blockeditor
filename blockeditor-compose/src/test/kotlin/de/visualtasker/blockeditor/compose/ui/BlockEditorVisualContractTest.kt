package de.visualtasker.blockeditor.compose.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.visualtasker.blockeditor.compose.theme.blockEditorColors
import de.visualtasker.blockeditor.compose.theme.darkBlockEditorColors
import de.visualtasker.blockeditor.compose.theme.lightBlockEditorColors
import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.Offset2
import de.visualtasker.blockeditor.domain.WorkspaceAction
import de.visualtasker.blockeditor.domain.withRootOffset
import de.visualtasker.blockeditor.domain.rootOffset
import de.visualtasker.blockeditor.compose.host.BlockEditorController
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.BlockCategories
import de.visualtasker.blockeditor.registry.SampleWorkspaceFactory
import de.visualtasker.blockeditor.registry.WorkspaceBootstrap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockEditorVisualContractTest {
    @Test
    fun `toolbar and trash affordances meet material touch target contract`() {
        assertEquals(48.dp, BlockEditorToolbarTouchTargetDp)
        assertTrue(BlockEditorTrashDropTargetSizeDp >= 48.dp)
    }

    @Test
    fun `light and dark editor tokens keep blocks grid snap and unsupported states visible`() {
        listOf(lightBlockEditorColors(), darkBlockEditorColors()).forEach { colors ->
            assertNotEquals(colors.workspaceBackground, colors.gridDot)
            assertNotEquals(colors.workspaceBackground, colors.snapHighlight)
            assertNotEquals(colors.workspaceBackground, colors.unsupportedFill)
            assertNotEquals(colors.unsupportedFill, colors.unsupportedStroke)
            assertNotEquals(colors.unsupportedFill, colors.unsupportedText)
            assertTrue(contrastRatio(colors.unsupportedFill, colors.unsupportedText) >= 4.5)
            assertTrue(colors.gridDot.alpha in 0.10f..0.50f)
            assertTrue(colors.snapHighlight.alpha in 0.20f..0.70f)
        }
    }

    @Test
    fun `command reference categories use distinct calm block colors`() {
        val categories = listOf(
            BlockCategories.INPUT,
            BlockCategories.PERCEPTION,
            BlockCategories.LOGIC,
            BlockCategories.VARIABLES,
            BlockCategories.FLOW,
            BlockCategories.RUNTIME,
        )
        val colors = categories.map(::blockEditorColors)

        assertEquals(categories.size, colors.distinct().size)
        assertTrue(colors.none { color -> color.red > 0.9f && color.green < 0.2f && color.blue < 0.2f })
    }

    @Test
    fun `empty workspace visual state remains empty and side-effect free`() {
        val controller = BlockEditorController(initialDocument = WorkspaceBootstrap.empty())

        controller.onCanvasSizeChange(Offset2(480f, 320f))
        controller.fitWorkspaceToCanvas(force = true)

        assertTrue(controller.document.blocks.isEmpty())
        assertTrue(controller.layoutCache.flatIndex.visibleBlocks.isEmpty())
        assertEquals(0, controller.historySize)

        controller.close()
    }

    @Test
    fun `multiple root positions remain stable across fit and layout rebuild`() {
        val controller = BlockEditorController(initialDocument = WorkspaceBootstrap.empty())
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_CLICK_TEXT, 96f, 120f))
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 360f, 240f))
        val roots = controller.document.rootBlocks
        val initialPositions = roots.associateWith { root -> controller.document.rootOffset(root) }

        controller.onCanvasSizeChange(Offset2(640f, 420f))
        controller.fitWorkspaceToCanvas(force = true)
        val rebuiltBlocks = controller.layoutCache.flatIndex.visibleBlocks.map { it.blockId }.toSet()

        assertEquals(roots.toSet(), rebuiltBlocks)
        assertEquals(initialPositions, roots.associateWith { root -> controller.document.rootOffset(root) })

        controller.close()
    }

    @Test
    fun `drag snap candidate is visible without document mutation`() {
        val controller = BlockEditorController(initialDocument = SampleWorkspaceFactory.createDemo())
        val before = controller.document
        val chain = SampleWorkspaceFactory.mainChain(controller.document)
        val repeatId = chain[2]
        val clickId = chain[3]
        val layout = controller.layoutCache.flatIndex
        val clickBounds = layout.visibleBlocks.first { it.blockId == clickId }.bounds
        val clickPrevious = controller.document.blocks.getValue(clickId).previous!!.id
        val repeatNext = controller.document.blocks.getValue(repeatId).next!!.id
        val clickAnchor = layout.connectionAnchors.first { it.connectionId == clickPrevious }
        val repeatAnchor = layout.connectionAnchors.first { it.connectionId == repeatNext }
        val startPointer = Offset2(clickBounds.x + 8f, clickBounds.y + 8f)

        assertTrue(controller.onLongPressDragStart(startPointer))
        controller.onPointerMove(
            Offset2(
                x = startPointer.x + repeatAnchor.x - clickAnchor.x,
                y = startPointer.y + repeatAnchor.y - clickAnchor.y + 8f,
            ),
        )

        assertEquals(before, controller.document)
        assertTrue(controller.dragRender?.snapCandidate != null)

        controller.close()
    }

    @Test
    fun `unsupported legacy block remains visible in layout projection`() {
        val unknown = de.visualtasker.blockeditor.domain.BlockNode(
            id = BlockId("legacy"),
            type = "legacy.unsupported",
        )
        val document = WorkspaceBootstrap.empty().copy(
            blocks = mapOf(unknown.id to unknown),
            rootBlocks = listOf(unknown.id),
        ).withRootOffset(unknown.id, 96f, 120f)
        val controller = BlockEditorController(initialDocument = document)

        assertEquals(listOf(unknown.id), controller.layoutCache.flatIndex.visibleBlocks.map { it.blockId })
        assertFalse(controller.layoutCache.flatIndex.visibleBlocks.single().bounds.width <= 0f)

        controller.close()
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val lighter = maxOf(relativeLuminance(a), relativeLuminance(b))
        val darker = minOf(relativeLuminance(a), relativeLuminance(b))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }
}
