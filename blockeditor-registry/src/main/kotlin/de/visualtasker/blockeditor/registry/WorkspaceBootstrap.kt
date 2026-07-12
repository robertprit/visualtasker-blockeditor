package de.visualtasker.blockeditor.registry

import de.visualtasker.blockeditor.domain.WorkspaceAction
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.WorkspaceReducer

/**
 * Host-facing workspace bootstrap. Does not include demo sample chains.
 */
object WorkspaceBootstrap {
    /** Empty workspace with no blocks. */
    fun empty(): WorkspaceDocument = WorkspaceDocument(id = "workspace")

    /** Minimal runnable workspace: single EVENT_START root block. */
    fun starter(): WorkspaceDocument {
        val factory = DefaultBlockRegistry.asFactory()
        var document = WorkspaceDocument(id = "workspace")
        document = WorkspaceReducer.reduce(
            document,
            WorkspaceAction.InstantiateBlock(BlockTypes.EVENT_START, 40f, 40f),
            factory,
        )
        return document
    }
}
