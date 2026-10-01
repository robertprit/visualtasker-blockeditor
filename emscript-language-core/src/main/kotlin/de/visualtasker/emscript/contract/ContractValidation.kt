package de.visualtasker.emscript.contract

enum class ContractDiagnosticCode {
    DUPLICATE_KEYWORD_ID,
    DUPLICATE_OPERATOR_ID,
    DUPLICATE_OPERATOR_SYMBOL_ARITY,
    DUPLICATE_TYPE_ID,
    DUPLICATE_TYPE_NAME,
    DUPLICATE_COMMAND_ID,
    DUPLICATE_CANONICAL_NAME,
    ALIAS_CANONICAL_COLLISION,
    AMBIGUOUS_ALIAS,
    DUPLICATE_PARAMETER_ID,
    REQUIRED_POSITIONAL_AFTER_OPTIONAL,
    VOID_PARAMETER_TYPE,
    QUERY_WITH_VOID_RETURN,
    PROJECTION_AS_COMMAND,
    INVALID_CANONICAL_COMMAND_NAME,
    INVALID_CANONICAL_TYPE_NAME,
    DEFAULT_TYPE_MISMATCH,
    SELF_REPLACEMENT,
    DEPRECATED_BEFORE_SINCE,
    REMOVED_BEFORE_SINCE,
    REMOVED_BEFORE_DEPRECATED,
    LEGACY_ALIAS_REMOVED_BEFORE_V2,
    PARAMETER_WITHOUT_CALL_FORM,
    VARIADIC_PARAMETER_NOT_LAST,
    DUPLICATE_SIDE_EFFECT,
    DUPLICATE_CAPABILITY,
}

data class ContractDiagnostic(
    val code: ContractDiagnosticCode,
    val path: String,
    val message: String,
)

object LanguageContractValidator {
    private val lowerCamelSegment = Regex("[a-z][A-Za-z0-9]*")
    private val pascalName = Regex("[A-Z][A-Za-z0-9]*")

    fun validate(
        core: LanguageCoreDefinition,
        commands: List<CommandDefinition> = emptyList(),
        projections: List<ProjectionDefinition> = emptyList(),
    ): List<ContractDiagnostic> = buildList {
        validateCore(core)
        validateCommands(core, commands)
        validateProjections(projections)
    }

    private fun MutableList<ContractDiagnostic>.validateCore(core: LanguageCoreDefinition) {
        duplicates(core.keywords.map { it.id }).forEach {
            issue(ContractDiagnosticCode.DUPLICATE_KEYWORD_ID, "keywords.${it.value}", "Keyword ID must be unique.")
        }
        duplicates(core.operators.map { it.id }).forEach {
            issue(ContractDiagnosticCode.DUPLICATE_OPERATOR_ID, "operators.${it.value}", "Operator ID must be unique.")
        }
        duplicates(core.operators.map { it.symbol to it.arity }).forEach { (symbol, arity) ->
            issue(
                ContractDiagnosticCode.DUPLICATE_OPERATOR_SYMBOL_ARITY,
                "operators.$symbol.$arity",
                "Operator symbol and arity must resolve uniquely.",
            )
        }
        duplicates(core.coreTypes.map { it.id }).forEach {
            issue(ContractDiagnosticCode.DUPLICATE_TYPE_ID, "types.${it.value}", "Type ID must be unique.")
        }
        duplicates(core.coreTypes.map { it.canonicalName }).forEach {
            issue(ContractDiagnosticCode.DUPLICATE_TYPE_NAME, "types.$it", "Canonical type name must be unique.")
        }
        core.coreTypes.filterNot { pascalName.matches(it.canonicalName) }.forEach {
            issue(
                ContractDiagnosticCode.INVALID_CANONICAL_TYPE_NAME,
                "types.${it.id.value}",
                "Canonical type names must be PascalCase.",
            )
        }
    }

