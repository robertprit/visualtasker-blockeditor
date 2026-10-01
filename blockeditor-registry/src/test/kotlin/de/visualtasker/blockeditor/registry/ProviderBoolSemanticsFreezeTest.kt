package de.visualtasker.blockeditor.registry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderBoolSemanticsFreezeTest {
    @Test
    fun `3S freeze remains complete after the bounded 3T migration`() {
        ProviderBoolSemanticsFreeze.validate()

        assertEquals(4, ProviderBoolSemanticsFreeze.decisions.size)
        assertEquals(12, QueryReturnContractAudit.ALL.size)
        assertTrue(QueryReturnContractAudit.MIGRATED_M1B_3R.contains("chromeTab.isSupported"))
        assertEquals(
            setOf("tasker.isInstalled", "shizuku.isInstalled", "termux.isInstalled"),
            QueryReturnContractAudit.MIGRATED_M1B_3T,
        )
        assertEquals(setOf("shizuku.isAvailable"), QueryReturnContractAudit.MIGRATED_M1B_3U)
        assertTrue(QueryReturnContractAudit.ALL.none { it.stableId == "shizuku.isAvailable" })
    }

    @Test
    fun `3T and 3U commands are generic Bool reporters`() {
        (QueryReturnContractAudit.MIGRATED_M1B_3T + QueryReturnContractAudit.MIGRATED_M1B_3U).forEach { id ->
            val entry = VisualTaskerCommandCatalog.findById(id)!!
            val block = DefaultBlockRegistry.getDefinition(entry.block!!.blockType)!!

            assertEquals(id, entry.canonicalName)
            assertTrue(entry.arguments.isEmpty())
            assertEquals(CommandCatalogKind.REPORTER, entry.kind)
            assertEquals("Bool", entry.returnType)
            assertFalse(block.hasPrevious)
            assertFalse(block.hasNext)
            assertEquals("Bool", block.outputType)
            assertTrue(block.isReporter)
        }
    }

    @Test
    fun `false is a successful negative answer only after technical checks succeed`() {
        ProviderBoolSemanticsFreeze.decisions.forEach { decision ->
            assertTrue(decision.falseMeaning.contains("successfully") || decision.falseMeaning.contains("definitively"))
            assertTrue(decision.failureStates.any { it.contains("failed") })
            assertTrue(decision.falseCollision.isNotEmpty())
            assertFalse(decision.legitimateAbsent)
        }
    }

    @Test
    fun `Shizuku availability remains a composite contract separate from installation`() {
        val installed = ProviderBoolSemanticsFreeze.decisions.single { it.commandId == "shizuku.isInstalled" }
        val available = ProviderBoolSemanticsFreeze.decisions.single { it.commandId == "shizuku.isAvailable" }

        assertFalse(installed.permissionDependent)
        assertFalse(installed.binderDependent)
        assertTrue(available.permissionDependent)
        assertTrue(available.binderDependent)
        assertTrue(available.currentBoolSource.contains("installed && permissionGranted && binderAlive"))
        assertTrue(available.evidence.any { it.contains("No shell command") })
    }

    @Test
    fun `excluded collision commands remain unchanged`() {
        listOf("tasker.isEnabled", "shizuku.getUid").forEach { id ->
            val entry = VisualTaskerCommandCatalog.findById(id)!!
            assertEquals(CommandCatalogKind.STATEMENT, entry.kind)
            assertNull(entry.returnType)
            assertTrue(QueryReturnContractAudit.ALL.any { it.stableId == id })
        }

        val touch = VisualTaskerCommandCatalog.findById("input.touch")!!
        assertEquals(CommandCatalogKind.STATEMENT, touch.kind)
        assertNull(touch.returnType)
    }

    @Test
    fun `bridge and native populations remain frozen`() {
        val report = LegacyCommandDefinitionBridge().analyze()

        assertEquals(127, report.results.size)
        assertEquals(3, report.results.count { it.migrationClass == CommandMigrationClass.NATIVE_V1 })
    }
}
