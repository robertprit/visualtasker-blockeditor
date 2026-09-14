package de.visualtasker.blockeditor.registry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockDesignFactoryTest {
    @Test
    fun create_registersCustomIdPrefix() {
        val definition = BlockDesignFactory.create(
            BlockDesignBlueprint(label = "My Step", category = BlockCategories.ACTION),
        )
        assertTrue(definition.id.startsWith(BlockTypes.CUSTOM_PREFIX))
        assertEquals("My Step", definition.label)
        assertEquals(BlockCategories.ACTION, definition.category)
    }

    @Test
    fun compositeRegistry_includesCustomDefinitions() {
        val registry = CompositeBlockRegistry()
        val custom = BlockDesignFactory.quickStatementBlock("Ping", BlockCategories.DEBUG)
        registry.register(custom)

        assertNotNull(registry.getDefinition(custom.id))
        assertTrue(registry.definitionsByCategory(BlockCategories.DEBUG).any { it.id == custom.id })
    }

    @Test
    fun blockDesignDefinitionSerializesAndDeserializesStably() {
        val original = BlockDesignFactory.findTemplateBlueprint()

        val json = BlockDesignFactory.toJson(original)
        val restored = BlockDesignFactory.fromJson(json)

        assertEquals(original, restored)
        assertEquals(json, BlockDesignFactory.toJson(restored))
    }

    @Test
    fun customConnectionTypesArePartOfBlueprint() {
        val blueprint = BlockDesignBlueprint(
            type = "vision.scanNode",
            label = "SCAN_NODE",
            customConnectionTypes = listOf(
                CustomConnectionTypeDefinition("AccessibilityNode"),
                CustomConnectionTypeDefinition("UiElementRecord"),
            ),
        )

        val restored = BlockDesignFactory.fromJson(BlockDesignFactory.toJson(blueprint))

        assertEquals(listOf("AccessibilityNode", "UiElementRecord"), restored.customConnectionTypes.map { it.name })
    }

    @Test
    fun findTemplateBlueprintCreatesExpectedBlockDefinition() {
        val blueprint = BlockDesignFactory.findTemplateBlueprint()
        val definition = BlockDesignFactory.create(blueprint)

        assertEquals("vision.findTemplate", definition.id)
        assertEquals("FIND_TEMPLATE", definition.label)
        assertEquals("Vision", definition.category)
        assertEquals(true, definition.hasPrevious)
        assertEquals(true, definition.hasNext)
        assertEquals(
            listOf("image", "thresholdInput", "timeoutInput", "retryInput", "regionInput"),
            definition.valueInputs.map { it.name },
        )
        assertEquals(
            listOf("imagePath", "threshold", "timeoutMs", "retryCount", "searchRegion", "regionSource"),
            definition.fields.map { it.key },
        )
        assertEquals(FieldKind.IMAGE_TEMPLATE, definition.fields.single { it.key == "imagePath" }.kind)
        assertEquals(FieldKind.THRESHOLD, definition.fields.single { it.key == "threshold" }.kind)
        assertEquals(FieldKind.REGION, definition.fields.single { it.key == "searchRegion" }.kind)
    }

    @Test
    fun previewUsesParameterNamesButNotConcreteValues() {
        val blueprint = BlockDesignFactory.findTemplateBlueprint().copy(
            elements = listOf(
                BlockDesignElement.field(
                    BlockDesignFieldBlueprint(
                        name = "imagePath",
                        defaultValue = "/sdcard/screenshots/very-long-private-path.png",
                        fieldType = BlockDesignFieldType.FILE_PATH,
                    ),
                ),
            ),
        )

        val preview = BlockDesignFactory.previewLabel(blueprint)

        assertTrue(preview.contains("FIND_TEMPLATE imagePath"))
        assertFalse(preview.contains("/sdcard"))
    }

    @Test
    fun generatorPreviewIsSeparatedFromShape() {
        val blueprint = BlockDesignFactory.findTemplateBlueprint()

        assertTrue(BlockDesignFactory.previewLabel(blueprint).contains("FIND_TEMPLATE imagePath"))
        assertTrue(BlockDesignFactory.generatorPreview(blueprint).contains("threshold=${'$'}{threshold}"))
        assertFalse(BlockDesignFactory.previewLabel(blueprint).contains("${'$'}{threshold}"))
    }

    @Test
    fun historySupportsUndoAndRedoForInputAndFieldChanges() {
        val baseline = BlockDesignFactory.findTemplateBlueprint()
        val withRenamedType = baseline.copy(type = "vision.findTemplate.v2")
        val withInput = withRenamedType.copy(
            elements = withRenamedType.elements + BlockDesignElement.input(
                BlockDesignInputDefinition(
                    kind = BlockDesignInputKind.VALUE,
                    name = "debug",
                    connectionType = "Bool",
                ),
            ),
        )
        val withField = withInput.copy(
            elements = withInput.elements + BlockDesignElement.field(
                BlockDesignFieldBlueprint(
                    name = "debugFlag",
                    fieldType = BlockDesignFieldType.SWITCH,
                    valueType = BlockDesignValueType.BOOL,
                ),
            ),
        )
        var state = BlockDesignHistoryState(baseline)
            .record(withRenamedType)
            .record(withInput)
            .record(withField)

        assertEquals("debugFlag", state.present.elements.last().field?.name)
        state = state.undo()!!
        assertEquals("debug", state.present.elements.last().input?.name)
        state = state.undo()!!
        assertEquals("vision.findTemplate.v2", state.present.type)
        state = state.redo()!!
        assertEquals("debug", state.present.elements.last().input?.name)
        state = state.redo()!!
        assertEquals("debugFlag", state.present.elements.last().field?.name)
    }

    @Test
    fun endRowInputsIncreaseFactoryLayoutRowsWithoutCreatingConnection() {
        val blueprint = BlockDesignBlueprint(
            type = "user.rows",
            label = "Rows",
            elements = listOf(
                BlockDesignElement.input(BlockDesignInputDefinition(BlockDesignInputKind.VALUE, "first", connectionType = "String")),
                BlockDesignElement.input(BlockDesignInputDefinition(BlockDesignInputKind.END_ROW, "row1")),
                BlockDesignElement.input(BlockDesignInputDefinition(BlockDesignInputKind.VALUE, "second", connectionType = "Number")),
                BlockDesignElement.field(BlockDesignFieldBlueprint("field1", fieldType = BlockDesignFieldType.SWITCH)),
                BlockDesignElement.input(BlockDesignInputDefinition(BlockDesignInputKind.STATEMENT, "BODY")),
            ),
        )

        val definition = BlockDesignFactory.create(blueprint)

        assertEquals(listOf("first", "second"), definition.valueInputs.map { it.name })
        assertEquals(listOf("BODY"), definition.statementInputs.map { it.name })
        assertEquals("true", definition.metadata["custom.layout.designer"])
        assertEquals("3", definition.metadata["custom.layout.rowCount"])
        assertEquals("2", definition.metadata["custom.layout.maxRowColumns"])
        assertEquals("0", definition.metadata["custom.layout.input.first.row"])
        assertEquals("1", definition.metadata["custom.layout.input.second.row"])
        assertEquals("2", definition.metadata["custom.layout.input.BODY.row"])
        assertEquals("1", definition.metadata["custom.layout.field.field1.row"])
        assertEquals("1", definition.metadata["custom.layout.field.field1.column"])
    }

    @Test
    fun repeatedEndRowsBeforeStatementDoNotCreateEmptyRows() {
        val blueprint = BlockDesignBlueprint(
            type = "user.compactStatement",
            label = "Compact Statement",
            elements = listOf(
                BlockDesignElement.input(BlockDesignInputDefinition(BlockDesignInputKind.VALUE, "first")),
                BlockDesignElement.input(BlockDesignInputDefinition(BlockDesignInputKind.END_ROW, "row1")),
                BlockDesignElement.input(BlockDesignInputDefinition(BlockDesignInputKind.END_ROW, "row2")),
                BlockDesignElement.input(BlockDesignInputDefinition(BlockDesignInputKind.END_ROW, "row3")),
                BlockDesignElement.input(BlockDesignInputDefinition(BlockDesignInputKind.STATEMENT, "body1")),
            ),
        )

        val definition = BlockDesignFactory.create(blueprint)

        assertEquals("2", definition.metadata["custom.layout.rowCount"])
        assertEquals("0", definition.metadata["custom.layout.input.first.row"])
        assertEquals("1", definition.metadata["custom.layout.input.body1.row"])
    }
}
