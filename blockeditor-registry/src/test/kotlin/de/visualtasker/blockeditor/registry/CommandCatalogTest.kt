package de.visualtasker.blockeditor.registry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandCatalogTest {
    @Test
    fun catalogCoversExistingBuiltinBlockDefinitions() {
        val catalogBlockTypes = VisualTaskerCommandCatalog.blockTypes()
        val visibleBuiltinBlockTypes = DefaultBlockRegistry.allDefinitions()
            .filter { it.paletteVisible }
            .map { it.id }
            .filterNot { it == BlockTypes.VARIABLE_REPORTER }
            .toSet()

        assertEquals(visibleBuiltinBlockTypes, catalogBlockTypes)
    }

    @Test
    fun basicCommandsHaveCanonicalNamesAndLegacyAliases() {
        assertCatalogEntry(
            blockType = BlockTypes.ACTION_WAIT,
            canonicalName = "wait",
            alias = "WAIT",
            capability = CommandCapability.TIMING,
        )
        assertCatalogEntry(
            blockType = BlockTypes.ACTION_CLICK_TEXT,
            canonicalName = "click",
            alias = "CLICK",
            capability = CommandCapability.A11Y,
        )
        assertCatalogEntry(
            blockType = BlockTypes.FEEDBACK_BEEP,
            canonicalName = "beep",
            alias = "BEEP",
            capability = CommandCapability.FEEDBACK,
        )
        assertCatalogEntry(
            blockType = BlockTypes.FEEDBACK_VIBRATE,
            canonicalName = "vibrate",
            alias = "VIBRATE",
            capability = CommandCapability.FEEDBACK,
        )
    }

    @Test
    fun variablesControlFlowAndOperatorsAreCataloguedForIrMigration() {
        val set = VisualTaskerCommandCatalog.findByBlockType(BlockTypes.VARIABLE_SET)!!
        assertEquals("set", set.canonicalName)
        assertEquals(CommandSideEffect.VARIABLE_WRITE, set.sideEffect)
        assertEquals(listOf("variable", "value"), set.arguments.map { it.name })

        val ifElseIfElse = VisualTaskerCommandCatalog.findByBlockType(BlockTypes.CONTROL_IF_ELSEIF_ELSE)!!
        assertEquals(CommandCatalogKind.CONTROL, ifElseIfElse.kind)
        assertTrue(ifElseIfElse.arguments.any { it.name == BlockTypes.SLOT_ELIF })
        assertTrue(ifElseIfElse.acceptedAliases.contains("ELSEIF"))

        val compare = VisualTaskerCommandCatalog.findByBlockType(BlockTypes.LOGIC_COMPARE)!!
        assertEquals(CommandCatalogKind.OPERATOR, compare.kind)
        assertEquals("Boolean", compare.returnType)
        assertTrue(compare.acceptedAliases.contains(">="))
    }

    @Test
    fun catalogLookupSupportsCanonicalNamesAndBlockTypes() {
        assertEquals(
            BlockTypes.ACTION_WAIT,
            VisualTaskerCommandCatalog.findByAcceptedName("WAIT")?.block?.blockType,
        )
        assertEquals(
            BlockTypes.ACTION_CLICK_TEXT,
            VisualTaskerCommandCatalog.findByCanonicalName("click")?.block?.blockType,
        )
        assertNotNull(VisualTaskerCommandCatalog.findById("logic.compare"))
    }

    @Test
    fun defaultBlockDefinitionsExposeCatalogMetadata() {
        val wait = DefaultBlockRegistry.getDefinition(BlockTypes.ACTION_WAIT)!!
        assertEquals("action.wait", wait.metadata[VisualTaskerCommandCatalog.METADATA_COMMAND_ID])
        assertEquals("wait", wait.metadata[VisualTaskerCommandCatalog.METADATA_CANONICAL_NAME])
        assertEquals(CommandCatalogKind.STATEMENT.name, wait.metadata[VisualTaskerCommandCatalog.METADATA_COMMAND_KIND])
        assertTrue(wait.metadata[VisualTaskerCommandCatalog.METADATA_RUNTIME_CAPABILITIES]!!.contains(CommandCapability.TIMING.name))

        val compare = DefaultBlockRegistry.getDefinition(BlockTypes.LOGIC_COMPARE)!!
        assertEquals("logic.compare", compare.metadata[VisualTaskerCommandCatalog.METADATA_COMMAND_ID])
        assertEquals(CommandCatalogKind.OPERATOR.name, compare.metadata[VisualTaskerCommandCatalog.METADATA_COMMAND_KIND])
    }

    @Test
    fun catalogMetadataHelperReturnsStableBlockMetadata() {
        val metadata = VisualTaskerCommandCatalog.metadataForBlockType(BlockTypes.FEEDBACK_VIBRATE)

        assertEquals("feedback.vibrate", metadata[VisualTaskerCommandCatalog.METADATA_COMMAND_ID])
        assertEquals("vibrate", metadata[VisualTaskerCommandCatalog.METADATA_CANONICAL_NAME])
        assertTrue(metadata[VisualTaskerCommandCatalog.METADATA_RUNTIME_CAPABILITIES]!!.contains(CommandCapability.FEEDBACK.name))
    }

    private fun assertCatalogEntry(
        blockType: String,
        canonicalName: String,
        alias: String,
        capability: CommandCapability,
    ) {
        val entry = VisualTaskerCommandCatalog.findByBlockType(blockType)
        assertNotNull(entry)
        assertEquals(canonicalName, entry!!.canonicalName)
        assertTrue(entry.acceptedAliases.contains(alias))
        assertTrue(entry.capabilities.contains(capability))
        assertEquals(blockType, entry.block?.blockType)
        assertEquals(capability, entry.runtime?.liveCapabilityGate)
    }
}
