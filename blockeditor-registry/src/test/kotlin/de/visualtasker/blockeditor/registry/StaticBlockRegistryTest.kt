package de.visualtasker.blockeditor.registry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class StaticBlockRegistryTest {
    private fun definition(id: String, visible: Boolean = true) = BlockDefinition(
        id = id,
        label = id,
        category = "test",
        hasPrevious = true,
        hasNext = true,
        paletteVisible = visible,
    )

    @Test fun preservesStableOrderAndReturnsDefensiveLists() {
        val registry = StaticBlockRegistry(listOf(definition("b"), definition("a")))
        assertEquals(listOf("b", "a"), registry.allDefinitions().map(BlockDefinition::id))
        assertEquals(listOf("b", "a"), registry.allDefinitions().map(BlockDefinition::id))
    }

    @Test fun rejectsDuplicateIds() {
        assertThrows(IllegalArgumentException::class.java) {
            StaticBlockRegistry(listOf(definition("same"), definition("same")))
        }
    }

    @Test fun hiddenDefinitionRemainsLoadable() {
        val registry = StaticBlockRegistry(listOf(definition("hidden", visible = false)))
        assertNotNull(registry.getDefinition("hidden"))
        assertNotNull(registry.getDefinition("hidden")!!.createNode(de.visualtasker.blockeditor.domain.BlockId("block")))
    }

    @Test fun emscriptCategoryIsVisibleBetweenActionAndControl() {
        assertEquals(
            listOf(BlockCategories.ACTION, BlockCategories.FEEDBACK, BlockCategories.EMSCRIPT, BlockCategories.INPUT, BlockCategories.PERCEPTION, BlockCategories.CONTROL),
            BlockCategories.all.map { it.id }
                .filter {
                    it in setOf(
                        BlockCategories.ACTION,
                        BlockCategories.FEEDBACK,
                        BlockCategories.EMSCRIPT,
                        BlockCategories.INPUT,
                        BlockCategories.PERCEPTION,
                        BlockCategories.CONTROL,
                    )
                },
        )
        assertEquals("EMScript", BlockCategories.metaFor(BlockCategories.EMSCRIPT).label)
    }

    @Test fun commandReferenceCategoriesHaveStableMetadata() {
        val expected = listOf(
            BlockCategories.INPUT to "Input",
            BlockCategories.FEEDBACK to "Feedback",
            BlockCategories.PERCEPTION to "Perception",
            BlockCategories.VISION to "Vision",
            BlockCategories.TEXT to "Text",
            BlockCategories.FILE to "File",
            BlockCategories.SYSTEM to "System",
            BlockCategories.CHROME_TAB to "ChromeTab",
            BlockCategories.TASKER to "Tasker",
            BlockCategories.SHIZUKU to "Shizuku",
            BlockCategories.TERMUX to "Termux",
            BlockCategories.SCRCPY to "scrcpy",
            BlockCategories.CHARTS to "Charts",
            BlockCategories.LOGIC to "Logic",
            BlockCategories.VARIABLES to "Variables",
            BlockCategories.FLOW to "Flow",
            BlockCategories.RUNTIME to "Runtime",
        )

        expected.forEach { (id, label) ->
            val meta = BlockCategories.metaFor(id)
            assertEquals(label, meta.label)
            assertNotNull(meta.accentArgb)
        }
    }

    @Test fun generatedCommandCatalogBlocksAreAvailableInDefaultRegistry() {
        val generatedEntries = VisualTaskerCommandCatalog.allEntries()
            .filter { it.block?.blockType?.startsWith(BlockTypes.EMSCRIPT_COMMAND_PREFIX) == true }
        assertNotNull(generatedEntries.firstOrNull { it.canonicalName == "Tasker.runTask" })
        assertNotNull(generatedEntries.firstOrNull { it.canonicalName == "Termux.shell" })
        assertNotNull(generatedEntries.firstOrNull { it.canonicalName == "Shizuku.exec" })
        assertNotNull(generatedEntries.firstOrNull { it.canonicalName == "Scrcpy.start" })
        assertNotNull(generatedEntries.firstOrNull { it.canonicalName == "Chart.create" })

        generatedEntries.forEach { entry ->
            val blockType = entry.block!!.blockType
            val definition = DefaultBlockRegistry.getDefinition(blockType)
            assertNotNull(definition)
            assertEquals(entry.canonicalName, definition!!.fields.first { it.key == "command" }.defaultValue)
        }
    }

    @Test fun validatesChoiceOptionsAndDefaults() {
        val valid = FieldDefinition(
            key = "target",
            label = "Target",
            kind = FieldKind.CHOICE,
            defaultValue = "SYSTEM",
            options = listOf(FieldOption("SYSTEM", "System"), FieldOption("CHROME", "Chrome")),
        )
        assertEquals("SYSTEM", valid.defaultValue)
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(options = emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(options = listOf(FieldOption("SYSTEM", "A"), FieldOption("SYSTEM", "B")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(defaultValue = "FIREFOX")
        }
    }
}
