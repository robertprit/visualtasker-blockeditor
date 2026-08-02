package de.visualtasker.blockeditor.layout

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.ConnectionKind
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.withRootOffset
import de.visualtasker.blockeditor.registry.BlockDefinition
import de.visualtasker.blockeditor.registry.BlockRegistry
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry
import de.visualtasker.blockeditor.registry.ValueInputDefinition
import de.visualtasker.blockeditor.registry.createNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutEngineInlineOperateTest {
    private val engine = LayoutEngine(DefaultBlockRegistry)

    @Test
    fun operateInlineReporter_placesInputsInsideBlockWithLeftOutput() {
        val operateId = BlockId("operate")
        val operate = DefaultBlockRegistry.getDefinition(BlockTypes.LOGIC_OPERATE)!!.createNode(operateId)
            .withRootOffset(0f, 0f)

        val document = WorkspaceDocument(
            id = "inline-operate",
            blocks = mapOf(operateId to operate),
            rootBlocks = listOf(operateId),
        )

        val cache = engine.build(document)
        val layout = cache.flatIndex.visibleBlocks.single { it.blockId == operateId }
        val inline = cache.flatIndex.inlineReporterLayouts.single()

        assertTrue(
            "Inline operate should be wider than a single reporter",
            layout.bounds.width > LayoutConstants.REPORTER_WIDTH * 2,
        )
        assertTrue(inline.leftSlot.x < inline.operatorBounds.x)
        assertTrue(inline.operatorBounds.x < inline.rightSlot.x)

        val outputAnchor = cache.flatIndex.connectionAnchors
            .single { it.ownerBlockId == operateId && it.kind == ConnectionKind.Output }
        assertTrue(outputAnchor.x <= layout.bounds.x + LayoutConstants.INLINE_OUTPUT_TAB)

        val inputHits = cache.flatIndex.hitPrimitives
            .filter { it.blockId == operateId && it.kind == HitKind.ValueInput }
            .map { it.inputName }
            .toSet()
        assertEquals(setOf("Input1", "Input2"), inputHits)
    }

    @Test
    fun inlineReporter_usesActualValueInputNamesForLeftAndRightSlots() {
        val registry = SingleBlockRegistry(
            BlockDefinition(
                id = "custom.add",
                label = "+",
                category = "logic",
                hasPrevious = false,
                hasNext = false,
                outputType = "Number",
                valueInputs = listOf(
                    ValueInputDefinition("left", "left", setOf("Number")),
                    ValueInputDefinition("right", "right", setOf("Number")),
                ),
                isReporter = true,
                inputsInline = true,
            ),
        )
        val engine = LayoutEngine(registry)
        val addId = BlockId("add")
        val add = registry.getDefinition("custom.add")!!.createNode(addId).withRootOffset(0f, 0f)
        val document = WorkspaceDocument(
            id = "inline-left-right",
            blocks = mapOf(addId to add),
            rootBlocks = listOf(addId),
        )

        val cache = engine.build(document)
        val inline = cache.flatIndex.inlineReporterLayouts.single()
        val inputHits = cache.flatIndex.hitPrimitives
            .filter { it.blockId == addId && it.kind == HitKind.ValueInput }
            .associateBy { it.inputName }
        val anchors = cache.flatIndex.connectionAnchors
            .filter { it.ownerBlockId == addId && it.kind == ConnectionKind.ValueInput }
            .associateBy { it.connectionId }
        val left = add.valueInputs.first { it.name == "left" }
        val right = add.valueInputs.first { it.name == "right" }

        assertEquals("left", inline.leftInputName)
        assertEquals("right", inline.rightInputName)
        assertEquals(setOf("left", "right"), inputHits.keys)
        assertEquals(inputHits.getValue("left").bounds.x, anchors.getValue(left.connection.id).x, 0.001f)
        assertEquals(inputHits.getValue("right").bounds.x, anchors.getValue(right.connection.id).x, 0.001f)
    }

    private class SingleBlockRegistry(
        private val definition: BlockDefinition,
    ) : BlockRegistry {
        override fun getDefinition(id: String): BlockDefinition? =
            definition.takeIf { it.id == id }

        override fun allDefinitions(): List<BlockDefinition> = listOf(definition)
    }
}
