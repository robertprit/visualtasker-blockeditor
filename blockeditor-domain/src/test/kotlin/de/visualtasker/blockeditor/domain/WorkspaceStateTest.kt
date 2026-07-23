package de.visualtasker.blockeditor.domain

import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry
import de.visualtasker.blockeditor.registry.asFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceStateTest {
    private val factory = DefaultBlockRegistry.asFactory()

    @Test
    fun recordUndoRedo_areWorkspaceTransactions() {
        val initial = WorkspaceDocument(id = "state-test")
        val next = WorkspaceReducer.reduce(
            initial,
            WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 10f, 20f),
            factory,
        )

        val recorded = WorkspaceState(initial).record(next)

        assertEquals(1, recorded.history.undoStack.size)
        assertTrue(recorded.history.redoStack.isEmpty())
        assertEquals(next, recorded.document)

        val undone = recorded.undo()!!
        assertEquals(initial, undone.document)
        assertEquals(listOf(next), undone.history.redoStack)

        val redone = undone.redo()!!
        assertEquals(next, redone.document)
        assertEquals(listOf(initial), redone.history.undoStack)
    }

    @Test
    fun replaceWithoutHistory_doesNotCreateTransaction() {
        val initial = WorkspaceDocument(id = "state-test")
        val replacement = initial.copy(version = 3L)

        val state = WorkspaceState(initial).replaceWithoutHistory(replacement)

        assertEquals(replacement, state.document)
        assertTrue(state.history.undoStack.isEmpty())
        assertTrue(state.history.redoStack.isEmpty())
    }
}
