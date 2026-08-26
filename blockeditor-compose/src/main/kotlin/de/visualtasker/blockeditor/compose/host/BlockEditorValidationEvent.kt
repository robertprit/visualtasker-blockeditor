package de.visualtasker.blockeditor.compose.host

import de.visualtasker.blockeditor.validation.ValidationError

enum class BlockEditorValidationPhase {
    INITIAL_LOAD,
    DRAG_START,
    DRAG_MOVE,
    SNAP_PREVIEW,
    BEFORE_DROP,
    AFTER_DROP,
    AFTER_DETACH,
    AFTER_DOCUMENT_MUTATION,
    AFTER_RESTORE,
    BEFORE_IR_PROJECTION,
}

data class BlockEditorValidationEvent(
    val phase: BlockEditorValidationPhase,
    val documentVersion: Long,
    val dragActive: Boolean,
    val snapActive: Boolean,
    val detachActive: Boolean,
    val errors: List<ValidationError>,
)
