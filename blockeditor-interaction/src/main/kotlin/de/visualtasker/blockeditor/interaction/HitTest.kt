package de.visualtasker.blockeditor.interaction

import de.visualtasker.blockeditor.domain.Offset2
import de.visualtasker.blockeditor.domain.Rect
import de.visualtasker.blockeditor.layout.FlatLayoutIndex
import de.visualtasker.blockeditor.layout.HitKind
import de.visualtasker.blockeditor.layout.HitPrimitive
import kotlin.math.hypot

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
            if (best == null || primitive.isBetterThan(best!!, workspacePoint)) {
                best = primitive
            }
        }
        val hit = best ?: return HitResult.Empty
        return when (hit.kind) {
            HitKind.Field -> HitResult.FieldHit(hit.blockId, hit.fieldName ?: "")
            HitKind.StatementSlot -> HitResult.StatementSlotHit(hit.blockId, hit.inputName ?: "")
            HitKind.ConnectionAnchor -> hit.connectionId?.let(HitResult::ConnectionHit) ?: HitResult.Empty
            else -> HitResult.BlockHit(hit.blockId)
        }
    }

    private fun HitPrimitive.isBetterThan(current: HitPrimitive, point: Offset2): Boolean {
        if (zIndex != current.zIndex) return zIndex > current.zIndex
        val priority = hitPriority()
        val currentPriority = current.hitPriority()
        if (priority != currentPriority) return priority > currentPriority
        val distance = distanceToCenter(point)
        val currentDistance = current.distanceToCenter(point)
        if (distance != currentDistance) return distance < currentDistance
        return stableId() < current.stableId()
    }

    private fun HitPrimitive.hitPriority(): Int = when (kind) {
        HitKind.ConnectionAnchor -> 5
        HitKind.Field -> 4
        HitKind.CollapseToggle -> 3
        HitKind.StatementSlot,
        HitKind.ValueInput,
        -> 2
        HitKind.Header -> 1
        HitKind.BlockBody -> 0
    }

    private fun HitPrimitive.distanceToCenter(point: Offset2): Float {
        val centerX = bounds.x + bounds.width / 2f
        val centerY = bounds.y + bounds.height / 2f
        return hypot((point.x - centerX).toDouble(), (point.y - centerY).toDouble()).toFloat()
    }

    private fun HitPrimitive.stableId(): String =
        connectionId?.value ?: inputName ?: fieldName ?: id
}
