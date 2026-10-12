package de.visualtasker.workflow.core

data class ProjectHistoryEntry(
    val project: ProjectDocument,
    val activeRootId: WorkflowRootId?,
)

/** Snapshot history around accepted project transactions; it does not mutate workflows itself. */
class ProjectHistoryController(private val maxDepth: Int = 100) {
    private val undoEntries = ArrayDeque<ProjectHistoryEntry>()
    private val redoEntries = ArrayDeque<ProjectHistoryEntry>()

    val undoDepth: Int get() = undoEntries.size
    val redoDepth: Int get() = redoEntries.size

    fun recordAcceptedTransition(before: ProjectDocument, after: ProjectDocument): Boolean {
        if (before == after) return false
        undoEntries.addLast(ProjectHistoryEntry(before, before.manifest.activeRootId))
        while (undoEntries.size > maxDepth) undoEntries.removeFirst()
        redoEntries.clear()
        return true
    }

    fun undo(current: ProjectDocument): ProjectHistoryEntry? {
        val previous = undoEntries.removeLastOrNull() ?: return null
        redoEntries.addLast(ProjectHistoryEntry(current, current.manifest.activeRootId))
        while (redoEntries.size > maxDepth) redoEntries.removeFirst()
        return previous
    }

    fun redo(current: ProjectDocument): ProjectHistoryEntry? {
        val next = redoEntries.removeLastOrNull() ?: return null
        undoEntries.addLast(ProjectHistoryEntry(current, current.manifest.activeRootId))
        while (undoEntries.size > maxDepth) undoEntries.removeFirst()
        return next
    }

    fun clear() {
        undoEntries.clear()
        redoEntries.clear()
    }
}
