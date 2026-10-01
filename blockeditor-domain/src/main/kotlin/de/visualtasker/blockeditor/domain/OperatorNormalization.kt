package de.visualtasker.blockeditor.domain

import de.visualtasker.emscript.contract.EmscriptV1OperatorIds
import de.visualtasker.emscript.contract.EmscriptV1Operators
import de.visualtasker.emscript.contract.OperatorId
import java.util.Locale

enum class CompareOperator(val operatorId: OperatorId) {
    EQUAL(EmscriptV1OperatorIds.EQUAL),
    NOT_EQUAL(EmscriptV1OperatorIds.NOT_EQUAL),
    LESS(EmscriptV1OperatorIds.LESS),
    LESS_OR_EQUAL(EmscriptV1OperatorIds.LESS_OR_EQUAL),
    GREATER(EmscriptV1OperatorIds.GREATER),
    GREATER_OR_EQUAL(EmscriptV1OperatorIds.GREATER_OR_EQUAL),
    ;

    val symbol: String
        get() = EmscriptV1Operators.requireDefinition(operatorId).symbol
}

enum class ArithmeticOperator(val operatorId: OperatorId) {
    ADD(EmscriptV1OperatorIds.ADD),
    SUB(EmscriptV1OperatorIds.SUBTRACT),
    MUL(EmscriptV1OperatorIds.MULTIPLY),
    DIV(EmscriptV1OperatorIds.DIVIDE),
    MOD(EmscriptV1OperatorIds.MODULO),
    ;

    val symbol: String
        get() = EmscriptV1Operators.requireDefinition(operatorId).symbol
}

sealed interface NormalizedOperator {
    data class Compare(val value: CompareOperator) : NormalizedOperator
    data class Arithmetic(val value: ArithmeticOperator) : NormalizedOperator
}

val NormalizedOperator.operatorId: OperatorId
    get() = when (this) {
        is NormalizedOperator.Compare -> value.operatorId
        is NormalizedOperator.Arithmetic -> value.operatorId
    }

val NormalizedOperator.canonicalSymbol: String
    get() = EmscriptV1Operators.requireDefinition(operatorId).symbol

object OperatorNormalization {
    private val stableValues: Map<String, NormalizedOperator> = buildMap {
        CompareOperator.entries.forEach { put(it.operatorId.value, NormalizedOperator.Compare(it)) }
        ArithmeticOperator.entries.forEach { put(it.operatorId.value, NormalizedOperator.Arithmetic(it)) }
    }

    private val legacyValues: Map<String, NormalizedOperator> = mapOf(
        "EQUAL" to NormalizedOperator.Compare(CompareOperator.EQUAL),
        "EQ" to NormalizedOperator.Compare(CompareOperator.EQUAL),
        "==" to NormalizedOperator.Compare(CompareOperator.EQUAL),
        "NOT_EQUAL" to NormalizedOperator.Compare(CompareOperator.NOT_EQUAL),
        "NEQ" to NormalizedOperator.Compare(CompareOperator.NOT_EQUAL),
        "NE" to NormalizedOperator.Compare(CompareOperator.NOT_EQUAL),
        "!=" to NormalizedOperator.Compare(CompareOperator.NOT_EQUAL),
        "LESS" to NormalizedOperator.Compare(CompareOperator.LESS),
        "LT" to NormalizedOperator.Compare(CompareOperator.LESS),
        "<" to NormalizedOperator.Compare(CompareOperator.LESS),
        "LESS_OR_EQUAL" to NormalizedOperator.Compare(CompareOperator.LESS_OR_EQUAL),
        "LTE" to NormalizedOperator.Compare(CompareOperator.LESS_OR_EQUAL),
        "<=" to NormalizedOperator.Compare(CompareOperator.LESS_OR_EQUAL),
        "GREATER" to NormalizedOperator.Compare(CompareOperator.GREATER),
        "GT" to NormalizedOperator.Compare(CompareOperator.GREATER),
        ">" to NormalizedOperator.Compare(CompareOperator.GREATER),
        "GREATER_OR_EQUAL" to NormalizedOperator.Compare(CompareOperator.GREATER_OR_EQUAL),
        "GTE" to NormalizedOperator.Compare(CompareOperator.GREATER_OR_EQUAL),
        ">=" to NormalizedOperator.Compare(CompareOperator.GREATER_OR_EQUAL),
        "ADD" to NormalizedOperator.Arithmetic(ArithmeticOperator.ADD),
        "+" to NormalizedOperator.Arithmetic(ArithmeticOperator.ADD),
        "SUB" to NormalizedOperator.Arithmetic(ArithmeticOperator.SUB),
        "-" to NormalizedOperator.Arithmetic(ArithmeticOperator.SUB),
        "MUL" to NormalizedOperator.Arithmetic(ArithmeticOperator.MUL),
        "*" to NormalizedOperator.Arithmetic(ArithmeticOperator.MUL),
        "DIV" to NormalizedOperator.Arithmetic(ArithmeticOperator.DIV),
        "/" to NormalizedOperator.Arithmetic(ArithmeticOperator.DIV),
        "MOD" to NormalizedOperator.Arithmetic(ArithmeticOperator.MOD),
        "%" to NormalizedOperator.Arithmetic(ArithmeticOperator.MOD),
    )

    fun normalize(raw: String?): NormalizedOperator? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        return stableValues[value] ?: legacyValues[value.uppercase(Locale.ROOT)]
    }
}
