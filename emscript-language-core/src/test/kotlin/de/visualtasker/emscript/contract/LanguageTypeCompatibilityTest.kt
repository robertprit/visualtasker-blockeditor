package de.visualtasker.emscript.contract

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageTypeCompatibilityTest {
    @Test
    fun concreteValuesAreAssignableToAny() {
        assertTrue(LanguageTypeCompatibility.isAssignable(CoreTypes.STRING.ref, CoreTypes.ANY.ref))
        assertTrue(LanguageTypeCompatibility.isAssignable(CoreTypes.NUMBER.ref, CoreTypes.ANY.ref))
        assertTrue(LanguageTypeCompatibility.isAssignable(CoreTypes.BOOL.ref, CoreTypes.ANY.ref))
    }

    @Test
    fun anyIsNotAssignableToConcreteTypes() {
        assertFalse(LanguageTypeCompatibility.isAssignable(CoreTypes.ANY.ref, CoreTypes.STRING.ref))
        assertFalse(LanguageTypeCompatibility.isAssignable(CoreTypes.ANY.ref, CoreTypes.NUMBER.ref))
        assertFalse(LanguageTypeCompatibility.isAssignable(CoreTypes.ANY.ref, CoreTypes.BOOL.ref))
    }

    @Test
    fun workspaceAliasesMapToTheSameCoreTypes() {
        assertTrue(
            LanguageTypeCompatibility.fromWorkspaceName("Text") ==
                LanguageTypeCompatibility.fromWorkspaceName("String"),
        )
        assertTrue(
            LanguageTypeCompatibility.fromWorkspaceName("Boolean") ==
                LanguageTypeCompatibility.fromWorkspaceName("Bool"),
        )
    }

    @Test
    fun nullableAssignabilityIsOrthogonalToTheBaseType() {
        val nullableString = CoreTypes.nullable(CoreTypes.STRING.ref)
        val nullableNumber = CoreTypes.nullable(CoreTypes.NUMBER.ref)
        val nullableBool = CoreTypes.nullable(CoreTypes.BOOL.ref)

        assertTrue(LanguageTypeCompatibility.isAssignable(CoreTypes.STRING.ref, nullableString))
        assertTrue(LanguageTypeCompatibility.isAssignable(nullableString, nullableString))
        assertFalse(LanguageTypeCompatibility.isAssignable(nullableString, CoreTypes.STRING.ref))
        assertTrue(LanguageTypeCompatibility.isAssignable(CoreTypes.NUMBER.ref, nullableNumber))
        assertFalse(LanguageTypeCompatibility.isAssignable(nullableNumber, CoreTypes.NUMBER.ref))
        assertTrue(LanguageTypeCompatibility.isAssignable(CoreTypes.BOOL.ref, nullableBool))
        assertFalse(LanguageTypeCompatibility.isAssignable(nullableBool, CoreTypes.BOOL.ref))
        assertFalse(LanguageTypeCompatibility.isAssignable(nullableString, nullableNumber))
        assertFalse(LanguageTypeCompatibility.isAssignable(nullableNumber, nullableBool))
    }

    @Test
    fun nullableAnyPreservesAbsenceInsteadOfSwallowingIt() {
        val nullableString = CoreTypes.nullable(CoreTypes.STRING.ref)
        val nullableAny = CoreTypes.nullable(CoreTypes.ANY.ref)

        assertTrue(LanguageTypeCompatibility.isAssignable(CoreTypes.STRING.ref, CoreTypes.ANY.ref))
        assertFalse(LanguageTypeCompatibility.isAssignable(nullableString, CoreTypes.ANY.ref))
        assertTrue(LanguageTypeCompatibility.isAssignable(CoreTypes.STRING.ref, nullableAny))
        assertTrue(LanguageTypeCompatibility.isAssignable(nullableString, nullableAny))
    }

    @Test
    fun nullableWorkspaceAndSourceNamesRoundtrip() {
        listOf("String?", "Number?", "Bool?", "Any?").forEach { source ->
            val parsed = requireNotNull(LanguageTypeCompatibility.fromWorkspaceName(source))
            assertTrue(parsed is LanguageTypeRef.Nullable)
            assertEquals(source, LanguageTypeCompatibility.sourceName(parsed))
            assertEquals(parsed, LanguageTypeCompatibility.fromWorkspaceName(LanguageTypeCompatibility.workspaceName(parsed)))
        }
    }
}
