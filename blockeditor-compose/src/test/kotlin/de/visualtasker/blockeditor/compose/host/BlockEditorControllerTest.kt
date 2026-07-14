package de.visualtasker.blockeditor.compose.host

import de.visualtasker.blockeditor.domain.Offset2
import de.visualtasker.blockeditor.domain.WorkspaceAction
import de.visualtasker.blockeditor.emscript.WorkspaceCodeGenerator
import de.visualtasker.blockeditor.interaction.ViewportState
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.WorkspaceBootstrap
import de.visualtasker.blockeditor.registry.BlockDefinition
import de.visualtasker.blockeditor.registry.CompositeBlockRegistry
import de.visualtasker.blockeditor.registry.FieldDefinition
import de.visualtasker.blockeditor.registry.FieldKind
import de.visualtasker.blockeditor.registry.FieldOption
import de.visualtasker.blockeditor.registry.StaticBlockRegistry
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
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BlockEditorControllerTest {
    @Test
    fun initialState_emitsValidationAndEmscriptOnly() {
        val callbacks = RecordingCallbacks()
        val controller = BlockEditorController(
            initialDocument = WorkspaceBootstrap.starter(),
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
            initialDocument = WorkspaceBootstrap.starter(),
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
