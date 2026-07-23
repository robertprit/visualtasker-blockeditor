package de.visualtasker.blockeditor.interaction

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.ConnectionId
import de.visualtasker.blockeditor.domain.Offset2
import de.visualtasker.blockeditor.layout.ConnectionAnchor

data class SnapConfig(
    val previewRadius: Float = 72f,
    val snapRadius: Float = 42f,
    val hysteresisBonus: Float = 18f,
    val switchThreshold: Float = 12f,
)

data class SnapCandidate(
    val sourceConnectionId: ConnectionId,
    val targetConnectionId: ConnectionId,
    val distance: Float,
    val snapOffset: Offset2,
)

/** Links am Block: Kette darunter mitziehen. Rechts: nur dieser Block (+ Slot-Inhalt). */
enum class DragPullMode {
    StackBelow,
    Single,
}

data class DragSession(
    val rootBlockId: BlockId,
    val includedBlocks: Set<BlockId>,
    val pullMode: DragPullMode,
    val startPointer: Offset2,
    val currentPointer: Offset2,
    /** Layout-Position beim Drag-Start (nicht rootOffset-Metadaten). */
    val originalLayoutPosition: Offset2,
    val dragOffset: Offset2,
    val originalAnchors: List<ConnectionAnchor>,
)

class DragRuntimeState(
    initialOffset: Offset2 = Offset2(0f, 0f),
    initialSnapCandidate: SnapCandidate? = null,
) {
    var dragOffset: Offset2 = initialOffset
        private set

    var snapCandidate: SnapCandidate? = initialSnapCandidate
        private set

    fun update(
        offset: Offset2,
        candidate: SnapCandidate?,
    ): Boolean {
        val changed = dragOffset != offset || snapCandidate != candidate
        dragOffset = offset
        snapCandidate = candidate
        return changed
    }
}

data class TransientEditorState(
    val viewport: ViewportState = ViewportState(),
    val dragSession: DragSession? = null,
    val activeSnapCandidate: SnapCandidate? = null,
    val selectedBlockId: BlockId? = null,
    val hoveredHit: HitResult? = null,
)

sealed interface HitResult {
    data class BlockHit(val blockId: BlockId) : HitResult
    data class FieldHit(val blockId: BlockId, val fieldName: String) : HitResult
    data class StatementSlotHit(val blockId: BlockId, val inputName: String) : HitResult
    data class ConnectionHit(val connectionId: ConnectionId) : HitResult
    data object Empty : HitResult
}
