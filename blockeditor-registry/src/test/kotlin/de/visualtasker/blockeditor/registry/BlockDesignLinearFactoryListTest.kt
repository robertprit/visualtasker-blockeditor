package de.visualtasker.blockeditor.registry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockDesignLinearFactoryListTest {
    @Test
    fun addAndDeleteLinearElementsWorks() {
        val initial = BlockDesignBlueprint(type = "test.linear", label = "Linear", category = BlockCategories.CUSTOM)
        val withField = initial.addElement(
            BlockDesignElement.field(
                BlockDesignFieldBlueprint(name = "label1", fieldType = BlockDesignFieldType.TEXT),
            ),
        )
        val withValue = withField.addElement(
            BlockDesignElement.input(
                BlockDesignInputDefinition(kind = BlockDesignInputKind.VALUE, name = "VALUE", connectionType = "Number"),
            ),
        )
        val endRow = withValue.addElement(
            BlockDesignElement.input(
                BlockDesignInputDefinition(kind = BlockDesignInputKind.END_ROW, name = "ROW_1"),
            ),
        )
        val statement = endRow.addElement(
            BlockDesignElement.input(
                BlockDesignInputDefinition(kind = BlockDesignInputKind.STATEMENT, name = "THEN"),
            ),
        )

        assertEquals(4, statement.elements.size)
        val removed = statement.removeElement(statement.elements[1].elementId)
        assertEquals(3, removed.elements.size)
    }

    @Test
    fun reorderKeepsIdentityButChangesOrder() {
        val blueprint = seededBlueprint()
        val firstId = blueprint.elements.first().elementId
        val reordered = blueprint.reorderElement(firstId, 2)

        assertEquals(firstId, reordered.elements[2].elementId)
        assertEquals(blueprint.elements.map { it.elementId }.toSet(), reordered.elements.map { it.elementId }.toSet())
    }

    @Test
    fun duplicateCreatesNewStableElementId() {
        val blueprint = seededBlueprint()
        val source = blueprint.elements.first()
        val duplicated = blueprint.duplicateElement(source.elementId)
        val clones = duplicated.elements.filter { it.kind == source.kind && it.field?.fieldType == source.field?.fieldType }

        assertTrue(duplicated.elements.size == blueprint.elements.size + 1)
        assertTrue(clones.map { it.elementId }.distinct().size == clones.size)
        assertNotEquals(source.elementId, clones.last().elementId)
    }

    @Test
    fun reorderDoesNotChangeElementTypeOrConnectorCheck() {
        val blueprint = BlockDesignBlueprint(type = "logic.compare", label = "COMPARE", category = BlockCategories.LOGIC)
            .addElement(
                BlockDesignElement.input(
                    BlockDesignInputDefinition(kind = BlockDesignInputKind.VALUE, name = "LEFT", connectionType = "Number"),
                ),
            )
            .addElement(
                BlockDesignElement.input(
                    BlockDesignInputDefinition(kind = BlockDesignInputKind.VALUE, name = "RIGHT", connectionType = "Number"),
                ),
            )
        val leftId = blueprint.elements.first().elementId
        val reordered = blueprint.reorderElement(leftId, 1)
        val moved = reordered.elements.single { it.elementId == leftId }.input!!

        assertEquals(BlockDesignInputKind.VALUE, moved.kind)
        assertEquals("Number", moved.connectionType)
    }

    @Test
    fun semanticPropertyRefsStayStableAfterReorder() {
        val blueprint = BlockDesignBlueprint(
            type = "logic.compare",
            label = "COMPARE",
            category = BlockCategories.LOGIC,
            valueInputs = listOf(
                ValueInputDefinition("LEFT", "left", setOf("Number")),
                ValueInputDefinition("RIGHT", "right", setOf("Number")),
            ),
        )
        val before = BlockNodeDesignerProjector.project(blueprint).nodeProjection.valueInputPorts.associateBy { it.name }
        val reordered = blueprint
            .addElement(
                BlockDesignElement.input(
                    BlockDesignInputDefinition(kind = BlockDesignInputKind.VALUE, name = "LEFT", connectionType = "Number"),
                ),
            )
            .addElement(
                BlockDesignElement.input(
                    BlockDesignInputDefinition(kind = BlockDesignInputKind.VALUE, name = "RIGHT", connectionType = "Number"),
                ),
            )
            .let {
                it.reorderElement(it.elements.first().elementId, 1)
            }
        val after = BlockNodeDesignerProjector.project(reordered).nodeProjection.valueInputPorts.associateBy { it.name }

        assertEquals(before["LEFT"]?.semanticPropertyId, after["LEFT"]?.semanticPropertyId)
        assertEquals(before["RIGHT"]?.semanticPropertyId, after["RIGHT"]?.semanticPropertyId)
    }

    @Test
    fun endRowHasNoRuntimePortSemantics() {
        val blueprint = BlockDesignBlueprint(type = "action.wait", label = "WAIT", category = BlockCategories.ACTION)
            .addElement(BlockDesignElement.input(BlockDesignInputDefinition(kind = BlockDesignInputKind.END_ROW, name = "R1")))
        val projection = BlockNodeDesignerProjector.project(blueprint)

        assertTrue(projection.nodeProjection.valueInputPorts.isEmpty())
        assertTrue(projection.nodeProjection.structurePorts.isEmpty())
    }

    @Test
    fun historyUndoRedoCoversReorderAndAddRemove() {
        val base = seededBlueprint()
        val added = base.addElement(BlockDesignElement.input(BlockDesignInputDefinition(kind = BlockDesignInputKind.END_ROW, name = "ROW_2")))
        val reordered = added.reorderElement(added.elements.first().elementId, added.elements.lastIndex)
        val removed = reordered.removeElement(reordered.elements.first().elementId)
        var history = BlockDesignHistoryState(base)
            .record(added)
            .record(reordered)
            .record(removed)

        history = history.undo()!!
        assertEquals(reordered.elements.map { it.elementId }, history.present.elements.map { it.elementId })
        history = history.undo()!!
        assertEquals(added.elements.map { it.elementId }, history.present.elements.map { it.elementId })
        history = history.redo()!!
        assertEquals(reordered.elements.map { it.elementId }, history.present.elements.map { it.elementId })
    }

    private fun seededBlueprint(): BlockDesignBlueprint =
        BlockDesignBlueprint(type = "test.seed", label = "Seed", category = BlockCategories.CUSTOM)
            .addElement(BlockDesignElement.field(BlockDesignFieldBlueprint(name = "label1", fieldType = BlockDesignFieldType.TEXT)))
            .addElement(BlockDesignElement.input(BlockDesignInputDefinition(kind = BlockDesignInputKind.VALUE, name = "VALUE", connectionType = "Any")))
            .addElement(BlockDesignElement.input(BlockDesignInputDefinition(kind = BlockDesignInputKind.STATEMENT, name = "THEN")))
}
