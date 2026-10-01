package de.visualtasker.blockeditor.registry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NullableQuerySemanticsAuditTest {
    private val bridgeAudit = LegacyCommandDefinitionBridge().analyze()

    @Test
    fun `empty absent and failure remain distinct`() {
        NullableQuerySemanticsAudit.validate()

        NullableQuerySemanticsAudit.ALL.forEach { decision ->
            assertTrue(NullableQueryResultState.EMPTY_VALUE in decision.supportedStates)
            assertTrue(NullableQueryResultState.ABSENT in decision.supportedStates)
            assertTrue(NullableQueryResultState.FAILURE in decision.supportedStates)
            assertNotEquals(decision.emptyMeaning, decision.absentMeaning)
            assertNotEquals(decision.absentMeaning, decision.failureMeaning)
        }
    }

    @Test
    fun `smallest selected model is nullable String without Any default Option or Result`() {
        NullableQuerySemanticsAudit.ALL.forEach { decision ->
            assertEquals("String?", decision.returnType)
            assertEquals(NullableQueryModel.NULLABLE_STRING, decision.selectedModel)
            assertFalse(decision.defaultParameter)
            assertNotEquals("Any", decision.returnType)
            assertFalse(decision.returnType.startsWith("Option"))
            assertFalse(decision.returnType.startsWith("Result"))
        }
    }

    @Test
    fun `contract freeze is implemented by nullable reporters`() {
        NullableQuerySemanticsAudit.ALL.forEach { decision ->
            val entry = VisualTaskerCommandCatalog.findById(decision.stableId)!!
            val block = DefaultBlockRegistry.getDefinition(entry.block!!.blockType)!!

            assertEquals(CommandCatalogKind.REPORTER, entry.kind)
            assertEquals("String?", entry.returnType)
            assertEquals("String?", block.outputType)
            assertTrue(block.isReporter)
        }
        assertEquals(12, QueryReturnContractAudit.currentDQueryIds(bridgeAudit).size)
        assertEquals(127, bridgeAudit.results.size)
        assertEquals(3, bridgeAudit.results.count { it.migrationClass == CommandMigrationClass.NATIVE_V1 })
    }

    @Test
    fun `M1B 3J queries remain non-null String`() {
        QueryReturnContractAudit.MIGRATED_M1B_3J.forEach { id ->
            val entry = VisualTaskerCommandCatalog.findById(id)!!
            assertEquals("String", entry.returnType)
            assertFalse(entry.returnType!!.endsWith("?"))
        }
    }
}
