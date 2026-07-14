package de.visualtasker.blockeditor.emscript

import de.visualtasker.blockeditor.domain.WorkspaceDocument

/** Host-neutral projection of an editor-local workspace document into derived code. */
fun interface WorkspaceCodeGenerator {
    fun generate(document: WorkspaceDocument): String
}
