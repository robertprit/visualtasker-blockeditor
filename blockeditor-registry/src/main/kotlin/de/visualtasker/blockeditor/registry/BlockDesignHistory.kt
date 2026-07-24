package de.visualtasker.blockeditor.registry

data class BlockDesignHistoryState(
    val present: BlockDesignBlueprint,
    val undoStack: List<BlockDesignBlueprint> = emptyList(),
    val redoStack: List<BlockDesignBlueprint> = emptyList(),
) {
    fun record(next: BlockDesignBlueprint): BlockDesignHistoryState {
        if (next == present) return this
        return copy(
            present = next,
            undoStack = undoStack + present,
            redoStack = emptyList(),
        )
    }

    fun undo(): BlockDesignHistoryState? {
        val previous = undoStack.lastOrNull() ?: return null
        return copy(
            present = previous,
            undoStack = undoStack.dropLast(1),
            redoStack = listOf(present) + redoStack,
        )
    }

    fun redo(): BlockDesignHistoryState? {
        val next = redoStack.firstOrNull() ?: return null
        return copy(
            present = next,
            undoStack = undoStack + present,
            redoStack = redoStack.drop(1),
        )
    }
}
