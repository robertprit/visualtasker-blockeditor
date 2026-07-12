package de.visualtasker.blockeditor.registry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
}
