package de.visualtasker.blockeditor.layout

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.ConnectionId
import de.visualtasker.blockeditor.domain.ConnectionKind
import de.visualtasker.blockeditor.domain.Rect

enum class HitKind {
    BlockBody,
    Header,
    Field,
    ValueInput,
    StatementSlot,
    ConnectionAnchor,
    CollapseToggle,
}

data class HitPrimitive(
    val id: String,
    val blockId: BlockId,
    val kind: HitKind,
    val bounds: Rect,
    val zIndex: Int,
    val fieldName: String? = null,
    val inputName: String? = null,
)

data class ConnectionAnchor(
    val connectionId: ConnectionId,
    val ownerBlockId: BlockId,
    val kind: ConnectionKind,
    val type: String?,
    val x: Float,
    val y: Float,
    val radius: Float,
    val zIndex: Int,
)

data class BlockLayout(
    val blockId: BlockId,
    val bounds: Rect,
    val subtreeBounds: Rect,
    val zIndex: Int,
    val collapsed: Boolean,
)

data class StatementSlotLayout(
    val blockId: BlockId,
    val slotName: String,
    val bounds: Rect,
    val zIndex: Int,
)

enum class BranchSectionKind {
    HeaderCondition,
    ElifCondition,
    BranchDivider,
}

/** Sichtbarer Zweig-Bereich (Bedingung oder Trennzeile zwischen Statement-Slots). */
data class BranchSectionLayout(
    val blockId: BlockId,
    val kind: BranchSectionKind,
    val label: String,
    val bounds: Rect,
    val inputName: String? = null,
    val zIndex: Int,
)

/** Inline-Reporter: Output links, Parameter links/rechts vom Operator. */
data class InlineReporterLayout(
    val blockId: BlockId,
    val leftSlot: Rect,
    val operatorBounds: Rect,
    val rightSlot: Rect,
    val zIndex: Int,
)

data class MeasuredBlockLayout(
    val blockId: BlockId,
    val width: Float,
    val height: Float,
    val collapsed: Boolean,
)

data class MeasuredLayoutTree(
    val documentVersion: Long,
    val blocks: Map<BlockId, MeasuredBlockLayout>,
)

data class PlacedBlockLayout(
    val blockId: BlockId,
    val bounds: Rect,
    val subtreeBounds: Rect,
    val zIndex: Int,
)

data class PlacedLayoutTree(
    val documentVersion: Long,
    val blocks: Map<BlockId, PlacedBlockLayout>,
)

data class FlatLayoutIndex(
    val visibleBlocks: List<BlockLayout>,
    val hitPrimitives: List<HitPrimitive>,
    val connectionAnchors: List<ConnectionAnchor>,
    val statementSlots: List<StatementSlotLayout>,
    val branchSections: List<BranchSectionLayout>,
    val inlineReporterLayouts: List<InlineReporterLayout> = emptyList(),
    val hitIndex: SpatialIndex<HitPrimitive>,
    val anchorIndex: SpatialIndex<ConnectionAnchor>,
)

data class LayoutCache(
    val documentVersion: Long,
    val flatIndex: FlatLayoutIndex,
    val measuredLayoutTree: MeasuredLayoutTree = MeasuredLayoutTree(documentVersion, emptyMap()),
    val placedLayoutTree: PlacedLayoutTree = PlacedLayoutTree(documentVersion, emptyMap()),
)
