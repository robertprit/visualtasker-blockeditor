package de.visualtasker.blockeditor.layout

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.BlockNode
import de.visualtasker.blockeditor.domain.Connection
import de.visualtasker.blockeditor.domain.ConnectionId
import de.visualtasker.blockeditor.domain.ConnectionKind
import de.visualtasker.blockeditor.domain.StatementInput
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.withRootOffset
import de.visualtasker.blockeditor.layout.HitKind
import de.visualtasker.blockeditor.layout.LayoutConstants
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry
import de.visualtasker.blockeditor.registry.SampleWorkspaceFactory
import de.visualtasker.blockeditor.registry.createNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutEngineTest {
    private val engine = LayoutEngine(DefaultBlockRegistry)

    @Test
    fun eachBlockAppearsOnce_whenRootListContainsStaleChainMember() {
        val document = SampleWorkspaceFactory.createDemo()
        val chain = SampleWorkspaceFactory.mainChain(document)
        val stale = document.copy(rootBlocks = document.rootBlocks + chain[2])

        val cache = engine.build(stale)
        val counts = cache.flatIndex.visibleBlocks.groupingBy { it.blockId }.eachCount()

        counts.forEach { (_, count) -> assertEquals(1, count) }
    }

    @Test
    fun layoutCache_exposesMeasureAndPlacePassArtifacts() {
        val document = SampleWorkspaceFactory.create()
        val cache = engine.build(document)
        val rootId = document.rootBlocks.single()

        assertEquals(document.version, cache.measuredLayoutTree.documentVersion)
        assertEquals(document.version, cache.placedLayoutTree.documentVersion)
        assertTrue(cache.measuredLayoutTree.blocks.containsKey(rootId))
        assertTrue(cache.placedLayoutTree.blocks.containsKey(rootId))
        assertEquals(
            cache.flatIndex.visibleBlocks.first { it.blockId == rootId }.bounds,
            cache.placedLayoutTree.blocks[rootId]!!.bounds,
        )
    }

    @Test
    fun linearEventStatementStack_usesSharedStackDockGeometry() {
        val startId = BlockId("start")
        val firstId = BlockId("first")
        val secondId = BlockId("second")
        val startDef = DefaultBlockRegistry.getDefinition(BlockTypes.EVENT_START)!!
        val statementDef = DefaultBlockRegistry.getDefinition("action.wait")!!
        val startNext = ConnectionId("start:next")
        val firstPrevious = ConnectionId("first:previous")
        val firstNext = ConnectionId("first:next")
        val secondPrevious = ConnectionId("second:previous")
        val start = startDef.createNode(startId)
            .withRootOffset(12f, 24f)
            .copy(next = Connection(startNext, startId, ConnectionKind.Next, connectedTo = firstPrevious))
        val first = statementDef.createNode(firstId).copy(
            previous = Connection(firstPrevious, firstId, ConnectionKind.Previous, connectedTo = startNext),
            next = Connection(firstNext, firstId, ConnectionKind.Next, connectedTo = secondPrevious),
        )
        val second = statementDef.createNode(secondId).copy(
            previous = Connection(secondPrevious, secondId, ConnectionKind.Previous, connectedTo = firstNext),
        )
        val document = WorkspaceDocument(
            id = "linear-stack",
            blocks = mapOf(startId to start, firstId to first, secondId to second),
            rootBlocks = listOf(startId),
        )

        val cache = engine.build(document)
        val startLayout = cache.flatIndex.visibleBlocks.first { it.blockId == startId }
        val firstLayout = cache.flatIndex.visibleBlocks.first { it.blockId == firstId }
        val secondLayout = cache.flatIndex.visibleBlocks.first { it.blockId == secondId }
        val anchors = cache.flatIndex.connectionAnchors

        assertEquals(startLayout.bounds.x + LayoutConstants.STACK_DOCK_X, anchors.anchor(startId, ConnectionKind.Next).x)
        assertEquals(firstLayout.bounds.x + LayoutConstants.STACK_DOCK_X, anchors.anchor(firstId, ConnectionKind.Previous).x)
        assertEquals(firstLayout.bounds.x + LayoutConstants.STACK_DOCK_X, anchors.anchor(firstId, ConnectionKind.Next).x)
        assertEquals(secondLayout.bounds.x + LayoutConstants.STACK_DOCK_X, anchors.anchor(secondId, ConnectionKind.Previous).x)
        assertEquals(startLayout.bounds.bottom + LayoutConstants.STACK_VERTICAL_GAP, anchors.anchor(startId, ConnectionKind.Next).y)
        assertEquals(firstLayout.bounds.y, anchors.anchor(firstId, ConnectionKind.Previous).y)
        assertEquals(firstLayout.bounds.bottom + LayoutConstants.STACK_VERTICAL_GAP, anchors.anchor(firstId, ConnectionKind.Next).y)
        assertEquals(secondLayout.bounds.y, anchors.anchor(secondId, ConnectionKind.Previous).y)
        assertEquals(
            startLayout.bounds.bottom + LayoutConstants.STACK_VERTICAL_GAP,
            firstLayout.bounds.y,
            0.001f,
        )
        assertEquals(
            firstLayout.bounds.bottom + LayoutConstants.STACK_VERTICAL_GAP,
            secondLayout.bounds.y,
            0.001f,
        )
    }

    @Test
    fun repeatContainer_growsWithBodyStack() {
        val empty = layoutRepeat(emptyList())
        val one = layoutRepeat(listOf("a"))
        val two = layoutRepeat(listOf("a", "b"))

        val emptyHeight = blockHeight(empty, BlockId("repeat"))
        val oneHeight = blockHeight(one, BlockId("repeat"))
        val twoHeight = blockHeight(two, BlockId("repeat"))

        assertTrue("One block body should increase height", oneHeight > emptyHeight)
        assertTrue("Two block body should increase height further", twoHeight > oneHeight)
    }

    @Test
    fun ifElseIfElseContainer_isTallerThanIfDueToElifSection() {
        val ifOnly = layoutControl(BlockTypes.CONTROL_IF)
        val ifElifElse = layoutControl(BlockTypes.CONTROL_IF_ELSEIF_ELSE)

        val ifHeight = blockHeight(ifOnly, BlockId("control"))
        val elifHeight = blockHeight(ifElifElse, BlockId("control"))

        assertTrue(
            "If/Else If/Else should reserve extra height for the elseif section",
            elifHeight > ifHeight,
        )
        assertTrue(
            "Height delta should include the elif section",
            elifHeight - ifHeight >= LayoutConstants.ELIF_SECTION_HEIGHT,
        )
    }

    @Test
    fun ifElseIfElseContainer_exposesValueInputHits() {
        val cache = layoutControl(BlockTypes.CONTROL_IF_ELSEIF_ELSE)
        val hits = cache.flatIndex.hitPrimitives
            .filter { it.blockId == BlockId("control") && it.kind == HitKind.ValueInput }
            .map { it.inputName }
            .toSet()

        assertEquals(setOf("CONDITION", "ELIF_CONDITION"), hits)
    }

    @Test
    fun ifElseIfElseContainer_placesStatementAnchorsAtEachSlotTop() {
        val cache = layoutControl(BlockTypes.CONTROL_IF_ELSEIF_ELSE)
        val controlId = BlockId("control")
        val blockLayout = cache.flatIndex.visibleBlocks.first { it.blockId == controlId }
        val slots = cache.flatIndex.statementSlots.filter { it.blockId == controlId }
        val anchors = cache.flatIndex.connectionAnchors.filter {
            it.ownerBlockId == controlId && it.kind == ConnectionKind.StatementInput
        }

        assertEquals(3, anchors.size)
        assertEquals(3, slots.size)

        slots.forEach { slot ->
            val anchor = anchors.firstOrNull {
                it.x == slot.bounds.x && it.y == slot.bounds.y
            }
            assertTrue("Missing anchor for slot ${slot.slotName}", anchor != null)
        }

        val elifSlot = slots.first { it.slotName == BlockTypes.SLOT_ELIF }
        val elifDividerY = elifSlot.bounds.y - blockLayout.bounds.y -
            LayoutConstants.ELIF_SECTION_HEIGHT - LayoutConstants.SLOT_PADDING
        assertTrue(
            "Else-if divider should sit below the then-slot",
            elifDividerY > LayoutConstants.HEADER_HEIGHT + LayoutConstants.STATEMENT_MIN_HEIGHT,
        )
    }

    @Test
    fun ifElseIfElseContainer_placesConditionAnchorsInsideBranchSections() {
        val cache = layoutControl(BlockTypes.CONTROL_IF_ELSEIF_ELSE)
        val controlId = BlockId("control")
        val anchors = cache.flatIndex.connectionAnchors.filter {
            it.ownerBlockId == controlId && it.kind == ConnectionKind.ValueInput
        }
        val sections = cache.flatIndex.branchSections.filter { it.blockId == controlId }

        assertEquals(2, anchors.size)
        assertEquals(setOf("CONDITION", "ELIF_CONDITION"), anchors.map { it.connectionId.value.substringAfter(":") }.toSet())

        val elifSection = sections.first { it.inputName == "ELIF_CONDITION" }
        val elifAnchor = anchors.first { it.connectionId.value.contains("ELIF_CONDITION") }
        val elifHit = cache.flatIndex.hitPrimitives.first {
            it.blockId == controlId && it.kind == HitKind.ValueInput && it.inputName == "ELIF_CONDITION"
        }
        val conditionHits = cache.flatIndex.hitPrimitives.filter {
            it.blockId == controlId && it.kind == HitKind.ValueInput
        }
        assertTrue(elifAnchor.x >= elifSection.bounds.x)
        assertTrue(elifAnchor.y >= elifSection.bounds.y)
        assertTrue(elifAnchor.y <= elifSection.bounds.bottom)
        assertTrue("Else-if reporter slot should not protrude above its branch header", elifHit.bounds.y >= elifSection.bounds.y)
        assertTrue("Else-if reporter slot should not protrude below its branch header", elifHit.bounds.bottom <= elifSection.bounds.bottom)
        conditionHits.forEach { hit ->
            assertEquals("Control reporter docks should use compact reporter width", LayoutConstants.REPORTER_WIDTH, hit.bounds.width, 0.001f)
        }
    }

    @Test
    fun ifElseIfElseContainer_doesNotRenderSeparateDividerBeforeElifCondition() {
        val cache = layoutControl(BlockTypes.CONTROL_IF_ELSEIF_ELSE)
        val controlId = BlockId("control")
        val sections = cache.flatIndex.branchSections.filter { it.blockId == controlId }
        val elifCondition = sections.single {
            it.kind == BranchSectionKind.ElifCondition && it.inputName == "ELIF_CONDITION"
        }
        val duplicateElifDividers = sections.filter {
            it.kind == BranchSectionKind.BranchDivider && it.label == "elseif"
        }

        assertFalse("Else-if must not render both a divider arm and a condition arm", duplicateElifDividers.isNotEmpty())
        assertEquals("elseif", elifCondition.label)
    }

    @Test
    fun ifElseIfElseContainer_usesElifConditionAsVisibleBranchArm() {
        val cache = layoutControl(BlockTypes.CONTROL_IF_ELSEIF_ELSE)
        val controlId = BlockId("control")
        val blockTop = cache.flatIndex.visibleBlocks.first { it.blockId == controlId }.bounds.y
        val sections = cache.flatIndex.branchSections.filter { it.blockId == controlId }
        val elifSection = sections.single {
            it.kind == BranchSectionKind.ElifCondition && it.inputName == "ELIF_CONDITION"
        }
        val dividerYs = ContainerBranchLayout.branchDividerYsFromSections(blockTop, sections)

        assertTrue(
            "Else-if condition must still produce one visible branch arm",
            dividerYs.contains(elifSection.bounds.y - blockTop),
        )
    }

    @Test
    fun ifElseIfElseContainer_keepsPaddingBetweenBranchHeadersAndStatementSlots() {
        val cache = layoutControl(BlockTypes.CONTROL_IF_ELSEIF_ELSE)
        val controlId = BlockId("control")
        val sections = cache.flatIndex.branchSections.filter { it.blockId == controlId }
        val slots = cache.flatIndex.statementSlots.associateBy { it.slotName }
        val elifSection = sections.single { it.kind == BranchSectionKind.ElifCondition }
        val elseDivider = sections.single { it.kind == BranchSectionKind.BranchDivider && it.label == "else" }

        assertEquals(
            LayoutConstants.SLOT_PADDING,
            slots.getValue(BlockTypes.SLOT_ELIF).bounds.y - elifSection.bounds.bottom,
            0.001f,
        )
        assertEquals(
            LayoutConstants.SLOT_PADDING,
            slots.getValue(BlockTypes.SLOT_ELSE).bounds.y - elseDivider.bounds.bottom,
            0.001f,
        )
    }

    @Test
    fun branchHeadersAreTallEnoughForReporterSocketsAndText() {
        val cache = layoutControl(BlockTypes.CONTROL_IF_ELSEIF_ELSE)
        val controlId = BlockId("control")
        val sections = cache.flatIndex.branchSections.filter { it.blockId == controlId }
        val elifSection = sections.single { it.kind == BranchSectionKind.ElifCondition }
        val elseDivider = sections.single { it.kind == BranchSectionKind.BranchDivider && it.label == "else" }

        assertTrue(
            "Else-if header must be tall enough to contain a reporter socket",
            elifSection.bounds.height >= LayoutConstants.REPORTER_HEIGHT,
        )
        assertTrue(
            "Else divider must be tall enough to render its label without clipping",
            elseDivider.bounds.height >= LayoutConstants.REPORTER_HEIGHT,
        )
    }

    @Test
    fun ifElseIfElseContainer_keepsNestedStatementsInsideBranchSlots() {
        val controlId = BlockId("control")
        val thenId = BlockId("then_wait")
        val elifId = BlockId("elif_wait")
        val elseId = BlockId("else_click")
        val ifReporterId = BlockId("if_reporter")
        val elifReporterId = BlockId("elif_reporter")
        val controlDef = DefaultBlockRegistry.getDefinition(BlockTypes.CONTROL_IF_ELSEIF_ELSE)!!
        val waitDef = DefaultBlockRegistry.getDefinition(BlockTypes.ACTION_WAIT)!!
        val clickDef = DefaultBlockRegistry.getDefinition(BlockTypes.ACTION_CLICK_TEXT)!!
        val reporterDef = DefaultBlockRegistry.getDefinition(BlockTypes.LOGIC_BOOLEAN)!!
        var control = controlDef.createNode(controlId).withRootOffset(0f, 0f)
        var thenWait = waitDef.createNode(thenId)
        var elifWait = waitDef.createNode(elifId)
        var elseClick = clickDef.createNode(elseId)
        val ifReporter = reporterDef.createNode(ifReporterId)
        val elifReporter = reporterDef.createNode(elifReporterId)
        val thenSlot = control.statementInputs.first { it.name == BlockTypes.SLOT_THEN }.connection
        val elifSlot = control.statementInputs.first { it.name == BlockTypes.SLOT_ELIF }.connection
        val elseSlot = control.statementInputs.first { it.name == BlockTypes.SLOT_ELSE }.connection
        val ifInput = control.valueInputs.first { it.name == "CONDITION" }.connection
        val elifInput = control.valueInputs.first { it.name == "ELIF_CONDITION" }.connection

        control = control.copy(
            valueInputs = control.valueInputs.map { input ->
                when (input.name) {
                    "CONDITION" -> input.copy(connection = ifInput.copy(connectedTo = ifReporter.output!!.id))
                    "ELIF_CONDITION" -> input.copy(connection = elifInput.copy(connectedTo = elifReporter.output!!.id))
                    else -> input
                }
            },
            statementInputs = control.statementInputs.map { input ->
                when (input.name) {
                    BlockTypes.SLOT_THEN -> input.copy(connection = thenSlot.copy(connectedTo = thenWait.previous!!.id))
                    BlockTypes.SLOT_ELIF -> input.copy(connection = elifSlot.copy(connectedTo = elifWait.previous!!.id))
                    BlockTypes.SLOT_ELSE -> input.copy(connection = elseSlot.copy(connectedTo = elseClick.previous!!.id))
                    else -> input
                }
            },
        )
        thenWait = thenWait.copy(previous = thenWait.previous!!.copy(connectedTo = thenSlot.id))
        elifWait = elifWait.copy(previous = elifWait.previous!!.copy(connectedTo = elifSlot.id))
        elseClick = elseClick.copy(previous = elseClick.previous!!.copy(connectedTo = elseSlot.id))
        val document = WorkspaceDocument(
            id = "branch-slots-with-content",
            blocks = mapOf(
                controlId to control,
                thenId to thenWait,
                elifId to elifWait,
                elseId to elseClick,
                ifReporterId to ifReporter.copy(output = ifReporter.output!!.copy(connectedTo = ifInput.id)),
                elifReporterId to elifReporter.copy(output = elifReporter.output!!.copy(connectedTo = elifInput.id)),
            ),
            rootBlocks = listOf(controlId),
        )

        val cache = engine.build(document)
        val slots = cache.flatIndex.statementSlots.associateBy { it.slotName }
        mapOf(
            BlockTypes.SLOT_THEN to thenId,
            BlockTypes.SLOT_ELIF to elifId,
            BlockTypes.SLOT_ELSE to elseId,
        ).forEach { (slotName, childId) ->
            val slotBounds = slots.getValue(slotName).bounds
            val childBounds = cache.flatIndex.visibleBlocks.first { it.blockId == childId }.bounds
            assertTrue("$slotName child should start inside its branch slot", childBounds.x >= slotBounds.x)
            assertTrue("$slotName child should fit inside its branch slot", childBounds.right <= slotBounds.right)
        }
    }

    @Test
    fun singleSlotContainers_doNotRenderBranchDividersForBodySlots() {
        val repeatSections = layoutControl(BlockTypes.CONTROL_REPEAT).flatIndex.branchSections
        val whileSections = layoutControl(BlockTypes.CONTROL_WHILE).flatIndex.branchSections

        assertFalse(
            "Repeat DO slot must not render as a branch divider",
            repeatSections.any { it.kind == BranchSectionKind.BranchDivider && it.label == "do" },
        )
        assertFalse(
            "While BODY slot must not render as a branch divider",
            whileSections.any { it.kind == BranchSectionKind.BranchDivider && it.label == "body" },
        )
    }

    @Test
    fun multiValueReporter_placesValueInputAnchorsAtDistinctSlots() {
        val andId = BlockId("and")
        val and = DefaultBlockRegistry.getDefinition(BlockTypes.LOGIC_AND)!!
            .createNode(andId)
            .withRootOffset(0f, 0f)
        val document = WorkspaceDocument(
            id = "multi-value-reporter-layout",
            blocks = mapOf(andId to and),
            rootBlocks = listOf(andId),
        )

        val cache = engine.build(document)
        val inputHits = cache.flatIndex.hitPrimitives
            .filter { it.blockId == andId && it.kind == HitKind.ValueInput }
            .associateBy { it.inputName }
        val inputAnchors = cache.flatIndex.connectionAnchors
            .filter { it.ownerBlockId == andId && it.kind == ConnectionKind.ValueInput }
            .associateBy { it.connectionId }
        val aInput = and.valueInputs.first { it.name == "A" }
        val bInput = and.valueInputs.first { it.name == "B" }
        val aAnchor = inputAnchors.getValue(aInput.connection.id)
        val bAnchor = inputAnchors.getValue(bInput.connection.id)

        assertEquals(setOf("A", "B"), inputHits.keys)
        assertEquals(inputHits.getValue("A").bounds.x, aAnchor.x, 0.001f)
        assertEquals(inputHits.getValue("B").bounds.x, bAnchor.x, 0.001f)
        assertTrue("A and B anchors must not overlap", aAnchor.x < bAnchor.x)
    }

    @Test
    fun reporterBlocksUseCompactWidth() {
        val reporterId = BlockId("number")
        val reporter = DefaultBlockRegistry.getDefinition(BlockTypes.LITERAL_NUMBER)!!
            .createNode(reporterId)
            .withRootOffset(0f, 0f)
        val document = WorkspaceDocument(
            id = "compact-reporter-layout",
            blocks = mapOf(reporterId to reporter),
            rootBlocks = listOf(reporterId),
        )

        val cache = engine.build(document)
        val layout = cache.flatIndex.visibleBlocks.single { it.blockId == reporterId }

        assertEquals(LayoutConstants.REPORTER_WIDTH, layout.bounds.width, 0.001f)
        assertTrue("Reporter width should stay clearly below statement width", layout.bounds.width < LayoutConstants.STANDARD_WIDTH / 2f)
    }

    @Test
    fun connectionHitPrimitivesKeepConcreteConnectionIds() {
        val operateId = BlockId("operate")
        val operate = DefaultBlockRegistry.getDefinition(BlockTypes.LOGIC_OPERATE)!!
            .createNode(operateId)
            .withRootOffset(0f, 0f)
        val document = WorkspaceDocument(
            id = "connection-hit-primitives",
            blocks = mapOf(operateId to operate),
            rootBlocks = listOf(operateId),
        )

        val cache = engine.build(document)
        val connectionHits = cache.flatIndex.hitPrimitives
            .filter { it.blockId == operateId && it.kind == HitKind.ConnectionAnchor }
            .associateBy { it.connectionId }
        val anchors = cache.flatIndex.connectionAnchors
            .filter { it.ownerBlockId == operateId }

        assertEquals(anchors.map { it.connectionId }.toSet(), connectionHits.keys)
        anchors.forEach { anchor ->
            val hit = connectionHits.getValue(anchor.connectionId)
            assertEquals(anchor.x - anchor.radius, hit.bounds.x, 0.001f)
            assertEquals(anchor.y - anchor.radius, hit.bounds.y, 0.001f)
        }
    }

    @Test
    fun ifElseContainer_usesIndependentSlotHeightsAcrossBranches() {
        val cache = layoutControl(BlockTypes.CONTROL_IF_ELSE)
        val controlId = BlockId("control")
        val slotHeights = cache.flatIndex.statementSlots
            .filter { it.blockId == controlId }
            .associate { it.slotName to it.bounds.height }

        assertEquals(LayoutConstants.STATEMENT_MIN_HEIGHT, slotHeights[BlockTypes.SLOT_THEN])
        assertEquals(LayoutConstants.STATEMENT_MIN_HEIGHT, slotHeights[BlockTypes.SLOT_ELSE])
    }

    @Test
    fun ifElseIfElseContainer_usesIndependentSlotHeightsAcrossBranches() {
        val controlId = BlockId("control")
        val controlDef = DefaultBlockRegistry.getDefinition(BlockTypes.CONTROL_IF_ELSEIF_ELSE)!!
        val waitDef = DefaultBlockRegistry.getDefinition("action.wait")!!

        var control = controlDef.createNode(controlId).withRootOffset(0f, 0f)
        val blocks = mutableMapOf<BlockId, BlockNode>(controlId to control)

        var prevConnId: ConnectionId? = control.statementInputs
            .first { it.name == BlockTypes.SLOT_THEN }
            .connection.id
        listOf("a", "b").forEach { suffix ->
            val blockId = BlockId("then_$suffix")
            var node = waitDef.createNode(blockId)
            if (suffix == "a") {
                val thenSlot = control.statementInputs.first { it.name == BlockTypes.SLOT_THEN }
                val thenConn = thenSlot.connection
                control = control.copy(
                    statementInputs = control.statementInputs.map { input ->
                        if (input.name == BlockTypes.SLOT_THEN) {
                            input.copy(connection = thenConn.copy(connectedTo = node.previous!!.id))
                        } else {
                            input
                        }
                    },
                )
                blocks[controlId] = control
                node = node.copy(previous = node.previous!!.copy(connectedTo = thenConn.id))
            } else {
                val prevBlock = blocks[BlockId("then_a")]!!
                val prevNextConn = prevBlock.next!!
                node = node.copy(previous = node.previous!!.copy(connectedTo = prevNextConn.id))
                blocks[prevBlock.id] = prevBlock.copy(
                    next = prevNextConn.copy(connectedTo = node.previous!!.id),
                )
            }
            blocks[blockId] = node
            prevConnId = node.next?.id
        }

        val document = WorkspaceDocument(
            id = "layout-test",
            blocks = blocks,
            rootBlocks = listOf(controlId),
        )
        val cache = engine.build(document)
        val slotHeights = cache.flatIndex.statementSlots
            .filter { it.blockId == controlId }
            .associate { it.slotName to it.bounds.height }

        assertEquals(setOf(BlockTypes.SLOT_THEN, BlockTypes.SLOT_ELIF, BlockTypes.SLOT_ELSE), slotHeights.keys)
        assertTrue(
            "Then branch should grow with stacked blocks",
            slotHeights[BlockTypes.SLOT_THEN]!! > LayoutConstants.STATEMENT_MIN_HEIGHT,
        )
        assertEquals(
            "Else-if branch stays at minimum height when empty",
            LayoutConstants.STATEMENT_MIN_HEIGHT,
            slotHeights[BlockTypes.SLOT_ELIF],
        )
        assertEquals(
            "Else branch stays at minimum height when empty",
            LayoutConstants.STATEMENT_MIN_HEIGHT,
            slotHeights[BlockTypes.SLOT_ELSE],
        )
    }

    @Test
    fun branchSlotPlacementUsesRecursiveSubtreeHeight() {
        val outerId = BlockId("outer")
        val innerId = BlockId("inner")
        val actionAId = BlockId("action-a")
        val actionBId = BlockId("action-b")
        val ifDef = DefaultBlockRegistry.getDefinition(BlockTypes.CONTROL_IF_ELSE)!!
        val actionDef = DefaultBlockRegistry.getDefinition(BlockTypes.ACTION_WAIT)!!
        var outer = ifDef.createNode(outerId).withRootOffset(0f, 0f)
        var inner = ifDef.createNode(innerId)
        var actionA = actionDef.createNode(actionAId)
        var actionB = actionDef.createNode(actionBId)
        val outerThen = outer.statementInputs.first { it.name == BlockTypes.SLOT_THEN }.connection
        val innerThen = inner.statementInputs.first { it.name == BlockTypes.SLOT_THEN }.connection
        val innerElse = inner.statementInputs.first { it.name == BlockTypes.SLOT_ELSE }.connection
        outer = outer.copy(
            statementInputs = outer.statementInputs.map {
                if (it.name == BlockTypes.SLOT_THEN) it.copy(connection = outerThen.copy(connectedTo = inner.previous!!.id)) else it
            },
        )
        inner = inner.copy(
            previous = inner.previous!!.copy(connectedTo = outerThen.id),
            statementInputs = inner.statementInputs.map {
                when (it.name) {
                    BlockTypes.SLOT_THEN -> it.copy(connection = innerThen.copy(connectedTo = actionA.previous!!.id))
                    BlockTypes.SLOT_ELSE -> it.copy(connection = innerElse.copy(connectedTo = actionB.previous!!.id))
                    else -> it
                }
            },
        )
        actionA = actionA.copy(previous = actionA.previous!!.copy(connectedTo = innerThen.id))
        actionB = actionB.copy(previous = actionB.previous!!.copy(connectedTo = innerElse.id))
        val document = WorkspaceDocument(
            id = "nested-branch-layout",
            blocks = mapOf(outerId to outer, innerId to inner, actionAId to actionA, actionBId to actionB),
            rootBlocks = listOf(outerId),
        )

        val cache = engine.build(document)
        val innerLayout = cache.flatIndex.visibleBlocks.first { it.blockId == innerId }
        val outerElseSlot = cache.flatIndex.statementSlots.first { it.blockId == outerId && it.slotName == BlockTypes.SLOT_ELSE }

        assertTrue(
            "Outer ELSE slot must start below the full nested IF subtree",
            outerElseSlot.bounds.y >= innerLayout.subtreeBounds.bottom + LayoutConstants.STACK_VERTICAL_GAP,
        )
    }

    private fun layoutControl(type: String): LayoutCache {
        val controlId = BlockId("control")
        val def = DefaultBlockRegistry.getDefinition(type)!!
        val control = def.createNode(controlId).withRootOffset(0f, 0f)
        val document = WorkspaceDocument(
            id = "layout-test",
            blocks = mapOf(controlId to control),
            rootBlocks = listOf(controlId),
        )
        return engine.build(document)
    }

    private fun layoutRepeat(stackIds: List<String>): LayoutCache {
        val repeatId = BlockId("repeat")
        val repeatDef = DefaultBlockRegistry.getDefinition("control.repeat")!!
        var repeat = repeatDef.createNode(repeatId).withRootOffset(0f, 0f)
        val blocks = mutableMapOf(repeatId to repeat)

        var prevNext: ConnectionId? = repeat.statementInputs.first().connection.id
        stackIds.forEachIndexed { index, suffix ->
            val id = BlockId("block_$suffix")
            val def = DefaultBlockRegistry.getDefinition("action.wait")!!
            var node = def.createNode(id)
            if (index == 0) {
                val slot = repeat.statementInputs.first()
                val stmtConn = slot.connection
                repeat = repeat.copy(
                    statementInputs = listOf(
                        StatementInput(
                            slot.name,
                            stmtConn.copy(connectedTo = node.previous!!.id),
                        ),
                    ),
                )
                blocks[repeatId] = repeat
                node = node.copy(previous = node.previous!!.copy(connectedTo = stmtConn.id))
            } else {
                val prevBlock = blocks[BlockId("block_${stackIds[index - 1]}")]!!
                val prevNextConn = prevBlock.next!!
                node = node.copy(previous = node.previous!!.copy(connectedTo = prevNextConn.id))
                blocks[prevBlock.id] = prevBlock.copy(
                    next = prevNextConn.copy(connectedTo = node.previous!!.id),
                )
            }
            blocks[id] = node
            prevNext = node.next?.id
        }

        val document = WorkspaceDocument(
            id = "layout-test",
            blocks = blocks,
            rootBlocks = listOf(repeatId),
        )
        return engine.build(document)
    }

    private fun blockHeight(cache: LayoutCache, blockId: BlockId): Float =
        cache.flatIndex.visibleBlocks.first { it.blockId == blockId }.bounds.height

    private fun List<ConnectionAnchor>.anchor(blockId: BlockId, kind: ConnectionKind): ConnectionAnchor =
        first { it.ownerBlockId == blockId && it.kind == kind }
}
