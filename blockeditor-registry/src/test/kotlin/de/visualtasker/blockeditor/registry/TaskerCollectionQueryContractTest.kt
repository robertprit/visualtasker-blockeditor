package de.visualtasker.blockeditor.registry

import de.visualtasker.workflow.semantics.*

import de.visualtasker.emscript.contract.CoreTypes
import de.visualtasker.emscript.contract.LanguageTypeCompatibility
import de.visualtasker.emscript.contract.ProviderTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskerCollectionQueryContractTest {
    @Test
    fun `Tasker collection query is a generic typed reporter`() {
        val entry = VisualTaskerCommandCatalog.findById("tasker.getVariables")!!
        val block = DefaultBlockRegistry.getDefinition(entry.block!!.blockType)!!

        assertEquals("tasker.getVariables", entry.canonicalName)
        assertEquals(listOf("Tasker.getVariables"), entry.acceptedAliases)
        assertEquals(CommandCatalogKind.REPORTER, entry.kind)
        assertEquals("List<TaskerVariable>", entry.returnType)
        assertEquals("List<TaskerVariable>", block.outputType)
        assertTrue(block.isReporter)
        assertFalse(block.hasPrevious)
        assertFalse(block.hasNext)
        assertEquals(listOf("pattern"), entry.arguments.map { it.name })
        assertFalse(entry.arguments.single().required)
    }

    @Test
    fun `Tasker variable list connects only to compatible value inputs`() {
        val actual = LanguageTypeCompatibility.fromWorkspaceName("List<TaskerVariable>")!!

        assertEquals(CoreTypes.listOf(ProviderTypes.TASKER_VARIABLE.ref), actual)
        assertTrue(WorkspaceValueTypeSystem.isCompatible("List<TaskerVariable>", setOf("List<TaskerVariable>")))
        assertTrue(WorkspaceValueTypeSystem.isCompatible("List<TaskerVariable>", setOf("Any")))
        assertFalse(WorkspaceValueTypeSystem.isCompatible("List<TaskerVariable>", setOf("String")))
        assertFalse(WorkspaceValueTypeSystem.isCompatible("List<TaskerVariable>", setOf("Number")))
        assertFalse(WorkspaceValueTypeSystem.isCompatible("List<TaskerVariable>", setOf("Boolean")))
    }

    @Test
    fun `Tasker collection query leaves D query return exactly once`() {
        val audit = LegacyCommandDefinitionBridge().analyze()

        assertEquals(setOf("tasker.getVariables"), QueryReturnContractAudit.MIGRATED_M1B_3X)
        assertFalse(QueryReturnContractAudit.currentDQueryIds(audit).contains("tasker.getVariables"))
        val bridgeResult = audit.results.single { it.sourceEntry.id == "tasker.getVariables" }
        assertFalse(bridgeResult is CommandBridgeResult.Conflict)
        assertEquals(
            CoreTypes.listOf(ProviderTypes.TASKER_VARIABLE.ref),
            bridgeResult.definition?.returnType,
        )
        assertEquals(0, QueryReturnContractAudit.currentDQueryIds(audit).size)
        assertEquals(134, audit.results.size)
        assertEquals(3, audit.results.count { it.migrationClass == CommandMigrationClass.NATIVE_V1 })
    }
}
