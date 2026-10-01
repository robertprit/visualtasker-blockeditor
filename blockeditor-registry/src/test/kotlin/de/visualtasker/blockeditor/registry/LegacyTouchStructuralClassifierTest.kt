package de.visualtasker.blockeditor.registry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyTouchStructuralClassifierTest {
    @Test
    fun `coordinate-only payload remains partial without invented gesture semantics`() {
        val raw = "[540, 1100]"
        val result = LegacyTouchStructuralClassifier.classify("touch", raw)

        assertEquals(raw, result.rawPayload)
        assertEquals(LegacyTouchClassification.PARTIAL, result.classification)
        assertEquals(LegacyTouchMigrationReadiness.REQUIRES_STRUCTURAL_MIGRATION, result.migrationReadiness)
        assertTrue(LegacyTouchEvidence.HAS_COORDINATES in result.evidence)
        assertFalse(LegacyTouchEvidence.HAS_DOWN in result.evidence)
        assertFalse(LegacyTouchEvidence.HAS_UP in result.evidence)
        assertFalse(LegacyTouchEvidence.HAS_TIMING in result.evidence)
        assertFalse(LegacyTouchEvidence.HAS_POINTER_ID in result.evidence)
        assertEquals(listOf(540.0 to 1100.0), result.coordinates.map { it.x to it.y })
        assertTrue(result.hasDiagnostic(LegacyTouchDiagnosticCode.UNKNOWN_COORDINATE_SPACE))
        assertTrue(result.hasDiagnostic(LegacyTouchDiagnosticCode.AMBIGUOUS_STRUCTURE))
    }

    @Test
    fun `array atomic states expose evidence but remain partial`() {
        val raw = "[\"down\", 120, 240, \"up\"]"
        val result = LegacyTouchStructuralClassifier.classify("touch", raw)

        assertEquals(raw, result.rawPayload)
        assertEquals(LegacyTouchClassification.PARTIAL, result.classification)
        assertTrue(LegacyTouchEvidence.HAS_COORDINATES in result.evidence)
        assertTrue(LegacyTouchEvidence.HAS_DOWN in result.evidence)
        assertTrue(LegacyTouchEvidence.HAS_UP in result.evidence)
        assertFalse(LegacyTouchEvidence.HAS_MOVE in result.evidence)
        assertTrue(result.hasDiagnostic(LegacyTouchDiagnosticCode.MISSING_TIMING))
        assertTrue(result.hasDiagnostic(LegacyTouchDiagnosticCode.MISSING_POINTER_ID))
        assertFalse(LegacyTouchEvidence.HAS_SEQUENCE_BOUNDARIES in result.evidence)
    }

    @Test
    fun `legacy atomic-call string exposes down move up and coordinates`() {
        val raw = "\"down(10,20);move(20,30);up(20,30)\""
        val result = LegacyTouchStructuralClassifier.classify("touch", raw)

        assertEquals(LegacyTouchClassification.PARTIAL, result.classification)
        assertTrue(LegacyTouchEvidence.HAS_DOWN in result.evidence)
        assertTrue(LegacyTouchEvidence.HAS_MOVE in result.evidence)
        assertTrue(LegacyTouchEvidence.HAS_UP in result.evidence)
        assertEquals(3, result.coordinates.size)
        assertFalse(LegacyTouchEvidence.HAS_SEQUENCE_BOUNDARIES in result.evidence)
        assertFalse(LegacyTouchEvidence.HAS_TIMING in result.evidence)
    }

    @Test
    fun `all historical aliases produce equal structural semantics`() {
        val payload = "[\"down\", 1, 2, \"up\"]"
        val results = LegacyTouchStructuralClassifier.acceptedAliases.map { alias ->
            LegacyTouchStructuralClassifier.classify(alias, payload)
        }

        assertEquals(setOf("touch", "Touch.dispatch", "TOUCH"), results.map { it.alias }.toSet())
        assertEquals(1, results.map { it.copy(alias = "touch") }.distinct().size)
    }

    @Test
    fun `invocation parser preserves exact argument text`() {
        val result = LegacyTouchStructuralClassifier.classifyInvocation("Touch.dispatch( [540, 1100] )")

        assertEquals("Touch.dispatch", result.alias)
        assertEquals(" [540, 1100] ", result.rawPayload)
        assertEquals(LegacyTouchClassification.PARTIAL, result.classification)
    }

    @Test
    fun `empty malformed and unknown payloads remain conservatively unknown`() {
        val fixtures = listOf(
            "" to LegacyTouchDiagnosticCode.EMPTY_PAYLOAD,
            "[" to LegacyTouchDiagnosticCode.MALFORMED_PAYLOAD,
            "\"mystery\"" to LegacyTouchDiagnosticCode.UNKNOWN_TOKEN,
        )

        fixtures.forEach { (raw, expectedDiagnostic) ->
            val result = LegacyTouchStructuralClassifier.classify("touch", raw)
            assertEquals(raw, result.rawPayload)
            assertEquals(LegacyTouchClassification.UNKNOWN, result.classification)
            assertEquals(LegacyTouchMigrationReadiness.PRESERVE_LEGACY, result.migrationReadiness)
            assertTrue(result.hasDiagnostic(expectedDiagnostic))
        }
    }

    @Test
    fun `negative and floating coordinates are retained`() {
        val result = LegacyTouchStructuralClassifier.classify("touch", "[-10.5, 20.25]")

        assertEquals(LegacyTouchClassification.PARTIAL, result.classification)
        assertEquals(-10.5, result.coordinates.single().x, 0.0)
        assertEquals(20.25, result.coordinates.single().y, 0.0)
        assertEquals("-10.5", result.coordinates.single().rawX)
        assertEquals("20.25", result.coordinates.single().rawY)
    }

    @Test
    fun `odd number count reports ambiguity without crashing`() {
        val result = LegacyTouchStructuralClassifier.classify("touch", "[1, 2, 3]")

        assertEquals(LegacyTouchClassification.PARTIAL, result.classification)
        assertEquals(1, result.coordinates.size)
        assertTrue(result.hasDiagnostic(LegacyTouchDiagnosticCode.ODD_COORDINATE_COUNT))
    }

    @Test
    fun `mixed primitives and extra unknown tokens stay partial when structure is present`() {
        val result = LegacyTouchStructuralClassifier.classify("touch", "[\"down\", 1, 2, true, \"mystery\", \"up\"]")

        assertEquals(LegacyTouchClassification.PARTIAL, result.classification)
        assertTrue(LegacyTouchEvidence.HAS_UNKNOWN_TOKEN in result.evidence)
        assertTrue(result.hasDiagnostic(LegacyTouchDiagnosticCode.UNKNOWN_TOKEN))
        assertEquals(1, result.coordinates.size)
    }

    @Test
    fun `nested arrays do not imply multi-pointer or gesture boundaries`() {
        val result = LegacyTouchStructuralClassifier.classify("touch", "[[1, 2], [3, 4]]")

        assertEquals(LegacyTouchClassification.PARTIAL, result.classification)
        assertEquals(2, result.coordinates.size)
        assertFalse(LegacyTouchEvidence.HAS_MULTI_POINTER_STRUCTURE in result.evidence)
        assertFalse(LegacyTouchEvidence.HAS_SEQUENCE_BOUNDARIES in result.evidence)
    }

    @Test
    fun `explicit metadata keys are evidence and never synthesized defaults`() {
        val result = LegacyTouchStructuralClassifier.classify(
            "touch",
            "{\"pointerId\":7,\"durationMs\":50,\"points\":[1,2]}",
        )

        assertTrue(LegacyTouchEvidence.HAS_POINTER_ID in result.evidence)
        assertTrue(LegacyTouchEvidence.HAS_TIMING in result.evidence)
        assertTrue(LegacyTouchEvidence.HAS_COORDINATES in result.evidence)
        assertEquals(1, result.coordinates.size)
        assertEquals(LegacyTouchClassification.PARTIAL, result.classification)
    }

    @Test
    fun `classification is deterministic and idempotent`() {
        val first = LegacyTouchStructuralClassifier.classify("TOUCH", "[\"down\", 12, 24, \"up\"]")
        val second = LegacyTouchStructuralClassifier.classify("TOUCH", first.rawPayload)

        assertEquals(first, second)
    }

    @Test
    fun `no current fixture is falsely classified lossless`() {
        val fixtures = listOf(
            "[540, 1100]",
            "[\"down\", 120, 240, \"up\"]",
            "\"down(10,20);move(20,30);up(20,30)\"",
        )

        assertTrue(fixtures.none {
            LegacyTouchStructuralClassifier.classify("touch", it).classification == LegacyTouchClassification.LOSSLESS
        })
    }

    private fun LegacyTouchStructure.hasDiagnostic(code: LegacyTouchDiagnosticCode): Boolean =
        diagnostics.any { it.code == code }
}
