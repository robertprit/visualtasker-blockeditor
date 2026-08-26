package de.visualtasker.blockeditor.compose.host

import de.visualtasker.blockeditor.validation.ValidationError

/**
 * Studio-agnostic host notification contract.
 *
 * Dirty state, persistence, save acknowledgment, and workflow authority are host-owned.
 */
interface BlockEditorHostCallbacks {
    fun onWorkspaceDocumentChanged(serializedJson: String)
    fun onEmscriptDraftChanged(emscript: String)
    fun onValidationErrors(errors: List<ValidationError>)
    fun onValidationEvent(event: BlockEditorValidationEvent) = Unit
    fun onEmscriptGenerationFailed(message: String) = Unit

    companion object {
        val NoOp: BlockEditorHostCallbacks = object : BlockEditorHostCallbacks {
            override fun onWorkspaceDocumentChanged(serializedJson: String) = Unit
            override fun onEmscriptDraftChanged(emscript: String) = Unit
            override fun onValidationErrors(errors: List<ValidationError>) = Unit
            override fun onValidationEvent(event: BlockEditorValidationEvent) = Unit
        }
    }
}
