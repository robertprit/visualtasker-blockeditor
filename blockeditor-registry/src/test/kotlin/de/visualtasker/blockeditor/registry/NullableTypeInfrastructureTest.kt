package de.visualtasker.blockeditor.registry

import de.visualtasker.emscript.contract.CoreTypes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NullableTypeInfrastructureTest {
    @Test
    fun connectorCompatibilityPreservesNullabilityAndAnyRules() {
        assertTrue(WorkspaceValueTypeSystem.isCompatible("String", setOf("String?")))
        assertTrue(WorkspaceValueTypeSystem.isCompatible("String?", setOf("String?")))
        assertFalse(WorkspaceValueTypeSystem.isCompatible("String?", setOf("String")))
        assertFalse(WorkspaceValueTypeSystem.isCompatible("String?", setOf("Any")))
        assertTrue(WorkspaceValueTypeSystem.isCompatible("String", setOf("Any?")))
        assertTrue(WorkspaceValueTypeSystem.isCompatible("String?", setOf("Any?")))
    }

    @Test
    fun syntheticNullableReporterIsExpressionCapable() {
        val entry = CommandCatalogEntry(
            id = "test.stringNullable",
            canonicalName = "test.stringNullable",
            kind = CommandCatalogKind.REPORTER,
            category = "test",
            returnType = "String?",
            sideEffect = CommandSideEffect.NONE,
            capabilities = setOf(CommandCapability.CORE),
            block = CommandBlockBinding(BlockTypes.EMSCRIPT_COMMAND_PREFIX + "test.stringNullable"),
        )
        val definition = entry.toGeneratedBlockDefinition()

        assertTrue(entry.canBeUsedAsExpression())
        assertTrue(definition.isReporter)
        assertTrue(definition.outputType == "String?")
        assertTrue(
            WorkspaceValueTypeSystem.isCompatible(
                CoreTypes.nullable(CoreTypes.STRING.ref),
                setOf(CoreTypes.nullable(CoreTypes.STRING.ref)),
            ),
        )
    }
}
