package de.visualtasker.blockeditor.registry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryReturnContractAuditTest {
    private val bridgeAudit = LegacyCommandDefinitionBridge().analyze()

    @Test
    fun `every current D query return entry is classified exactly once`() {
        QueryReturnContractAudit.validateAgainst(bridgeAudit)

        assertEquals(12, QueryReturnContractAudit.ALL.size)
        assertEquals(12, QueryReturnContractAudit.ALL.map { it.stableId }.distinct().size)
        assertEquals(
            QueryReturnContractAudit.currentDQueryIds(bridgeAudit),
            QueryReturnContractAudit.ALL.mapTo(linkedSetOf()) { it.stableId },
        )
        assertTrue(QueryReturnContractAudit.ALL.all { decision ->
            bridgeAudit.results.single { it.sourceEntry.id == decision.stableId }
                .sourceEntry.role == CommandCatalogRole.LANGUAGE_COMMAND
        })
    }

    @Test
    fun `classification is exclusive complete and evidence backed`() {
        val counts = QueryReturnContractAudit.ALL.groupingBy { it.primaryClass }.eachCount()

        assertEquals(0, counts[QueryReturnClass.A_NULLABLE_SCALAR_READY] ?: 0)
        assertEquals(0, counts[QueryReturnClass.B_NONNULL_SCALAR_READY] ?: 0)
        assertEquals(2, counts[QueryReturnClass.C_STRUCTURED_RESULT_TYPE])
        assertEquals(5, counts[QueryReturnClass.D_RUNTIME_RESULT_GAP])
        assertEquals(0, counts[QueryReturnClass.E_PROVIDER_CONTRACT_GAP] ?: 0)
        assertEquals(5, counts[QueryReturnClass.F_SENTINEL_OR_ERROR_COLLISION])
        assertEquals(0, counts[QueryReturnClass.G_LANGUAGE_SEMANTICS_GAP] ?: 0)
        assertEquals(0, counts[QueryReturnClass.H_AMBIGUOUS] ?: 0)
        assertTrue(QueryReturnContractAudit.ALL.all { it.evidence.isNotBlank() })
        assertTrue(QueryReturnContractAudit.ALL.all { it.recommendedNextSlice.isNotBlank() })
        assertTrue(QueryReturnContractAudit.ALL.all { it.firstLossPoint.isNotBlank() })
        assertTrue(QueryReturnContractAudit.ALL.all { it.provider.isNotBlank() })
        assertTrue(QueryReturnContractAudit.ALL.all { it.producer.isNotBlank() && it.consumer.isNotBlank() })
        assertTrue(QueryReturnContractAudit.ALL.none { it.commandCallReady || it.workspaceReady || it.irReady })
        assertTrue(QueryReturnContractAudit.ALL.all { it.sideEffectClass == QueryEffectClass.READ_ONLY_EXTERNAL })
        assertEquals(7, QueryReturnContractAudit.ALL.count { it.runtimeValueExists })
        assertTrue(QueryReturnContractAudit.ALL.none { it.migrationRisk == QueryMigrationRisk.LOW })
        assertTrue(QueryReturnContractAudit.ALL.none { it.primaryClass == QueryReturnClass.B_NONNULL_SCALAR_READY })
    }

    @Test
    fun `remaining D query blocks remain statement shaped without output`() {
        QueryReturnContractAudit.ALL.forEach { decision ->
            val entry = VisualTaskerCommandCatalog.findById(decision.stableId)!!
            val block = DefaultBlockRegistry.getDefinition(entry.block!!.blockType)!!

            assertEquals(CommandCatalogKind.STATEMENT, entry.kind)
            assertNull(entry.returnType)
            assertTrue(block.hasPrevious)
            assertTrue(block.hasNext)
            assertNull(block.outputType)
            assertFalse(block.isReporter)
        }
    }

    @Test
    fun `approved String query groups left D query return`() {
        assertEquals(
            setOf("clipboard.get", "system.info", "system.env"),
            QueryReturnContractAudit.MIGRATED_M1B_3J,
        )
        assertTrue(QueryReturnContractAudit.ALL.none { it.migrationRisk == QueryMigrationRisk.LOW })
        QueryReturnContractAudit.MIGRATED_M1B_3J.forEach { id ->
            val entry = VisualTaskerCommandCatalog.findById(id)!!
            val block = DefaultBlockRegistry.getDefinition(entry.block!!.blockType)!!
            assertEquals("String", entry.returnType)
            assertEquals("String", block.outputType)
            assertTrue(block.isReporter)
            assertFalse(block.hasPrevious)
            assertFalse(block.hasNext)
        }
        assertEquals(
            setOf("file.readText", "system.datastoreGet"),
            QueryReturnContractAudit.MIGRATED_M1B_3M,
        )
        QueryReturnContractAudit.MIGRATED_M1B_3M.forEach { id ->
            val entry = VisualTaskerCommandCatalog.findById(id)!!
            val block = DefaultBlockRegistry.getDefinition(entry.block!!.blockType)!!
            assertEquals("String?", entry.returnType)
            assertEquals("String?", block.outputType)
            assertTrue(block.isReporter)
            assertFalse(block.hasPrevious)
            assertFalse(block.hasNext)
        }
        assertEquals(setOf("vision.templateCompare"), QueryReturnContractAudit.MIGRATED_M1B_3P)
        val templateCompare = VisualTaskerCommandCatalog.findById("vision.templateCompare")!!
        val templateBlock = DefaultBlockRegistry.getDefinition(templateCompare.block!!.blockType)!!
        assertEquals(CommandCatalogKind.REPORTER, templateCompare.kind)
        assertEquals("Number", templateCompare.returnType)
        assertEquals("Number", templateBlock.outputType)
        assertTrue(templateBlock.isReporter)
        assertFalse(templateBlock.hasPrevious)
        assertFalse(templateBlock.hasNext)
        assertEquals(setOf("chromeTab.isSupported"), QueryReturnContractAudit.MIGRATED_M1B_3R)
        val chromeTabSupported = VisualTaskerCommandCatalog.findById("chromeTab.isSupported")!!
        val chromeTabBlock = DefaultBlockRegistry.getDefinition(chromeTabSupported.block!!.blockType)!!
        assertEquals(CommandCatalogKind.REPORTER, chromeTabSupported.kind)
        assertEquals("Bool", chromeTabSupported.returnType)
        assertEquals("Bool", chromeTabBlock.outputType)
        assertTrue(chromeTabBlock.isReporter)
        assertEquals(
            setOf("tasker.isInstalled", "shizuku.isInstalled", "termux.isInstalled"),
            QueryReturnContractAudit.MIGRATED_M1B_3T,
        )
        QueryReturnContractAudit.MIGRATED_M1B_3T.forEach { id ->
            val entry = VisualTaskerCommandCatalog.findById(id)!!
            val block = DefaultBlockRegistry.getDefinition(entry.block!!.blockType)!!
            assertEquals(id, entry.canonicalName)
            assertEquals(CommandCatalogKind.REPORTER, entry.kind)
            assertEquals("Bool", entry.returnType)
            assertEquals("Bool", block.outputType)
            assertTrue(block.isReporter)
            assertFalse(block.hasPrevious)
            assertFalse(block.hasNext)
        }
        assertEquals(setOf("shizuku.isAvailable"), QueryReturnContractAudit.MIGRATED_M1B_3U)
        val shizukuAvailable = VisualTaskerCommandCatalog.findById("shizuku.isAvailable")!!
        val shizukuAvailableBlock = DefaultBlockRegistry.getDefinition(shizukuAvailable.block!!.blockType)!!
        assertEquals("shizuku.isAvailable", shizukuAvailable.canonicalName)
        assertEquals(CommandCatalogKind.REPORTER, shizukuAvailable.kind)
        assertEquals("Bool", shizukuAvailable.returnType)
        assertEquals("Bool", shizukuAvailableBlock.outputType)
        assertTrue(shizukuAvailableBlock.isReporter)
        assertFalse(shizukuAvailableBlock.hasPrevious)
        assertFalse(shizukuAvailableBlock.hasNext)
        assertFalse(chromeTabBlock.hasPrevious)
        assertFalse(chromeTabBlock.hasNext)
    }

    @Test
    fun `query decision csv is deterministic and matches bridge inventory`() {
        val first = QueryReturnContractAudit.renderCsv(bridgeAudit)
        val second = QueryReturnContractAudit.renderCsv(LegacyCommandDefinitionBridge().analyze())

        assertEquals(first, second)
        assertEquals(QueryReturnContractAudit.ALL.size + 1, first.lineSequence().count { it.isNotBlank() })
        assertTrue(first.lineSequence().first().contains("recommendedSlice"))
        assertTrue(first.lineSequence().first().contains("structuredTypeNeeded"))
        assertTrue(first.lineSequence().first().contains("primaryClass"))
        assertTrue(first.lineSequence().first().contains("firstLossPoint"))
        assertTrue(first.lineSequence().first().contains("transportReady"))
        assertTrue(first.lineSequence().first().contains("consumptionReady"))
        assertTrue(first.lineSequence().first().contains("bridgeStatus"))
        assertTrue(first.lineSequence().first().contains("testEvidence"))
        assertTrue(QueryReturnContractAudit.renderMarkdown(bridgeAudit).contains("## Recommended Next Slice"))
        assertTrue(QueryReturnContractAudit.renderMarkdown(bridgeAudit).contains("## Structured Result Evidence"))
    }

    @Test
    fun `nullable transport and consumption readiness remain distinct`() {
        val nullable = QueryReturnContractAudit.ALL.filter { it.nullable == QueryNullability.YES }

        assertEquals(
            setOf("action.findTemplate", "vision.markerLoad", "shizuku.getUid", "termux.get"),
            nullable.mapTo(linkedSetOf()) { it.stableId },
        )
        assertTrue(nullable.none { it.consumptionReady })
        assertEquals(
            setOf("shizuku.getUid", "termux.get"),
            nullable.filter { it.transportReady }.mapTo(linkedSetOf()) { it.stableId },
        )
    }

    @Test
    fun `template compare freezes a non-null score and structured failure contract`() {
        TemplateCompareSemantics.validate()
        assertTrue(TemplateCompareSemantics.STABLE_ID in QueryReturnContractAudit.MIGRATED_M1B_3P)
        assertTrue(QueryReturnContractAudit.ALL.none { it.stableId == TemplateCompareSemantics.STABLE_ID })
        assertEquals(0.0, TemplateCompareSemantics.MIN_SCORE, 0.0)
        assertEquals(1.0, TemplateCompareSemantics.MAX_SCORE, 0.0)
        assertTrue(TemplateCompareSemantics.FORMULA.contains("mean(abs"))
        assertTrue(TemplateCompareSemantics.THRESHOLD_CONTRACT.contains("No acceptance threshold"))
        assertEquals(
            0,
            TemplateCompareSemantics.states.count {
                it.v1Kind == TemplateCompareResultKind.ABSENT
            },
        )
        assertTrue(
            TemplateCompareSemantics.states
                .filter { it.v1Kind == TemplateCompareResultKind.FAILURE }
                .all { it.diagnostic in TemplateCompareSemantics.diagnostics },
        )
        assertTrue(TemplateCompareSemantics.nullOrigins.size >= 4)
        val catalog = VisualTaskerCommandCatalog.findById(TemplateCompareSemantics.STABLE_ID)!!
        assertEquals(TemplateCompareSemantics.CANONICAL_NAME, catalog.canonicalName)
        assertEquals(TemplateCompareSemantics.aliases, catalog.acceptedAliases)
        assertEquals(listOf("name", "region", "processing"), catalog.arguments.map { it.name })
        assertFalse(catalog.arguments.any { it.name.equals("threshold", ignoreCase = true) })
        assertEquals(CommandCatalogKind.REPORTER, catalog.kind)
        assertEquals("Number", catalog.returnType)
    }

    @Test
    fun `chrome tab support converges true false and failure through typed transport`() {
        ChromeTabSupportedSemantics.validate()
        val catalog = VisualTaskerCommandCatalog.findById(ChromeTabSupportedSemantics.STABLE_ID)!!
        val block = DefaultBlockRegistry.getDefinition(catalog.block!!.blockType)!!

        assertEquals(ChromeTabSupportedSemantics.PROPOSED_CANONICAL_NAME, catalog.canonicalName)
        assertEquals(listOf(ChromeTabSupportedSemantics.LEGACY_CANONICAL_NAME), catalog.acceptedAliases)
        assertTrue(catalog.arguments.isEmpty())
        assertTrue(ChromeTabSupportedSemantics.STABLE_ID in QueryReturnContractAudit.MIGRATED_M1B_3R)
        assertTrue(QueryReturnContractAudit.ALL.none { it.stableId == ChromeTabSupportedSemantics.STABLE_ID })
        assertEquals(1, ChromeTabSupportedSemantics.states.count { it.v1Kind == ChromeTabSupportResultKind.TRUE })
        assertEquals(1, ChromeTabSupportedSemantics.states.count { it.v1Kind == ChromeTabSupportResultKind.FALSE })
        assertEquals(2, ChromeTabSupportedSemantics.states.count { it.v1Kind == ChromeTabSupportResultKind.FAILURE })
        assertTrue(ChromeTabSupportedSemantics.states.single { it.v1Kind == ChromeTabSupportResultKind.FALSE }.diagnostic.isBlank())
        assertEquals(CommandCatalogKind.REPORTER, catalog.kind)
        assertEquals("Bool", catalog.returnType)
        assertFalse(block.hasPrevious)
        assertFalse(block.hasNext)
        assertEquals("Bool", block.outputType)
        assertTrue(block.isReporter)
    }

    @Test
    fun `sentinel and structured evidence are explicit`() {
        assertEquals(
            setOf("shizuku.getUid", "termux.get"),
            QueryReturnContractAudit.ALL.filter { it.sentinelUsed }.mapTo(linkedSetOf()) { it.stableId },
        )
        assertEquals(
            mapOf("action.findTemplate" to "ImageMatch", "vision.markerLoad" to "Region"),
            QueryReturnContractAudit.ALL.filter { it.structuredTypeNeeded }
                .associate { it.stableId to it.proposedStructuredType },
        )
    }

    @Test
    fun `query audit does not alter native V1 population`() {
        assertEquals(127, bridgeAudit.results.size)
        assertEquals(3, bridgeAudit.results.count { it.migrationClass == CommandMigrationClass.NATIVE_V1 })
        assertTrue(QueryReturnContractAudit.ALL.none { decision ->
            bridgeAudit.results.single { it.sourceEntry.id == decision.stableId }
                .migrationClass == CommandMigrationClass.NATIVE_V1
        })
    }
}
