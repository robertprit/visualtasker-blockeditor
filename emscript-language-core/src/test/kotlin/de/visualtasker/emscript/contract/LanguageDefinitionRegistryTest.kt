package de.visualtasker.emscript.contract

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageDefinitionRegistryTest {
    @Test
    fun `registry resolves stable IDs canonical names aliases types and operators`() {
        val clickText = command(
            id = "input.clickText",
            name = "clickText",
            semanticClass = SemanticClass.ACTION,
            returnType = CoreTypes.VOID.ref,
            aliases = listOf(alias("CLICK_TEXT")),
            effects = listOf(CommandSideEffect.INPUT, CommandSideEffect.UI),
            capabilities = listOf(CapabilityId("a11y.click")),
        )
        val query = command(
            id = "perception.screenContains",
            name = "screenContains",
            semanticClass = SemanticClass.QUERY,
            returnType = CoreTypes.BOOL.ref,
            effects = listOf(CommandSideEffect.UI),
        )
        val event = command(
            id = "event.onStart",
            name = "onStart",
            semanticClass = SemanticClass.EVENT,
            returnType = CoreTypes.VOID.ref,
        )
        val projection = ProjectionDefinition(
            id = ProjectionId("projection.groupStart"),
            canonicalName = "@group.start",
            extent = ProjectionExtent.RANGE_START,
            stablePairIdRequired = true,
            lifecycle = DefinitionLifecycle(LanguageVersion.V1_0),
            documentation = "Starts a runtime-inert projection range.",
        )
        val pointType = TypeDefinition(TypeId("geometry.point"), "Point", TypeDefinitionKind.DOMAIN)
        val registry = ImmutableLanguageDefinitionRegistry(
            core = EmscriptV1LanguageCore.definition,
            commands = listOf(clickText, query, event),
            projections = listOf(projection),
            domainTypes = listOf(pointType),
        )

        assertSame(clickText, registry.commandById(CommandId("input.clickText")))
        assertSame(clickText, registry.commandByCanonicalName("clickText"))
        assertSame(clickText, registry.commandByAcceptedName("CLICK_TEXT"))
        assertSame(query, registry.commandByAcceptedName("screenContains"))
        assertSame(pointType, registry.typeByCanonicalName("Point"))
        assertEquals(OperatorId("negate"), registry.operatorBySymbol("-", OperatorArity.UNARY)?.id)
        assertEquals(OperatorId("subtract"), registry.operatorBySymbol("-", OperatorArity.BINARY)?.id)
        assertEquals(listOf(projection), registry.projections())
        assertNull(registry.commandByCanonicalName(projection.canonicalName))
        assertFalse(projection.runtimeDispatchable)
        assertEquals(SemanticClass.PROJECTION, projection.semanticClass)
    }

    @Test
    fun `command contracts serialize deterministically without Android or UI identity`() {
        val command = command(
            id = "tasker.runTask",
            name = "tasker.runTask",
            semanticClass = SemanticClass.ACTION,
            returnType = CoreTypes.VOID.ref,
            aliases = listOf(alias("Tasker.runTask")),
            effects = listOf(CommandSideEffect.EXTERNAL_PROVIDER, CommandSideEffect.STATE),
            capabilities = listOf(CapabilityId("tasker.run")),
            provider = ProviderId("tasker"),
        )
        val json = Json { encodeDefaults = true }
        val first = json.encodeToString(command)
        val second = json.encodeToString(command)
        val decoded = json.decodeFromString<CommandDefinition>(first)

        assertEquals(first, second)
        assertEquals(command, decoded)
        assertFalse(first.contains("android"))
        assertFalse(first.contains("Context"))
        assertTrue(command.aliases.all(CommandAliasDefinition::legacyOnly))
        assertTrue(command.aliases.none { it.name == command.canonicalName })
    }

    private fun command(
        id: String,
        name: String,
        semanticClass: SemanticClass,
        returnType: LanguageTypeRef,
        aliases: List<CommandAliasDefinition> = emptyList(),
        effects: List<CommandSideEffect> = listOf(CommandSideEffect.NONE),
        capabilities: List<CapabilityId> = emptyList(),
        provider: ProviderId? = null,
    ) = CommandDefinition(
        id = CommandId(id),
        canonicalName = name,
        aliases = aliases,
        semanticClass = semanticClass,
        domain = if (provider == null) CommandDomains.CORE else CommandDomains.PROVIDER,
        returnType = returnType,
        sideEffects = effects,
        requiredCapabilities = capabilities,
        provider = provider,
        lifecycle = DefinitionLifecycle(LanguageVersion.V1_0),
        documentation = CommandDocumentation("Fixture command"),
    )

    private fun alias(name: String) = CommandAliasDefinition(
        name = name,
        sinceVersion = LanguageVersion.V1_0,
        deprecatedSince = LanguageVersion.V1_0,
        removedSince = LanguageVersion.V2_0,
    )
}
