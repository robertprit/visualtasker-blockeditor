package de.visualtasker.emscript.contract

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NullableCommandContractTest {
    @Test
    fun nullableTypeRefSerializesWithoutTypeSpecificVariants() {
        val type = CoreTypes.nullable(CoreTypes.STRING.ref)
        val encoded = Json.encodeToString<LanguageTypeRef>(type)

        assertEquals(type, Json.decodeFromString<LanguageTypeRef>(encoded))
        assertTrue(encoded.contains("nullable"))
        assertFalse(encoded.contains("STRING_NULLABLE"))
    }

    @Test
    fun commandParametersAndReturnsCarryNullableTypeRefs() {
        val nullableString = CoreTypes.nullable(CoreTypes.STRING.ref)
        val command = CommandDefinition(
            id = CommandId("test.stringNullable"),
            canonicalName = "test.stringNullable",
            semanticClass = SemanticClass.QUERY,
            domain = CommandDomainId("test"),
            parameters = listOf(
                ParameterDefinition(ParameterId("value"), nullableString, required = true),
            ),
            returnType = nullableString,
            sideEffects = listOf(CommandSideEffect.NONE),
            lifecycle = DefinitionLifecycle(LanguageVersion(1, 0)),
            documentation = CommandDocumentation("Synthetic nullable contract"),
        )

        assertTrue(command.canBeUsedAsExpression())
        assertEquals(nullableString, command.parameters.single().type)
        assertEquals(nullableString, command.returnType)
        assertFalse(EmscriptV1Commands.WAIT.canBeUsedAsExpression())
    }
}
