package de.visualtasker.emscript.contract

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandContractInvariantTest {
    @Test
    fun `valid command set satisfies all contract invariants`() {
        val optional = ParameterDefinition(
            id = ParameterId("timeout"),
            type = CoreTypes.NUMBER.ref,
            required = false,
            defaultValue = ContractValue.NumberValue("500"),
        )
        val namedOnlyRequired = ParameterDefinition(
            id = ParameterId("mode"),
            type = CoreTypes.STRING.ref,
            required = true,
            positionalAllowed = false,
        )
        val action = command(
            id = "core.wait",
            name = "wait",
            semanticClass = SemanticClass.ACTION,
            parameters = listOf(optional, namedOnlyRequired),
            returnType = CoreTypes.VOID.ref,
        )
        val query = command(
            id = "core.isReady",
            name = "isReady",
            semanticClass = SemanticClass.QUERY,
            returnType = CoreTypes.BOOL.ref,
            effects = listOf(CommandSideEffect.DEVICE),
        )

        val diagnostics = LanguageContractValidator.validate(
            EmscriptV1LanguageCore.definition,
            listOf(action, query),
        )

        assertTrue(diagnostics.toString(), diagnostics.isEmpty())
    }

    @Test
    fun `validator reports identity alias parameter type lifecycle and projection violations`() {
        val versionOne = LanguageVersion.V1_0
        val versionBefore = LanguageVersion(0, 9, 0)
        val badParameters = listOf(
            ParameterDefinition(
                id = ParameterId("value"),
                type = CoreTypes.NUMBER.ref,
                required = false,
                defaultValue = ContractValue.StringValue("wrong"),
                variadic = true,
            ),
            ParameterDefinition(
                id = ParameterId("value"),
                type = CoreTypes.VOID.ref,
                required = true,
                positionalAllowed = true,
                namedAllowed = false,
            ),
            ParameterDefinition(
                id = ParameterId("hidden"),
                type = CoreTypes.STRING.ref,
                required = false,
                positionalAllowed = false,
                namedAllowed = false,
            ),
        )
        val first = command(
            id = "bad.command",
            name = "Bad.Command",
            semanticClass = SemanticClass.QUERY,
            parameters = badParameters,
            returnType = CoreTypes.VOID.ref,
            aliases = listOf(
                CommandAliasDefinition(
                    name = "sharedAlias",
                    sinceVersion = versionOne,
                    deprecatedSince = versionBefore,
                    removedSince = versionBefore,
                ),
            ),
            effects = listOf(CommandSideEffect.STATE, CommandSideEffect.STATE),
            capabilities = listOf(CapabilityId("core.test"), CapabilityId("core.test")),
            lifecycle = DefinitionLifecycle(
                sinceVersion = versionOne,
                deprecatedSince = versionBefore,
                removedSince = versionBefore,
                replacementCommandId = CommandId("bad.command"),
            ),
        )
        val second = command(
            id = "bad.command",
            name = "Bad.Command",
            semanticClass = SemanticClass.PROJECTION,
            returnType = CoreTypes.VOID.ref,
            aliases = listOf(alias("sharedAlias"), alias("wait")),
        )
        val canonicalOwner = command(
            id = "core.wait",
            name = "wait",
            semanticClass = SemanticClass.ACTION,
            returnType = CoreTypes.VOID.ref,
        )

        val codes = LanguageContractValidator.validate(
            EmscriptV1LanguageCore.definition,
            listOf(first, second, canonicalOwner),
        ).map { it.code }.toSet()

        val expected = setOf(
            ContractDiagnosticCode.DUPLICATE_COMMAND_ID,
            ContractDiagnosticCode.DUPLICATE_CANONICAL_NAME,
            ContractDiagnosticCode.AMBIGUOUS_ALIAS,
            ContractDiagnosticCode.ALIAS_CANONICAL_COLLISION,
            ContractDiagnosticCode.DUPLICATE_PARAMETER_ID,
            ContractDiagnosticCode.REQUIRED_POSITIONAL_AFTER_OPTIONAL,
            ContractDiagnosticCode.VOID_PARAMETER_TYPE,
            ContractDiagnosticCode.QUERY_WITH_VOID_RETURN,
            ContractDiagnosticCode.PROJECTION_AS_COMMAND,
            ContractDiagnosticCode.INVALID_CANONICAL_COMMAND_NAME,
            ContractDiagnosticCode.DEFAULT_TYPE_MISMATCH,
            ContractDiagnosticCode.SELF_REPLACEMENT,
            ContractDiagnosticCode.DEPRECATED_BEFORE_SINCE,
            ContractDiagnosticCode.REMOVED_BEFORE_SINCE,
            ContractDiagnosticCode.LEGACY_ALIAS_REMOVED_BEFORE_V2,
            ContractDiagnosticCode.PARAMETER_WITHOUT_CALL_FORM,
            ContractDiagnosticCode.VARIADIC_PARAMETER_NOT_LAST,
            ContractDiagnosticCode.DUPLICATE_SIDE_EFFECT,
            ContractDiagnosticCode.DUPLICATE_CAPABILITY,
        )
        assertTrue("Missing ${expected - codes}; actual=$codes", codes.containsAll(expected))
    }

    @Test
    fun `actions may return Void while queries may not`() {
        val action = command(
            id = "core.action",
            name = "action",
            semanticClass = SemanticClass.ACTION,
            returnType = CoreTypes.VOID.ref,
        )
        val query = command(
            id = "core.query",
            name = "query",
            semanticClass = SemanticClass.QUERY,
            returnType = CoreTypes.VOID.ref,
        )

        val diagnostics = LanguageContractValidator.validate(
            EmscriptV1LanguageCore.definition,
            listOf(action, query),
        )

        assertFalse(diagnostics.any { it.path.startsWith("commands.core.action") })
        assertTrue(diagnostics.any { it.code == ContractDiagnosticCode.QUERY_WITH_VOID_RETURN })
    }

    @Test
    fun `non Void command definitions are expression capable independent of command name`() {
        val query = command(
            id = "test.queryString",
            name = "test.queryString",
            semanticClass = SemanticClass.QUERY,
            returnType = CoreTypes.STRING.ref,
        )
        val action = command(
            id = "test.action",
            name = "test.action",
            semanticClass = SemanticClass.ACTION,
            returnType = CoreTypes.VOID.ref,
        )

        assertTrue(query.canBeUsedAsExpression())
        assertFalse(action.canBeUsedAsExpression())
    }

    private fun command(
        id: String,
        name: String,
        semanticClass: SemanticClass,
        parameters: List<ParameterDefinition> = emptyList(),
        returnType: LanguageTypeRef,
        aliases: List<CommandAliasDefinition> = emptyList(),
        effects: List<CommandSideEffect> = listOf(CommandSideEffect.NONE),
        capabilities: List<CapabilityId> = emptyList(),
        lifecycle: DefinitionLifecycle = DefinitionLifecycle(LanguageVersion.V1_0),
    ) = CommandDefinition(
        id = CommandId(id),
        canonicalName = name,
        aliases = aliases,
        semanticClass = semanticClass,
        domain = CommandDomains.CORE,
        parameters = parameters,
        returnType = returnType,
        sideEffects = effects,
        requiredCapabilities = capabilities,
        lifecycle = lifecycle,
        documentation = CommandDocumentation("Test command"),
    )

    private fun alias(name: String) = CommandAliasDefinition(
        name = name,
        sinceVersion = LanguageVersion.V1_0,
        deprecatedSince = LanguageVersion.V1_0,
        removedSince = LanguageVersion.V2_0,
    )
}
