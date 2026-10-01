package de.visualtasker.emscript.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmscriptV1CommandsTest {
    @Test
    fun `event start is a valid native V1 entry contract`() {
        val event = EmscriptV1Commands.EVENT_START
        val diagnostics = LanguageContractValidator.validate(
            EmscriptV1LanguageCore.definition,
            EmscriptV1Commands.ALL,
        )

        assertTrue(diagnostics.toString(), diagnostics.isEmpty())
        assertEquals(CommandId("event.start"), event.id)
        assertEquals("onStart", event.canonicalName)
        assertEquals(SemanticClass.EVENT, event.semanticClass)
        assertEquals(CommandDomainId("event"), event.domain)
        assertTrue(event.parameters.isEmpty())
        assertEquals(CoreTypes.VOID.ref, event.returnType)
        assertEquals(listOf(CommandSideEffect.STATE), event.sideEffects)
        assertEquals(listOf(CapabilityId("capability.core")), event.requiredCapabilities)
        assertEquals(DefinitionLifecycle(LanguageVersion.V1_0), event.lifecycle)
        assertNull(event.provider)
    }

    @Test
    fun `native registry resolves event start without inventing source syntax`() {
        val registry = ImmutableLanguageDefinitionRegistry(
            core = EmscriptV1LanguageCore.definition,
            commands = EmscriptV1Commands.ALL,
        )

        assertEquals(EmscriptV1Commands.EVENT_START, registry.commandById(EmscriptV1CommandIds.EVENT_START))
        assertEquals(EmscriptV1Commands.EVENT_START, registry.commandByCanonicalName("onStart"))
        assertEquals(EmscriptV1Commands.EVENT_START, registry.commandByAcceptedName("EVENT.ON_START"))
        assertEquals(EmscriptV1Commands.EVENT_START, registry.commandByAcceptedName("em_on_start"))
    }

    @Test
    fun `wait is a valid native V1 command with a typed default`() {
        val wait = EmscriptV1Commands.WAIT
        val diagnostics = LanguageContractValidator.validate(
            EmscriptV1LanguageCore.definition,
            EmscriptV1Commands.ALL,
        )

        assertTrue(diagnostics.toString(), diagnostics.isEmpty())
        assertEquals(CommandId("action.wait"), wait.id)
        assertEquals("wait", wait.canonicalName)
        assertEquals(SemanticClass.ACTION, wait.semanticClass)
        assertEquals(CoreTypes.VOID.ref, wait.returnType)
        assertEquals(listOf(CommandSideEffect.TRACE), wait.sideEffects)
        assertEquals(DefinitionLifecycle(LanguageVersion.V1_0), wait.lifecycle)
        assertNull(wait.provider)

        val duration = wait.parameters.single()
        assertEquals(ParameterId("ms"), duration.id)
        assertEquals(CoreTypes.NUMBER.ref, duration.type)
        assertEquals(ContractValue.NumberValue("500"), duration.defaultValue)
    }

    @Test
    fun `native registry resolves wait by id canonical name and legacy alias`() {
        val registry = ImmutableLanguageDefinitionRegistry(
            core = EmscriptV1LanguageCore.definition,
            commands = EmscriptV1Commands.ALL,
        )

        assertEquals(EmscriptV1Commands.WAIT, registry.commandById(EmscriptV1CommandIds.ACTION_WAIT))
        assertEquals(EmscriptV1Commands.WAIT, registry.commandByCanonicalName("wait"))
        assertEquals(EmscriptV1Commands.WAIT, registry.commandByAcceptedName("WAIT"))
    }

    @Test
    fun `beep is a valid native V1 command with typed defaults`() {
        val beep = EmscriptV1Commands.BEEP
        val diagnostics = LanguageContractValidator.validate(
            EmscriptV1LanguageCore.definition,
            EmscriptV1Commands.ALL,
        )

        assertTrue(diagnostics.toString(), diagnostics.isEmpty())
        assertEquals(CommandId("feedback.beep"), beep.id)
        assertEquals("beep", beep.canonicalName)
        assertEquals(SemanticClass.ACTION, beep.semanticClass)
        assertEquals(CommandDomains.FEEDBACK, beep.domain)
        assertEquals(CoreTypes.VOID.ref, beep.returnType)
        assertEquals(listOf(CommandSideEffect.DEVICE), beep.sideEffects)
        assertEquals(DefinitionLifecycle(LanguageVersion.V1_0), beep.lifecycle)
        assertNull(beep.provider)
        assertEquals(
            listOf(
                ParameterId("frequency") to ContractValue.NumberValue("1000"),
                ParameterId("durationMs") to ContractValue.NumberValue("200"),
                ParameterId("volume") to ContractValue.NumberValue("100"),
            ),
            beep.parameters.map { it.id to it.defaultValue },
        )
        assertTrue(beep.parameters.all { it.type == CoreTypes.NUMBER.ref })
    }

    @Test
    fun `native registry resolves beep by id canonical name and legacy alias`() {
        val registry = ImmutableLanguageDefinitionRegistry(
            core = EmscriptV1LanguageCore.definition,
            commands = EmscriptV1Commands.ALL,
        )

        assertEquals(EmscriptV1Commands.BEEP, registry.commandById(EmscriptV1CommandIds.FEEDBACK_BEEP))
        assertEquals(EmscriptV1Commands.BEEP, registry.commandByCanonicalName("beep"))
        assertEquals(EmscriptV1Commands.BEEP, registry.commandByAcceptedName("BEEP"))
    }
}
