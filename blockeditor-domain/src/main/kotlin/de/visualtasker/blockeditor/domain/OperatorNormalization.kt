package de.visualtasker.blockeditor.domain

enum class CompareOperator(val symbol: String) {
    EQUAL("=="),
    NOT_EQUAL("!="),
    LESS("<"),
    LESS_OR_EQUAL("<="),
    GREATER(">"),
    GREATER_OR_EQUAL(">="),
}

enum class ArithmeticOperator(val symbol: String) {
    ADD("+"),
    SUB("-"),
    MUL("*"),
    DIV("/"),
    MOD("%"),
}

sealed interface NormalizedOperator {
    data class Compare(val value: CompareOperator) : NormalizedOperator
    data class Arithmetic(val value: ArithmeticOperator) : NormalizedOperator
}

object OperatorNormalization {
    fun normalize(raw: String?): NormalizedOperator? {
        val normalized = raw?.trim()?.uppercase().orEmpty()
        if (normalized.isBlank()) return null
        return when (normalized) {
            "EQUAL", "EQ", "==" -> NormalizedOperator.Compare(CompareOperator.EQUAL)
            "NOT_EQUAL", "NEQ", "NE", "!=" -> NormalizedOperator.Compare(CompareOperator.NOT_EQUAL)
            "LESS", "LT", "<" -> NormalizedOperator.Compare(CompareOperator.LESS)
            "LESS_OR_EQUAL", "LTE", "<=" -> NormalizedOperator.Compare(CompareOperator.LESS_OR_EQUAL)
            "GREATER", "GT", ">" -> NormalizedOperator.Compare(CompareOperator.GREATER)
            "GREATER_OR_EQUAL", "GTE", ">=" -> NormalizedOperator.Compare(CompareOperator.GREATER_OR_EQUAL)
            "ADD", "+" -> NormalizedOperator.Arithmetic(ArithmeticOperator.ADD)
            "SUB", "-" -> NormalizedOperator.Arithmetic(ArithmeticOperator.SUB)
            "MUL", "*" -> NormalizedOperator.Arithmetic(ArithmeticOperator.MUL)
            "DIV", "/" -> NormalizedOperator.Arithmetic(ArithmeticOperator.DIV)
            "MOD", "%" -> NormalizedOperator.Arithmetic(ArithmeticOperator.MOD)
            else -> null
        }
    }
}
