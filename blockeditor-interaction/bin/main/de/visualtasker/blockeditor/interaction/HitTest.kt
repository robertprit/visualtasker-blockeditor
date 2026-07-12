package de.visualtasker.blockeditor.interaction

import de.visualtasker.blockeditor.domain.Offset2
import de.visualtasker.blockeditor.domain.Rect
import de.visualtasker.blockeditor.layout.FlatLayoutIndex
import de.visualtasker.blockeditor.layout.HitKind
import de.visualtasker.blockeditor.layout.HitPrimitive

object HitTest {
    private val queryBuffer = mutableListOf<HitPrimitive>()

    fun hitTest(
        layout: FlatLayoutIndex,
        workspacePoint: Offset2,
        excludedBlocks: Set<de.visualtasker.blockeditor.domain.BlockId> = emptySet(),
    ): HitResult {
        val radius = 2f
        val queryBounds = Rect(
            workspacePoint.x - radius,
            workspacePoint.y - radius,
            radius * 2,
            radius * 2,
        )
        val candidates = layout.hitIndex.query(queryBounds, queryBuffer)
        var best: HitPrimitive? = null
        for (primitive in candidates) {
            if (primitive.blockId in excludedBlocks) continue
            if (!primitive.bounds.contains(workspacePoint.x, workspacePoint.y)) continue
            if (best == null || primitive.zIndex > best!!.zIndex) {
                best = primitive
            }
        }
        val hit = best ?: return HitResult.Empty
        return when (hit.kind) {
            HitKind.Field -> HitResult.FieldHit(hit.blockId, hit.fieldName ?: "")
            HitKind.StatementSlot -> HitResult.StatementSlotHit(hit.blockId, hit.inputName ?: "")
            HitKind.ConnectionAnchor -> {
                val anchor = layout.connectionAnchors.find { it.ownerBlockId == hit.blockId }
                if (anchor != null) HitResult.ConnectionHit(anchor.connectionId) else HitResult.BlockHit(hit.blockId)
            }
            else -> HitResult.BlockHit(hit.blockId)
        }
    }
}
