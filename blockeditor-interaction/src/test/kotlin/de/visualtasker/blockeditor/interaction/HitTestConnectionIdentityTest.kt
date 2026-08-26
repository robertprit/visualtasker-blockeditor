package de.visualtasker.blockeditor.interaction

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.BlockNode
import de.visualtasker.blockeditor.domain.Connection
import de.visualtasker.blockeditor.domain.ConnectionId
import de.visualtasker.blockeditor.domain.ConnectionKind
import de.visualtasker.blockeditor.domain.FieldValue
import de.visualtasker.blockeditor.domain.Offset2
import de.visualtasker.blockeditor.domain.Rect
import de.visualtasker.blockeditor.domain.ValueInput
import de.visualtasker.blockeditor.domain.WorkspaceAction
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.WorkspaceReducer
import de.visualtasker.blockeditor.domain.withRootOffset
import de.visualtasker.blockeditor.layout.ConnectionAnchor
import de.visualtasker.blockeditor.layout.FlatLayoutIndex
import de.visualtasker.blockeditor.layout.HitKind
import de.visualtasker.blockeditor.layout.HitPrimitive
import de.visualtasker.blockeditor.layout.LayoutEngine
import de.visualtasker.blockeditor.layout.SpatialIndex
import de.visualtasker.blockeditor.registry.BlockDefinition
import de.visualtasker.blockeditor.registry.BlockRegistry
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry
import de.visualtasker.blockeditor.registry.ValueInputDefinition
import de.visualtasker.blockeditor.registry.createNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HitTestConnectionIdentityTest {
    private val engine = LayoutEngine(DefaultBlockRegistry)

    @Test
    fun operateValueInputHitsResolveConcreteConnectionIds() {
        val operateId = BlockId("operate")
        val operate = DefaultBlockRegistry.getDefinition(BlockTypes.LOGIC_OPERATE)!!
            .createNode(operateId)
            .withRootOffset(0f, 0f)
        val document = WorkspaceDocument(
            id = "operate-hit",
            blocks = mapOf(operateId to operate),
            rootBlocks = listOf(operateId),
        )
        val layout = engine.build(document).flatIndex
        val input1 = operate.valueInputs.first { it.name == "Input1" }.connection.id
        val input2 = operate.valueInputs.first { it.name == "Input2" }.connection.id

        val input1Hit = HitTest.hitTest(layout, layout.anchorPoint(input1))
        val input2Hit = HitTest.hitTest(layout, layout.anchorPoint(input2))
        val reversedAnchors = layout.copy(connectionAnchors = layout.connectionAnchors.reversed())

        assertEquals(HitResult.ConnectionHit(input1), input1Hit)
        assertEquals(HitResult.ConnectionHit(input2), input2Hit)
        assertEquals(HitResult.ConnectionHit(input1), HitTest.hitTest(reversedAnchors, layout.anchorPoint(input1)))
        assertEquals(HitResult.ConnectionHit(input2), HitTest.hitTest(reversedAnchors, layout.anchorPoint(input2)))
        assertEquals(operateId, layout.connectionAnchors.first { it.connectionId == input1 }.ownerBlockId)
        assertEquals(operateId, layout.connectionAnchors.first { it.connectionId == input2 }.ownerBlockId)
    }

    @Test
    fun compareInputAndOutputHitsRemainDistinctOnSameBlock() {
        val compareId = BlockId("compare")
        val compare = CompareRegistry.getDefinition(COMPARE_TYPE)!!
            .createNode(compareId)
            .withRootOffset(20f, 30f)
        val document = WorkspaceDocument(
            id = "compare-hit",
            blocks = mapOf(compareId to compare),
            rootBlocks = listOf(compareId),
        )
        val layout = LayoutEngine(CompareRegistry).build(document).flatIndex
        val left = compare.valueInputs.first { it.name == "LEFT" }.connection.id
        val right = compare.valueInputs.first { it.name == "RIGHT" }.connection.id
        val output = compare.output!!.id

        assertEquals(HitResult.ConnectionHit(left), HitTest.hitTest(layout, layout.anchorPoint(left)))
        assertEquals(HitResult.ConnectionHit(right), HitTest.hitTest(layout, layout.anchorPoint(right)))
        assertEquals(HitResult.ConnectionHit(output), HitTest.hitTest(layout, layout.anchorPoint(output)))
        assertNotEquals(left, output)
        assertNotEquals(right, output)
    }

    @Test
    fun overlappingConnectionHitsPreferNearestCenterThenStableId() {
        val owner = BlockId("owner")
        val left = ConnectionId("left")
        val right = ConnectionId("right")
        val layout = connectionOnlyLayout(
            HitPrimitive("right-hit", owner, HitKind.ConnectionAnchor, Rect(6f, 0f, 20f, 20f), 0, connectionId = right),
            HitPrimitive("left-hit", owner, HitKind.ConnectionAnchor, Rect(0f, 0f, 20f, 20f), 0, connectionId = left),
        )

        assertEquals(HitResult.ConnectionHit(right), HitTest.hitTest(layout, Offset2(16f, 10f)))
        assertEquals(HitResult.ConnectionHit(left), HitTest.hitTest(layout, Offset2(10f, 10f)))
    }

    @Test
    fun workspaceOffsetConnectionHitUsesWorkspaceAnchorPosition() {
        val operateId = BlockId("offset-operate")
        val operate = DefaultBlockRegistry.getDefinition(BlockTypes.LOGIC_OPERATE)!!
            .createNode(operateId)
            .withRootOffset(120f, 80f)
        val document = WorkspaceDocument(
            id = "offset-hit",
            blocks = mapOf(operateId to operate),
            rootBlocks = listOf(operateId),
        )
        val layout = engine.build(document).flatIndex
        val input2 = operate.valueInputs.first { it.name == "Input2" }.connection.id
        val input2Anchor = layout.connectionAnchors.first { it.connectionId == input2 }

        assertTrue(input2Anchor.x > 120f)
        assertTrue(input2Anchor.y > 80f)
        assertEquals(HitResult.ConnectionHit(input2), HitTest.hitTest(layout, Offset2(input2Anchor.x, input2Anchor.y)))
    }

    @Test
    fun recomputedLayoutDropsStaleConnectionHitGeometry() {
        val parentId = BlockId("parent")
        val childId = BlockId("child")
        var parent = DefaultBlockRegistry.getDefinition(BlockTypes.LOGIC_OPERATE)!!
            .createNode(parentId)
            .withRootOffset(0f, 0f)
        val child = DefaultBlockRegistry.getDefinition(BlockTypes.LOGIC_OPERATE)!!
            .createNode(childId)
        val input1 = parent.valueInputs.first { it.name == "Input1" }.connection
        val input2 = parent.valueInputs.first { it.name == "Input2" }.connection.id
        val initial = WorkspaceDocument(
            id = "layout-change-hit",
            blocks = mapOf(parentId to parent),
            rootBlocks = listOf(parentId),
        )
        val initialLayout = engine.build(initial).flatIndex
        val oldInput2Point = initialLayout.anchorPoint(input2)
        parent = parent.copy(
            valueInputs = parent.valueInputs.map { input ->
                if (input.name == "Input1") {
                    input.copy(connection = input1.copy(connectedTo = child.output!!.id))
                } else {
                    input
                }
            },
        )
        val connectedChild = child.copy(output = child.output!!.copy(connectedTo = input1.id))
        val changed = WorkspaceDocument(
            id = "layout-change-hit",
            blocks = mapOf(parentId to parent, childId to connectedChild),
            rootBlocks = listOf(parentId, childId),
        )
        val changedLayout = engine.build(changed).flatIndex
        val newInput2Point = changedLayout.anchorPoint(input2)

        assertTrue(newInput2Point.x > oldInput2Point.x)
        assertEquals(HitResult.ConnectionHit(input2), HitTest.hitTest(changedLayout, newInput2Point))
        assertNotEquals(HitResult.ConnectionHit(input2), HitTest.hitTest(changedLayout, oldInput2Point))
    }

    @Test
    fun disconnectUsesExactlyHitConnection() {
        val operateId = BlockId("operate")
        val varAId = BlockId("varA")
        val varBId = BlockId("varB")
        var operate = DefaultBlockRegistry.getDefinition(BlockTypes.LOGIC_OPERATE)!!
            .createNode(operateId)
            .withRootOffset(0f, 0f)
        val varA = variableReporter(varAId, "a")
        val varB = variableReporter(varBId, "b")
        val input1 = operate.valueInputs.first { it.name == "Input1" }.connection
        val input2 = operate.valueInputs.first { it.name == "Input2" }.connection
        operate = operate.copy(
            valueInputs = operate.valueInputs.map { input ->
                when (input.name) {
                    "Input1" -> input.copy(connection = input1.copy(connectedTo = varA.output!!.id))
                    "Input2" -> input.copy(connection = input2.copy(connectedTo = varB.output!!.id))
                    else -> input
                }
            },
        )
        val document = WorkspaceDocument(
            id = "disconnect-hit",
            blocks = mapOf(
                operateId to operate,
                varAId to varA.copy(output = varA.output!!.copy(connectedTo = input1.id)),
                varBId to varB.copy(output = varB.output!!.copy(connectedTo = input2.id)),
            ),
            rootBlocks = listOf(operateId, varAId, varBId),
        )
        val layout = engine.build(document).flatIndex
        val hit = HitTest.hitTest(layout, layout.anchorPoint(varB.output!!.id))
        assertEquals(HitResult.ConnectionHit(varB.output!!.id), hit)

        val disconnected = WorkspaceReducer.reduce(document, WorkspaceAction.Disconnect((hit as HitResult.ConnectionHit).connectionId))

        assertEquals(varA.output!!.id, disconnected.blocks[operateId]!!.valueInputs.first { it.name == "Input1" }.connection.connectedTo)
        assertEquals(null, disconnected.blocks[operateId]!!.valueInputs.first { it.name == "Input2" }.connection.connectedTo)
    }

    private fun FlatLayoutIndex.anchorPoint(connectionId: ConnectionId): Offset2 {
        val anchor = connectionAnchors.first { it.connectionId == connectionId }
        return Offset2(anchor.x, anchor.y)
    }

    private fun connectionOnlyLayout(vararg hits: HitPrimitive): FlatLayoutIndex {
        val hitIndex = SpatialIndex<HitPrimitive>()
        hits.forEach { hitIndex.insert(it, it.bounds) }
        return FlatLayoutIndex(
            visibleBlocks = emptyList(),
            hitPrimitives = hits.toList(),
            connectionAnchors = emptyList(),
            statementSlots = emptyList(),
            branchSections = emptyList(),
            hitIndex = hitIndex,
            anchorIndex = SpatialIndex<ConnectionAnchor>(),
        )
    }

    private fun variableReporter(id: BlockId, name: String): BlockNode =
        DefaultBlockRegistry.getDefinition(BlockTypes.VARIABLE_GET)!!
            .createNode(id)
            .copy(fields = mapOf("variable" to FieldValue.Text(name)))

    private object CompareRegistry : BlockRegistry {
        private val definition = BlockDefinition(
            id = COMPARE_TYPE,
            label = "Compare",
            category = "logic",
            hasPrevious = false,
            hasNext = false,
            outputType = "Boolean",
            isReporter = true,
            inputsInline = true,
            valueInputs = listOf(
                ValueInputDefinition("LEFT", "left", setOf("Any", "Number")),
                ValueInputDefinition("RIGHT", "right", setOf("Any", "Number")),
            ),
        )

        override fun getDefinition(id: String): BlockDefinition? =
            definition.takeIf { it.id == id } ?: DefaultBlockRegistry.getDefinition(id)

        override fun allDefinitions(): List<BlockDefinition> =
            DefaultBlockRegistry.allDefinitions() + definition
    }

    private companion object {
        const val COMPARE_TYPE = "logic.compare"
    }
}
