package de.visualtasker.blockeditor.domain

import de.visualtasker.emscript.contract.EmscriptV1OperatorIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OperatorNormalizationTest {
    @Test
    fun normalizeAcceptsSymbolicAndNamedCompareOperators() {
        assertEquals(
            NormalizedOperator.Compare(CompareOperator.EQUAL),
            OperatorNormalization.normalize("=="),
        )
        assertEquals(
            NormalizedOperator.Compare(CompareOperator.GREATER_OR_EQUAL),
            OperatorNormalization.normalize("gte"),
        )
    }

    @Test
    fun normalizeAcceptsArithmeticSymbols() {
        assertEquals(
            NormalizedOperator.Arithmetic(ArithmeticOperator.MOD),
            OperatorNormalization.normalize("%"),
        )
    }

    @Test
    fun everyBlockOperatorMapsExplicitlyToAStableV1Identity() {
        val mapped = CompareOperator.entries.map(CompareOperator::operatorId) +
            ArithmeticOperator.entries.map(ArithmeticOperator::operatorId)

        assertEquals(
            setOf(
                EmscriptV1OperatorIds.EQUAL,
                EmscriptV1OperatorIds.NOT_EQUAL,
                EmscriptV1OperatorIds.LESS,
                EmscriptV1OperatorIds.LESS_OR_EQUAL,
                EmscriptV1OperatorIds.GREATER,
                EmscriptV1OperatorIds.GREATER_OR_EQUAL,
                EmscriptV1OperatorIds.ADD,
                EmscriptV1OperatorIds.SUBTRACT,
                EmscriptV1OperatorIds.MULTIPLY,
                EmscriptV1OperatorIds.DIVIDE,
                EmscriptV1OperatorIds.MODULO,
            ),
            mapped.toSet(),
        )
        assertEquals(mapped.size, mapped.toSet().size)
    }

    @Test
    fun stableIdsAndLegacyValuesResolveToTheSameOperator() {
        assertEquals(OperatorNormalization.normalize("greaterOrEqual"), OperatorNormalization.normalize("GREATER_OR_EQUAL"))
        assertEquals(OperatorNormalization.normalize("multiply"), OperatorNormalization.normalize("MUL"))
        assertEquals(EmscriptV1OperatorIds.GREATER_OR_EQUAL, OperatorNormalization.normalize(">=")?.operatorId)
        assertEquals(EmscriptV1OperatorIds.MULTIPLY, OperatorNormalization.normalize("*")?.operatorId)
    }

    @Test
    fun normalizeRejectsBlankAndUnknownValues() {
        assertNull(OperatorNormalization.normalize(null))
        assertNull(OperatorNormalization.normalize("  "))
        assertNull(OperatorNormalization.normalize("???"))
    }
}
