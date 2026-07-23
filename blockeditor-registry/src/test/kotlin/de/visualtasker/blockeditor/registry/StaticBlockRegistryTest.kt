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
            listOf(BlockCategories.ACTION, BlockCategories.EMSCRIPT, BlockCategories.CONTROL),
            BlockCategories.all.map { it.id }
                .filter { it in setOf(BlockCategories.ACTION, BlockCategories.EMSCRIPT, BlockCategories.CONTROL) },
        )
        assertEquals("EMScript", BlockCategories.metaFor(BlockCategories.EMSCRIPT).label)
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
