package de.visualtasker.blockeditor.registry

import de.visualtasker.workflow.semantics.*

import de.visualtasker.emscript.contract.CoreTypes
import de.visualtasker.emscript.contract.DomainTypes
import de.visualtasker.emscript.contract.LanguageTypeCompatibility
import de.visualtasker.emscript.contract.LanguageTypeRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerceptionDomainValueContractTest {
    private val contracts = linkedMapOf(
        "action.findTemplate" to "ImageMatch?",
        "vision.findText" to "TextMatch?",
        "vision.markerLoad" to "Marker?",
    )

    @Test
    fun `perception queries are generic nullable reporters with stable names`() {
        contracts.forEach { (id, returnType) ->
            val entry = VisualTaskerCommandCatalog.findById(id)!!
            val block = DefaultBlockRegistry.getDefinition(entry.block!!.blockType)!!

            assertEquals(CommandCatalogKind.REPORTER, entry.kind)
            assertEquals(returnType, entry.returnType)
            assertTrue(entry.block!!.blockType.startsWith(BlockTypes.EMSCRIPT_COMMAND_PREFIX))
            assertEquals(returnType, block.outputType)
            assertTrue(block.isReporter)
            assertFalse(block.hasPrevious)
            assertFalse(block.hasNext)
        }

        assertEquals("findTemplate", VisualTaskerCommandCatalog.findById("action.findTemplate")!!.canonicalName)
        assertEquals(listOf("FIND_TEMPLATE"), VisualTaskerCommandCatalog.findById("action.findTemplate")!!.acceptedAliases)
        assertEquals(listOf("Region.findText", "FIND_TEXT"), VisualTaskerCommandCatalog.findById("vision.findText")!!.acceptedAliases)
        assertEquals(listOf("Marker.load", "LOAD_MARKER"), VisualTaskerCommandCatalog.findById("vision.markerLoad")!!.acceptedAliases)
    }

    @Test
    fun `domain types are distinct nullable and slot compatible only by contract`() {
        val image = LanguageTypeCompatibility.fromWorkspaceName("ImageMatch")!!
        val text = LanguageTypeCompatibility.fromWorkspaceName("TextMatch")!!
        val marker = LanguageTypeCompatibility.fromWorkspaceName("Marker")!!

        assertEquals(DomainTypes.IMAGE_MATCH.ref, image)
        assertEquals(DomainTypes.TEXT_MATCH.ref, text)
        assertEquals(DomainTypes.MARKER.ref, marker)
        assertEquals(LanguageTypeRef.Nullable(image), LanguageTypeCompatibility.fromWorkspaceName("ImageMatch?"))
        assertEquals(LanguageTypeRef.Nullable(text), LanguageTypeCompatibility.fromWorkspaceName("TextMatch?"))
        assertEquals(LanguageTypeRef.Nullable(marker), LanguageTypeCompatibility.fromWorkspaceName("Marker?"))
        assertFalse(LanguageTypeCompatibility.isAssignable(image, text))
        assertFalse(LanguageTypeCompatibility.isAssignable(image, marker))
        assertFalse(LanguageTypeCompatibility.isAssignable(text, marker))
        assertFalse(LanguageTypeCompatibility.isAssignable(image, CoreTypes.STRING.ref))
        assertFalse(LanguageTypeCompatibility.isAssignable(text, CoreTypes.NUMBER.ref))
        assertFalse(LanguageTypeCompatibility.isAssignable(marker, CoreTypes.BOOL.ref))
        assertTrue(WorkspaceValueTypeSystem.isCompatible("ImageMatch?", setOf("ImageMatch?")))
        assertFalse(WorkspaceValueTypeSystem.isCompatible("ImageMatch?", setOf("Bool")))
        assertFalse(WorkspaceValueTypeSystem.isCompatible("TextMatch?", setOf("String")))
        assertFalse(WorkspaceValueTypeSystem.isCompatible("Marker?", setOf("Number")))
    }

    @Test
    fun `perception migration remains stable after query return closure`() {
        val audit = LegacyCommandDefinitionBridge().analyze()

        assertEquals(136, audit.results.size)
        assertEquals(3, audit.results.count { it.migrationClass == CommandMigrationClass.NATIVE_V1 })
        assertEquals(0, audit.results.count { it.definition == null })
        assertTrue(QueryReturnContractAudit.currentDQueryIds(audit).isEmpty())
        contracts.keys.forEach { id ->
            val result = audit.results.single { it.sourceEntry.id == id }
            assertFalse(result.issues.any { it.type == CommandBridgeIssueType.QUERY_RETURNS_VOID })
            assertFalse(result.issues.any { it.type == CommandBridgeIssueType.MISSING_RETURN_TYPE })
        }
    }
}
