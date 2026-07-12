package de.visualtasker.blockeditor.compose.host

import de.visualtasker.blockeditor.domain.Offset2
import de.visualtasker.blockeditor.domain.WorkspaceAction
import de.visualtasker.blockeditor.interaction.ViewportState
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.WorkspaceBootstrap
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
        assertTrue(callbacks.emscriptDrafts.single().contains("# Script:"))

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
    }

    private class RecordingCallbacks : BlockEditorHostCallbacks {
        val documentChanges = mutableListOf<String>()
        val emscriptDrafts = mutableListOf<String>()
        val validationBatches = mutableListOf<List<ValidationError>>()

        fun clear() {
            documentChanges.clear()
            emscriptDrafts.clear()
            validationBatches.clear()
        }

        override fun onWorkspaceDocumentChanged(serializedJson: String) {
            documentChanges += serializedJson
        }

        override fun onEmscriptDraftChanged(emscript: String) {
            emscriptDrafts += emscript
        }

        override fun onValidationErrors(errors: List<ValidationError>) {
            validationBatches += errors
        }
    }
}