    private fun MutableList<ContractDiagnostic>.validateCommands(
        core: LanguageCoreDefinition,
        commands: List<CommandDefinition>,
    ) {
        duplicates(commands.map { it.id }).forEach {
            issue(ContractDiagnosticCode.DUPLICATE_COMMAND_ID, "commands.${it.value}", "Command ID must be unique.")
        }
        duplicates(commands.map { it.canonicalName }).forEach {
            issue(ContractDiagnosticCode.DUPLICATE_CANONICAL_NAME, "commands.$it", "Canonical name must be unique.")
        }

        val canonicalOwners = commands.associateBy { it.canonicalName }
        val aliasOwners = mutableMapOf<String, CommandDefinition>()
        commands.forEach { command ->
            if (!isCanonicalQualifiedName(command.canonicalName)) {
                issue(
                    ContractDiagnosticCode.INVALID_CANONICAL_COMMAND_NAME,
                    "commands.${command.id.value}.canonicalName",
                    "Canonical command and namespace segments must be lowerCamelCase.",
                )
            }
            if (command.semanticClass == SemanticClass.PROJECTION) {
                issue(
                    ContractDiagnosticCode.PROJECTION_AS_COMMAND,
                    "commands.${command.id.value}.semanticClass",
                    "Projection metadata must use ProjectionDefinition and cannot be runtime-dispatchable.",
                )
            }
            if (command.semanticClass == SemanticClass.QUERY && command.returnType.isVoid()) {
                issue(
                    ContractDiagnosticCode.QUERY_WITH_VOID_RETURN,
                    "commands.${command.id.value}.returnType",
                    "A query requires an explicit non-Void return type.",
                )
            }
            validateLifecycle(command.id, command.lifecycle, "commands.${command.id.value}.lifecycle")

            command.aliases.forEach { alias ->
                val canonicalOwner = canonicalOwners[alias.name]
                if (canonicalOwner != null) {
                    issue(
                        ContractDiagnosticCode.ALIAS_CANONICAL_COLLISION,
                        "commands.${command.id.value}.aliases.${alias.name}",
                        "Alias collides with a canonical command name.",
                    )
                }
                val previous = aliasOwners.putIfAbsent(alias.name, command)
                if (previous != null && previous !== command) {
                    issue(
                        ContractDiagnosticCode.AMBIGUOUS_ALIAS,
                        "aliases.${alias.name}",
                        "Alias resolves to more than one command.",
                    )
                }
                validateAliasLifecycle(alias, "commands.${command.id.value}.aliases.${alias.name}")
            }

            duplicates(command.parameters.map { it.id }).forEach {
                issue(
                    ContractDiagnosticCode.DUPLICATE_PARAMETER_ID,
                    "commands.${command.id.value}.parameters.${it.value}",
                    "Parameter IDs must be unique within a command.",
                )
            }
            validateParameterOrder(command)
            command.parameters.forEachIndexed { index, parameter ->
                val path = "commands.${command.id.value}.parameters[$index]"
                if (parameter.type.containsVoid()) {
                    issue(ContractDiagnosticCode.VOID_PARAMETER_TYPE, path, "Void cannot be a parameter type.")
                }
                if (!parameter.positionalAllowed && !parameter.namedAllowed) {
                    issue(ContractDiagnosticCode.PARAMETER_WITHOUT_CALL_FORM, path, "Parameter must allow positional or named use.")
                }
                if (parameter.variadic && index != command.parameters.lastIndex) {
                    issue(ContractDiagnosticCode.VARIADIC_PARAMETER_NOT_LAST, path, "Variadic parameter must be last.")
                }
                val default = parameter.defaultValue
                if (default != null && !LanguageTypeCompatibility.isAssignable(default.type, parameter.type)) {
                    issue(ContractDiagnosticCode.DEFAULT_TYPE_MISMATCH, path, "Default value is not compatible with parameter type.")
                }
            }
            duplicates(command.sideEffects).forEach {
                issue(ContractDiagnosticCode.DUPLICATE_SIDE_EFFECT, "commands.${command.id.value}.sideEffects", "Side effects must be unique.")
            }
            duplicates(command.requiredCapabilities).forEach {
                issue(ContractDiagnosticCode.DUPLICATE_CAPABILITY, "commands.${command.id.value}.capabilities", "Capabilities must be unique.")
            }
        }

        // A domain type can be registered later; only known core Void receives a hard parameter rule here.
        check(core.coreTypes.any { it.id == CoreTypeIds.VOID && !it.storable && !it.parameterAllowed }) {
            "The V1 core must define non-storable, non-parameter Void."
        }
    }

