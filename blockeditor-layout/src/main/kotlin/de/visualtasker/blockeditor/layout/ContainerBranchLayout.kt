package de.visualtasker.blockeditor.layout

import de.visualtasker.blockeditor.registry.BlockTypes

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.Offset2
import de.visualtasker.blockeditor.domain.Rect

object ContainerBranchLayout {
    /**
     * Y-Positionen (relativ zum Block-Top) der horizontalen Zweig-Trenner
     * (Mittelsteg für else-if / else), abgeleitet aus dem gemessenen Layout.
     */
    fun branchDividerYsFromSections(
        blockTop: Float,
        branchSections: List<BranchSectionLayout>,
    ): List<Float> =
        branchSections
            .filter { it.kind == BranchSectionKind.BranchDivider || it.kind == BranchSectionKind.ElifCondition }
            .map { it.bounds.y - blockTop }

    fun branchDividerYsFromSlots(
        blockTop: Float,
        statementSlots: List<StatementSlotLayout>,
    ): List<Float> =
        statementSlots.mapNotNull { slot ->
            when (slot.slotName) {
                BlockTypes.SLOT_ELIF ->
                    slot.bounds.y - blockTop - LayoutConstants.ELIF_SECTION_HEIGHT - LayoutConstants.SLOT_PADDING
                BlockTypes.SLOT_ELSE ->
                    slot.bounds.y - blockTop - LayoutConstants.BRANCH_SHELF - LayoutConstants.SLOT_PADDING
                else -> if (slot.slotName.startsWith("ELIF_")) {
                    slot.bounds.y - blockTop - LayoutConstants.ELIF_SECTION_HEIGHT - LayoutConstants.SLOT_PADDING
                } else {
                    null
                }
            }
        }

    fun branchDividerYs(
        definition: de.visualtasker.blockeditor.registry.BlockDefinition?,
    ): List<Float> {
        if (definition?.statementInputs == null || definition.statementInputs.size <= 1) {
            return emptyList()
        }
        val dividers = mutableListOf<Float>()
        var slotY = LayoutConstants.HEADER_HEIGHT + LayoutConstants.SLOT_PADDING
        definition.statementInputs.forEach { slotDef ->
            if (slotDef.name.needsBranchDivider()) {
                dividers += slotY
                slotY += LayoutConstants.BRANCH_SHELF + LayoutConstants.SLOT_PADDING
            }
            if (slotDef.name.isElifSlot()) {
                dividers += slotY
                slotY += LayoutConstants.ELIF_SECTION_HEIGHT + LayoutConstants.SLOT_PADDING
            }
            slotY += LayoutConstants.STATEMENT_MIN_HEIGHT + LayoutConstants.SLOT_PADDING
        }
        return dividers
    }

    private fun String.isElifSlot(): Boolean =
        this == BlockTypes.SLOT_ELIF || startsWith("ELIF_")

    private fun String.needsBranchDivider(): Boolean =
        this == BlockTypes.SLOT_ELSE

    fun containerVisuals(
        blockId: BlockId,
        blockTopLeft: Offset2,
        layout: LayoutCache,
    ): Pair<List<Float>, List<BranchSectionLayout>> {
        val sections = layout.flatIndex.branchSections.filter { it.blockId == blockId }
        val slots = layout.flatIndex.statementSlots.filter { it.blockId == blockId }
        val dividerYs = if (sections.isNotEmpty()) {
            branchDividerYsFromSections(blockTopLeft.y, sections)
        } else {
            branchDividerYsFromSlots(blockTopLeft.y, slots)
        }
        val relativeSections = sections.map { section ->
            section.copy(
                bounds = Rect(
                    section.bounds.x - blockTopLeft.x,
                    section.bounds.y - blockTopLeft.y,
                    section.bounds.width,
                    section.bounds.height,
                ),
            )
        }
        return dividerYs to relativeSections
    }
}
