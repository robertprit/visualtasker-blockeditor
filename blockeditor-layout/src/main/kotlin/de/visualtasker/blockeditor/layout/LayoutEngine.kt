package de.visualtasker.blockeditor.layout

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.BlockNode
import de.visualtasker.blockeditor.domain.ConnectionKind
import de.visualtasker.blockeditor.domain.Rect
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.WorkspaceGraph
import de.visualtasker.blockeditor.domain.rootOffset
import de.visualtasker.blockeditor.registry.BlockDefinition
import de.visualtasker.blockeditor.registry.BlockRegistry
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry

class LayoutEngine(
    private val registry: BlockRegistry = DefaultBlockRegistry,
) {
    private val hitIndex = SpatialIndex<HitPrimitive>()
    private val anchorIndex = SpatialIndex<ConnectionAnchor>()

    fun build(document: WorkspaceDocument): LayoutCache {
        val visibleBlocks = mutableListOf<BlockLayout>()
        val hitPrimitives = mutableListOf<HitPrimitive>()
        val connectionAnchors = mutableListOf<ConnectionAnchor>()
        val statementSlots = mutableListOf<StatementSlotLayout>()
        val branchSections = mutableListOf<BranchSectionLayout>()
        val inlineReporterLayouts = mutableListOf<InlineReporterLayout>()
        val visited = mutableSetOf<BlockId>()
        var zCounter = 0

        fun layoutBlock(blockId: BlockId, x: Float, y: Float, zStart: Int): Int {
            if (blockId in visited) return zStart
            visited += blockId
            val block = document.blocks[blockId] ?: return zStart
            val definition = registry.getDefinition(block.type)
            if (block.collapsed) {
                val bounds = Rect(x, y, blockWidth(document, definition, block), LayoutConstants.COLLAPSED_HEIGHT)
                visibleBlocks += BlockLayout(blockId, bounds, bounds, zStart, collapsed = true)
                hitPrimitives += HitPrimitive(
                    id = "${blockId.value}:body",
                    blockId = blockId,
                    kind = HitKind.BlockBody,
                    bounds = bounds,
                    zIndex = zStart,
                )
                addAnchors(block, blockId, bounds, zStart, connectionAnchors)
                return zStart + 1
            }

            if (definition?.statementInputs?.isNotEmpty() == true) {
                return layoutContainer(
                    document, block, definition, blockId, x, y, zStart,
                    visibleBlocks, hitPrimitives, connectionAnchors, statementSlots, branchSections,
                ) { id, cx, cy, z -> layoutBlock(id, cx, cy, z) }
            }

            if (definition?.inputsInline == true) {
                return layoutInlineReporter(
                    document = document,
                    block = block,
                    definition = definition,
                    blockId = blockId,
                    x = x,
                    y = y,
                    zStart = zStart,
                    visibleBlocks = visibleBlocks,
                    hitPrimitives = hitPrimitives,
                    connectionAnchors = connectionAnchors,
                    inlineReporterLayouts = inlineReporterLayouts,
                    layoutChild = { id, cx, cy, z -> layoutBlock(id, cx, cy, z) },
                )
            }

            val width = blockWidth(document, definition, block)
            val height = if (definition?.isReporter == true) LayoutConstants.REPORTER_HEIGHT else LayoutConstants.HEADER_HEIGHT
            val bounds = Rect(x, y, width, height)
            var maxZ = zStart + 1
            var subtreeBottom = bounds.bottom

            visibleBlocks += BlockLayout(blockId, bounds, bounds, zStart, collapsed = false)
            hitPrimitives += HitPrimitive(
                id = "${blockId.value}:header",
                blockId = blockId,
                kind = HitKind.Header,
                bounds = bounds,
                zIndex = zStart,
            )

            block.valueInputs.forEachIndexed { index, input ->
                val slotX = x + width + LayoutConstants.VALUE_DOCK_GAP(index)
                val slotBounds = Rect(slotX, y, LayoutConstants.REPORTER_WIDTH, LayoutConstants.REPORTER_HEIGHT)
                hitPrimitives += HitPrimitive(
                    id = "${blockId.value}:value:${input.name}",
                    blockId = blockId,
                    kind = HitKind.ValueInput,
                    bounds = slotBounds,
                    zIndex = zStart,
                    inputName = input.name,
                )
                input.connection.connectedTo?.let { connId ->
                    val (valueBlockId, _) = WorkspaceGraph.findConnection(document, connId) ?: return@let
                    maxZ = layoutBlock(valueBlockId, slotX, y, maxZ)
                    subtreeBottom = maxOf(subtreeBottom, maxZBound(visibleBlocks, valueBlockId))
                }
            }

            addAnchors(block, blockId, bounds, zStart, connectionAnchors)
            updateSubtreeBounds(visibleBlocks, blockId, bounds, subtreeBottom)
            return maxOf(maxZ, zStart + 1)
        }

        WorkspaceGraph.topLevelRoots(document).forEach { rootId ->
            val block = document.blocks[rootId] ?: return@forEach
            val offset = block.rootOffset() ?: de.visualtasker.blockeditor.domain.Offset2(0f, 0f)
            var currentId = rootId
            var currentY = offset.y
            while (true) {
                zCounter = layoutBlock(currentId, offset.x, currentY, zCounter)
                val next = WorkspaceGraph.nextChain(document, currentId) ?: break
                val layout = visibleBlocks.find { it.blockId == currentId }
                currentY = (layout?.bounds?.bottom ?: currentY) + LayoutConstants.BLOCK_GAP
                currentId = next
            }
        }

        hitIndex.clear()
        anchorIndex.clear()
        hitPrimitives.forEach { hitIndex.insert(it, it.bounds) }
        connectionAnchors.forEach { anchorIndex.insert(it, anchorHitBounds(it)) }

        return LayoutCache(
            documentVersion = document.version,
            flatIndex = FlatLayoutIndex(
                visibleBlocks = visibleBlocks,
                hitPrimitives = hitPrimitives,
                connectionAnchors = connectionAnchors,
                statementSlots = statementSlots,
                branchSections = branchSections,
                inlineReporterLayouts = inlineReporterLayouts,
                hitIndex = hitIndex,
                anchorIndex = anchorIndex,
            ),
        )
    }

    private fun computeStatementStackHeight(
        document: WorkspaceDocument,
        blockId: BlockId,
        slotName: String,
    ): Float {
        val stack = WorkspaceGraph.statementStack(document, blockId, slotName)
        if (stack.isEmpty()) return LayoutConstants.STATEMENT_MIN_HEIGHT
        var height = LayoutConstants.BLOCK_GAP * (stack.size - 1)
        stack.forEach { childId ->
            val childDef = registry.getDefinition(document.blocks[childId]?.type ?: "")
            height += estimatedStackBlockHeight(document, childId, childDef)
        }
        return height.coerceAtLeast(LayoutConstants.STATEMENT_MIN_HEIGHT)
    }

    private fun estimatedStackBlockHeight(
        document: WorkspaceDocument,
        blockId: BlockId,
        definition: BlockDefinition?,
    ): Float {
        if (definition?.statementInputs?.isNotEmpty() == true) {
            if (document.blocks[blockId] == null) {
                return LayoutConstants.HEADER_HEIGHT + LayoutConstants.FOOTER_HEIGHT
            }
            var body = LayoutConstants.HEADER_HEIGHT + LayoutConstants.SLOT_PADDING
            definition.statementInputs.forEach { slotDef ->
                var slotContent = computeStatementStackHeight(document, blockId, slotDef.name)
                if (slotDef.name != de.visualtasker.blockeditor.registry.BlockTypes.SLOT_THEN) {
                    slotContent += LayoutConstants.BRANCH_SHELF
                }
                if (slotDef.name == de.visualtasker.blockeditor.registry.BlockTypes.SLOT_ELIF) {
                    slotContent += LayoutConstants.ELIF_SECTION_HEIGHT
                }
                body += slotContent + LayoutConstants.SLOT_PADDING
            }
            if (definition.statementInputs.size > 1) {
                body += LayoutConstants.CORNER_RADIUS
            }
            return body + LayoutConstants.FOOTER_HEIGHT
        }
        return if (definition?.isReporter == true) {
            LayoutConstants.REPORTER_HEIGHT
        } else {
            LayoutConstants.HEADER_HEIGHT
        }
    }

    private fun layoutContainer(
        document: WorkspaceDocument,
        block: BlockNode,
        definition: BlockDefinition,
        blockId: BlockId,
        x: Float,
        y: Float,
        zStart: Int,
        visibleBlocks: MutableList<BlockLayout>,
        hitPrimitives: MutableList<HitPrimitive>,
        connectionAnchors: MutableList<ConnectionAnchor>,
        statementSlots: MutableList<StatementSlotLayout>,
        branchSections: MutableList<BranchSectionLayout>,
        layoutChild: (BlockId, Float, Float, Int) -> Int,
    ): Int {
        val width = LayoutConstants.STANDARD_WIDTH
        var slotY = y + LayoutConstants.HEADER_HEIGHT + LayoutConstants.SLOT_PADDING
        var maxZ = zStart + 1
        var bodyBottom = slotY

        val conditionDef = definition.valueInputs.find { it.name == "CONDITION" }
        if (conditionDef != null) {
            val headerSection = Rect(
                x + LayoutConstants.NESTED_INDENT,
                y,
                width - LayoutConstants.NESTED_INDENT - LayoutConstants.SLOT_PADDING,
                LayoutConstants.HEADER_HEIGHT,
            )
            branchSections += BranchSectionLayout(
                blockId = blockId,
                kind = BranchSectionKind.HeaderCondition,
                label = conditionDef.label,
                bounds = headerSection,
                inputName = conditionDef.name,
                zIndex = zStart,
            )
            maxZ = layoutConditionInput(
                document, block, blockId, conditionDef.name,
                headerSection, hitPrimitives, connectionAnchors, layoutChild, maxZ, zStart,
            )
        }

        definition.statementInputs.forEach { slotDef ->
            if (slotDef.name != de.visualtasker.blockeditor.registry.BlockTypes.SLOT_THEN) {
                val dividerBounds = Rect(
                    x + LayoutConstants.NESTED_INDENT,
                    slotY,
                    width - LayoutConstants.NESTED_INDENT - LayoutConstants.SLOT_PADDING,
                    LayoutConstants.BRANCH_SHELF,
                )
                branchSections += BranchSectionLayout(
                    blockId = blockId,
                    kind = BranchSectionKind.BranchDivider,
                    label = slotDef.label,
                    bounds = dividerBounds,
                    zIndex = zStart,
                )
                slotY += LayoutConstants.BRANCH_SHELF
            }

            if (slotDef.name == de.visualtasker.blockeditor.registry.BlockTypes.SLOT_ELIF) {
                val elifDef = definition.valueInputs.find { it.name == "ELIF_CONDITION" }
                val elifSection = Rect(
                    x + LayoutConstants.NESTED_INDENT,
                    slotY,
                    width - LayoutConstants.NESTED_INDENT - LayoutConstants.SLOT_PADDING,
                    LayoutConstants.ELIF_SECTION_HEIGHT,
                )
                if (elifDef != null) {
                    branchSections += BranchSectionLayout(
                        blockId = blockId,
                        kind = BranchSectionKind.ElifCondition,
                        label = elifDef.label,
                        bounds = elifSection,
                        inputName = elifDef.name,
                        zIndex = zStart,
                    )
                    maxZ = layoutConditionInput(
                        document, block, blockId, elifDef.name,
                        elifSection, hitPrimitives, connectionAnchors, layoutChild, maxZ, zStart,
                    )
                }
                slotY += LayoutConstants.ELIF_SECTION_HEIGHT
                bodyBottom = maxOf(bodyBottom, slotY)
            }

            val slotHeight = computeStatementStackHeight(document, blockId, slotDef.name)
            val slotBounds = Rect(
                x + LayoutConstants.NESTED_INDENT,
                slotY,
                width - LayoutConstants.NESTED_INDENT - LayoutConstants.SLOT_PADDING,
                slotHeight,
            )
            statementSlots += StatementSlotLayout(blockId, slotDef.name, slotBounds, zStart)
            block.statementInputs.find { it.name == slotDef.name }?.let { input ->
                connectionAnchors += ConnectionAnchor(
                    connectionId = input.connection.id,
                    ownerBlockId = blockId,
                    kind = ConnectionKind.StatementInput,
                    type = null,
                    x = slotBounds.x,
                    y = slotBounds.y,
                    radius = LayoutConstants.ANCHOR_RADIUS,
                    zIndex = zStart,
                )
            }
            hitPrimitives += HitPrimitive(
                id = "${blockId.value}:stmt:${slotDef.name}",
                blockId = blockId,
                kind = HitKind.StatementSlot,
                bounds = slotBounds,
                zIndex = zStart,
                inputName = slotDef.name,
            )

            var childY = slotBounds.y
            val stack = WorkspaceGraph.statementStack(document, blockId, slotDef.name)
            stack.forEach { childId ->
                maxZ = layoutChild(childId, slotBounds.x, childY, maxZ)
                val childLayout = visibleBlocks.find { it.blockId == childId }
                val childHeight = childLayout?.bounds?.height ?: LayoutConstants.HEADER_HEIGHT
                childY += childHeight + LayoutConstants.BLOCK_GAP
                bodyBottom = maxOf(bodyBottom, childY)
            }

            slotY = slotBounds.bottom + LayoutConstants.SLOT_PADDING
            bodyBottom = maxOf(bodyBottom, slotBounds.bottom)
        }

        if (definition.statementInputs.size > 1) {
            bodyBottom += LayoutConstants.CORNER_RADIUS
        }

        val totalHeight = (bodyBottom - y) + LayoutConstants.FOOTER_HEIGHT
        val bounds = Rect(x, y, width, totalHeight)
        val headerBounds = Rect(x, y, width, LayoutConstants.HEADER_HEIGHT)

        visibleBlocks += BlockLayout(blockId, bounds, bounds, zStart, collapsed = false)
        hitPrimitives += HitPrimitive(
            id = "${blockId.value}:header",
            blockId = blockId,
            kind = HitKind.Header,
            bounds = headerBounds,
            zIndex = zStart,
        )
        hitPrimitives += HitPrimitive(
            id = "${blockId.value}:body",
            blockId = blockId,
            kind = HitKind.BlockBody,
            bounds = bounds,
            zIndex = zStart,
        )

        addAnchors(block, blockId, bounds, zStart, connectionAnchors, includeStatementInputs = false, includeValueInputs = false)
        updateSubtreeBounds(visibleBlocks, blockId, bounds, bodyBottom + LayoutConstants.FOOTER_HEIGHT)
        return maxZ
    }

    private fun layoutConditionInput(
        document: WorkspaceDocument,
        block: BlockNode,
        blockId: BlockId,
        inputName: String,
        sectionBounds: Rect,
        hitPrimitives: MutableList<HitPrimitive>,
        connectionAnchors: MutableList<ConnectionAnchor>,
        layoutChild: (BlockId, Float, Float, Int) -> Int,
        zStart: Int,
        zIndex: Int,
    ): Int {
        val input = block.valueInputs.find { it.name == inputName } ?: return zStart
        val dockX = sectionBounds.right - LayoutConstants.REPORTER_WIDTH - LayoutConstants.SLOT_PADDING
        val dockY = sectionBounds.y + (sectionBounds.height - LayoutConstants.REPORTER_HEIGHT) / 2f
        val slotBounds = Rect(
            dockX,
            dockY,
            LayoutConstants.REPORTER_WIDTH,
            LayoutConstants.REPORTER_HEIGHT,
        )
        hitPrimitives += HitPrimitive(
            id = "${blockId.value}:value:${input.name}",
            blockId = blockId,
            kind = HitKind.ValueInput,
            bounds = slotBounds,
            zIndex = zIndex,
            inputName = input.name,
        )
        connectionAnchors += ConnectionAnchor(
            connectionId = input.connection.id,
            ownerBlockId = blockId,
            kind = ConnectionKind.ValueInput,
            type = input.connection.accepts.firstOrNull(),
            x = slotBounds.x,
            y = slotBounds.y + LayoutConstants.REPORTER_HEIGHT / 2f,
            radius = LayoutConstants.ANCHOR_RADIUS,
            zIndex = zIndex,
        )
        var maxZ = zStart
        input.connection.connectedTo?.let { connId ->
            val (valueBlockId, _) = WorkspaceGraph.findConnection(document, connId) ?: return@let
            maxZ = layoutChild(valueBlockId, dockX, dockY, maxZ)
        }
        return maxZ
    }

    private fun layoutInlineReporter(
        document: WorkspaceDocument,
        block: BlockNode,
        definition: BlockDefinition,
        blockId: BlockId,
        x: Float,
        y: Float,
        zStart: Int,
        visibleBlocks: MutableList<BlockLayout>,
        hitPrimitives: MutableList<HitPrimitive>,
        connectionAnchors: MutableList<ConnectionAnchor>,
        inlineReporterLayouts: MutableList<InlineReporterLayout>,
        layoutChild: (BlockId, Float, Float, Int) -> Int,
    ): Int {
        val leftWidth = inlineSlotWidth(document, block, "Input1")
        val rightWidth = inlineSlotWidth(document, block, "Input2")
        val width = inlineReporterWidth(leftWidth, rightWidth)
        val height = LayoutConstants.REPORTER_HEIGHT
        val bounds = Rect(x, y, width, height)

        val leftSlotX = x + LayoutConstants.INLINE_OUTPUT_TAB + LayoutConstants.SLOT_PADDING
        val leftSlotY = y + (height - LayoutConstants.REPORTER_HEIGHT) / 2f
        val leftSlot = Rect(leftSlotX, leftSlotY, leftWidth, LayoutConstants.REPORTER_HEIGHT)

        val operatorX = leftSlot.right + LayoutConstants.INLINE_SLOT_GAP
        val operatorBounds = Rect(
            operatorX,
            y + LayoutConstants.SLOT_PADDING / 2f,
            LayoutConstants.INLINE_OPERATOR_WIDTH,
            height - LayoutConstants.SLOT_PADDING,
        )

        val rightSlotX = operatorBounds.right + LayoutConstants.INLINE_SLOT_GAP
        val rightSlot = Rect(rightSlotX, leftSlotY, rightWidth, LayoutConstants.REPORTER_HEIGHT)

        var maxZ = zStart + 1
        var subtreeBottom = bounds.bottom

        visibleBlocks += BlockLayout(blockId, bounds, bounds, zStart, collapsed = false)
        hitPrimitives += HitPrimitive(
            id = "${blockId.value}:header",
            blockId = blockId,
            kind = HitKind.Header,
            bounds = bounds,
            zIndex = zStart,
        )
        inlineReporterLayouts += InlineReporterLayout(
            blockId = blockId,
            leftSlot = leftSlot,
            operatorBounds = operatorBounds,
            rightSlot = rightSlot,
            zIndex = zStart,
        )

        listOf("Input1" to leftSlot, "Input2" to rightSlot).forEach { (inputName, slotBounds) ->
            val input = block.valueInputs.find { it.name == inputName } ?: return@forEach
            hitPrimitives += HitPrimitive(
                id = "${blockId.value}:value:${input.name}",
                blockId = blockId,
                kind = HitKind.ValueInput,
                bounds = slotBounds,
                zIndex = zStart,
                inputName = input.name,
            )
            connectionAnchors += ConnectionAnchor(
                connectionId = input.connection.id,
                ownerBlockId = blockId,
                kind = ConnectionKind.ValueInput,
                type = input.connection.accepts.firstOrNull(),
                x = slotBounds.x,
                y = slotBounds.y + LayoutConstants.REPORTER_HEIGHT / 2f,
                radius = LayoutConstants.ANCHOR_RADIUS,
                zIndex = zStart,
            )
            input.connection.connectedTo?.let { connId ->
                val (valueBlockId, _) = WorkspaceGraph.findConnection(document, connId) ?: return@let
                maxZ = layoutChild(valueBlockId, slotBounds.x, slotBounds.y, maxZ)
                subtreeBottom = maxOf(subtreeBottom, maxZBound(visibleBlocks, valueBlockId))
            }
        }

        block.output?.let { conn ->
            connectionAnchors += ConnectionAnchor(
                connectionId = conn.id,
                ownerBlockId = blockId,
                kind = ConnectionKind.Output,
                type = conn.provides,
                x = bounds.x,
                y = bounds.y + height / 2f,
                radius = LayoutConstants.ANCHOR_RADIUS,
                zIndex = zStart,
            )
        }

        updateSubtreeBounds(visibleBlocks, blockId, bounds, subtreeBottom)
        return maxOf(maxZ, zStart + 1)
    }

    private fun inlineReporterWidth(leftWidth: Float, rightWidth: Float): Float =
        LayoutConstants.INLINE_OUTPUT_TAB +
            LayoutConstants.SLOT_PADDING +
            leftWidth +
            LayoutConstants.INLINE_SLOT_GAP +
            LayoutConstants.INLINE_OPERATOR_WIDTH +
            LayoutConstants.INLINE_SLOT_GAP +
            rightWidth +
            LayoutConstants.SLOT_PADDING

    private fun inlineSlotWidth(document: WorkspaceDocument, block: BlockNode, inputName: String): Float {
        val input = block.valueInputs.find { it.name == inputName }
            ?: return LayoutConstants.INLINE_MIN_SLOT_WIDTH
        val connected = input.connection.connectedTo
            ?: return LayoutConstants.INLINE_MIN_SLOT_WIDTH
        val (childId, _) = WorkspaceGraph.findConnection(document, connected)
            ?: return LayoutConstants.INLINE_MIN_SLOT_WIDTH
        val child = document.blocks[childId] ?: return LayoutConstants.INLINE_MIN_SLOT_WIDTH
        val childDef = registry.getDefinition(child.type)
        return if (childDef?.inputsInline == true) {
            inlineReporterWidth(
                inlineSlotWidth(document, child, "Input1"),
                inlineSlotWidth(document, child, "Input2"),
            )
        } else {
            LayoutConstants.INLINE_MIN_SLOT_WIDTH
        }
    }

    private fun addAnchors(
        block: BlockNode,
        blockId: BlockId,
        bounds: Rect,
        zIndex: Int,
        out: MutableList<ConnectionAnchor>,
        includeStatementInputs: Boolean = true,
        includeValueInputs: Boolean = true,
    ) {
        block.previous?.let { conn ->
            out += ConnectionAnchor(
                connectionId = conn.id,
                ownerBlockId = blockId,
                kind = ConnectionKind.Previous,
                type = null,
                x = bounds.x + LayoutConstants.NESTED_INDENT,
                y = bounds.y,
                radius = LayoutConstants.ANCHOR_RADIUS,
                zIndex = zIndex,
            )
        }
        block.next?.let { conn ->
            out += ConnectionAnchor(
                connectionId = conn.id,
                ownerBlockId = blockId,
                kind = ConnectionKind.Next,
                type = null,
                x = bounds.x + bounds.width / 2,
                y = bounds.bottom,
                radius = LayoutConstants.ANCHOR_RADIUS,
                zIndex = zIndex,
            )
        }
        block.output?.let { conn ->
            out += ConnectionAnchor(
                connectionId = conn.id,
                ownerBlockId = blockId,
                kind = ConnectionKind.Output,
                type = conn.provides,
                x = bounds.right,
                y = bounds.y + bounds.height / 2,
                radius = LayoutConstants.ANCHOR_RADIUS,
                zIndex = zIndex,
            )
        }
        if (includeValueInputs) {
            block.valueInputs.forEach { input ->
                out += ConnectionAnchor(
                    connectionId = input.connection.id,
                    ownerBlockId = blockId,
                    kind = ConnectionKind.ValueInput,
                    type = input.connection.accepts.firstOrNull(),
                    x = bounds.right - LayoutConstants.SLOT_PADDING,
                    y = bounds.y + LayoutConstants.HEADER_HEIGHT / 2,
                    radius = LayoutConstants.ANCHOR_RADIUS,
                    zIndex = zIndex,
                )
            }
        }
        if (includeStatementInputs) {
            block.statementInputs.forEach { input ->
                out += ConnectionAnchor(
                    connectionId = input.connection.id,
                    ownerBlockId = blockId,
                    kind = ConnectionKind.StatementInput,
                    type = null,
                    x = bounds.x + LayoutConstants.NESTED_INDENT,
                    y = bounds.y + LayoutConstants.HEADER_HEIGHT,
                    radius = LayoutConstants.ANCHOR_RADIUS,
                    zIndex = zIndex,
                )
            }
        }
    }

    private fun blockWidth(
        document: WorkspaceDocument,
        definition: BlockDefinition?,
        block: BlockNode,
    ): Float = when {
        definition?.inputsInline == true -> inlineReporterWidth(
            inlineSlotWidth(document, block, "Input1"),
            inlineSlotWidth(document, block, "Input2"),
        )
        definition?.isReporter == true -> LayoutConstants.REPORTER_WIDTH
        definition?.statementInputs?.isNotEmpty() == true -> LayoutConstants.STANDARD_WIDTH
        else -> LayoutConstants.STANDARD_WIDTH
    }

    private fun updateSubtreeBounds(
        layouts: MutableList<BlockLayout>,
        blockId: BlockId,
        bounds: Rect,
        bottom: Float,
    ) {
        val index = layouts.indexOfLast { it.blockId == blockId }
        if (index >= 0) {
            layouts[index] = layouts[index].copy(
                subtreeBounds = Rect(bounds.x, bounds.y, bounds.width, bottom - bounds.y),
            )
        }
    }

    private fun maxZBound(layouts: List<BlockLayout>, blockId: BlockId): Float =
        layouts.find { it.blockId == blockId }?.subtreeBounds?.bottom ?: 0f

    private fun anchorHitBounds(anchor: ConnectionAnchor): Rect {
        val d = anchor.radius * 2
        return Rect(anchor.x - anchor.radius, anchor.y - anchor.radius, d, d)
    }

    private companion object {
        fun LayoutConstants.VALUE_DOCK_GAP(index: Int): Float = OUTPUT_TAB + index * (REPORTER_WIDTH + 4f)
    }
}
