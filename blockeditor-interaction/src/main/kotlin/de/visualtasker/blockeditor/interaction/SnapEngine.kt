package de.visualtasker.blockeditor.interaction

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.Connection
import de.visualtasker.blockeditor.domain.ConnectionKind
import de.visualtasker.blockeditor.domain.Offset2
import de.visualtasker.blockeditor.domain.WorkspaceGraph
import de.visualtasker.blockeditor.layout.ConnectionAnchor
import de.visualtasker.blockeditor.layout.FlatLayoutIndex
import kotlin.math.hypot

class SnapEngine(
    private val config: SnapConfig = SnapConfig(),
) {
    fun findSnapCandidate(
        layout: FlatLayoutIndex,
        dragSession: DragSession,
        document: de.visualtasker.blockeditor.domain.WorkspaceDocument,
        currentCandidate: SnapCandidate? = null,
    ): SnapCandidate? {
        val excluded = dragSession.includedBlocks
        val dragOffset = dragSession.dragOffset
        val movedAnchors = dragSession.originalAnchors.map { anchor ->
            anchor.withVirtualOffset(dragOffset)
        }

        var best: SnapCandidate? = null
        var bestDistance = Float.MAX_VALUE
        var bestPriority = Int.MAX_VALUE
        val snapSources = filterSnapSources(layout, dragSession, document, movedAnchors)

        for (source in snapSources) {
            val compatible = compatibleTargets(source)
            val nearby = layout.anchorIndex.queryPoint(source.x, source.y, config.previewRadius)
            for (target in nearby) {
                if (target.ownerBlockId in excluded) continue
                if (target.connectionId == source.connectionId) continue
                if (areAlreadyConnected(document, source, target)) continue
                if (target.kind !in compatible) continue
                if (!kindsCompatible(source.kind, target.kind)) continue
                if (!valueTypesCompatible(document, source, target)) continue
                if (WorkspaceGraph.isDescendantOf(document, target.ownerBlockId, dragSession.rootBlockId)) continue
                if (shouldSkipParentSlotTarget(layout, dragSession, document, target)) continue
                if (shouldSkipInsertAboveParentContainer(document, dragSession, target)) continue
                if (shouldSkipOccupiedTarget(document, dragSession, target)) continue

                val dx = source.x - target.x
                val dy = source.y - target.y
                val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                val threshold = if (currentCandidate?.targetConnectionId == target.connectionId) {
                    config.snapRadius + config.hysteresisBonus
                } else {
                    config.snapRadius
                }
                if (distance > threshold) continue
                val priority = snapPriority(
                    source = source,
                    target = target,
                    document = document,
                    dragSession = dragSession,
                )
                val isBetter = priority < bestPriority ||
                    (priority == bestPriority && distance < bestDistance)
                if (isBetter) {
                    bestPriority = priority
                    bestDistance = distance
                    best = SnapCandidate(
                        sourceConnectionId = source.connectionId,
                        targetConnectionId = target.connectionId,
                        distance = distance,
                        snapOffset = Offset2(
                            dragOffset.x + (target.x - source.x),
                            dragOffset.y + (target.y - source.y),
                        ),
                    )
                }
            }
        }

        if (currentCandidate != null && best != null) {
            if (best.targetConnectionId != currentCandidate.targetConnectionId) {
                val improvement = currentCandidate.distance - best.distance
                if (improvement < config.switchThreshold) {
                    return currentCandidate
                }
            }
        }

        if (currentCandidate != null && best == null) {
            val stillValid = isCandidateStillValid(layout, dragSession, currentCandidate)
            if (stillValid) return currentCandidate
        }

        return best
    }

    private fun snapPriority(
        source: ConnectionAnchor,
        target: ConnectionAnchor,
        document: de.visualtasker.blockeditor.domain.WorkspaceDocument,
        dragSession: DragSession,
    ): Int {
        val dragged = document.blocks[dragSession.rootBlockId]
        val isContainer = dragged?.statementInputs?.isNotEmpty() == true
        if (isContainer && source.kind == ConnectionKind.Previous) {
            return when (target.kind) {
                ConnectionKind.StatementInput -> 0
                ConnectionKind.Next -> 1
                else -> 2
            }
        }
        return 0
    }

    /**
     * Container-Blöcke: nahe einem Statement-Slot nicht über `next` andocken,
     * sonst landet Repeat oft unter dem äußeren Repeat statt im DO-Slot.
     */
    private fun filterSnapSources(
        layout: FlatLayoutIndex,
        dragSession: DragSession,
        document: de.visualtasker.blockeditor.domain.WorkspaceDocument,
        movedAnchors: List<ConnectionAnchor>,
    ): List<ConnectionAnchor> {
        val block = document.blocks[dragSession.rootBlockId] ?: return movedAnchors
        if (block.statementInputs.isEmpty()) return movedAnchors

        // Container blocks should expose only external attach points while dragging.
        // Internal slot anchors (StatementInput/ValueInput) and NEXT create ambiguous
        // matches and can outrank the intended DO-slot nesting target.
        return movedAnchors.filter {
            it.kind == ConnectionKind.Previous || it.kind == ConnectionKind.Output
        }
    }

    /** Eltern-Slot nur, wenn der Block noch im Container liegt – sonst Kette außen. */
    private fun shouldSkipParentSlotTarget(
        layout: FlatLayoutIndex,
        dragSession: DragSession,
        document: de.visualtasker.blockeditor.domain.WorkspaceDocument,
        target: ConnectionAnchor,
    ): Boolean {
        if (target.kind != ConnectionKind.StatementInput) return false
        val parentSlot = WorkspaceGraph.slotContaining(document, dragSession.rootBlockId) ?: return false
        if (parentSlot.first != target.ownerBlockId) return false

        val parentLayout = layout.visibleBlocks.find { it.blockId == parentSlot.first } ?: return false
        val dragLayout = layout.visibleBlocks.find { it.blockId == dragSession.rootBlockId } ?: return false
        val centerX = dragLayout.bounds.x + dragSession.dragOffset.x + dragLayout.bounds.width / 2f
        val centerY = dragLayout.bounds.y + dragSession.dragOffset.y + dragLayout.bounds.height / 2f
        return !parentLayout.bounds.contains(centerX, centerY)
    }

    /** Verhindert, dass Slot-Kinder an wait.next oberhalb des Containers andocken. */
    private fun shouldSkipInsertAboveParentContainer(
        document: de.visualtasker.blockeditor.domain.WorkspaceDocument,
        dragSession: DragSession,
        target: ConnectionAnchor,
    ): Boolean {
        if (target.kind != ConnectionKind.Next) return false
        val parentSlot = WorkspaceGraph.slotContaining(document, dragSession.rootBlockId) ?: return false
        val chainAbove = WorkspaceGraph.previousChain(document, parentSlot.first) ?: return false
        return target.ownerBlockId == chainAbove
    }

    /**
     * Belegter Snap-Punkt: nur erlauben, wenn der gezogene Block freies `next` hat,
     * damit der bisherige Partner darunter gestapelt werden kann.
     * ValueInputs haben keine implizite Replace-Interaktion; belegte Reporter-Slots
     * bleiben ohne expliziten Disconnect/Replace geschlossen.
     */
    private fun shouldSkipOccupiedTarget(
        document: de.visualtasker.blockeditor.domain.WorkspaceDocument,
        dragSession: DragSession,
        target: ConnectionAnchor,
    ): Boolean {
        if (target.kind == ConnectionKind.ValueInput) {
            val partnerId = document.blocks[target.ownerBlockId]
                ?.valueInputs
                ?.firstOrNull { it.connection.id == target.connectionId }
                ?.connection
                ?.connectedTo
            if (partnerId != null) {
                return true
            }
        }
        val occupiedPartnerId = occupiedPartnerBlockId(document, target) ?: return false
        if (occupiedPartnerId == dragSession.rootBlockId) return false
        if (occupiedPartnerId in dragSession.includedBlocks) return true
        val dragged = document.blocks[dragSession.rootBlockId] ?: return true
        return dragged.next?.connectedTo != null
    }

    private fun occupiedPartnerBlockId(
        document: de.visualtasker.blockeditor.domain.WorkspaceDocument,
        target: ConnectionAnchor,
    ): BlockId? {
        val partnerConnId = when (target.kind) {
            ConnectionKind.Next -> document.blocks[target.ownerBlockId]?.next?.connectedTo
            ConnectionKind.StatementInput -> {
                val owner = document.blocks[target.ownerBlockId] ?: return null
                owner.statementInputs
                    .firstOrNull { it.connection.id == target.connectionId }
                    ?.connection
                    ?.connectedTo
            }
            else -> null
        } ?: return null
        val (blockId, conn) = WorkspaceGraph.findConnection(document, partnerConnId) ?: return null
        return if (conn.kind == ConnectionKind.Previous) blockId else null
    }

    private fun areAlreadyConnected(
        document: de.visualtasker.blockeditor.domain.WorkspaceDocument,
        source: ConnectionAnchor,
        target: ConnectionAnchor,
    ): Boolean {
        val sourcePartner = WorkspaceGraph.findConnection(document, source.connectionId)
            ?.second
            ?.connectedTo
        val targetPartner = WorkspaceGraph.findConnection(document, target.connectionId)
            ?.second
            ?.connectedTo
        return sourcePartner == target.connectionId || targetPartner == source.connectionId
    }

    private fun isCandidateStillValid(
        layout: FlatLayoutIndex,
        dragSession: DragSession,
        candidate: SnapCandidate,
    ): Boolean {
        val target = layout.connectionAnchors.find { it.connectionId == candidate.targetConnectionId } ?: return false
        val source = dragSession.originalAnchors.find { it.connectionId == candidate.sourceConnectionId } ?: return false
        val moved = source.withVirtualOffset(dragSession.dragOffset)
        val distance = hypot(
            (moved.x - target.x).toDouble(),
            (moved.y - target.y).toDouble(),
        ).toFloat()
        return distance <= config.previewRadius + config.hysteresisBonus
    }

    private fun compatibleTargets(source: ConnectionAnchor): Set<ConnectionKind> = when (source.kind) {
        ConnectionKind.Next -> setOf(ConnectionKind.Previous)
        ConnectionKind.Previous -> setOf(ConnectionKind.Next, ConnectionKind.StatementInput)
        ConnectionKind.Output -> setOf(ConnectionKind.ValueInput)
        ConnectionKind.ValueInput -> setOf(ConnectionKind.Output)
        ConnectionKind.StatementInput -> setOf(ConnectionKind.Previous)
    }

    private fun kindsCompatible(a: ConnectionKind, b: ConnectionKind): Boolean =
        when (a) {
            ConnectionKind.Next -> b == ConnectionKind.Previous
            ConnectionKind.Previous -> b == ConnectionKind.Next || b == ConnectionKind.StatementInput
            ConnectionKind.Output -> b == ConnectionKind.ValueInput
            ConnectionKind.ValueInput -> b == ConnectionKind.Output
            ConnectionKind.StatementInput -> b == ConnectionKind.Previous
        }

    private fun valueTypesCompatible(
        document: de.visualtasker.blockeditor.domain.WorkspaceDocument,
        source: ConnectionAnchor,
        target: ConnectionAnchor,
    ): Boolean {
        val outputAnchor = when (source.kind) {
            ConnectionKind.Output -> source
            ConnectionKind.ValueInput -> target.takeIf { it.kind == ConnectionKind.Output }
            else -> return true
        } ?: return true
        val inputAnchor = when (source.kind) {
            ConnectionKind.Output -> target.takeIf { it.kind == ConnectionKind.ValueInput }
            ConnectionKind.ValueInput -> source
            else -> return true
        } ?: return true
        val output = WorkspaceGraph.findConnection(document, outputAnchor.connectionId)?.second
        val input = WorkspaceGraph.findConnection(document, inputAnchor.connectionId)?.second
        return if (output != null && input != null) {
            connectionTypesCompatible(output, input)
        } else {
            anchorTypesCompatible(outputAnchor, inputAnchor)
        }
    }

    private fun connectionTypesCompatible(output: Connection, input: Connection): Boolean {
        val outputType = output.provides ?: output.accepts.firstOrNull() ?: return true
        if (input.accepts.isEmpty()) return true
        if (outputType == "Any" || "Any" in input.accepts) return true
        return outputType in input.accepts
    }

    private fun anchorTypesCompatible(output: ConnectionAnchor, input: ConnectionAnchor): Boolean {
        val outputType = output.type ?: return true
        val inputType = input.type ?: return true
        return outputType == "Any" || inputType == "Any" || outputType == inputType
    }

    private fun ConnectionAnchor.withVirtualOffset(offset: Offset2): ConnectionAnchor =
        copy(x = x + offset.x, y = y + offset.y)
}
