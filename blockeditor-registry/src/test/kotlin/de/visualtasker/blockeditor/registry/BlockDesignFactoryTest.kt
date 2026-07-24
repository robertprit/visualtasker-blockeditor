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
            listOf("image", "threshold", "timeout", "retry", "region"),
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
            infoFields = listOf(
                BlockDesignFieldBlueprint(
                    name = "imagePath",
                    defaultValue = "/sdcard/screenshots/very-long-private-path.png",
                    fieldType = BlockDesignFieldType.FILE_PATH,
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
            inputs = withRenamedType.inputs + BlockDesignInputDefinition(
                kind = BlockDesignInputKind.VALUE,
                name = "debug",
                connectionType = "Bool",
            ),
        )
        val withField = withInput.copy(
            infoFields = withInput.infoFields + BlockDesignFieldBlueprint(
                name = "debugFlag",
                fieldType = BlockDesignFieldType.SWITCH,
                valueType = BlockDesignValueType.BOOL,
            ),
        )
        var state = BlockDesignHistoryState(baseline)
            .record(withRenamedType)
            .record(withInput)
            .record(withField)

        assertEquals("debugFlag", state.present.infoFields.last().name)
        state = state.undo()!!
        assertEquals("debug", state.present.inputs.last().name)
        state = state.undo()!!
        assertEquals("vision.findTemplate.v2", state.present.type)
        state = state.redo()!!
        assertEquals("debug", state.present.inputs.last().name)
        state = state.redo()!!
        assertEquals("debugFlag", state.present.infoFields.last().name)
    }
}