    private fun MutableList<ContractDiagnostic>.validateProjections(projections: List<ProjectionDefinition>) {
        projections.forEach { projection ->
            validateLifecycle(
                id = null,
                lifecycle = projection.lifecycle,
                path = "projections.${projection.id.value}.lifecycle",
            )
        }
    }

    private fun MutableList<ContractDiagnostic>.validateParameterOrder(command: CommandDefinition) {
        var optionalPositionalSeen = false
        command.parameters.forEachIndexed { index, parameter ->
            if (!parameter.positionalAllowed) return@forEachIndexed
            val optional = !parameter.required || parameter.defaultValue != null
            if (!optional && optionalPositionalSeen) {
                issue(
                    ContractDiagnosticCode.REQUIRED_POSITIONAL_AFTER_OPTIONAL,
                    "commands.${command.id.value}.parameters[$index]",
                    "Required positional parameters cannot follow optional positional parameters.",
                )
            }
            optionalPositionalSeen = optionalPositionalSeen || optional
        }
    }

    private fun MutableList<ContractDiagnostic>.validateAliasLifecycle(alias: CommandAliasDefinition, path: String) {
        val lifecycle = DefinitionLifecycle(
            sinceVersion = alias.sinceVersion,
            deprecatedSince = alias.deprecatedSince,
            removedSince = alias.removedSince,
        )
        validateLifecycle(null, lifecycle, path)
        if (alias.legacyOnly && alias.removedSince != null && alias.removedSince < LanguageVersion.V2_0) {
            issue(
                ContractDiagnosticCode.LEGACY_ALIAS_REMOVED_BEFORE_V2,
                path,
                "A V1 legacy alias cannot be removed before EMScript 2.0.",
            )
        }
    }

    private fun MutableList<ContractDiagnostic>.validateLifecycle(
        id: CommandId?,
        lifecycle: DefinitionLifecycle,
        path: String,
    ) {
        if (lifecycle.deprecatedSince != null && lifecycle.deprecatedSince < lifecycle.sinceVersion) {
            issue(ContractDiagnosticCode.DEPRECATED_BEFORE_SINCE, path, "deprecatedSince cannot precede sinceVersion.")
        }
        if (lifecycle.removedSince != null && lifecycle.removedSince < lifecycle.sinceVersion) {
            issue(ContractDiagnosticCode.REMOVED_BEFORE_SINCE, path, "removedSince cannot precede sinceVersion.")
        }
        if (
            lifecycle.removedSince != null &&
            lifecycle.deprecatedSince != null &&
            lifecycle.removedSince < lifecycle.deprecatedSince
        ) {
            issue(ContractDiagnosticCode.REMOVED_BEFORE_DEPRECATED, path, "removedSince cannot precede deprecatedSince.")
        }
        if (id != null && lifecycle.replacementCommandId == id) {
            issue(ContractDiagnosticCode.SELF_REPLACEMENT, path, "A command cannot replace itself.")
        }
    }

    private fun isCanonicalQualifiedName(name: String): Boolean =
        name.split('.').all(lowerCamelSegment::matches)

    private fun LanguageTypeRef.isVoid(): Boolean = this == CoreTypes.VOID.ref

    private fun LanguageTypeRef.containsVoid(): Boolean = when (this) {
        is LanguageTypeRef.Named -> id == CoreTypeIds.VOID
        is LanguageTypeRef.ListOf -> elementType.containsVoid()
        is LanguageTypeRef.Nullable -> baseType.containsVoid()
    }

    private fun MutableList<ContractDiagnostic>.issue(
        code: ContractDiagnosticCode,
        path: String,
        message: String,
    ) {
        add(ContractDiagnostic(code, path, message))
    }

    private fun <T> duplicates(values: List<T>): Set<T> {
        val seen = mutableSetOf<T>()
        return values.filterNot(seen::add).toSet()
    }
}
