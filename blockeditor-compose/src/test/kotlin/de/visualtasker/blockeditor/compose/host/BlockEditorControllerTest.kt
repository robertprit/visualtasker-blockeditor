package de.visualtasker.blockeditor.compose.host

import de.visualtasker.blockeditor.domain.Offset2
import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.BlockNode
import de.visualtasker.blockeditor.domain.FieldValue
import de.visualtasker.blockeditor.domain.VariableDefinition
import de.visualtasker.blockeditor.domain.VariableRegistry
import de.visualtasker.blockeditor.domain.VariableScope
import de.visualtasker.blockeditor.domain.WorkspaceAction
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.WorkspaceGraph
import de.visualtasker.blockeditor.domain.WorkspacePoint
import de.visualtasker.blockeditor.domain.WorkspaceReducer
import de.visualtasker.blockeditor.domain.asString
import de.visualtasker.blockeditor.domain.rootOffset
import de.visualtasker.blockeditor.domain.withRootOffset
import de.visualtasker.blockeditor.compose.render.contrastTextColor
import de.visualtasker.blockeditor.compose.model.REPORTER_VISUAL_MODE_METADATA_KEY
import de.visualtasker.blockeditor.compose.model.ReporterVisualMode
import de.visualtasker.blockeditor.compose.theme.darkBlockEditorColors
import de.visualtasker.blockeditor.compose.theme.lightBlockEditorColors
import de.visualtasker.blockeditor.compose.viewmodel.parameterSourceFieldKey
import de.visualtasker.blockeditor.emscript.WorkspaceCodeGenerator
import de.visualtasker.blockeditor.interaction.DragPullMode
import de.visualtasker.blockeditor.interaction.ViewportState
import de.visualtasker.blockeditor.layout.LayoutConstants
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.BlockCategories
import de.visualtasker.blockeditor.registry.WorkspaceBootstrap
import de.visualtasker.blockeditor.registry.BlockDefinition
import de.visualtasker.blockeditor.registry.CompositeBlockRegistry
import de.visualtasker.blockeditor.registry.FieldDefinition
import de.visualtasker.blockeditor.registry.FieldKind
import de.visualtasker.blockeditor.registry.FieldOption
import de.visualtasker.blockeditor.registry.StatementInputDefinition
import de.visualtasker.blockeditor.registry.StaticBlockRegistry
import de.visualtasker.blockeditor.registry.ValueInputDefinition
import de.visualtasker.blockeditor.registry.VariableReporterFactory
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry
import de.visualtasker.blockeditor.registry.asFactory
import de.visualtasker.blockeditor.registry.createNode
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
        assertTrue(BlockEditorHostUiConfig().showToolbox)
        assertTrue(BlockEditorHostUiConfig().gridEnabled)
        assertTrue(BlockEditorHostUiConfig().extraCategories.isEmpty())
        assertEquals(BlockPaletteInsertMode.TapToAdd, BlockEditorHostUiConfig().paletteInsertMode)
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
    fun displayModeFieldUpdatesReporterVisualMetadata() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
        )
        controller.createVariable("counter", "Number")
        val blockId = controller.document.blocks.entries.first {
            it.value.type.startsWith(BlockTypes.VARIABLE_REPORTER_PREFIX)
        }.key
        controller.selectBlockCenter(blockId)

        controller.updateBlockField("displayMode", "detailed")

        val block = controller.document.blocks.getValue(blockId)
        assertEquals(ReporterVisualMode.DETAILED.name, block.metadata[REPORTER_VISUAL_MODE_METADATA_KEY])
        assertEquals("detailed", controller.selectedBlockInfo()!!.fields.single { it.key == "displayMode" }.value)

        controller.updateBlockField("displayMode", "compact")

        val compactBlock = controller.document.blocks.getValue(blockId)
        assertEquals(ReporterVisualMode.COMPACT.name, compactBlock.metadata[REPORTER_VISUAL_MODE_METADATA_KEY])
        assertEquals("compact", controller.selectedBlockInfo()!!.fields.single { it.key == "displayMode" }.value)

        controller.close()
    }

    @Test
    fun initialLayoutRegistersDynamicVariableReportersBeforeMeasuring() {
        val variable = VariableDefinition(
            id = "thresholdLow",
            name = "thresholdLow",
            type = "Number",
            scope = VariableScope.Global,
        )
        val reporterDefinition = VariableReporterFactory.create(variable)
        val reporter = reporterDefinition.createNode(BlockId("thresholdLow-reporter"))
        val document = WorkspaceDocument(
            id = "initial-variable-layout",
            blocks = mapOf(reporter.id to reporter),
            rootBlocks = listOf(reporter.id),
            rootPositions = mapOf(reporter.id to WorkspacePoint(96f, 120f)),
            variables = VariableRegistry(mapOf(variable.id to variable)),
        )

        val controller = BlockEditorController(initialDocument = document)

        val bounds = controller.layoutCache.flatIndex.visibleBlocks
            .single { it.blockId == reporter.id }
            .bounds
        assertEquals(LayoutConstants.REPORTER_WIDTH, bounds.width, 0.001f)
        assertEquals(LayoutConstants.REPORTER_HEIGHT, bounds.height, 0.001f)

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
    fun undoRedoCoversAddDeleteMoveDockUndockBranchFieldAndAutoArrange() {
        assertUndoRedoRestores(
            mutate = { controller ->
                controller.addBlockFromPalette(DefaultBlockRegistry.getDefinition(BlockTypes.ACTION_WAIT)!!)
            },
            changed = { before, after -> after.blocks.size == before.blocks.size + 1 },
        )
        assertUndoRedoRestores(
            seed = {
                singleBlockDocument(BlockTypes.ACTION_WAIT, BlockId("wait"))
            },
            mutate = { controller ->
                controller.selectBlockCenter(BlockId("wait"))
                controller.deleteSelectedBlock()
            },
            changed = { before, after -> after.blocks.size == before.blocks.size - 1 },
        )
        assertUndoRedoRestores(
            seed = {
                singleBlockDocument(BlockTypes.ACTION_WAIT, BlockId("wait"))
            },
            mutate = { controller ->
                controller.onAction(WorkspaceAction.MoveRoot(BlockId("wait"), 180f, 240f))
            },
            changed = { before, after -> before.rootOffset(BlockId("wait")) != after.rootOffset(BlockId("wait")) },
        )
        assertUndoRedoRestores(
            seed = ::dockableReporterDocument,
            mutate = { controller ->
                val reporter = controller.document.blocks.getValue(BlockId("number"))
                val compare = controller.document.blocks.getValue(BlockId("compare"))
                controller.onAction(WorkspaceAction.Connect(reporter.output!!.id, compare.valueInputs.first { it.name == "LEFT" }.connection.id))
            },
            changed = { _, after ->
                after.blocks.getValue(BlockId("compare")).valueInputs.first { it.name == "LEFT" }.connection.connectedTo != null
            },
        )
        assertUndoRedoRestores(
            seed = {
                dockableReporterDocument().let { doc ->
                    val reporter = doc.blocks.getValue(BlockId("number"))
                    val compare = doc.blocks.getValue(BlockId("compare"))
                    WorkspaceReducer.reduce(
                        doc,
                        WorkspaceAction.Connect(reporter.output!!.id, compare.valueInputs.first { it.name == "LEFT" }.connection.id),
                        DefaultBlockRegistry.asFactory(),
                    )
                }
            },
            mutate = { controller ->
                controller.onAction(WorkspaceAction.Disconnect(controller.document.blocks.getValue(BlockId("number")).output!!.id))
            },
            changed = { _, after ->
                after.blocks.getValue(BlockId("compare")).valueInputs.first { it.name == "LEFT" }.connection.connectedTo == null
            },
        )
        assertUndoRedoRestores(
            seed = {
                singleBlockDocument(BlockTypes.CONTROL_IF, BlockId("if"))
            },
            mutate = { controller ->
                controller.selectBlockCenter(BlockId("if"))
                controller.addSelectedIfBranch(BlockTypes.CONTROL_IF, BlockTypes.CONTROL_IF_ELSE)
            },
            changed = { before, after -> before.blocks.getValue(BlockId("if")).statementInputs.size != after.blocks.getValue(BlockId("if")).statementInputs.size },
        )
        assertUndoRedoRestores(
            seed = {
                singleBlockDocument(BlockTypes.ACTION_WAIT, BlockId("wait"))
            },
            mutate = { controller ->
                controller.selectBlockCenter(BlockId("wait"))
                controller.updateBlockField("ms", "750")
            },
            changed = { before, after -> before.blocks.getValue(BlockId("wait")).fields != after.blocks.getValue(BlockId("wait")).fields },
        )
        assertUndoRedoRestores(
            seed = ::multiRootDocument,
            mutate = { controller ->
                controller.onCanvasSizeChange(Offset2(480f, 360f))
                controller.autoArrangeWorkspace()
            },
            changed = { before, after -> before.rootPositions != after.rootPositions },
        )
    }

    @Test
    fun createVariableAndReporterIsSingleUndoableAction() {
        val controller = BlockEditorController(initialDocument = WorkspaceBootstrap.empty())

        controller.createVariable("score", "Number")

        assertEquals(1, controller.historySize)
        assertEquals(1, controller.document.variables.variables.size)
        assertEquals(1, controller.document.blocks.size)

        assertTrue(controller.undo())
        assertEquals(0, controller.document.variables.variables.size)
        assertEquals(0, controller.document.blocks.size)

        assertTrue(controller.redo())
        assertEquals(1, controller.document.variables.variables.size)
        assertEquals(1, controller.document.blocks.size)

        controller.close()
    }

    @Test
    fun selectedControlBlockCanCollapseExpandAndParticipatesInUndoRedo() {
        val blockId = BlockId("if")
        val controller = BlockEditorController(
            initialDocument = singleBlockDocument(BlockTypes.CONTROL_IF_ELSE, blockId),
        )
        val expandedHeight = controller.layoutCache.flatIndex.visibleBlocks
            .single { it.blockId == blockId }
            .bounds.height

        controller.selectBlockCenter(blockId)

        assertTrue(controller.canToggleSelectedBlockCollapse)
        assertFalse(controller.selectedBlockCollapsed)
        assertTrue(controller.toggleSelectedBlockCollapse())
        assertTrue(controller.selectedBlockCollapsed)
        assertEquals(1, controller.historySize)
        assertTrue(
            controller.layoutCache.flatIndex.visibleBlocks
                .single { it.blockId == blockId }
                .bounds.height < expandedHeight,
        )

        assertTrue(controller.undo())
        assertFalse(controller.selectedBlockCollapsed)
        assertTrue(controller.redo())
        assertTrue(controller.selectedBlockCollapsed)
        assertTrue(controller.toggleSelectedBlockCollapse())
        assertFalse(controller.selectedBlockCollapsed)

        controller.close()
    }

    @Test
    fun selectedReporterCollapseUsesCompactBounds() {
        val blockId = BlockId("reporter")
        val controller = BlockEditorController(
            initialDocument = singleBlockDocument(BlockTypes.VARIABLE_GET, blockId),
        )
        val expandedBounds = controller.layoutCache.flatIndex.visibleBlocks
            .single { it.blockId == blockId }
            .bounds

        controller.selectBlockCenter(blockId)

        assertTrue(controller.canToggleSelectedBlockCollapse)
        assertTrue(controller.toggleSelectedBlockCollapse())
        val collapsedBounds = controller.layoutCache.flatIndex.visibleBlocks
            .single { it.blockId == blockId }
            .bounds

        assertTrue(collapsedBounds.width < expandedBounds.width)
        assertTrue(collapsedBounds.height < expandedBounds.height)
        assertEquals(LayoutConstants.COLLAPSED_REPORTER_WIDTH, collapsedBounds.width, 0.001f)
        assertEquals(LayoutConstants.COLLAPSED_REPORTER_HEIGHT, collapsedBounds.height, 0.001f)

        controller.close()
    }

    @Test
    fun tappingLeftDragZoneRequestsBlockContextMenu() {
        val blockId = BlockId("wait")
        val controller = BlockEditorController(
            initialDocument = singleBlockDocument(BlockTypes.ACTION_WAIT, blockId),
        )
        val bounds = controller.layoutCache.flatIndex.visibleBlocks.single { it.blockId == blockId }.bounds

        controller.onTap(Offset2(bounds.x + 8f, bounds.y + 12f))

        assertEquals(blockId, controller.blockContextMenuRequest?.blockId)
        controller.dismissBlockContextMenu()
        assertNull(controller.blockContextMenuRequest)

        controller.close()
    }

    @Test
    fun contextActionsUpdateSelectedBlockStateAndBranches() {
        val blockId = BlockId("if")
        val controller = BlockEditorController(
            initialDocument = singleBlockDocument(BlockTypes.CONTROL_IF, blockId),
        )

        controller.selectBlockCenter(blockId)

        assertTrue(controller.toggleSelectedBlockActive())
        assertEquals("false", controller.document.blocks.getValue(blockId).fields.getValue("active").asString())
        assertTrue(controller.updateSelectedBlockNote("small display"))
        assertEquals("small display", controller.document.blocks.getValue(blockId).fields.getValue("note").asString())
        assertTrue(controller.addSelectedIfBranch(BlockTypes.CONTROL_IF, BlockTypes.CONTROL_IF_ELSEIF_ELSE))
        assertTrue(controller.selectedBlockInfo()!!.canRemoveBranch)
        assertTrue(controller.removeSelectedIfBranch(BlockTypes.CONTROL_IF, BlockTypes.CONTROL_IF_ELSEIF_ELSE))
        assertEquals(BlockTypes.CONTROL_IF, controller.document.blocks.getValue(blockId).type)
        assertTrue(controller.selectedBlockInfo()!!.typeOptions.any { it.typeId == BlockTypes.CONTROL_WHILE })

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
    fun cancellingActiveDragClearsTransientDragState() {
        val callbacks = RecordingCallbacks()
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
            callbacks = callbacks,
        )
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 96f, 120f))
        val blockId = controller.document.rootBlocks.single()
        val bounds = controller.layoutCache.flatIndex.visibleBlocks.single { it.blockId == blockId }.bounds
        val center = Offset2(bounds.x + bounds.width * 0.5f, bounds.y + bounds.height * 0.5f)

        assertTrue(controller.onLongPressDragStart(center))
        assertTrue(callbacks.validationEvents.any { it.phase == BlockEditorValidationPhase.DRAG_START })

        controller.cancelActiveDrag()

        assertNull(controller.dragRender)
        assertTrue(callbacks.validationEvents.any { it.phase == BlockEditorValidationPhase.DRAG_CANCEL })

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
    fun draggingVisibleRightReporterOutputDetachesOnlyRightOperand() {
        val fixture = operateWithTwoVariables()
        val controller = BlockEditorController(initialDocument = fixture.document)
        val rightOutput = controller.layoutCache.flatIndex.connectionAnchors
            .single { it.connectionId == fixture.rightReporter.output!!.id }
        val start = Offset2(rightOutput.x, rightOutput.y)
        val beforeRightBounds = controller.layoutCache.flatIndex.visibleBlocks.single { it.blockId == fixture.rightId }.bounds
        val beforeOperatorOffset = controller.document.rootOffset(fixture.operateId)

        assertTrue(controller.onLongPressDragStart(start))
        assertEquals(fixture.rightId, controller.dragRender!!.session.rootBlockId)
        assertEquals(DragPullMode.Single, controller.dragRender!!.session.pullMode)
        assertEquals(beforeRightBounds.x, controller.dragRender!!.session.originalLayoutPosition.x, 0.01f)
        assertEquals(beforeRightBounds.y, controller.dragRender!!.session.originalLayoutPosition.y, 0.01f)

        val drop = Offset2(start.x + 180f, start.y + 40f)
        controller.onPointerMove(drop)
        controller.onPointerUp(drop)

        val operate = controller.document.blocks[fixture.operateId]!!
        assertEquals(fixture.leftReporter.output!!.id, operate.valueInputs.first { it.name == "Input1" }.connection.connectedTo)
        assertNull(operate.valueInputs.first { it.name == "Input2" }.connection.connectedTo)
        assertNull(controller.document.blocks[fixture.rightId]!!.output!!.connectedTo)
        assertEquals(beforeOperatorOffset, controller.document.rootOffset(fixture.operateId))
        assertTrue(fixture.rightId in controller.document.rootBlocks)

        controller.close()
    }

    @Test
    fun draggingVisibleLeftReporterOutputIsIndependentOfAnchorOrder() {
        val fixture = operateWithTwoVariables()
        val controller = BlockEditorController(initialDocument = fixture.document)
        val leftOutput = controller.layoutCache.flatIndex.connectionAnchors
            .reversed()
            .single { it.connectionId == fixture.leftReporter.output!!.id }
        val start = Offset2(leftOutput.x, leftOutput.y)

        assertTrue(controller.onLongPressDragStart(start))
        assertEquals(fixture.leftId, controller.dragRender!!.session.rootBlockId)

        val drop = Offset2(start.x + 160f, start.y + 32f)
        controller.onPointerMove(drop)
        controller.onPointerUp(drop)

        val operate = controller.document.blocks[fixture.operateId]!!
        assertNull(operate.valueInputs.first { it.name == "Input1" }.connection.connectedTo)
        assertEquals(fixture.rightReporter.output!!.id, operate.valueInputs.first { it.name == "Input2" }.connection.connectedTo)
        assertNull(controller.document.blocks[fixture.leftId]!!.output!!.connectedTo)

        controller.close()
    }

    @Test
    fun draggingNestedOperateReporterKeepsOwnOperandsAndLeavesSiblingReporterAttached() {
        val fixture = nestedOperateInBooleanParent()
        val controller = BlockEditorController(initialDocument = fixture.document)
        val operateOutput = controller.layoutCache.flatIndex.connectionAnchors
            .single { it.connectionId == fixture.operate.output!!.id }
        val start = Offset2(operateOutput.x, operateOutput.y)

        assertTrue(controller.onLongPressDragStart(start))
        assertEquals(fixture.operateId, controller.dragRender!!.session.rootBlockId)
        assertTrue(fixture.leftId in controller.dragRender!!.session.includedBlocks)
        assertTrue(fixture.rightId in controller.dragRender!!.session.includedBlocks)
        assertTrue(fixture.siblingId !in controller.dragRender!!.session.includedBlocks)

        val drop = Offset2(start.x + 220f, start.y + 60f)
        controller.onPointerMove(drop)
        controller.onPointerUp(drop)

        val parent = controller.document.blocks[fixture.parentId]!!
        val operate = controller.document.blocks[fixture.operateId]!!
        assertNull(parent.valueInputs.first { it.name == "A" }.connection.connectedTo)
        assertEquals(fixture.sibling.output!!.id, parent.valueInputs.first { it.name == "B" }.connection.connectedTo)
        assertEquals(fixture.leftReporter.output!!.id, operate.valueInputs.first { it.name == "Input1" }.connection.connectedTo)
        assertEquals(fixture.rightReporter.output!!.id, operate.valueInputs.first { it.name == "Input2" }.connection.connectedTo)
        assertTrue(fixture.operateId in controller.document.rootBlocks)

        controller.close()
    }

    @Test
    fun draggingParentKeepsReporterConnectionsAttached() {
        val fixture = nestedOperateInBooleanParent()
        val controller = BlockEditorController(initialDocument = fixture.document)
        val parentBounds = controller.layoutCache.flatIndex.visibleBlocks.single { it.blockId == fixture.parentId }.bounds
        val start = Offset2(parentBounds.x + parentBounds.width * 0.5f, parentBounds.y + parentBounds.height * 0.5f)

        assertTrue(controller.onLongPressDragStart(start))
        assertEquals(fixture.parentId, controller.dragRender!!.session.rootBlockId)
        assertTrue(fixture.operateId in controller.dragRender!!.session.includedBlocks)
        assertTrue(fixture.leftId in controller.dragRender!!.session.includedBlocks)
        assertTrue(fixture.rightId in controller.dragRender!!.session.includedBlocks)
        assertTrue(fixture.siblingId in controller.dragRender!!.session.includedBlocks)
        controller.onPointerMove(Offset2(start.x + 180f, start.y + 30f))
        controller.onPointerUp(Offset2(start.x + 180f, start.y + 30f))

        val parent = controller.document.blocks[fixture.parentId]!!
        val operate = controller.document.blocks[fixture.operateId]!!
        assertEquals(fixture.operate.output!!.id, parent.valueInputs.first { it.name == "A" }.connection.connectedTo)
        assertEquals(fixture.sibling.output!!.id, parent.valueInputs.first { it.name == "B" }.connection.connectedTo)
        assertEquals(fixture.leftReporter.output!!.id, operate.valueInputs.first { it.name == "Input1" }.connection.connectedTo)
        assertEquals(fixture.rightReporter.output!!.id, operate.valueInputs.first { it.name == "Input2" }.connection.connectedTo)

        controller.close()
    }

    @Test
    fun tapOnConnectedReporterDoesNotDetachButDragCreatesSingleUndoableDetach() {
        val fixture = operateWithTwoVariables()
        val controller = BlockEditorController(initialDocument = fixture.document)
        val rightOutput = controller.layoutCache.flatIndex.connectionAnchors
            .single { it.connectionId == fixture.rightReporter.output!!.id }
        val start = Offset2(rightOutput.x, rightOutput.y)
        val initialHistory = controller.historySize

        controller.onTap(start)
        assertEquals(fixture.rightReporter.output!!.id, controller.document.blocks[fixture.operateId]!!.valueInputs.first { it.name == "Input2" }.connection.connectedTo)
        assertEquals(initialHistory, controller.historySize)

        assertTrue(controller.onLongPressDragStart(start))
        controller.onPointerMove(Offset2(start.x + 140f, start.y + 40f))
        controller.onPointerMove(Offset2(start.x + 180f, start.y + 60f))
        assertEquals(initialHistory, controller.historySize)
        controller.onPointerUp(Offset2(start.x + 180f, start.y + 60f))

        assertEquals(initialHistory + 1, controller.historySize)
        assertNull(controller.document.blocks[fixture.operateId]!!.valueInputs.first { it.name == "Input2" }.connection.connectedTo)
        assertTrue(controller.undo())
        assertEquals(fixture.rightReporter.output!!.id, controller.document.blocks[fixture.operateId]!!.valueInputs.first { it.name == "Input2" }.connection.connectedTo)
        assertTrue(controller.redo())
        assertNull(controller.document.blocks[fixture.operateId]!!.valueInputs.first { it.name == "Input2" }.connection.connectedTo)

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
    fun rootDragMoveKeepsFollowingPointerOutsideVisibleWorkspace() {
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

        assertTrue(offset.x < -1000f)
        assertTrue(offset.y < -900f)
        controller.onPointerUp(Offset2(-1200f, -900f))

        assertEquals(Offset2(0f, 0f), controller.document.rootOffset(blockId))

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
    fun renameVariableKeepsStableIdAndParticipatesInUndoRedo() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
        )
        controller.createVariable("score", "Number")
        val variableId = controller.document.variables.variables.values.single().id

        assertTrue(controller.renameVariable(variableId, "points"))

        assertEquals(variableId, controller.document.variables.variables.values.single().id)
        assertEquals("points", controller.document.variables.variables.getValue(variableId).name)

        controller.undo()
        assertEquals("score", controller.document.variables.variables.getValue(variableId).name)

        controller.redo()
        assertEquals("points", controller.document.variables.variables.getValue(variableId).name)

        assertFalse(controller.renameVariable(variableId, "not valid"))
        assertEquals("points", controller.document.variables.variables.getValue(variableId).name)

        controller.close()
    }

    @Test
    fun duplicatingVariableGetterKeepsReferencedVariableId() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
        )
        controller.createVariable("score", "Number")
        val variableId = controller.document.variables.variables.values.single().id
        val reporterId = controller.document.rootBlocks.single()
        val bounds = controller.layoutCache.flatIndex.visibleBlocks.single { it.blockId == reporterId }.bounds

        controller.onDoubleTap(Offset2(bounds.x + bounds.width * 0.5f, bounds.y + 12f))

        val reporters = controller.document.blocks.values.filter { it.type.startsWith(BlockTypes.VARIABLE_REPORTER_PREFIX) }
        assertEquals(2, reporters.size)
        assertTrue(reporters.all { it.type == VariableReporterFactory.reporterId(variableId) })
        assertEquals(1, controller.document.variables.variables.size)

        controller.close()
    }

    @Test
    fun duplicatingVariableDeclarationCreatesNewVariableId() {
        val registry = CompositeBlockRegistry().apply {
            register(variableDeclareDefinition())
        }
        val controller = BlockEditorController(
            initialDocument = WorkspaceDocument(
                id = "duplicate-declaration",
                variables = VariableRegistry(
                    mapOf("score" to VariableDefinition("score", "score", "Number", VariableScope.Global)),
                ),
            ),
            registry = registry,
        )

        controller.addBlockFromPalette(requireNotNull(registry.getDefinition("emscript:variable.declare")))
        val declareId = controller.document.rootBlocks.single()
        val bounds = controller.layoutCache.flatIndex.visibleBlocks.single { it.blockId == declareId }.bounds

        controller.onDoubleTap(Offset2(bounds.x + bounds.width * 0.5f, bounds.y + 12f))

        val declarations = controller.document.blocks.values.filter { it.type == "emscript:variable.declare" }
        val variableIds = declarations.map { it.fields.getValue("variableId").asString() }.toSet()
        assertEquals(2, declarations.size)
        assertEquals(2, variableIds.size)
        assertTrue("score" in variableIds)
        assertEquals(setOf("score", "scoreCopy"), controller.document.variables.variables.values.map { it.name }.toSet())

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
    fun draggingStatementToElseBranchSnapsToConcreteElseConnection() {
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.empty(),
        )
        controller.onCanvasSizeChange(Offset2(720f, 540f))
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.CONTROL_IF_ELSE, 96f, 120f))
        controller.onAction(WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 360f, 320f))
        val ifId = controller.document.rootBlocks[0]
        val actionId = controller.document.rootBlocks[1]
        val actionBounds = controller.layoutCache.flatIndex.visibleBlocks.single { it.blockId == actionId }.bounds
        val start = Offset2(actionBounds.x + actionBounds.width * 0.5f, actionBounds.y + actionBounds.height * 0.5f)
        val sourceAnchor = controller.layoutCache.flatIndex.connectionAnchors
            .single { it.connectionId == controller.document.blocks.getValue(actionId).previous!!.id }
        val elseInput = controller.document.blocks.getValue(ifId).statementInputs.single { it.name == BlockTypes.SLOT_ELSE }
        val elseAnchor = controller.layoutCache.flatIndex.connectionAnchors
            .single { it.connectionId == elseInput.connection.id }
        val thenInput = controller.document.blocks.getValue(ifId).statementInputs.single { it.name == BlockTypes.SLOT_THEN }

        assertTrue(controller.onLongPressDragStart(start))
        val drop = Offset2(
            start.x + (elseAnchor.x - sourceAnchor.x),
            start.y + (elseAnchor.y - sourceAnchor.y),
        )
        controller.onPointerMove(drop)

        assertEquals(elseInput.connection.id, controller.dragRender!!.snapCandidate!!.targetConnectionId)
        assertEquals(controller.document.blocks.getValue(actionId).previous!!.id, controller.dragRender!!.snapCandidate!!.sourceConnectionId)

        controller.onPointerUp(drop)

        val ifBlock = controller.document.blocks.getValue(ifId)
        assertEquals(controller.document.blocks.getValue(actionId).previous!!.id, ifBlock.statementInputs.single { it.name == BlockTypes.SLOT_ELSE }.connection.connectedTo)
        assertNull(ifBlock.statementInputs.single { it.name == BlockTypes.SLOT_THEN }.connection.connectedTo)
        assertEquals(thenInput.connection.id, controller.document.blocks.getValue(ifId).statementInputs.single { it.name == BlockTypes.SLOT_THEN }.connection.id)
        assertEquals(ifId to BlockTypes.SLOT_ELSE, WorkspaceGraph.slotContaining(controller.document, actionId))
        assertFalse(actionId in controller.document.rootBlocks)

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

    @Test fun closeTopMostPanelClosesFactoryBeforeBottomPanel() {
        val controller = BlockEditorController(initialDocument = WorkspaceBootstrap.empty())
        controller.openBlockFactory()

        assertTrue(controller.showBlockFactory)
        assertTrue(controller.showBottomPanel)
        assertTrue(controller.closeTopMostPanel())

        assertFalse(controller.showBlockFactory)
        assertEquals(BlockCategories.CUSTOM, controller.expandedCategory)
        assertTrue(controller.showBottomPanel)
        controller.close()
    }

    @Test fun closeTopMostPanelClosesPaletteBeforeBottomPanel() {
        val controller = BlockEditorController(initialDocument = WorkspaceBootstrap.empty())
        controller.onCategoryClick(BlockCategories.ACTION)

        assertEquals(BlockCategories.ACTION, controller.expandedCategory)
        assertTrue(controller.showBottomPanel)
        assertTrue(controller.closeTopMostPanel())

        assertNull(controller.expandedCategory)
        assertTrue(controller.showBottomPanel)
        controller.close()
    }

    @Test fun closeTopMostPanelClosesOnlyBottomPanelWhenNoInnerPanelIsOpen() {
        val controller = BlockEditorController(initialDocument = WorkspaceBootstrap.empty())

        assertTrue(controller.showBottomPanel)
        assertTrue(controller.closeTopMostPanel())

        assertFalse(controller.showBottomPanel)
        assertFalse(controller.closeTopMostPanel())
        controller.close()
    }

    @Test fun closeTopMostPanelReturnsFalseWhenHostMayCloseOuterPanel() {
        val controller = BlockEditorController(initialDocument = WorkspaceBootstrap.empty())
        controller.setBottomPanelVisible(false)

        assertFalse(controller.showBlockFactory)
        assertNull(controller.expandedCategory)
        assertFalse(controller.showBottomPanel)
        assertFalse(controller.closeTopMostPanel())
        controller.close()
    }

    @Test fun repeatedCloseTopMostPanelClosesOneLayerAtATime() {
        val controller = BlockEditorController(initialDocument = WorkspaceBootstrap.empty())
        controller.openBlockFactory()

        assertTrue(controller.closeTopMostPanel())
        assertFalse(controller.showBlockFactory)
        assertEquals(BlockCategories.CUSTOM, controller.expandedCategory)
        assertTrue(controller.showBottomPanel)

        assertTrue(controller.closeTopMostPanel())
        assertFalse(controller.showBlockFactory)
        assertNull(controller.expandedCategory)
        assertTrue(controller.showBottomPanel)

        assertTrue(controller.closeTopMostPanel())
        assertFalse(controller.showBottomPanel)

        assertFalse(controller.closeTopMostPanel())
        controller.close()
    }

    private fun assertUndoRedoRestores(
        seed: () -> WorkspaceDocument = { WorkspaceBootstrap.empty() },
        mutate: (BlockEditorController) -> Unit,
        changed: (WorkspaceDocument, WorkspaceDocument) -> Boolean,
    ) {
        val controller = BlockEditorController(initialDocument = seed())
        val before = controller.document

        mutate(controller)

        val after = controller.document
        assertTrue("Mutation should change document", changed(before, after))
        assertEquals("Mutation should create one undo entry", 1, controller.historySize)
        assertEquals("Mutation should clear redo stack", 0, controller.redoSize)

        assertTrue(controller.undo())
        assertEquals(before, controller.document)
        assertEquals(0, controller.historySize)
        assertEquals(1, controller.redoSize)

        assertTrue(controller.redo())
        assertEquals(after, controller.document)
        assertEquals(1, controller.historySize)
        assertEquals(0, controller.redoSize)

        controller.close()
    }

    private fun singleBlockDocument(type: String, id: BlockId): WorkspaceDocument {
        val block = DefaultBlockRegistry.getDefinition(type)!!
            .createNode(id)
        return WorkspaceDocument(
            id = "single-${id.value}",
            blocks = mapOf(id to block),
            rootBlocks = listOf(id),
        ).withRootOffset(id, 96f, 120f)
    }

    private fun dockableReporterDocument(): WorkspaceDocument {
        val compareId = BlockId("compare")
        val numberId = BlockId("number")
        val compare = DefaultBlockRegistry.getDefinition(BlockTypes.LOGIC_COMPARE)!!
            .createNode(compareId)
        val number = DefaultBlockRegistry.getDefinition(BlockTypes.LITERAL_NUMBER)!!
            .createNode(numberId)
        var document = WorkspaceDocument(
            id = "dockable-reporter",
            blocks = mapOf(
                compareId to compare,
                numberId to number,
            ),
            rootBlocks = listOf(compareId, numberId),
        )
        document = document.withRootOffset(compareId, 96f, 120f)
        document = document.withRootOffset(numberId, 96f, 200f)
        return document
    }

    private fun multiRootDocument(): WorkspaceDocument {
        val firstId = BlockId("first")
        val secondId = BlockId("second")
        val waitDefinition = DefaultBlockRegistry.getDefinition(BlockTypes.ACTION_WAIT)!!
        var document = WorkspaceDocument(
            id = "multi-root",
            blocks = mapOf(
                firstId to waitDefinition.createNode(firstId),
                secondId to waitDefinition.createNode(secondId),
            ),
            rootBlocks = listOf(firstId, secondId),
        )
        document = document.withRootOffset(firstId, 320f, 280f)
        document = document.withRootOffset(secondId, 40f, 40f)
        return document
    }

    private fun operateWithTwoVariables(): OperateFixture {
        val operateId = BlockId("operate")
        val leftId = BlockId("v1")
        val rightId = BlockId("v2")
        var operate = DefaultBlockRegistry.getDefinition(BlockTypes.LOGIC_OPERATE)!!
            .createNode(operateId)
            .withRootOffset(40f, 40f)
        val left = variableReporter(leftId, "v1")
        val right = variableReporter(rightId, "v2")
        val leftInput = operate.valueInputs.first { it.name == "Input1" }.connection
        val rightInput = operate.valueInputs.first { it.name == "Input2" }.connection
        operate = operate.copy(
            valueInputs = operate.valueInputs.map { input ->
                when (input.name) {
                    "Input1" -> input.copy(connection = leftInput.copy(connectedTo = left.output!!.id))
                    "Input2" -> input.copy(connection = rightInput.copy(connectedTo = right.output!!.id))
                    else -> input
                }
            },
        )
        val connectedLeft = left.copy(output = left.output!!.copy(connectedTo = leftInput.id))
        val connectedRight = right.copy(output = right.output!!.copy(connectedTo = rightInput.id))
        return OperateFixture(
            document = WorkspaceDocument(
                id = "operate-two-vars",
                blocks = mapOf(
                    operateId to operate,
                    leftId to connectedLeft,
                    rightId to connectedRight,
                ),
                rootBlocks = listOf(operateId, leftId, rightId),
            ),
            operateId = operateId,
            leftId = leftId,
            rightId = rightId,
            operate = operate,
            leftReporter = connectedLeft,
            rightReporter = connectedRight,
        )
    }

    private fun nestedOperateInBooleanParent(): NestedOperateFixture {
        val operateFixture = operateWithTwoVariables()
        val parentId = BlockId("compare")
        val siblingId = BlockId("v3")
        var parent = DefaultBlockRegistry.getDefinition(BlockTypes.LOGIC_AND)!!
            .createNode(parentId)
            .withRootOffset(24f, 24f)
        val sibling = variableReporter(siblingId, "v3")
        val inputA = parent.valueInputs.first { it.name == "A" }.connection
        val inputB = parent.valueInputs.first { it.name == "B" }.connection
        parent = parent.copy(
            valueInputs = parent.valueInputs.map { input ->
                when (input.name) {
                    "A" -> input.copy(connection = inputA.copy(connectedTo = operateFixture.operate.output!!.id))
                    "B" -> input.copy(connection = inputB.copy(connectedTo = sibling.output!!.id))
                    else -> input
                }
            },
        )
        val operate = operateFixture.operate.copy(
            output = operateFixture.operate.output!!.copy(connectedTo = inputA.id),
        )
        val connectedSibling = sibling.copy(output = sibling.output!!.copy(connectedTo = inputB.id))
        return NestedOperateFixture(
            document = WorkspaceDocument(
                id = "nested-operate",
                blocks = mapOf(
                    parentId to parent,
                    operateFixture.operateId to operate,
                    operateFixture.leftId to operateFixture.leftReporter,
                    operateFixture.rightId to operateFixture.rightReporter,
                    siblingId to connectedSibling,
                ),
                rootBlocks = listOf(parentId, operateFixture.operateId, operateFixture.leftId, operateFixture.rightId, siblingId),
            ),
            parentId = parentId,
            operateId = operateFixture.operateId,
            leftId = operateFixture.leftId,
            rightId = operateFixture.rightId,
            siblingId = siblingId,
            parent = parent,
            operate = operate,
            leftReporter = operateFixture.leftReporter,
            rightReporter = operateFixture.rightReporter,
            sibling = connectedSibling,
        )
    }

    private fun variableReporter(id: BlockId, name: String): BlockNode =
        DefaultBlockRegistry.getDefinition(BlockTypes.VARIABLE_GET)!!
            .createNode(id)
            .copy(fields = mapOf("variable" to FieldValue.Text(name)))

    private data class OperateFixture(
        val document: WorkspaceDocument,
        val operateId: BlockId,
        val leftId: BlockId,
        val rightId: BlockId,
        val operate: BlockNode,
        val leftReporter: BlockNode,
        val rightReporter: BlockNode,
    )

    private data class NestedOperateFixture(
        val document: WorkspaceDocument,
        val parentId: BlockId,
        val operateId: BlockId,
        val leftId: BlockId,
        val rightId: BlockId,
        val siblingId: BlockId,
        val parent: BlockNode,
        val operate: BlockNode,
        val leftReporter: BlockNode,
        val rightReporter: BlockNode,
        val sibling: BlockNode,
    )

    private class RecordingCallbacks : BlockEditorHostCallbacks {
        val documentChanges = mutableListOf<String>()
        val emscriptDrafts = mutableListOf<String>()
        val emscriptGenerationFailures = mutableListOf<String>()
        val validationBatches = mutableListOf<List<ValidationError>>()
        val validationEvents = mutableListOf<BlockEditorValidationEvent>()
        var lastEmscriptDraft: String? = null

        fun clear() {
            documentChanges.clear()
            emscriptDrafts.clear()
            emscriptGenerationFailures.clear()
            validationBatches.clear()
            validationEvents.clear()
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

        override fun onValidationEvent(event: BlockEditorValidationEvent) {
            validationEvents += event
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

private fun variableDeclareDefinition(): BlockDefinition =
    BlockDefinition(
        id = "emscript:variable.declare",
        label = "let",
        category = BlockCategories.VARIABLE,
        hasPrevious = true,
        hasNext = true,
        fields = listOf(
            FieldDefinition("variableId", "id", defaultValue = "score"),
            FieldDefinition("name", "name", defaultValue = "score"),
            FieldDefinition("type", "type", FieldKind.CHOICE, "Number", options = listOf(
                FieldOption("String", "String"),
                FieldOption("Number", "Number"),
                FieldOption("Bool", "Bool"),
                FieldOption("Any", "Any"),
            )),
        ),
        valueInputs = listOf(ValueInputDefinition("initialValue", "=", setOf("Number", "Any"))),
    )
