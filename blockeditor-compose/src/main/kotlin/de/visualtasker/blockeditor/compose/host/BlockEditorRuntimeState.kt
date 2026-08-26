package de.visualtasker.blockeditor.compose.host

import de.visualtasker.blockeditor.validation.ValidationError

enum class BlockEditorRuntimeStatus {
    BLOCKED,
    RUNNING_WITH_GUARDS,
    RUNNING,
}

enum class BlockEditorTemporaryGuard {
    RELEASE_CHECKLIST_INCOMPLETE,
    PERSISTENT_VALIDATION_ERRORS,
    EMSCRIPT_GENERATION_UNAVAILABLE,
}

enum class BlockEditorPermanentGuard {
    VALIDATE_AFTER_MUTATION,
    VALIDATE_BEFORE_SAVE,
    VALIDATE_AFTER_LOAD,
    VALIDATE_BEFORE_IR_PROJECTION,
    VALIDATE_BEFORE_EMSCRIPT_GENERATION,
    CYCLE_DETECTION,
    PARENT_CHILD_CONSISTENCY,
    CONNECTION_TYPE_COMPATIBILITY,
    INVALID_IMPORT_PROTECTION,
    UNDO_REDO_INTEGRITY,
}

data class BlockEditorReleaseChecklist(
    val successfulReferenceRuns: Int = 0,
    val saveLoadRoundtripSuccessful: Boolean = false,
    val restartRoundtripSuccessful: Boolean = false,
    val undoRedoSuccessful: Boolean = false,
    val irGenerationSuccessful: Boolean = false,
    val emscriptGenerationSuccessful: Boolean = false,
    val persistentValidationCleanAfterDrop: Boolean = false,
    val subtreeDragIntegritySuccessful: Boolean = false,
    val noSilentDataLossDetected: Boolean = false,
    val noPreviewOrLegacyFallbackDetected: Boolean = false,
) {
    val isCompleteForRunning: Boolean
        get() = successfulReferenceRuns >= 3 &&
            saveLoadRoundtripSuccessful &&
            restartRoundtripSuccessful &&
            undoRedoSuccessful &&
            irGenerationSuccessful &&
            emscriptGenerationSuccessful &&
            persistentValidationCleanAfterDrop &&
            subtreeDragIntegritySuccessful &&
            noSilentDataLossDetected &&
            noPreviewOrLegacyFallbackDetected
}

data class BlockEditorRuntimeState(
    val status: BlockEditorRuntimeStatus = BlockEditorRuntimeStatus.RUNNING_WITH_GUARDS,
    val checklist: BlockEditorReleaseChecklist = BlockEditorReleaseChecklist(),
    val temporaryGuards: Set<BlockEditorTemporaryGuard> = setOf(
        BlockEditorTemporaryGuard.RELEASE_CHECKLIST_INCOMPLETE,
    ),
    val persistentValidationErrors: List<ValidationError> = emptyList(),
    val lastEmscriptGenerationFailure: String? = null,
    val permanentGuards: Set<BlockEditorPermanentGuard> = DEFAULT_PERMANENT_GUARDS,
) {
    companion object {
        val DEFAULT_PERMANENT_GUARDS: Set<BlockEditorPermanentGuard> = setOf(
            BlockEditorPermanentGuard.VALIDATE_AFTER_MUTATION,
            BlockEditorPermanentGuard.VALIDATE_BEFORE_SAVE,
            BlockEditorPermanentGuard.VALIDATE_AFTER_LOAD,
            BlockEditorPermanentGuard.VALIDATE_BEFORE_IR_PROJECTION,
            BlockEditorPermanentGuard.VALIDATE_BEFORE_EMSCRIPT_GENERATION,
            BlockEditorPermanentGuard.CYCLE_DETECTION,
            BlockEditorPermanentGuard.PARENT_CHILD_CONSISTENCY,
            BlockEditorPermanentGuard.CONNECTION_TYPE_COMPATIBILITY,
            BlockEditorPermanentGuard.INVALID_IMPORT_PROTECTION,
            BlockEditorPermanentGuard.UNDO_REDO_INTEGRITY,
        )
    }
}
