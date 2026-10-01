package de.visualtasker.emscript.contract

object EmscriptV1CommandIds {
    val EVENT_START = CommandId("event.start")
    val ACTION_WAIT = CommandId("action.wait")
    val FEEDBACK_BEEP = CommandId("feedback.beep")
}

/** Native EMScript v1 command definitions. Add commands here only after their migration slice is complete. */
object EmscriptV1Commands {
    val EVENT_START = CommandDefinition(
        id = EmscriptV1CommandIds.EVENT_START,
        canonicalName = "onStart",
        aliases = listOf(
            CommandAliasDefinition(
                name = "EVENT.ON_START",
                sinceVersion = LanguageVersion.V1_0,
                deprecatedSince = LanguageVersion.V1_0,
                removedSince = LanguageVersion.V2_0,
                legacyOnly = true,
            ),
            CommandAliasDefinition(
                name = "em_on_start",
                sinceVersion = LanguageVersion.V1_0,
                deprecatedSince = LanguageVersion.V1_0,
                removedSince = LanguageVersion.V2_0,
                legacyOnly = true,
            ),
        ),
        semanticClass = SemanticClass.EVENT,
        domain = CommandDomainId("event"),
        family = CommandFamilyId("event"),
        variant = CommandVariantId("onStart"),
        parameters = emptyList(),
        returnType = CoreTypes.VOID.ref,
        sideEffects = listOf(CommandSideEffect.STATE),
        requiredCapabilities = listOf(CapabilityId("capability.core")),
        provider = null,
        lifecycle = DefinitionLifecycle(LanguageVersion.V1_0),
        documentation = CommandDocumentation(
            summary = "Defines the implicit single workflow entry point.",
            details = "The current source format has no explicit onStart statement; import creates one root block and serialization omits it.",
        ),
    )

    val WAIT = CommandDefinition(
        id = EmscriptV1CommandIds.ACTION_WAIT,
        canonicalName = "wait",
        aliases = listOf(
            CommandAliasDefinition(
                name = "WAIT",
                sinceVersion = LanguageVersion.V1_0,
                deprecatedSince = LanguageVersion.V1_0,
                removedSince = LanguageVersion.V2_0,
                legacyOnly = true,
            ),
        ),
        semanticClass = SemanticClass.ACTION,
        domain = CommandDomainId("action"),
        family = CommandFamilyId("timing"),
        variant = CommandVariantId("wait"),
        parameters = listOf(
            ParameterDefinition(
                id = ParameterId("ms"),
                type = CoreTypes.NUMBER.ref,
                required = true,
                defaultValue = ContractValue.NumberValue("500"),
                documentation = "Wait duration in milliseconds.",
            ),
        ),
        returnType = CoreTypes.VOID.ref,
        sideEffects = listOf(CommandSideEffect.TRACE),
        requiredCapabilities = listOf(
            CapabilityId("capability.core"),
            CapabilityId("capability.timing"),
        ),
        provider = null,
        lifecycle = DefinitionLifecycle(LanguageVersion.V1_0),
        documentation = CommandDocumentation(
            summary = "Suspends workflow execution for the requested duration.",
            examples = listOf("wait(500)"),
        ),
    )

    val BEEP = CommandDefinition(
        id = EmscriptV1CommandIds.FEEDBACK_BEEP,
        canonicalName = "beep",
        aliases = listOf(
            CommandAliasDefinition(
                name = "BEEP",
                sinceVersion = LanguageVersion.V1_0,
                deprecatedSince = LanguageVersion.V1_0,
                removedSince = LanguageVersion.V2_0,
                legacyOnly = true,
            ),
        ),
        semanticClass = SemanticClass.ACTION,
        domain = CommandDomains.FEEDBACK,
        family = CommandFamilyId("feedback"),
        variant = CommandVariantId("beep"),
        parameters = listOf(
            ParameterDefinition(
                id = ParameterId("frequency"),
                type = CoreTypes.NUMBER.ref,
                required = true,
                defaultValue = ContractValue.NumberValue("1000"),
                documentation = "Tone frequency in hertz.",
            ),
            ParameterDefinition(
                id = ParameterId("durationMs"),
                type = CoreTypes.NUMBER.ref,
                required = true,
                defaultValue = ContractValue.NumberValue("200"),
                documentation = "Tone duration in milliseconds.",
            ),
            ParameterDefinition(
                id = ParameterId("volume"),
                type = CoreTypes.NUMBER.ref,
                required = true,
                defaultValue = ContractValue.NumberValue("100"),
                documentation = "Volume percentage.",
            ),
        ),
        returnType = CoreTypes.VOID.ref,
        sideEffects = listOf(CommandSideEffect.DEVICE),
        requiredCapabilities = listOf(
            CapabilityId("capability.core"),
            CapabilityId("capability.feedback"),
        ),
        provider = null,
        lifecycle = DefinitionLifecycle(LanguageVersion.V1_0),
        documentation = CommandDocumentation(
            summary = "Plays a tone through the existing feedback runtime.",
            examples = listOf("beep()", "beep(880, 150, 75)"),
        ),
    )

    val ALL: List<CommandDefinition> = listOf(EVENT_START, WAIT, BEEP)
}
