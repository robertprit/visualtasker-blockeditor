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
            LayoutConstants.ELIF_SECTION_HEIGHT - LayoutConstants.BRANCH_SHELF
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
        assertTrue(elifAnchor.x >= elifSection.bounds.x)
        assertTrue(elifAnchor.y >= elifSection.bounds.y)
        assertTrue(elifAnchor.y <= elifSection.bounds.bottom)
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
}
