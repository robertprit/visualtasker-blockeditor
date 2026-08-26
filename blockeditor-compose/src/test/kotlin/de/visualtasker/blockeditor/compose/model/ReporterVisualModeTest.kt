package de.visualtasker.blockeditor.compose.model

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.BlockNode
import de.visualtasker.blockeditor.registry.BlockDefinition
import de.visualtasker.blockeditor.registry.BlockTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReporterVisualModeTest {
    @Test
    fun defaultsToCompactAndStoresDetailedInMetadata() {
        val block = BlockNode(id = BlockId("r1"), type = BlockTypes.LITERAL_NUMBER)
        assertEquals(ReporterVisualMode.COMPACT, reporterVisualModeFor(block))
        val detailed = blockWithReporterVisualMode(block, ReporterVisualMode.DETAILED)
        assertEquals(ReporterVisualMode.DETAILED, reporterVisualModeFor(detailed))
        assertEquals("DETAILED", detailed.metadata[REPORTER_VISUAL_MODE_METADATA_KEY])
    }

    @Test
    fun templateAssetsLiveInsideThePluginReporterFolder() {
        val asset = reporterTemplateAsset(ReporterFamily.NUMBER, ReporterVisualMode.COMPACT)
        assertTrue(asset.startsWith("$REPORTER_ASSET_DIR/"))
        assertTrue(asset.endsWith(".svg"))
        assertTrue(!asset.startsWith("/home/"))
    }

    @Test
    fun numberLiteralIsACompactReporterFamily() {
        val definition = BlockDefinition(
            id = BlockTypes.LITERAL_NUMBER,
            label = "Number",
            category = "logic",
            hasPrevious = false,
            hasNext = false,
            outputType = "Number",
            isReporter = true,
        )
        assertEquals(ReporterFamily.NUMBER, resolveReporterFamily(BlockTypes.LITERAL_NUMBER, definition))
    }
}
