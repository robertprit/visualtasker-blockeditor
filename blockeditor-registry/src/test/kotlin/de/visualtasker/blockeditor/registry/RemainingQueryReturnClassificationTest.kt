package de.visualtasker.blockeditor.registry

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemainingQueryReturnClassificationTest {
    private val audit = LegacyCommandDefinitionBridge().analyze()

    @Test
    fun `3V classification remains exhaustive after 3X migration`() {
        RemainingQueryReturnClassification.validate(audit)

        val decisions = RemainingQueryReturnClassification.decisions
        assertEquals(12, decisions.size)
        assertEquals(
            QueryReturnContractAudit.currentDQueryIds(audit) +
                QueryReturnContractAudit.MIGRATED_M1B_3W +
                QueryReturnContractAudit.MIGRATED_M1B_3X,
            decisions.mapTo(linkedSetOf()) { it.commandId },
        )
        assertEquals(12, RemainingQueryReturnClassification.groups.sumOf { it.expectedReduction })
        assertTrue(decisions.all { it.genericCommandCallSufficient && it.genericReporterSufficient })
        val migrated = QueryReturnContractAudit.MIGRATED_M1B_3W + QueryReturnContractAudit.MIGRATED_M1B_3X
        decisions.filterNot { it.commandId in migrated }.forEach { decision ->
            val entry = VisualTaskerCommandCatalog.findById(decision.commandId)!!
            assertEquals(CommandCatalogKind.STATEMENT, entry.kind)
            assertEquals(null, entry.returnType)
        }
        decisions.filter { it.commandId in migrated }.forEach { decision ->
            val entry = VisualTaskerCommandCatalog.findById(decision.commandId)!!
            assertEquals(CommandCatalogKind.REPORTER, entry.kind)
            assertEquals(decision.proposedV1ReturnType, entry.returnType)
        }
    }

    @Test
    fun `3V separates absent negative and failure for every command`() {
        RemainingQueryReturnClassification.decisions.forEach { decision ->
            assertTrue(decision.absentSemantics.isNotBlank())
            assertTrue(decision.negativeResultSemantics.isNotBlank())
            assertTrue(decision.failureSemantics.isNotBlank())
            assertTrue(decision.firstLossPoint.isNotBlank())
            assertTrue(decision.diagnostics.isNotEmpty())
        }
    }

    @Test
    fun `3V freezes scalar collection perception and chart migration groups`() {
        val groups = RemainingQueryReturnClassification.groups.associateBy { it.id }

        assertEquals(
            setOf("tasker.isEnabled", "tasker.getVariable", "shizuku.getUid", "termux.get", "scrcpy.isRunning", "scrcpy.get"),
            groups.getValue("M1B-3W").commandIds.toSet(),
        )
        assertEquals(listOf("tasker.getVariables"), groups.getValue("M1B-3X").commandIds)
        assertEquals(
            setOf("action.findTemplate", "vision.findText", "vision.markerLoad"),
            groups.getValue("M1B-3Y").commandIds.toSet(),
        )
        assertEquals(setOf("chart.exists", "chart.get"), groups.getValue("M1B-3Z").commandIds.toSet())
    }

    @Test
    fun `3V return decisions preserve empty zero and identity distinctions`() {
        val decisions = RemainingQueryReturnClassification.decisions.associateBy { it.commandId }

        assertEquals("Bool", decisions.getValue("tasker.isEnabled").proposedV1ReturnType)
        assertEquals("String?", decisions.getValue("tasker.getVariable").proposedV1ReturnType)
        assertTrue(decisions.getValue("tasker.getVariable").negativeResultSemantics.contains("empty String"))
        assertEquals("List<TaskerVariable>", decisions.getValue("tasker.getVariables").proposedV1ReturnType)
        assertTrue(decisions.getValue("tasker.getVariables").negativeResultSemantics.contains("empty list"))
        assertEquals("Number?", decisions.getValue("shizuku.getUid").proposedV1ReturnType)
        assertTrue(decisions.getValue("shizuku.getUid").negativeResultSemantics.contains("0 is a legitimate UID"))
        assertEquals("Marker?", decisions.getValue("vision.markerLoad").proposedV1ReturnType)
        assertTrue(decisions.getValue("vision.markerLoad").requiredNewValueType.contains("markerId"))
        assertEquals("ChartSnapshot?", decisions.getValue("chart.get").proposedV1ReturnType)
        assertFalse(decisions.getValue("chart.exists").nullable)
    }

    @Test
    fun `3V machine and human reports are deterministic`() {
        val csv = RemainingQueryReturnClassification.renderCsv(audit)
        val markdown = RemainingQueryReturnClassification.renderMarkdown(audit)

        assertEquals(13, csv.lineSequence().count { it.isNotBlank() })
        assertTrue(csv.lineSequence().first().contains("absent_semantics"))
        assertTrue(csv.lineSequence().first().contains("negative_result_semantics"))
        assertTrue(csv.lineSequence().first().contains("failure_semantics"))
        assertTrue(markdown.contains("## Migration Groups"))
        assertTrue(markdown.contains("6 + 1 + 3 + 2 = 12"))
        assertEquals(csv, RemainingQueryReturnClassification.renderCsv(LegacyCommandDefinitionBridge().analyze()))
    }

    @Test
    fun `optional 3V report generation writes deterministic artifacts`() {
        val reportDirectory = System.getenv("M1B_3V_REPORT_DIR")?.let(::File) ?: return
        reportDirectory.mkdirs()

        File(reportDirectory, "EMSCRIPT_V1_REMAINING_QUERY_RETURN_CLASSIFICATION.csv")
            .writeText(RemainingQueryReturnClassification.renderCsv(audit))
        File(reportDirectory, "EMSCRIPT_V1_REMAINING_QUERY_RETURN_CLASSIFICATION.md")
            .writeText(RemainingQueryReturnClassification.renderMarkdown(audit))
    }
}
