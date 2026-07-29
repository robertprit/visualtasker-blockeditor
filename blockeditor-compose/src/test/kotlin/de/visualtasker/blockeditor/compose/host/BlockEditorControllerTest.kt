package de.visualtasker.blockeditor.compose.host

import de.visualtasker.blockeditor.domain.Offset2
import de.visualtasker.blockeditor.domain.WorkspaceAction
import de.visualtasker.blockeditor.domain.WorkspaceGraph
import de.visualtasker.blockeditor.domain.asString
import de.visualtasker.blockeditor.domain.rootOffset
import de.visualtasker.blockeditor.compose.render.contrastTextColor
import de.visualtasker.blockeditor.compose.theme.darkBlockEditorColors
import de.visualtasker.blockeditor.compose.theme.lightBlockEditorColors
import de.visualtasker.blockeditor.compose.viewmodel.parameterSourceFieldKey
import de.visualtasker.blockeditor.emscript.WorkspaceCodeGenerator
import de.visualtasker.blockeditor.interaction.DragPullMode
import de.visualtasker.blockeditor.interaction.ViewportState
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.WorkspaceBootstrap
import de.visualtasker.blockeditor.registry.BlockDefinition
import de.visualtasker.blockeditor.registry.CompositeBlockRegistry
import de.visualtasker.blockeditor.registry.FieldDefinition
import de.visualtasker.blockeditor.registry.FieldKind
import de.visualtasker.blockeditor.registry.FieldOption
import de.visualtasker.blockeditor.registry.StatementInputDefinition
import de.visualtasker.blockeditor.registry.StaticBlockRegistry
import de.visualtasker.blockeditor.registry.ValueInputDefinition
import androidx.compose.ui.graphics.Color
import de.visualtasker.blockeditor.validation.ValidationError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BlockEditorControllerTest {
    @Test
    fun hostSoundEffectsAreOptInByDefault() {
        assertFalse(BlockEditorHostUiConfig().soundEffectsEnabled)
        assertFalse(BlockEditorHostUiConfig().hapticFeedbackEnabled)
    }

    @Test
    fun unsupportedBlockColorsRemainVisibleInLightAndDark() {
        listOf(lightBlockEditorColors(), darkBlockEditorColors()).forEach { colors ->
            assertNotEquals(colors.workspaceBackground, colors.unsupportedFill)
            assertNotEquals(colors.workspaceBackground, colors.gridDot)
            assertNotEquals(colors.unsupportedFill, colors.unsupportedStroke)
            assertNotEquals(colors.unsupportedFill, colors.unsupportedText)
        }
    }

    @Test
    fun blockTextColorContrastsWithBlockFill() {
        assertEquals(Color(0xFF111827), contrastTextColor(Color(0xFFFFC107)))
        assertEquals(Color(0xFFF8FAFC), contrastTextColor(Color(0xFF263238)))
    }

    @Test
    fun longTextValueDoesNotChangeBlockSize() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
        )
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_CLICK_TEXT, 96f, 120f))
        val blockId = controller.document.rootBlocks.single()
        val before = controller.layoutCache.flatIndex.visibleBlocks.single { it.blockId == blockId }.bounds

        controller.selectBlockCenter(blockId)
        controller.updateBlockField(
            "text",
            "A very long concrete value that must stay in the InfoPanel and never resize the workspace block",
        )

        val after = controller.layoutCache.flatIndex.visibleBlocks.single { it.blockId == blockId }.bounds
        assertEquals(before.width, after.width, 0.01f)
        assertEquals(before.height, after.height, 0.01f)

        controller.close()
    }

    @Test
    fun filePathValueIsStoredButOnlyExposedThroughInfoPanel() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
        )
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_FIND_TEMPLATE, 96f, 120f))
        val blockId = controller.document.rootBlocks.single()
        val before = controller.layoutCache.flatIndex.visibleBlocks.single { it.blockId == blockId }.bounds

        controller.selectBlockCenter(blockId)
        controller.updateBlockField("imagePath", "/sdcard/screenshots/login/template-with-long-name.png")

        val infoField = controller.selectedBlockInfo()!!.fields.single { it.key == "imagePath" }
        val after = controller.layoutCache.flatIndex.visibleBlocks.single { it.blockId == blockId }.bounds
        assertEquals("/sdcard/screenshots/login/template-with-long-name.png", infoField.value)
        assertEquals(before.width, after.width, 0.01f)
        assertEquals(before.height, after.height, 0.01f)

        controller.close()
    }

    @Test
    fun findTemplateParametersAndSourcesAreStoredInWorkspaceDocument() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
        )
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_FIND_TEMPLATE, 96f, 120f))
        val blockId = controller.document.rootBlocks.single()
        controller.selectBlockCenter(blockId)

        controller.updateBlockField("imagePath", "/sdcard/templates/login.png")
        controller.updateBlockFieldSource("imagePath", "VARIABLE")
        controller.updateBlockField("threshold", "0.91")
        controller.updateBlockField("timeoutMs", "2500")
        controller.updateBlockField("retryCount", "3")
        controller.updateBlockField("searchRegion", "0,0,400,600")
        controller.updateBlockFieldSource("searchRegion", "REGION_REPORTER")

        val fields = controller.document.blocks[blockId]!!.fields
        assertEquals("/sdcard/templates/login.png", fields["imagePath"]!!.asString())
        assertEquals("VARIABLE", fields[parameterSourceFieldKey("imagePath")]!!.asString())
        assertEquals("0.91", fields["threshold"]!!.asString())
        assertEquals("2500.0", fields["timeoutMs"]!!.asString())
        assertEquals("3.0", fields["retryCount"]!!.asString())
        assertEquals("0,0,400,600", fields["searchRegion"]!!.asString())
        assertEquals("REGION_REPORTER", fields[parameterSourceFieldKey("searchRegion")]!!.asString())

        controller.close()
    }

    @Test
    fun undoAndRedoRestoreParameterAndSourceChanges() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
        )
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_FIND_TEMPLATE, 96f, 120f))
        val blockId = controller.document.rootBlocks.single()
        controller.selectBlockCenter(blockId)

        controller.updateBlockField("threshold", "0.7")
        controller.updateBlockFieldSource("imagePath", "VARIABLE")

        assertEquals("VARIABLE", controller.document.blocks[blockId]!!.fields[parameterSourceFieldKey("imagePath")]!!.asString())
        assertTrue(controller.undo())
        assertEquals(null, controller.document.blocks[blockId]!!.fields[parameterSourceFieldKey("imagePath")])
        assertTrue(controller.undo())
        assertEquals("0.82", controller.document.blocks[blockId]!!.fields["threshold"]!!.asString())
        assertTrue(controller.redo())
        assertEquals("0.7", controller.document.blocks[blockId]!!.fields["threshold"]!!.asString())
        assertTrue(controller.redo())
        assertEquals("VARIABLE", controller.document.blocks[blockId]!!.fields[parameterSourceFieldKey("imagePath")]!!.asString())

        controller.close()
    }

    @Test
    fun infoPanelReportsMissingRequiredValue() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
        )
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_FIND_TEMPLATE, 96f, 120f))
        val blockId = controller.document.rootBlocks.single()

        controller.selectBlockCenter(blockId)

        val imagePath = controller.selectedBlockInfo()!!.fields.single { it.key == "imagePath" }
        assertEquals("Datei oder Template fehlt.", imagePath.diagnostic)

        controller.close()
    }

    @Test
    fun startBlockScriptNameAndColorStayEditableInInfoPanel() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.starter(),
        )
        val blockId = controller.document.rootBlocks.single()

        controller.selectBlockCenter(blockId)
        controller.updateBlockField("script", "LoginFlow.ems")
        controller.updateBlockField("color", "violet")

        val fields = controller.document.blocks[blockId]!!.fields
        assertEquals("LoginFlow.ems", fields["script"]!!.asString())
        assertEquals("violet", fields["color"]!!.asString())
        assertTrue(controller.undo())
        assertEquals("orange", controller.document.blocks[blockId]!!.fields["color"]!!.asString())
        assertTrue(controller.redo())
        assertEquals("violet", controller.document.blocks[blockId]!!.fields["color"]!!.asString())

        controller.close()
    }

    @Test
    fun selectedBlockDeleteUsesWorkspaceActionAndEmitsPersistentChange() {
        val callbacks = RecordingCallbacks()
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
            callbacks = callbacks,
        )
        callbacks.clear()
        controller.onAction(
            WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_CLICK_TEXT, 96f, 120f),
        )
        callbacks.clear()
        val blockId = controller.document.rootBlocks.single()

        controller.onTap(Offset2(108f, 132f))
        val deleted = controller.deleteSelectedBlock()

        assertTrue(deleted)
        assertFalse(blockId in controller.document.blocks)
        assertTrue(controller.selectedBlockIds.isEmpty())
        assertEquals(1, callbacks.documentChanges.size)

        controller.close()
    }

    @Test
    fun undoAndRedoRestorePersistentWorkspaceActions() {
        val callbacks = RecordingCallbacks()
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
            callbacks = callbacks,
        )
        callbacks.clear()

        controller.onAction(
            WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_CLICK_TEXT, 96f, 120f),
        )
        assertEquals(1, controller.document.blocks.size)

        assertTrue(controller.undo())
        assertEquals(0, controller.document.blocks.size)
        assertTrue(controller.redo())
        assertEquals(1, controller.document.blocks.size)
        assertEquals(3, callbacks.documentChanges.size)

        controller.close()
    }

    @Test
    fun replaceWorkspaceDocumentCreatesSingleUndoablePersistentChange() {
        val callbacks = RecordingCallbacks()
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
            callbacks = callbacks,
        )
        callbacks.clear()
        val imported = WorkspaceBootstrap.starter()

        controller.replaceWorkspaceDocument(imported)

        assertEquals(imported, controller.document)
        assertEquals(1, controller.historySize)
        assertEquals(1, callbacks.documentChanges.size)

        assertTrue(controller.undo())
        assertEquals(WorkspaceBootstrap.empty(), controller.document)
        assertTrue(controller.redo())
        assertEquals(imported, controller.document)

        controller.close()
    }

    @Test
    fun replaceWorkspaceDocumentCanFocusAndSelectImportedRootBlock() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
        )
        val imported = WorkspaceBootstrap.starter()
        val rootId = imported.rootBlocks.single()

        controller.onCanvasSizeChange(Offset2(480f, 360f))
        controller.replaceWorkspaceDocument(
            newDocument = imported,
            focusBlockId = rootId,
            selectFocusedBlock = true,
        )

        assertEquals(setOf(rootId), controller.selectedBlockIds)
        assertEquals(imported.blocks.keys, controller.layoutCache.flatIndex.visibleBlocks.map { it.blockId }.toSet())
        assertTrue(
            controller.layoutCache.flatIndex.visibleBlocks.any { block ->
                val bounds = block.subtreeBounds
                val left = bounds.x * controller.viewport.scale + controller.viewport.panX
                val top = bounds.y * controller.viewport.scale + controller.viewport.panY
                val right = bounds.right * controller.viewport.scale + controller.viewport.panX
                val bottom = bounds.bottom * controller.viewport.scale + controller.viewport.panY
                block.blockId == rootId && left <= 480f && top <= 360f && right >= 0f && bottom >= 0f
            },
        )

        controller.close()
    }

    @Test
    fun replaceWorkspaceDocumentDefersImportedRootFocusUntilCanvasSizeIsKnown() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
        )
        val imported = WorkspaceBootstrap.starter()
        val rootId = imported.rootBlocks.single()

        controller.replaceWorkspaceDocument(
            newDocument = imported,
            focusBlockId = rootId,
            selectFocusedBlock = true,
        )

        assertEquals(setOf(rootId), controller.selectedBlockIds)
        assertEquals(ViewportState(), controller.viewport)

        controller.onCanvasSizeChange(Offset2(480f, 360f))

        assertEquals(setOf(rootId), controller.selectedBlockIds)
        val rootLayout = controller.layoutCache.flatIndex.visibleBlocks.single { it.blockId == rootId }
        val left = rootLayout.subtreeBounds.x * controller.viewport.scale + controller.viewport.panX
        val top = rootLayout.subtreeBounds.y * controller.viewport.scale + controller.viewport.panY
        val right = rootLayout.subtreeBounds.right * controller.viewport.scale + controller.viewport.panX
        val bottom = rootLayout.subtreeBounds.bottom * controller.viewport.scale + controller.viewport.panY
        assertTrue(left <= 480f && top <= 360f && right >= 0f && bottom >= 0f)
        assertNotEquals(ViewportState(), controller.viewport)

        controller.close()
    }

    @Test
    fun selectedBlockDeletePreservesNextChainAndUndoRestoresIt() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
        )
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_CLICK_TEXT, 96f, 120f))
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 96f, 220f))
        val first = controller.document.rootBlocks[0]
        val second = controller.document.rootBlocks[1]
        controller.onAction(
            WorkspaceAction.Connect(
                controller.document.blocks[first]!!.next!!.id,
                controller.document.blocks[second]!!.previous!!.id,
            ),
        )

        controller.onTap(Offset2(108f, 132f))
        assertTrue(controller.deleteSelectedBlock())

        assertFalse(first in controller.document.blocks)
        assertTrue(second in controller.document.blocks)
        assertEquals(listOf(second), controller.document.rootBlocks)
        assertEquals(null, controller.document.blocks[second]!!.previous!!.connectedTo)

        assertTrue(controller.undo())
        assertTrue(first in controller.document.blocks)
        assertTrue(second in controller.document.blocks)
        assertEquals(first, controller.document.rootBlocks.single())

        controller.close()
    }

    @Test
    fun blockTouchZonesSeparateInfoPanelFromGroupAndSingleDrag() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
        )
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_CLICK_TEXT, 96f, 120f))
        val blockId = controller.document.rootBlocks.single()
        val bounds = controller.layoutCache.flatIndex.visibleBlocks.single { it.blockId == blockId }.bounds
        val left = Offset2(bounds.x + bounds.width * 0.12f, bounds.y + 12f)
        val center = Offset2(bounds.x + bounds.width * 0.50f, bounds.y + 12f)
        val right = Offset2(bounds.x + bounds.width * 0.88f, bounds.y + 12f)

        controller.onTap(left)
        assertEquals(setOf(blockId), controller.selectedBlockIds)
        assertEquals(blockId, controller.selectedBlockInfo()!!.blockId)

        controller.onTap(center)
        assertEquals(blockId, controller.selectedBlockInfo()!!.blockId)
        assertTrue(controller.onLongPressDragStart(center))
        assertEquals(DragPullMode.Single, controller.dragRender!!.session.pullMode)
        controller.onPointerUp(center)

        assertTrue(controller.onLongPressDragStart(left))
        assertEquals(DragPullMode.StackBelow, controller.dragRender!!.session.pullMode)
        controller.onPointerUp(left)

        assertTrue(controller.onLongPressDragStart(right))
        assertEquals(DragPullMode.Single, controller.dragRender!!.session.pullMode)
        controller.onPointerUp(right)

        controller.close()
    }

    @Test
    fun trashDeleteUsesDragModeForStackOrSingleBlock() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
        )
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_CLICK_TEXT, 96f, 120f))
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 96f, 220f))
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.DEBUG_LOG, 96f, 320f))
        val first = controller.document.rootBlocks[0]
        val second = controller.document.rootBlocks[1]
        val third = controller.document.rootBlocks[2]
        controller.onAction(WorkspaceAction.Connect(controller.document.blocks[first]!!.next!!.id, controller.document.blocks[second]!!.previous!!.id))
        controller.onAction(WorkspaceAction.Connect(controller.document.blocks[second]!!.next!!.id, controller.document.blocks[third]!!.previous!!.id))
        val firstBounds = controller.layoutCache.flatIndex.visibleBlocks.single { it.blockId == first }.bounds
        val left = Offset2(firstBounds.x + firstBounds.width * 0.12f, firstBounds.y + firstBounds.height * 0.5f)
        val right = Offset2(firstBounds.x + firstBounds.width * 0.88f, firstBounds.y + firstBounds.height * 0.5f)

        assertTrue(controller.onLongPressDragStart(left))
        assertEquals(DragPullMode.StackBelow, controller.dragRender!!.session.pullMode)
        assertEquals(setOf(first, second, third), controller.dragRender!!.session.includedBlocks)
        assertTrue(controller.deleteSelectedBlock())
        assertTrue(controller.document.blocks.isEmpty())
        assertTrue(controller.undo())

        assertTrue(controller.onLongPressDragStart(right))
        assertEquals(DragPullMode.Single, controller.dragRender!!.session.pullMode)
        assertTrue(controller.deleteSelectedBlock())
        assertFalse(first in controller.document.blocks)
        assertTrue(second in controller.document.blocks)
        assertTrue(third in controller.document.blocks)

        controller.close()
    }

    @Test
    fun trashDeleteRemovesActiveDragBlockEvenWhenSelectionWasCleared() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
        )
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 96f, 120f))
        val blockId = controller.document.rootBlocks.single()
        val bounds = controller.layoutCache.flatIndex.visibleBlocks.single { it.blockId == blockId }.bounds
        val rightHandle = Offset2(bounds.x + bounds.width * 0.88f, bounds.y + bounds.height * 0.5f)

        assertTrue(controller.onLongPressDragStart(rightHandle))
        controller.onTap(Offset2(-100f, -100f))

        assertTrue(controller.deleteSelectedBlock())
        assertFalse(blockId in controller.document.blocks)
        assertTrue(controller.document.rootBlocks.isEmpty())

        controller.close()
    }

    @Test
    fun centerLongPressDetachesSingleBlockFromConnectedStack() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
        )
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_CLICK_TEXT, 96f, 120f))
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 96f, 220f))
        val first = controller.document.rootBlocks[0]
        val second = controller.document.rootBlocks[1]
        controller.onAction(
            WorkspaceAction.Connect(
                controller.document.blocks[first]!!.next!!.id,
                controller.document.blocks[second]!!.previous!!.id,
            ),
        )
        val secondBounds = controller.layoutCache.flatIndex.visibleBlocks.single { it.blockId == second }.bounds
        val center = Offset2(secondBounds.x + secondBounds.width * 0.5f, secondBounds.y + secondBounds.height * 0.5f)

        assertTrue(controller.onLongPressDragStart(center))
        assertEquals(DragPullMode.Single, controller.dragRender!!.session.pullMode)
        controller.onPointerMove(Offset2(center.x + 180f, center.y))
        controller.onPointerUp(Offset2(center.x + 180f, center.y))

        assertEquals(null, de.visualtasker.blockeditor.domain.WorkspaceGraph.nextChain(controller.document, first))
        assertEquals(null, de.visualtasker.blockeditor.domain.WorkspaceGraph.previousChain(controller.document, second))
        assertEquals(null, de.visualtasker.blockeditor.domain.WorkspaceGraph.nextChain(controller.document, second))
        assertTrue(second in controller.document.rootBlocks)

        controller.close()
    }

    @Test
    fun rootDragDropClampsPositionToVisibleWorkspaceBounds() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
        )
        controller.onCanvasSizeChange(Offset2(320f, 220f))
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 96f, 120f))
        val blockId = controller.document.rootBlocks.single()
        val bounds = controller.layoutCache.flatIndex.visibleBlocks.single { it.blockId == blockId }.bounds
        val rightHandle = Offset2(bounds.x + bounds.width * 0.88f, bounds.y + bounds.height * 0.5f)

        assertTrue(controller.onLongPressDragStart(rightHandle))
        controller.onPointerMove(Offset2(-1200f, -900f))
        controller.onPointerUp(Offset2(-1200f, -900f))

        assertEquals(Offset2(0f, 0f), controller.document.rootOffset(blockId))

        assertTrue(controller.onLongPressDragStart(Offset2(bounds.width * 0.88f, bounds.height * 0.5f)))
        controller.onPointerMove(Offset2(1400f, 1100f))
        controller.onPointerUp(Offset2(1400f, 1100f))
        val movedBounds = controller.layoutCache.flatIndex.visibleBlocks.single { it.blockId == blockId }.bounds
        val maxX = 320f / controller.viewport.scale - movedBounds.width
        val maxY = 220f / controller.viewport.scale - movedBounds.height

        assertEquals(maxX, controller.document.rootOffset(blockId)!!.x, 0.01f)
        assertEquals(maxY, controller.document.rootOffset(blockId)!!.y, 0.01f)

        controller.close()
    }

    @Test
    fun rootDragMoveClipsBlockAtVisibleWorkspaceEdge() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
        )
        controller.onCanvasSizeChange(Offset2(320f, 220f))
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 96f, 120f))
        val blockId = controller.document.rootBlocks.single()
        val bounds = controller.layoutCache.flatIndex.visibleBlocks.single { it.blockId == blockId }.bounds
        val rightHandle = Offset2(bounds.x + bounds.width * 0.88f, bounds.y + bounds.height * 0.5f)

        assertTrue(controller.onLongPressDragStart(rightHandle))
        controller.onPointerMove(Offset2(-1200f, -900f))

        val render = controller.dragRender!!
        val offset = render.session.dragOffset
        val draggedBounds = render.dragLayoutCache.flatIndex.visibleBlocks.single { it.blockId == blockId }.subtreeBounds
        val left = (draggedBounds.x + offset.x) * controller.viewport.scale + controller.viewport.panX
        val top = (draggedBounds.y + offset.y) * controller.viewport.scale + controller.viewport.panY

        assertEquals(0f, left, 0.01f)
        assertEquals(0f, top, 0.01f)
        controller.onPointerUp(Offset2(-1200f, -900f))

        controller.close()
    }

    @Test
    fun selectedBlockTypeCanSwitchIfBranchVariantWithoutLosingSharedConnections() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
        )
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.CONTROL_IF, 96f, 120f))
        val blockId = controller.document.rootBlocks.single()
        controller.selectBlockCenter(blockId)

        assertTrue(controller.replaceSelectedBlockType(BlockTypes.CONTROL_IF_ELSE))
        val ifElse = controller.document.blocks.getValue(blockId)
        assertEquals(BlockTypes.CONTROL_IF_ELSE, ifElse.type)
        assertEquals(listOf(BlockTypes.SLOT_THEN, BlockTypes.SLOT_ELSE), ifElse.statementInputs.map { it.name })
        assertEquals(setOf(blockId), controller.selectedBlockIds)
        assertEquals(blockId, controller.selectedBlockInfo()!!.blockId)

        assertTrue(controller.replaceSelectedBlockType(BlockTypes.CONTROL_IF))
        val ifOnly = controller.document.blocks.getValue(blockId)
        assertEquals(BlockTypes.CONTROL_IF, ifOnly.type)
        assertEquals(listOf(BlockTypes.SLOT_THEN), ifOnly.statementInputs.map { it.name })

        controller.close()
    }

    @Test
    fun selectedIfBlockCanGrowToEightBranchesAndShrinkBack() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
        )
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.CONTROL_IF, 96f, 120f))
        val blockId = controller.document.rootBlocks.single()
        controller.selectBlockCenter(blockId)

        repeat(7) {
            assertTrue(controller.addSelectedIfBranch(BlockTypes.CONTROL_IF, BlockTypes.CONTROL_IF_ELSE))
        }

        val expanded = controller.document.blocks.getValue(blockId)
        assertEquals(BlockTypes.CONTROL_IF_ELSE, expanded.type)
        assertEquals(8, controller.selectedBlockInfo()!!.branchCount)
        assertEquals(
            listOf(
                BlockTypes.SLOT_THEN,
                "ELIF_1",
                "ELIF_2",
                "ELIF_3",
                "ELIF_4",
                "ELIF_5",
                "ELIF_6",
                BlockTypes.SLOT_ELSE,
            ),
            expanded.statementInputs.map { it.name },
        )
        assertFalse(controller.addSelectedIfBranch(BlockTypes.CONTROL_IF, BlockTypes.CONTROL_IF_ELSE))

        repeat(7) {
            assertTrue(controller.removeSelectedIfBranch(BlockTypes.CONTROL_IF, BlockTypes.CONTROL_IF_ELSE))
        }

        val collapsed = controller.document.blocks.getValue(blockId)
        assertEquals(BlockTypes.CONTROL_IF, collapsed.type)
        assertEquals(listOf(BlockTypes.SLOT_THEN), collapsed.statementInputs.map { it.name })
        assertEquals(1, controller.selectedBlockInfo()!!.branchCount)

        controller.close()
    }

    @Test
    fun createVariablePlacesReporterBlockInWorkspace() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
        )

        controller.createVariable("loginText", "String")

        assertEquals(1, controller.document.variables.variables.size)
        val reporterId = controller.document.rootBlocks.single()
        val reporter = controller.document.blocks.getValue(reporterId)
        assertTrue(reporter.type.startsWith(BlockTypes.VARIABLE_REPORTER_PREFIX))
        assertEquals("loginText", reporter.fields["variable"]!!.asString())

        controller.close()
    }

    @Test
    fun singleDraggingBlockOutOfStartStackKeepsGhostUntilDropThenBridgesGap() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.starter(),
        )
        controller.onCanvasSizeChange(Offset2(720f, 540f))
        repeat(5) { index ->
            controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 96f, 120f + index * 80f))
            val newBlock = controller.document.rootBlocks.last()
            val previous = if (index == 0) {
                controller.document.rootBlocks.first()
            } else {
                controller.document.rootBlocks[controller.document.rootBlocks.size - 2]
            }
            controller.onAction(WorkspaceAction.Connect(controller.document.blocks[previous]!!.next!!.id, controller.document.blocks[newBlock]!!.previous!!.id))
        }
        val startId = controller.document.rootBlocks.single()
        val firstDetached = WorkspaceGraph.nextChain(controller.document, startId)!!
        val remainingHead = WorkspaceGraph.nextChain(controller.document, firstDetached)!!
        val firstBounds = controller.layoutCache.flatIndex.visibleBlocks.single { it.blockId == firstDetached }.bounds
        val remainingBoundsBefore = controller.layoutCache.flatIndex.visibleBlocks.single { it.blockId == remainingHead }.bounds
        val workspaceCenter = Offset2(firstBounds.x + firstBounds.width * 0.5f, firstBounds.y + firstBounds.height * 0.5f)
        val screenCenter = Offset2(
            workspaceCenter.x * controller.viewport.scale + controller.viewport.panX,
            workspaceCenter.y * controller.viewport.scale + controller.viewport.panY,
        )

        assertTrue(controller.onLongPressDragStart(screenCenter))
        val staticRemainingBounds = controller.dragRender!!.staticLayoutCache.flatIndex.visibleBlocks
            .single { it.blockId == remainingHead }
            .bounds
        assertEquals(remainingBoundsBefore.y, staticRemainingBounds.y, 0.01f)
        val detachedDropPoint = Offset2(screenCenter.x + 360f, screenCenter.y + 160f)
        controller.onPointerMove(detachedDropPoint)
        assertNull(controller.dragRender!!.snapCandidate)
        controller.onPointerUp(detachedDropPoint)

        assertEquals(remainingHead, WorkspaceGraph.nextChain(controller.document, startId))
        assertTrue(firstDetached in controller.document.rootBlocks)

        controller.close()
    }

    @Test
    fun draggingStartAwayPreservesPromotedContainerRootPositionAndNestedChildren() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.starter(),
        )
        controller.onCanvasSizeChange(Offset2(720f, 540f))
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.CONTROL_IF_ELSE, 96f, 220f))
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 160f, 320f))
        val startId = controller.document.rootBlocks[0]
        val ifId = controller.document.rootBlocks[1]
        val childId = controller.document.rootBlocks[2]
        controller.onAction(WorkspaceAction.Connect(controller.document.blocks[startId]!!.next!!.id, controller.document.blocks[ifId]!!.previous!!.id))
        controller.onAction(WorkspaceAction.Connect(controller.document.blocks[ifId]!!.statementInputs.single { it.name == BlockTypes.SLOT_THEN }.connection.id, controller.document.blocks[childId]!!.previous!!.id))
        val ifBoundsBefore = controller.layoutCache.flatIndex.visibleBlocks.single { it.blockId == ifId }.bounds
        val childSlotBefore = de.visualtasker.blockeditor.domain.WorkspaceGraph.slotContaining(controller.document, childId)
        val startBounds = controller.layoutCache.flatIndex.visibleBlocks.single { it.blockId == startId }.bounds
        val workspaceRightHandle = Offset2(startBounds.x + startBounds.width * 0.88f, startBounds.y + startBounds.height * 0.5f)
        val rightHandle = Offset2(
            workspaceRightHandle.x * controller.viewport.scale + controller.viewport.panX,
            workspaceRightHandle.y * controller.viewport.scale + controller.viewport.panY,
        )

        assertTrue(controller.onLongPressDragStart(rightHandle))
        val previewIfBounds = controller.dragRender!!.staticLayoutCache.flatIndex.visibleBlocks.single { it.blockId == ifId }.bounds
        assertEquals(ifBoundsBefore.x, previewIfBounds.x, 0.01f)
        assertEquals(ifBoundsBefore.y, previewIfBounds.y, 0.01f)

        controller.onPointerMove(Offset2(rightHandle.x + 220f, rightHandle.y))
        controller.onPointerUp(Offset2(rightHandle.x + 220f, rightHandle.y))

        assertTrue(ifId in controller.document.rootBlocks)
        assertEquals(ifBoundsBefore.x, controller.document.rootOffset(ifId)!!.x, 0.01f)
        assertEquals(ifBoundsBefore.y, controller.document.rootOffset(ifId)!!.y, 0.01f)
        assertEquals(childSlotBefore, de.visualtasker.blockeditor.domain.WorkspaceGraph.slotContaining(controller.document, childId))

        controller.close()
    }

    @Test
    fun draggingIfContainerKeepsBranchChildrenAttached() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
        )
        controller.onCanvasSizeChange(Offset2(720f, 540f))
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.CONTROL_IF_ELSE, 96f, 160f))
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 160f, 260f))
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_CLICK_TEXT, 160f, 340f))
        val ifId = controller.document.rootBlocks[0]
        val thenChild = controller.document.rootBlocks[1]
        val elseChild = controller.document.rootBlocks[2]
        controller.onAction(WorkspaceAction.Connect(controller.document.blocks[ifId]!!.statementInputs.single { it.name == BlockTypes.SLOT_THEN }.connection.id, controller.document.blocks[thenChild]!!.previous!!.id))
        controller.onAction(WorkspaceAction.Connect(controller.document.blocks[ifId]!!.statementInputs.single { it.name == BlockTypes.SLOT_ELSE }.connection.id, controller.document.blocks[elseChild]!!.previous!!.id))
        val ifBounds = controller.layoutCache.flatIndex.visibleBlocks.single { it.blockId == ifId }.bounds
        val workspaceCenter = Offset2(ifBounds.x + ifBounds.width * 0.5f, ifBounds.y + ifBounds.height * 0.5f)
        val screenCenter = Offset2(
            workspaceCenter.x * controller.viewport.scale + controller.viewport.panX,
            workspaceCenter.y * controller.viewport.scale + controller.viewport.panY,
        )

        assertTrue(controller.onLongPressDragStart(screenCenter))
        assertTrue(thenChild in controller.dragRender!!.session.includedBlocks)
        assertTrue(elseChild in controller.dragRender!!.session.includedBlocks)
        controller.onPointerMove(Offset2(screenCenter.x + 180f, screenCenter.y + 20f))
        controller.onPointerUp(Offset2(screenCenter.x + 180f, screenCenter.y + 20f))

        assertEquals(ifId to BlockTypes.SLOT_THEN, de.visualtasker.blockeditor.domain.WorkspaceGraph.slotContaining(controller.document, thenChild))
        assertEquals(ifId to BlockTypes.SLOT_ELSE, de.visualtasker.blockeditor.domain.WorkspaceGraph.slotContaining(controller.document, elseChild))
        assertFalse(thenChild in controller.document.rootBlocks)
        assertFalse(elseChild in controller.document.rootBlocks)

        controller.close()
    }

    @Test
    fun draggingEmscriptIfContainerKeepsBranchChildrenAttached() {
        val registry = CompositeBlockRegistry().apply {
            register(
                BlockDefinition(
                    id = "emscript:control.if_else",
                    label = "If / Else",
                    category = "flow",
                    hasPrevious = true,
                    hasNext = true,
                    valueInputs = listOf(ValueInputDefinition("condition", "if", setOf("Bool", "Boolean"))),
                    statementInputs = listOf(
                        StatementInputDefinition(BlockTypes.SLOT_THEN, "then"),
                        StatementInputDefinition(BlockTypes.SLOT_ELSE, "else"),
                    ),
                ),
            )
        }
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
            registry = registry,
        )
        controller.onCanvasSizeChange(Offset2(720f, 540f))
        controller.onAction(WorkspaceAction.InstantiateBlock("emscript:control.if_else", 96f, 160f))
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 160f, 260f))
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_CLICK_TEXT, 160f, 340f))
        val ifId = controller.document.rootBlocks[0]
        val thenChild = controller.document.rootBlocks[1]
        val elseChild = controller.document.rootBlocks[2]
        controller.onAction(WorkspaceAction.Connect(controller.document.blocks[ifId]!!.statementInputs.single { it.name == BlockTypes.SLOT_THEN }.connection.id, controller.document.blocks[thenChild]!!.previous!!.id))
        controller.onAction(WorkspaceAction.Connect(controller.document.blocks[ifId]!!.statementInputs.single { it.name == BlockTypes.SLOT_ELSE }.connection.id, controller.document.blocks[elseChild]!!.previous!!.id))
        val ifBounds = controller.layoutCache.flatIndex.visibleBlocks.single { it.blockId == ifId }.bounds
        val workspaceCenter = Offset2(ifBounds.x + ifBounds.width * 0.5f, ifBounds.y + ifBounds.height * 0.5f)
        val screenCenter = Offset2(
            workspaceCenter.x * controller.viewport.scale + controller.viewport.panX,
            workspaceCenter.y * controller.viewport.scale + controller.viewport.panY,
        )

        assertTrue(controller.onLongPressDragStart(screenCenter))
        assertTrue(thenChild in controller.dragRender!!.session.includedBlocks)
        assertTrue(elseChild in controller.dragRender!!.session.includedBlocks)
        controller.onPointerMove(Offset2(screenCenter.x + 180f, screenCenter.y + 20f))
        controller.onPointerUp(Offset2(screenCenter.x + 180f, screenCenter.y + 20f))

        assertEquals(ifId to BlockTypes.SLOT_THEN, de.visualtasker.blockeditor.domain.WorkspaceGraph.slotContaining(controller.document, thenChild))
        assertEquals(ifId to BlockTypes.SLOT_ELSE, de.visualtasker.blockeditor.domain.WorkspaceGraph.slotContaining(controller.document, elseChild))
        assertFalse(thenChild in controller.document.rootBlocks)
        assertFalse(elseChild in controller.document.rootBlocks)

        controller.close()
    }

    @Test
    fun initialState_emitsValidationAndEmscriptOnly() {
        val callbacks = RecordingCallbacks()
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
            callbacks = callbacks,
        )

        assertEquals(0, callbacks.documentChanges.size)
        assertEquals(1, callbacks.validationBatches.size)
        assertEquals(1, callbacks.emscriptDrafts.size)
        assertEquals(0, callbacks.emscriptGenerationFailures.size)
        assertEquals("", callbacks.emscriptDrafts.single())

        controller.close()
    }

    @Test
    fun committedAction_emitsDocumentValidationAndEmscript() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val callbacks = RecordingCallbacks()
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.starter(),
            callbacks = callbacks,
            coroutineScope = CoroutineScope(SupervisorJob() + dispatcher),
            debounceMillis = 200L,
        )
        callbacks.clear()

        controller.onAction(
            WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_CLICK_TEXT, 96f, 120f),
        )

        assertEquals(1, callbacks.documentChanges.size)
        assertTrue(callbacks.documentChanges.single().contains("\"schemaVersion\":1"))

        advanceTimeBy(200L)
        runCurrent()

        assertEquals(1, callbacks.validationBatches.size)
        assertEquals(1, callbacks.emscriptDrafts.size)

        controller.close()
    }

    @Test
    fun transientViewportChange_doesNotEmitPersistentOutput() {
        val callbacks = RecordingCallbacks()
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
            callbacks = callbacks,
        )
        callbacks.clear()

        controller.onViewportChange(ViewportState(scale = 1.5f, panX = 12f, panY = 8f))
        controller.onCanvasSizeChange(Offset2(800f, 600f))
        controller.onTap(Offset2(100f, 100f))

        assertEquals(0, callbacks.documentChanges.size)
        assertEquals(0, callbacks.validationBatches.size)
        assertEquals(0, callbacks.emscriptDrafts.size)

        controller.close()
    }

    @Test
    fun persistentWorkspaceChangesDoNotAutoCenterViewport() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
        )
        controller.onCanvasSizeChange(Offset2(480f, 360f))
        controller.onViewportChange(ViewportState(scale = 1f, panX = 72f, panY = 48f))

        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 96f, 120f))

        assertEquals(ViewportState(scale = 1f, panX = 72f, panY = 48f), controller.viewport)

        controller.close()
    }

    @Test
    fun toolboxCanvasResizeDoesNotRecenterViewportAfterInitialFit() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
        )
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 96f, 120f))

        controller.onCanvasSizeChange(Offset2(640f, 420f))
        controller.onViewportChange(controller.viewport.copy(panX = 24f, panY = 18f))
        val beforeToolboxResize = controller.viewport

        controller.onCanvasSizeChange(Offset2(420f, 420f))

        assertEquals(beforeToolboxResize, controller.viewport)

        controller.close()
    }

    @Test
    fun fitWorkspaceToCanvasKeepsLoadedBlocksInsidePanelViewport() {
        val callbacks = RecordingCallbacks()
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
            callbacks = callbacks,
        )
        controller.onAction(
            WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_CLICK_TEXT, 1800f, 1200f),
        )

        controller.onCanvasSizeChange(Offset2(480f, 360f))

        val viewport = controller.viewport
        controller.layoutCache.flatIndex.visibleBlocks.forEach { block ->
            val bounds = block.subtreeBounds
            val left = bounds.x * viewport.scale + viewport.panX
            val top = bounds.y * viewport.scale + viewport.panY
            val right = bounds.right * viewport.scale + viewport.panX
            val bottom = bounds.bottom * viewport.scale + viewport.panY
            assertTrue(left <= 480f)
            assertTrue(top <= 360f)
            assertTrue(right >= 0f)
            assertTrue(bottom >= 0f)
        }

        controller.close()
    }

    @Test
    fun viewportPanCannotMoveEveryLoadedBlockOutsidePanelViewport() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
            callbacks = RecordingCallbacks(),
        )
        controller.onAction(
            WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_CLICK_TEXT, 1800f, 1200f),
        )
        controller.onCanvasSizeChange(Offset2(480f, 360f))

        controller.onViewportChange(controller.viewport.copy(panX = -5000f, panY = -5000f))

        val viewport = controller.viewport
        assertTrue(
            controller.layoutCache.flatIndex.visibleBlocks.any { block ->
                val bounds = block.subtreeBounds
                val left = bounds.x * viewport.scale + viewport.panX
                val top = bounds.y * viewport.scale + viewport.panY
                val right = bounds.right * viewport.scale + viewport.panX
                val bottom = bounds.bottom * viewport.scale + viewport.panY
                left <= 480f && top <= 360f && right >= 0f && bottom >= 0f
            },
        )

        controller.close()
    }

    @Test
    fun transientPointerMove_doesNotEmitPersistentOutput() {
        val callbacks = RecordingCallbacks()
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.starter(),
            callbacks = callbacks,
        )
        callbacks.clear()

        controller.onPointerMove(Offset2(50f, 50f))
        controller.onPointerMove(Offset2(60f, 60f))

        assertEquals(0, callbacks.documentChanges.size)
        assertEquals(0, callbacks.validationBatches.size)
        assertEquals(0, callbacks.emscriptDrafts.size)

        controller.close()
    }

    @Test
    fun rootDragMoveIsTransientAndDropCreatesSingleHistoryEntry() {
        val callbacks = RecordingCallbacks()
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
            callbacks = callbacks,
        )
        callbacks.clear()
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 96f, 120f))
        val rootId = controller.document.rootBlocks.single()
        val initialHistorySize = controller.historySize
        val initialPosition = controller.document.rootOffset(rootId)
        callbacks.clear()

        assertTrue(controller.onLongPressDragStart(Offset2(108f, 132f)))
        val runtimeState = controller.dragRender!!.runtimeState
        controller.onPointerMove(Offset2(140f, 150f))
        controller.onPointerMove(Offset2(160f, 172f))

        assertEquals(initialHistorySize, controller.historySize)
        assertEquals(0, callbacks.documentChanges.size)
        assertEquals(runtimeState, controller.dragRender!!.runtimeState)
        assertNotEquals(Offset2(0f, 0f), runtimeState.dragOffset)

        controller.onPointerUp(Offset2(160f, 172f))

        assertEquals(initialHistorySize + 1, controller.historySize)
        assertEquals(1, callbacks.documentChanges.size)
        assertNotEquals(initialPosition, controller.document.rootOffset(rootId))

        assertTrue(controller.undo())
        assertEquals(initialPosition, controller.document.rootOffset(rootId))
        assertTrue(controller.redo())
        assertNotEquals(initialPosition, controller.document.rootOffset(rootId))

        controller.close()
    }

    @Test
    fun debounce_coalescesDerivedOutputs() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val callbacks = RecordingCallbacks()
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.starter(),
            callbacks = callbacks,
            coroutineScope = CoroutineScope(SupervisorJob() + dispatcher),
            debounceMillis = 200L,
        )
        callbacks.clear()

        controller.onAction(
            WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_CLICK_TEXT, 96f, 120f),
        )
        controller.onAction(
            WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 96f, 180f),
        )

        assertEquals(2, callbacks.documentChanges.size)
        assertEquals(0, callbacks.validationBatches.size)
        assertEquals(0, callbacks.emscriptDrafts.size)

        advanceTimeBy(199L)
        runCurrent()
        assertEquals(0, callbacks.validationBatches.size)

        advanceTimeBy(1L)
        runCurrent()
        assertEquals(1, callbacks.validationBatches.size)
        assertEquals(1, callbacks.emscriptDrafts.size)
        assertEquals(0, callbacks.emscriptGenerationFailures.size)

        controller.close()
    }

    @Test
    fun dispose_suppressesFurtherCallbacks() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val callbacks = RecordingCallbacks()
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.starter(),
            callbacks = callbacks,
            coroutineScope = CoroutineScope(SupervisorJob() + dispatcher),
            debounceMillis = 200L,
        )
        callbacks.clear()

        controller.close()
        assertTrue(controller.isDisposed)

        controller.onAction(
            WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_CLICK_TEXT, 96f, 120f),
        )
        advanceTimeBy(250L)
        runCurrent()

        assertEquals(0, callbacks.documentChanges.size)
        assertEquals(0, callbacks.validationBatches.size)
        assertEquals(0, callbacks.emscriptDrafts.size)
    }

    @Test
    fun dispose_cancelsPendingDebounce() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val callbacks = RecordingCallbacks()
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.starter(),
            callbacks = callbacks,
            coroutineScope = CoroutineScope(SupervisorJob() + dispatcher),
            debounceMillis = 200L,
        )
        callbacks.clear()

        controller.onAction(
            WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_CLICK_TEXT, 96f, 120f),
        )
        controller.close()

        advanceTimeBy(300L)
        runCurrent()

        assertEquals(1, callbacks.documentChanges.size)
        assertEquals(0, callbacks.validationBatches.size)
        assertEquals(0, callbacks.emscriptDrafts.size)
        assertEquals(0, callbacks.emscriptGenerationFailures.size)
    }

    @Test
    fun generationFailure_emitsFailureCallback_withoutEmptyDraft_and_keepsLastValidDraft() = runTest {
        val callbacks = RecordingCallbacks()
        val dispatcher = StandardTestDispatcher(testScheduler)
        var shouldFail = false
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.starter(),
            callbacks = callbacks,
            coroutineScope = CoroutineScope(SupervisorJob() + dispatcher),
            debounceMillis = 50L,
            workspaceCodeGenerator = WorkspaceCodeGenerator { doc ->
                if (shouldFail) error("generator boom")
                "# Script: ${doc.id}\nLOG \"ok\""
            },
        )

        assertEquals(listOf("# Script: workspace\nLOG \"ok\""), callbacks.emscriptDrafts)
        assertEquals("# Script: workspace\nLOG \"ok\"", callbacks.lastEmscriptDraft)
        callbacks.clear()

        shouldFail = true
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 96f, 120f))
        advanceTimeBy(50L)
        runCurrent()

        assertEquals(1, callbacks.documentChanges.size)
        assertEquals(0, callbacks.emscriptDrafts.size)
        assertEquals(1, callbacks.emscriptGenerationFailures.size)
        assertNotEquals("", callbacks.emscriptGenerationFailures.single())
        assertTrue(callbacks.emscriptGenerationFailures.single().contains("EMScript generation failed:"))
        assertEquals("# Script: workspace\nLOG \"ok\"", callbacks.lastEmscriptDraft)

        shouldFail = false
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_CLICK_TEXT, 96f, 140f))
        advanceTimeBy(50L)
        runCurrent()

        assertEquals(1, callbacks.emscriptDrafts.size)
        assertEquals(1, callbacks.emscriptGenerationFailures.size)
        controller.close()
    }

    @Test
    fun generationFailure_inDebounce_doesNotBreakFutureProcessing() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val callbacks = RecordingCallbacks()
        var shouldFail = false
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.starter(),
            callbacks = callbacks,
            coroutineScope = CoroutineScope(SupervisorJob() + dispatcher),
            debounceMillis = 50L,
            workspaceCodeGenerator = WorkspaceCodeGenerator {
                if (shouldFail) error("debounce failure")
                "# Script: workspace\nLOG \"after-failure\""
            }
        )
        callbacks.clear()

        shouldFail = true
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 96f, 120f))
        advanceTimeBy(50L)
        runCurrent()

        assertEquals(0, callbacks.emscriptDrafts.size)
        assertEquals(1, callbacks.emscriptGenerationFailures.size)

        shouldFail = false
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_CLICK_TEXT, 96f, 140f))
        advanceTimeBy(50L)
        runCurrent()

        assertEquals(1, callbacks.emscriptDrafts.size)
        assertEquals(1, callbacks.emscriptGenerationFailures.size)
        controller.close()
    }

    @Test
    fun generationFailureCallbacks_stopAfterDisposal() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val callbacks = RecordingCallbacks()
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.starter(),
            callbacks = callbacks,
            coroutineScope = CoroutineScope(SupervisorJob() + dispatcher),
            debounceMillis = 50L,
            workspaceCodeGenerator = WorkspaceCodeGenerator { error("always fail") },
        )
        callbacks.clear()

        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 96f, 120f))
        controller.close()
        advanceTimeBy(100L)
        runCurrent()

        assertEquals(0, callbacks.emscriptGenerationFailures.size)
        assertEquals(1, callbacks.documentChanges.size)
    }

    @Test
    fun injectedGeneratorDrivesInitialPreviewDebounceAndExplicitRegeneration() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val callbacks = RecordingCallbacks()
        var calls = 0
        val generator = WorkspaceCodeGenerator { "generated-${++calls}" }
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.starter(),
            callbacks = callbacks,
            workspaceCodeGenerator = generator,
            coroutineScope = CoroutineScope(SupervisorJob() + dispatcher),
            debounceMillis = 20L,
        )

        assertEquals(listOf("generated-1"), callbacks.emscriptDrafts)
        assertEquals("generated-2", controller.codePreview)
        assertEquals("generated-3", controller.regenerateCode())
        assertEquals("generated-3", callbacks.emscriptDrafts.last())

        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 96f, 120f))
        advanceTimeBy(20L)
        runCurrent()
        assertEquals("generated-4", callbacks.emscriptDrafts.last())
        controller.close()
    }

    @Test
    fun previewFailureRetainsPreviousValidDraftAndReportsFailure() {
        val callbacks = RecordingCallbacks()
        var fail = false
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.starter(),
            callbacks = callbacks,
            workspaceCodeGenerator = WorkspaceCodeGenerator {
                if (fail) error("preview failed") else "valid-draft"
            },
        )
        fail = true

        assertEquals("valid-draft", controller.codePreview)
        assertEquals(0, callbacks.emscriptGenerationFailures.size)
        assertEquals(listOf("valid-draft"), callbacks.emscriptDrafts)
        controller.close()
    }

    @Test fun invalidChoiceValueIsRejectedWithoutDocumentChange() {
        val choice = BlockDefinition(
            "choice",
            "Choice",
            "test",
            true,
            true,
            fields = listOf(
                FieldDefinition(
                    key = "mode",
                    label = "Mode",
                    kind = FieldKind.CHOICE,
                    defaultValue = "a",
                    options = listOf(FieldOption("a", "A"), FieldOption("b", "B")),
                ),
            ),
        )
        val callbacks = RecordingCallbacks()
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
            callbacks = callbacks,
            registry = CompositeBlockRegistry(StaticBlockRegistry(listOf(choice))),
        )
        callbacks.clear()

        controller.addBlockFromPalette(choice)
        controller.onTap(Offset2(96f, 120f))
        callbacks.clear()

        controller.updateBlockField("mode", "invalid")
        assertEquals(0, callbacks.documentChanges.size)

        controller.updateBlockField("mode", "b")
        assertEquals(1, callbacks.documentChanges.size)
        controller.close()
    }

    @Test fun hiddenDefinitionsAreExcludedFromPaletteButRemainInRegistry() {
        val hidden = BlockDefinition("hidden", "Hidden", "test", true, true, paletteVisible = false)
        val visible = BlockDefinition("visible", "Visible", "test", true, true, paletteOrder = 5)
        val registry = CompositeBlockRegistry(StaticBlockRegistry(listOf(hidden, visible)))
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
            registry = registry,
        )
        controller.onCategoryClick("test")
        assertEquals(listOf("visible"), controller.definitionsForExpandedCategory().map(BlockDefinition::id))
        assertEquals("hidden", registry.getDefinition("hidden")?.id)
        controller.close()
    }

    private class RecordingCallbacks : BlockEditorHostCallbacks {
        val documentChanges = mutableListOf<String>()
        val emscriptDrafts = mutableListOf<String>()
        val emscriptGenerationFailures = mutableListOf<String>()
        val validationBatches = mutableListOf<List<ValidationError>>()
        var lastEmscriptDraft: String? = null

        fun clear() {
            documentChanges.clear()
            emscriptDrafts.clear()
            emscriptGenerationFailures.clear()
            validationBatches.clear()
        }

        override fun onWorkspaceDocumentChanged(serializedJson: String) {
            documentChanges += serializedJson
        }

        override fun onEmscriptDraftChanged(emscript: String) {
            emscriptDrafts += emscript
            lastEmscriptDraft = emscript
        }

        override fun onValidationErrors(errors: List<ValidationError>) {
            validationBatches += errors
        }

        override fun onEmscriptGenerationFailed(message: String) {
            emscriptGenerationFailures += message
        }
    }
}

private fun BlockEditorController.selectBlockCenter(blockId: de.visualtasker.blockeditor.domain.BlockId) {
    val bounds = layoutCache.flatIndex.visibleBlocks.single { it.blockId == blockId }.bounds
    onTap(Offset2(bounds.x + bounds.width * 0.5f, bounds.y + 12f))
}
