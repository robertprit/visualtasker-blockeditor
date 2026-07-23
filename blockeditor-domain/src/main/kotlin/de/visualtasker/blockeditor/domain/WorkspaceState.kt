package de.visualtasker.blockeditor.domain

data class WorkspaceHistory(
    val undoStack: List<WorkspaceDocument> = emptyList(),
    val redoStack: List<WorkspaceDocument> = emptyList(),
)

data class WorkspaceState(
    val document: WorkspaceDocument,
    val history: WorkspaceHistory = WorkspaceHistory(),
) {
    fun record(nextDocument: WorkspaceDocument): WorkspaceState {
        if (nextDocument == document) return this
        return copy(
            document = nextDocument,
            history = WorkspaceHistory(
                undoStack = history.undoStack + document,
                redoStack = emptyList(),
            ),
        )
    }

    fun replaceWithoutHistory(nextDocument: WorkspaceDocument): WorkspaceState =
        copy(document = nextDocument)

    fun undo(): WorkspaceState? {
        val previous = history.undoStack.lastOrNull() ?: return null
        return copy(
            document = previous,
            history = WorkspaceHistory(
                undoStack = history.undoStack.dropLast(1),
                redoStack = history.redoStack + document,
            ),
        )
    }

    fun redo(): WorkspaceState? {
        val next = history.redoStack.lastOrNull() ?: return null
        return copy(
            document = next,
            history = WorkspaceHistory(
                undoStack = history.undoStack + document,
                redoStack = history.redoStack.dropLast(1),
            ),
        )
    }
}
