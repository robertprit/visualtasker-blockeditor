package de.visualtasker.blockeditor.registry

import de.visualtasker.emscript.contract.CoreTypes
import de.visualtasker.emscript.contract.DomainTypes
import de.visualtasker.emscript.contract.LanguageTypeCompatibility
import de.visualtasker.emscript.contract.LanguageTypeRef
import de.visualtasker.emscript.contract.SemanticClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartDomainValueContractTest {
    @Test
    fun `chart queries are generic reporters with stable ids and legacy aliases`() {
        val exists = VisualTaskerCommandCatalog.findById("chart.exists")!!
        val get = VisualTaskerCommandCatalog.findById("chart.get")!!

        assertEquals("chart.exists", exists.canonicalName)
        assertEquals(listOf("Chart.exists"), exists.acceptedAliases)
        assertEquals("Bool", exists.returnType)
        assertEquals(listOf("id"), exists.arguments.map { it.name })

        assertEquals("chart.get", get.canonicalName)
        assertEquals(listOf("Chart.get"), get.acceptedAliases)
        assertEquals("ChartSnapshot?", get.returnType)
        assertEquals(listOf("id", "key"), get.arguments.map { it.name })
        assertFalse(get.arguments.last().required)

        listOf(exists, get).forEach { entry ->
            val block = DefaultBlockRegistry.getDefinition(entry.block!!.blockType)!!
            assertEquals(CommandCatalogKind.REPORTER, entry.kind)
            assertEquals(entry.returnType, block.outputType)
            assertTrue(block.isReporter)
            assertFalse(block.hasPrevious)
            assertFalse(block.hasNext)
            assertTrue(entry.block!!.blockType.startsWith(BlockTypes.EMSCRIPT_COMMAND_PREFIX))
        }
    }

    @Test
    fun `chart snapshot is a distinct nullable domain type`() {
        val snapshot = LanguageTypeCompatibility.fromWorkspaceName("ChartSnapshot")!!
        val nullableSnapshot = LanguageTypeCompatibility.fromWorkspaceName("ChartSnapshot?")!!

        assertEquals(DomainTypes.CHART_SNAPSHOT.ref, snapshot)
        assertEquals(LanguageTypeRef.Nullable(snapshot), nullableSnapshot)
        assertTrue(LanguageTypeCompatibility.isAssignable(snapshot, nullableSnapshot))
        assertFalse(LanguageTypeCompatibility.isAssignable(nullableSnapshot, snapshot))
        assertFalse(LanguageTypeCompatibility.isAssignable(snapshot, CoreTypes.BOOL.ref))
        assertFalse(LanguageTypeCompatibility.isAssignable(CoreTypes.BOOL.ref, snapshot))
        assertTrue(WorkspaceValueTypeSystem.isCompatible("ChartSnapshot?", setOf("ChartSnapshot?")))
        assertFalse(WorkspaceValueTypeSystem.isCompatible("ChartSnapshot?", setOf("Bool")))
        assertFalse(WorkspaceValueTypeSystem.isCompatible("ChartSnapshot?", setOf("String")))
        assertFalse(WorkspaceValueTypeSystem.isCompatible("Bool", setOf("ChartSnapshot?")))
    }

    @Test
    fun `query return audit is closed without changing catalog population`() {
        val audit = LegacyCommandDefinitionBridge().analyze()

        QueryReturnContractAudit.validateAgainst(audit)
        assertEquals(setOf("chart.exists", "chart.get"), QueryReturnContractAudit.MIGRATED_M1B_3Z)
        assertTrue(QueryReturnContractAudit.ALL.isEmpty())
        assertTrue(QueryReturnContractAudit.currentDQueryIds(audit).isEmpty())
        assertEquals(127, audit.results.size)
        assertEquals(3, audit.results.count { it.migrationClass == CommandMigrationClass.NATIVE_V1 })
        assertEquals(0, audit.results.count { it.definition == null })
        assertEquals(0, audit.results.count { it.status == CommandBridgeStatus.UNMAPPABLE })
        assertTrue(
            audit.results
                .mapNotNull { it.definition }
                .filter { it.semanticClass == SemanticClass.QUERY }
                .all { it.returnType != CoreTypes.VOID.ref },
        )
    }
}
