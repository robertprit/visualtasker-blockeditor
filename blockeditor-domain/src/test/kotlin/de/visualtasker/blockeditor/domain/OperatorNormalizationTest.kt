package de.visualtasker.blockeditor.domain

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
    fun normalizeRejectsBlankAndUnknownValues() {
        assertNull(OperatorNormalization.normalize(null))
        assertNull(OperatorNormalization.normalize("  "))
        assertNull(OperatorNormalization.normalize("???"))
    }
}
