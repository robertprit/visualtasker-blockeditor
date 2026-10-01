package de.visualtasker.emscript.contract

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmscriptV1LanguageCoreTest {
    @Test
    fun `stable operator IDs and symbol arity pairs are complete and unique`() {
        val operators = EmscriptV1LanguageCore.definition.operators

        assertEquals(EmscriptV1OperatorIds.ALL, operators.map(OperatorDefinition::id).toSet())
        assertEquals(operators.size, operators.map { it.symbol to it.arity }.toSet().size)
        operators.forEach { operator ->
            assertEquals(operator, EmscriptV1Operators.requireDefinition(operator.id))
            assertEquals(operator, EmscriptV1Operators.definition(operator.symbol, operator.arity))
        }
    }

    private val json = Json {
        encodeDefaults = true
        prettyPrint = false
    }

    @Test
    fun `v1 language core matches frozen keywords operators types and termination`() {
        val core = EmscriptV1LanguageCore.definition

        assertEquals(LanguageVersion.V1_0, core.languageVersion)
        assertEquals(
            listOf(
                "LET", "SET", "IF", "ELSEIF", "ELSE", "END", "REPEAT", "WHILE",
                "FOR", "IN", "BREAK", "CONTINUE", "FUNCTION", "RETURN", "TRUE", "FALSE",
            ),
            core.keywords.map(KeywordDefinition::canonical),
        )
        assertEquals(listOf("ELSE IF"), core.keywords.single { it.id == KeywordId("elseif") }.legacyAliases)
        assertEquals(listOf("LOOP"), core.keywords.single { it.id == KeywordId("repeat") }.legacyAliases)

        val expectedOperators = mapOf(
            "add" to Triple("+", 4, OperatorArity.BINARY),
            "subtract" to Triple("-", 4, OperatorArity.BINARY),
            "multiply" to Triple("*", 3, OperatorArity.BINARY),
            "divide" to Triple("/", 3, OperatorArity.BINARY),
            "modulo" to Triple("%", 3, OperatorArity.BINARY),
            "less" to Triple("<", 5, OperatorArity.BINARY),
            "lessOrEqual" to Triple("<=", 5, OperatorArity.BINARY),
            "greater" to Triple(">", 5, OperatorArity.BINARY),
            "greaterOrEqual" to Triple(">=", 5, OperatorArity.BINARY),
            "equal" to Triple("==", 6, OperatorArity.BINARY),
            "notEqual" to Triple("!=", 6, OperatorArity.BINARY),
            "and" to Triple("&&", 7, OperatorArity.BINARY),
            "or" to Triple("||", 8, OperatorArity.BINARY),
            "not" to Triple("!", 2, OperatorArity.UNARY),
            "negate" to Triple("-", 2, OperatorArity.UNARY),
        )
        assertEquals(expectedOperators.keys, core.operators.map { it.id.value }.toSet())
        core.operators.forEach { operator ->
            val expected = expectedOperators.getValue(operator.id.value)
            assertEquals(expected.first, operator.symbol)
            assertEquals(expected.second, operator.precedenceRank)
            assertEquals(expected.third, operator.arity)
            assertEquals(
                if (operator.arity == OperatorArity.UNARY) OperatorAssociativity.RIGHT else OperatorAssociativity.LEFT,
                operator.associativity,
            )
        }

        assertEquals(listOf("String", "Number", "Bool", "Any", "Void"), core.coreTypes.map { it.canonicalName })
        assertFalse(CoreTypes.VOID.storable)
        assertFalse(CoreTypes.VOID.parameterAllowed)
        assertEquals(StatementTerminator.NEWLINE, core.statementTermination.canonical)
        assertEquals(
            listOf(StatementTerminator.NEWLINE, StatementTerminator.SEMICOLON),
            core.statementTermination.accepted,
        )
        assertTrue(core.statementTermination.serializerOmitsOptionalSemicolons)
        assertFalse(core.statementTermination.newlineTerminatesInsideOpenDelimiters)
    }

    @Test
    fun `language core serialization is deterministic and process independent`() {
        val first = json.encodeToString(EmscriptV1LanguageCore.definition)
        val second = json.encodeToString(EmscriptV1LanguageCore.definition)
        val decoded = json.decodeFromString<LanguageCoreDefinition>(first)

        assertEquals(first, second)
        assertEquals(EmscriptV1LanguageCore.definition, decoded)
        assertFalse(first.contains("android"))
        assertFalse(first.contains("Context"))
    }

    @Test
    fun `core validator detects duplicate operator and type identities`() {
        val core = EmscriptV1LanguageCore.definition
        val invalid = core.copy(
            operators = core.operators + core.operators.first().copy(id = OperatorId("duplicate")) + core.operators.first(),
            coreTypes = core.coreTypes + core.coreTypes.first().copy(id = TypeId("duplicate.type")),
        )

        val codes = LanguageContractValidator.validate(invalid).map { it.code }.toSet()

        assertTrue(ContractDiagnosticCode.DUPLICATE_OPERATOR_ID in codes)
        assertTrue(ContractDiagnosticCode.DUPLICATE_OPERATOR_SYMBOL_ARITY in codes)
        assertTrue(ContractDiagnosticCode.DUPLICATE_TYPE_NAME in codes)
    }
}
