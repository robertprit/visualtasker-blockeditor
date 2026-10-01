package de.visualtasker.blockeditor.registry

import de.visualtasker.emscript.contract.CapabilityId
import de.visualtasker.emscript.contract.CommandAliasDefinition
import de.visualtasker.emscript.contract.CommandDefinition
import de.visualtasker.emscript.contract.CommandDocumentation
import de.visualtasker.emscript.contract.CommandDomainId
import de.visualtasker.emscript.contract.CommandFamilyId
import de.visualtasker.emscript.contract.CommandId
import de.visualtasker.emscript.contract.CommandSideEffect as V1SideEffect
import de.visualtasker.emscript.contract.CommandVariantId
import de.visualtasker.emscript.contract.ContractValue
import de.visualtasker.emscript.contract.CoreTypes
import de.visualtasker.emscript.contract.DefinitionLifecycle
import de.visualtasker.emscript.contract.EmscriptV1Commands
import de.visualtasker.emscript.contract.LanguageTypeRef
import de.visualtasker.emscript.contract.LanguageVersion
import de.visualtasker.emscript.contract.ParameterDefinition
import de.visualtasker.emscript.contract.ParameterId
import de.visualtasker.emscript.contract.ProviderId
import de.visualtasker.emscript.contract.SemanticClass
import de.visualtasker.emscript.contract.TypeId
import java.util.Locale

enum class CommandBridgeIssueType {
    DUPLICATE_CANONICAL_NAME,
    AMBIGUOUS_ALIAS,
    NON_CANONICAL_NAME,
    NAMING_FAMILY_OUTLIER,
    UNTYPED_PARAMETER,
    UNTYPED_DEFAULT,
    INVALID_DEFAULT,
    DEFAULT_SOURCE_CONFLICT,
    QUERY_RETURNS_VOID,
    MISSING_RETURN_TYPE,
    CAPABILITY_PROVIDER_MIXED,
    PLUGIN_OWNER_NOT_PROVIDER,
    MISSING_CAPABILITY,
    MISSING_PROVIDER_METADATA,
    LIVE_FLAG_WITHOUT_DISPATCH,
    DISPATCH_WITHOUT_LIVE_FLAG,
    MULTIPLE_RUNTIME_DISPATCH,
    PROJECTION_MODELED_AS_COMMAND,
    CONTROL_DUPLICATE,
    REPORTER_DUPLICATE,
    LEGACY_ALIAS_REQUIRED,
    SIGNATURE_CONFLICT,
    VARIABLE_IDENTITY_MODEL_CONFLICT,
    OPERATOR_OR_LITERAL_MODEL,
    NATIVE_DEFINITION_PARITY_MISMATCH,
    UNMAPPABLE_ENTRY,
}

enum class CommandBridgeStatus {
    CLEAN,
    NORMALIZABLE,
    CONFLICT,
    UNMAPPABLE,
}

enum class CommandExpressionStatus {
    NOT_APPLICABLE,
    LOSSLESS,
    CONFLICT,
}

enum class CommandMigrationClass {
    NONE,
    CANONICAL_NORMALIZATION,
    LEGACY_ALIAS,
    CONTRACT_DECISION,
    STRUCTURAL_MIGRATION,
    PROVIDER_METADATA,
    PROJECTION_MIGRATION,
    NATIVE_V1,
}

enum class CommandLiveStatus {
    LIVE_CONFIRMED,
    LIVE_PROVIDER_DEPENDENT,
    DRY_RUN_ONLY,
    CATALOG_ONLY,
    NO_DISPATCH,
    UNKNOWN,
}

enum class BridgeConfidence {
    HIGH,
    MEDIUM,
    LOW,
}

enum class CommandNamingStatus {
    CANONICAL,
    RESOLVED_WITH_LEGACY_ALIAS,
    NEEDS_NORMALIZATION,
    CONFLICT,
}

data class CommandBridgeIssue(
    val type: CommandBridgeIssueType,
    val message: String,
    val evidence: String,
)

data class CommandDispatchEvidence(
    val status: CommandLiveStatus,
    val routes: List<String>,
    val observedResult: String,
    val evidence: String,
)

data class CommandAliasCollision(
    val inputName: String,
    val candidateIds: List<String>,
    val currentWinnerId: String,
    val reason: String,
)

data class CommandNamingAuditEntry(
    val family: String,
    val currentCommand: String,
    val siblingPattern: String,
    val proposedCanonical: String,
    val legacyAliasRequired: Boolean,
    val confidence: BridgeConfidence,
    val reason: String,
)

data class CommandDefaultAuditEntry(
    val commandId: String,
    val parameter: String,
    val declaredType: String,
    val rawDefault: String,
    val parserInterpretation: String,
    val runtimeInterpretation: String,
    val targetType: String,
    val result: String,
)

data class CommandQueryAuditEntry(
    val commandId: String,
    val currentKind: CommandCatalogKind,
    val currentReturn: String,
    val observedRuntimeResult: String,
    val blockOutput: String,
    val proposedSemanticClass: SemanticClass,
    val proposedReturnType: String,
    val evidence: String,
    val confidence: BridgeConfidence,
)

sealed interface CommandBridgeResult {
    val sourceEntry: CommandCatalogEntry
    val definition: CommandDefinition?
    val issues: List<CommandBridgeIssue>
    val status: CommandBridgeStatus
    val migrationClass: CommandMigrationClass
    val dispatchEvidence: CommandDispatchEvidence

    data class Success(
        override val sourceEntry: CommandCatalogEntry,
        override val definition: CommandDefinition,
        override val dispatchEvidence: CommandDispatchEvidence,
        override val migrationClass: CommandMigrationClass = CommandMigrationClass.NONE,
    ) : CommandBridgeResult {
        override val issues: List<CommandBridgeIssue> = emptyList()
        override val status: CommandBridgeStatus = CommandBridgeStatus.CLEAN
    }

    data class Warning(
        override val sourceEntry: CommandCatalogEntry,
        override val definition: CommandDefinition,
        override val issues: List<CommandBridgeIssue>,
        override val migrationClass: CommandMigrationClass,
        override val dispatchEvidence: CommandDispatchEvidence,
    ) : CommandBridgeResult {
        override val status: CommandBridgeStatus = CommandBridgeStatus.NORMALIZABLE
    }

    data class Conflict(
        override val sourceEntry: CommandCatalogEntry,
        override val definition: CommandDefinition?,
        override val issues: List<CommandBridgeIssue>,
        override val status: CommandBridgeStatus,
        override val migrationClass: CommandMigrationClass,
        override val dispatchEvidence: CommandDispatchEvidence,
    ) : CommandBridgeResult
}

fun CommandBridgeResult.namingStatus(): CommandNamingStatus {
    val normalization = EmscriptV1NamingNormalizations.byStableId(sourceEntry.id)
    if (normalization != null) {
        val aliasesPresent = normalization.legacyAliases.all(sourceEntry.acceptedAliases::contains)
        return if (sourceEntry.canonicalName == normalization.canonicalName && aliasesPresent) {
            CommandNamingStatus.RESOLVED_WITH_LEGACY_ALIAS
        } else {
            CommandNamingStatus.NEEDS_NORMALIZATION
        }
    }
    if (issues.any { it.type in setOf(CommandBridgeIssueType.DUPLICATE_CANONICAL_NAME, CommandBridgeIssueType.AMBIGUOUS_ALIAS) }) {
        return CommandNamingStatus.CONFLICT
    }
    return if (definition?.canonicalName != null && definition?.canonicalName != sourceEntry.canonicalName) {
        CommandNamingStatus.NEEDS_NORMALIZATION
    } else {
        CommandNamingStatus.CANONICAL
    }
}

fun CommandBridgeResult.typeStatus(): CommandTypeStatus {
    EmscriptV1TypeConflictDecisions.byStableId(sourceEntry.id)?.let { decision ->
        return if (decision.implemented) CommandTypeStatus.RESOLVED else CommandTypeStatus.CONTRACT_DECIDED
    }
    val typeIssues = setOf(
        CommandBridgeIssueType.UNTYPED_PARAMETER,
        CommandBridgeIssueType.UNTYPED_DEFAULT,
        CommandBridgeIssueType.INVALID_DEFAULT,
        CommandBridgeIssueType.DEFAULT_SOURCE_CONFLICT,
        CommandBridgeIssueType.SIGNATURE_CONFLICT,
        CommandBridgeIssueType.REPORTER_DUPLICATE,
        CommandBridgeIssueType.VARIABLE_IDENTITY_MODEL_CONFLICT,
    )
    return if (issues.any { it.type in typeIssues }) CommandTypeStatus.CONFLICT else CommandTypeStatus.CANONICAL
}

fun CommandBridgeResult.expressionStatus(): CommandExpressionStatus {
    val blockDefinition = sourceEntry.block
        ?.blockType
        ?.let(DefaultBlockRegistry::getDefinition)
        ?: return CommandExpressionStatus.NOT_APPLICABLE
    if (blockDefinition.valueInputs.isEmpty()) return CommandExpressionStatus.NOT_APPLICABLE
    val argumentNames = sourceEntry.arguments
        .filter { it.type != CommandArgumentType.STATEMENT_BODY }
        .mapTo(mutableSetOf()) { it.name }
    return if (blockDefinition.valueInputs.all { it.name in argumentNames }) {
        CommandExpressionStatus.LOSSLESS
    } else {
        CommandExpressionStatus.CONFLICT
    }
}

data class LegacyCommandBridgeAudit(
    val results: List<CommandBridgeResult>,
    val aliasCollisions: List<CommandAliasCollision>,
    val namingAudit: List<CommandNamingAuditEntry>,
    val queryAudit: List<CommandQueryAuditEntry>,
    val defaultAudit: List<CommandDefaultAuditEntry>,
) {
    init {
        require(results.map { it.sourceEntry.id }.distinct().size == results.size)
    }

    val countsByStatus: Map<CommandBridgeStatus, Int> =
        CommandBridgeStatus.entries.associateWith { status -> results.count { it.status == status } }
}

class LegacyCommandDefinitionBridge(
    private val entries: List<CommandCatalogEntry> = VisualTaskerCommandCatalog.allEntries(),
    private val dispatchEvidence: Map<String, CommandDispatchEvidence> = LegacyCommandDispatchEvidence.byCommandId(entries),
    nativeDefinitions: List<CommandDefinition> = EmscriptV1Commands.ALL,
) {
    private val nativeDefinitionsById = nativeDefinitions.associateBy { it.id.value }

    fun analyze(): LegacyCommandBridgeAudit {
        val canonicalDuplicates = entries
            .groupBy { it.canonicalName.lowercase(Locale.ROOT) }
            .filterValues { it.size > 1 }
        val aliasCollisions = findAliasCollisions(entries)
        val collisionsByEntry = aliasCollisions
            .flatMap { collision -> collision.candidateIds.map { it to collision } }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })

        val results = entries.map { entry ->
            bridge(
                entry = entry,
                duplicateCanonical = canonicalDuplicates[entry.canonicalName.lowercase(Locale.ROOT)].orEmpty(),
                aliasCollisions = collisionsByEntry[entry.id].orEmpty(),
            )
        }
        return LegacyCommandBridgeAudit(
            results = results,
            aliasCollisions = aliasCollisions,
            namingAudit = namingAudit(entries),
            queryAudit = queryAudit(results),
            defaultAudit = entries.flatMap(::defaultAudit),
        )
    }

    private fun bridge(
        entry: CommandCatalogEntry,
        duplicateCanonical: List<CommandCatalogEntry>,
        aliasCollisions: List<CommandAliasCollision>,
    ): CommandBridgeResult {
        val issues = mutableListOf<CommandBridgeIssue>()
        val resolvedDynamicTypeContract = EmscriptV1TypeConflictDecisions.byStableId(entry.id)
            ?.let { decision ->
                decision.implemented && decision.classification == TypeConflictClassification.TYPECHECKER_RULE
            } == true
        val proposedName = proposedCanonicalName(entry)
        val semanticClass = proposedSemanticClass(entry)
        val dispatch = dispatchEvidence[entry.id] ?: CommandDispatchEvidence(
            status = CommandLiveStatus.UNKNOWN,
            routes = emptyList(),
            observedResult = "unknown",
            evidence = "No explicit dispatch evidence was registered for this catalog entry.",
        )

        if (duplicateCanonical.size > 1 && entry.role == CommandCatalogRole.LANGUAGE_COMMAND) {
            issues += issue(
                CommandBridgeIssueType.DUPLICATE_CANONICAL_NAME,
                "${entry.canonicalName} is owned by ${duplicateCanonical.joinToString { it.id }}.",
                "VisualTaskerCommandCatalog groups canonical names case-insensitively and returns firstOrNull().",
            )
            if (entry.kind == CommandCatalogKind.CONTROL) {
                issues += issue(
                    CommandBridgeIssueType.CONTROL_DUPLICATE,
                    "Control variants share one command name but have different branch contracts.",
                    "${entry.id} parameters=${entry.arguments.joinToString { it.name }} block=${entry.block?.blockType}",
                )
            }
            if (entry.kind == CommandCatalogKind.REPORTER) {
                issues += issue(
                    CommandBridgeIssueType.REPORTER_DUPLICATE,
                    "Reporter entries share one command name but represent different block semantics.",
                    "${entry.id} block=${entry.block?.blockType} runtime=${entry.runtime?.dryRunBehavior}",
                )
            }
        }
        aliasCollisions
            .takeIf { entry.role == CommandCatalogRole.LANGUAGE_COMMAND }
            .orEmpty()
            .forEach { collision ->
            issues += issue(
                CommandBridgeIssueType.AMBIGUOUS_ALIAS,
                "Accepted name ${collision.inputName} resolves to ${collision.candidateIds.joinToString()}.",
                "Current winner is ${collision.currentWinnerId} because lookup uses firstOrNull().",
            )
        }

        if (!isCanonicalQualifiedName(entry.canonicalName)) {
            issues += issue(
                CommandBridgeIssueType.NON_CANONICAL_NAME,
                "${entry.canonicalName} does not satisfy the V1 lowerCamelCase namespace rule.",
                "LanguageContractValidator requires every qualified-name segment to be lowerCamelCase.",
            )
        }
        if (proposedName != entry.canonicalName) {
            issues += issue(
                CommandBridgeIssueType.NAMING_FAMILY_OUTLIER,
                "Proposed V1 canonical name is $proposedName.",
                namingReason(entry),
            )
            issues += issue(
                CommandBridgeIssueType.LEGACY_ALIAS_REQUIRED,
                "${entry.canonicalName} must remain an import-only legacy spelling.",
                "Changing a canonical spelling without an alias would break existing scripts.",
            )
        }

        entry.arguments.forEach { argument ->
            if (
                argument.type == CommandArgumentType.ANY &&
                argument.acceptedTypes.isEmpty() &&
                !resolvedDynamicTypeContract
            ) {
                issues += issue(
                    CommandBridgeIssueType.UNTYPED_PARAMETER,
                    "Parameter ${argument.name} uses legacy ANY.",
                    "Accepted legacy types: ${argument.acceptedTypes.ifEmpty { setOf("unspecified") }.joinToString()}.",
                )
            }
            if (
                argument.defaultValue != null &&
                argument.type == CommandArgumentType.ANY &&
                argument.acceptedTypes.isEmpty() &&
                !resolvedDynamicTypeContract
            ) {
                issues += issue(
                    CommandBridgeIssueType.UNTYPED_DEFAULT,
                    "Default for ${argument.name} has no authoritative target type.",
                    "Raw default is '${argument.defaultValue}'.",
                )
            }
            if (
                argument.defaultValue != null &&
                argument.type in setOf(
                    CommandArgumentType.VARIABLE_REF,
                    CommandArgumentType.IMAGE_TEMPLATE,
                    CommandArgumentType.REGION,
                )
            ) {
                issues += issue(
                    CommandBridgeIssueType.DEFAULT_SOURCE_CONFLICT,
                    "Default for ${argument.name} is a legacy string but its proposed V1 parameter is domain-typed.",
                    "The current ContractValue model has no domain-scalar value for ${argument.type}.",
                )
            }
            defaultValue(argument).error?.let { message ->
                issues += issue(
                    CommandBridgeIssueType.INVALID_DEFAULT,
                    "Invalid default for ${argument.name}: $message",
                    "Declared type=${argument.type}, raw='${argument.defaultValue}'.",
                )
            }
        }

        if (semanticClass == SemanticClass.QUERY && entry.returnType.isNullOrBlank()) {
            issues += issue(
                CommandBridgeIssueType.QUERY_RETURNS_VOID,
                "Query-like command has no legacy return type.",
                queryEvidence(entry),
            )
            issues += issue(
                CommandBridgeIssueType.MISSING_RETURN_TYPE,
                "V1 requires an explicit non-Void return type for queries.",
                "No repository-backed concrete result type is declared in CommandCatalogEntry.",
            )
        }

        if (entry.id == "variable.get" && entry.role == CommandCatalogRole.LANGUAGE_COMMAND) {
            issues += issue(
                CommandBridgeIssueType.VARIABLE_IDENTITY_MODEL_CONFLICT,
                "The fixed variable.get catalog block is not the parser/importer's variable-read identity model.",
                "Source variable references create variable.reporter.<id> blocks with separate variableId and variableLabel; canonical serialization emits the variable reference, not get(...).",
            )
        }
        if (entry.id in setOf("logic.and", "logic.or")) {
            issues += issue(
                CommandBridgeIssueType.OPERATOR_OR_LITERAL_MODEL,
                "The catalog entry is the visual block projection of an existing native OperatorDefinition.",
                "M1B-1 owns &&/|| identity, precedence and serialization through EmscriptV1Operators; a CommandDefinition would create competing truth.",
            )
        }
        if (entry.id == "literal.number") {
            issues += issue(
                CommandBridgeIssueType.OPERATOR_OR_LITERAL_MODEL,
                "The catalog entry is a visual projection of the grammar and IR number literal, not a callable command.",
                "Parser NumberLiteral, IrExpression.LiteralNumber and canonical numeric serialization do not call number(...).",
            )
        }

        if (entry.id == "action.clickText") {
            issues += issue(
                CommandBridgeIssueType.SIGNATURE_CONFLICT,
                "Legacy click(text) conflicts with V1 click(Point/coordinates) and clickText(text).",
                "Catalog id action.clickText currently has canonicalName=click and a TEXT parameter.",
            )
        }
        if (
            entry.id == "feedback.vibrate" &&
            EmscriptV1TypeConflictDecisions.byStableId(entry.id)?.implemented != true
        ) {
            issues += issue(
                CommandBridgeIssueType.SIGNATURE_CONFLICT,
                "Catalog models one duration while parser/generator/runtime accept a duration sequence.",
                "EmscriptGenerator emits vibrate(pattern...), parser accepts multiple arguments, runtime reads a comma-separated list.",
            )
            issues += issue(
                CommandBridgeIssueType.DEFAULT_SOURCE_CONFLICT,
                "The single catalog default does not define defaults for a vibration pattern.",
                "Catalog default=80; runtime fallback=listOf(80L).",
            )
        }
        if (entry.id.startsWith("rem.")) {
            issues += issue(
                CommandBridgeIssueType.PROJECTION_MODELED_AS_COMMAND,
                "Projection metadata is modeled as a runtime statement.",
                "kind=${entry.kind}, sideEffect=${entry.sideEffect}, liveImplemented=${entry.runtime?.liveImplemented}.",
            )
        }

        providerIssues(entry, dispatch).forEach(issues::add)
        runtimeIssues(entry, dispatch).forEach(issues::add)

        val nativeDefinition = nativeDefinitionsById[entry.id]
        if (nativeDefinition != null) {
            NativeCommandLegacyDefinitions.parityIssues(entry, nativeDefinition).forEach { mismatch ->
                issues += issue(
                    CommandBridgeIssueType.NATIVE_DEFINITION_PARITY_MISMATCH,
                    "Native V1 definition differs from its legacy compatibility projection.",
                    mismatch,
                )
            }
        }

        val definition = runCatching { nativeDefinition ?: buildDefinition(entry, proposedName, semanticClass) }
            .onFailure { error ->
                issues += issue(
                    CommandBridgeIssueType.UNMAPPABLE_ENTRY,
                    "CommandDefinition construction failed: ${error.message}",
                    "legacy id=${entry.id}, name=${entry.canonicalName}",
                )
            }
            .getOrNull()

        val status = statusFor(issues, definition)
        val migrationClass = if (nativeDefinition != null && issues.isEmpty()) {
            CommandMigrationClass.NATIVE_V1
        } else {
            migrationClassFor(entry, issues)
        }
        return when (status) {
            CommandBridgeStatus.CLEAN -> CommandBridgeResult.Success(entry, requireNotNull(definition), dispatch, migrationClass)
            CommandBridgeStatus.NORMALIZABLE -> CommandBridgeResult.Warning(
                entry,
                requireNotNull(definition),
                issues.sortedBy { it.type.name },
                migrationClass,
                dispatch,
            )
            CommandBridgeStatus.CONFLICT,
            CommandBridgeStatus.UNMAPPABLE,
            -> CommandBridgeResult.Conflict(
                entry,
                definition,
                issues.sortedBy { it.type.name },
                status,
                migrationClass,
                dispatch,
            )
        }
    }

    private fun buildDefinition(
        entry: CommandCatalogEntry,
        proposedName: String,
        semanticClass: SemanticClass,
    ): CommandDefinition = CommandDefinition(
        id = CommandId(entry.id),
        canonicalName = proposedName,
        aliases = buildAliases(entry, proposedName),
        semanticClass = semanticClass,
        domain = CommandDomainId(entry.category),
        family = CommandFamilyId(familyFor(entry)),
        variant = CommandVariantId(variantFor(entry)),
        parameters = entry.arguments.map(::parameterDefinition),
        returnType = returnType(entry, semanticClass),
        sideEffects = listOf(sideEffect(entry.sideEffect)),
        requiredCapabilities = entry.capabilities
            .map { CapabilityId("capability.${it.name.lowercase(Locale.ROOT)}") }
            .distinct(),
        provider = providerCandidate(entry),
        lifecycle = DefinitionLifecycle(LanguageVersion.V1_0),
        documentation = CommandDocumentation(
            summary = "Read-only bridge projection of legacy command ${entry.id}.",
            details = "Generated for M1B-2A analysis; not a production migration.",
        ),
    )

    private fun buildAliases(entry: CommandCatalogEntry, proposedName: String): List<CommandAliasDefinition> =
        (entry.acceptedAliases + listOfNotNull(entry.canonicalName.takeIf { it != proposedName }))
            .distinct()
            .map { alias ->
                CommandAliasDefinition(
                    name = alias,
                    sinceVersion = LanguageVersion.V1_0,
                    deprecatedSince = LanguageVersion.V1_0,
                    removedSince = LanguageVersion.V2_0,
                    legacyOnly = true,
                )
            }

    private fun parameterDefinition(argument: CommandArgument): ParameterDefinition {
        val converted = defaultValue(argument)
        return ParameterDefinition(
            id = ParameterId(argument.name),
            type = targetType(argument.type),
            required = argument.required,
            defaultValue = converted.value,
            variadic = argument.variadic,
            documentation = "Legacy type=${argument.type}; accepted=${argument.acceptedTypes.joinToString()}.",
        )
    }

    private fun returnType(entry: CommandCatalogEntry, semanticClass: SemanticClass): LanguageTypeRef {
        val rawType = entry.returnType
        if (rawType?.endsWith('?') == true) {
            return LanguageTypeRef.Nullable(
                returnType(entry.copy(returnType = rawType.dropLast(1)), semanticClass),
            )
        }
        return when (rawType?.lowercase(Locale.ROOT)) {
            "boolean", "bool" -> CoreTypes.BOOL.ref
            "number" -> CoreTypes.NUMBER.ref
            "text", "string" -> CoreTypes.STRING.ref
            "any" -> CoreTypes.ANY.ref
            null -> if (semanticClass == SemanticClass.QUERY) CoreTypes.VOID.ref else CoreTypes.VOID.ref
            else -> LanguageTypeRef.Named(TypeId("legacy.${sanitizeId(entry.returnType)}"))
        }
    }

    private fun proposedSemanticClass(entry: CommandCatalogEntry): SemanticClass = when {
        entry.id.startsWith("rem.") -> SemanticClass.PROJECTION
        entry.kind == CommandCatalogKind.EVENT -> SemanticClass.EVENT
        entry.kind == CommandCatalogKind.CONTROL -> SemanticClass.CONTROL
        entry.kind == CommandCatalogKind.OPERATOR -> SemanticClass.EXPRESSION
        entry.kind == CommandCatalogKind.REPORTER && entry.id.startsWith("literal.") -> SemanticClass.VALUE
        entry.kind == CommandCatalogKind.REPORTER -> SemanticClass.QUERY
        entry.kind == CommandCatalogKind.VARIABLE && entry.returnType != null -> SemanticClass.QUERY
        entry.kind == CommandCatalogKind.VARIABLE -> SemanticClass.ACTION
        isQueryLike(entry) -> SemanticClass.QUERY
        else -> SemanticClass.ACTION
    }

    private fun proposedCanonicalName(entry: CommandCatalogEntry): String = when (entry.id) {
        "action.clickText" -> "clickText"
        "action.findTemplate" -> "templateFind"
        else -> entry.canonicalName
            .split('.')
            .joinToString(".") { segment -> segment.replaceFirstChar(Char::lowercaseChar) }
    }

    private fun namingReason(entry: CommandCatalogEntry): String = when (entry.id) {
        "action.clickText" -> "Frozen V1 separates coordinate click from clickText."
        "action.findTemplate" -> "Sibling commands templateDefine and templateCompare establish the template+operation family."
        else -> "V1 qualified command names require lowerCamelCase namespace segments."
    }

    private fun providerIssues(
        entry: CommandCatalogEntry,
        dispatch: CommandDispatchEvidence,
    ): List<CommandBridgeIssue> = buildList {
        val provider = providerCandidate(entry)
        if (entry.pluginOwner != "visualtasker.core") {
            add(issue(
                CommandBridgeIssueType.PLUGIN_OWNER_NOT_PROVIDER,
                "pluginOwner is organizational metadata, not a V1 provider contract.",
                "pluginOwner=${entry.pluginOwner}; providerCandidate=${provider?.value ?: "none"}.",
            ))
        }
        if (dispatch.status == CommandLiveStatus.LIVE_PROVIDER_DEPENDENT && provider == null) {
            add(issue(
                CommandBridgeIssueType.MISSING_PROVIDER_METADATA,
                "Provider-dependent dispatch has no repository-backed provider candidate.",
                dispatch.evidence,
            ))
        }
        if (provider != null && entry.runtime?.liveCapabilityGate == null) {
            add(issue(
                CommandBridgeIssueType.MISSING_CAPABILITY,
                "Provider candidate exists without a runtime capability gate.",
                "provider=${provider.value}",
            ))
        }
        if (provider != null && entry.pluginOwner.removePrefix("visualtasker.") == entry.runtime?.liveCapabilityGate?.name?.lowercase(Locale.ROOT)) {
            add(issue(
                CommandBridgeIssueType.CAPABILITY_PROVIDER_MIXED,
                "Capability, plugin owner and provider currently encode the same family in different fields.",
                "capability=${entry.runtime?.liveCapabilityGate}, owner=${entry.pluginOwner}, providerCandidate=${provider.value}",
            ))
        }
    }

    private fun runtimeIssues(
        entry: CommandCatalogEntry,
        dispatch: CommandDispatchEvidence,
    ): List<CommandBridgeIssue> = buildList {
        val liveFlag = entry.runtime?.liveImplemented == true
        if (liveFlag && dispatch.status in setOf(
                CommandLiveStatus.DRY_RUN_ONLY,
                CommandLiveStatus.CATALOG_ONLY,
                CommandLiveStatus.NO_DISPATCH,
                CommandLiveStatus.UNKNOWN,
            )
        ) {
            add(issue(
                CommandBridgeIssueType.LIVE_FLAG_WITHOUT_DISPATCH,
                "Catalog marks the command live although no complete live path was confirmed.",
                dispatch.evidence,
            ))
        }
        if (!liveFlag && dispatch.status in setOf(CommandLiveStatus.LIVE_CONFIRMED, CommandLiveStatus.LIVE_PROVIDER_DEPENDENT)) {
            add(issue(
                CommandBridgeIssueType.DISPATCH_WITHOUT_LIVE_FLAG,
                "A dispatch path exists while the catalog live flag is false.",
                dispatch.evidence,
            ))
        }
        if (dispatch.routes.distinct().size > 1) {
            add(issue(
                CommandBridgeIssueType.MULTIPLE_RUNTIME_DISPATCH,
                "More than one runtime route can execute this command.",
                dispatch.routes.joinToString(),
            ))
        }
    }

    private fun statusFor(
        issues: List<CommandBridgeIssue>,
        definition: CommandDefinition?,
    ): CommandBridgeStatus {
        if (definition == null || issues.any { it.type == CommandBridgeIssueType.UNMAPPABLE_ENTRY }) {
            return CommandBridgeStatus.UNMAPPABLE
        }
        val conflicts = setOf(
            CommandBridgeIssueType.DUPLICATE_CANONICAL_NAME,
            CommandBridgeIssueType.AMBIGUOUS_ALIAS,
            CommandBridgeIssueType.QUERY_RETURNS_VOID,
            CommandBridgeIssueType.MISSING_RETURN_TYPE,
            CommandBridgeIssueType.LIVE_FLAG_WITHOUT_DISPATCH,
            CommandBridgeIssueType.PROJECTION_MODELED_AS_COMMAND,
            CommandBridgeIssueType.CONTROL_DUPLICATE,
            CommandBridgeIssueType.REPORTER_DUPLICATE,
            CommandBridgeIssueType.SIGNATURE_CONFLICT,
            CommandBridgeIssueType.VARIABLE_IDENTITY_MODEL_CONFLICT,
            CommandBridgeIssueType.OPERATOR_OR_LITERAL_MODEL,
            CommandBridgeIssueType.DEFAULT_SOURCE_CONFLICT,
            CommandBridgeIssueType.NATIVE_DEFINITION_PARITY_MISMATCH,
        )
        return when {
            issues.any { it.type in conflicts } -> CommandBridgeStatus.CONFLICT
            issues.isNotEmpty() -> CommandBridgeStatus.NORMALIZABLE
            else -> CommandBridgeStatus.CLEAN
        }
    }

    private fun migrationClassFor(
        entry: CommandCatalogEntry,
        issues: List<CommandBridgeIssue>,
    ): CommandMigrationClass = when {
        entry.role == CommandCatalogRole.LEGACY_EXPRESSION_ALIAS -> CommandMigrationClass.LEGACY_ALIAS
        entry.role == CommandCatalogRole.CANONICAL_EXPRESSION_PROJECTION -> CommandMigrationClass.NONE
        entry.id.startsWith("rem.") -> CommandMigrationClass.PROJECTION_MIGRATION
        EmscriptV1TypeConflictDecisions.byStableId(entry.id)?.classification == TypeConflictClassification.STRUCTURAL_MIGRATION ->
            CommandMigrationClass.STRUCTURAL_MIGRATION
        issues.any {
            it.type in setOf(
                CommandBridgeIssueType.DUPLICATE_CANONICAL_NAME,
                CommandBridgeIssueType.SIGNATURE_CONFLICT,
                CommandBridgeIssueType.QUERY_RETURNS_VOID,
                CommandBridgeIssueType.VARIABLE_IDENTITY_MODEL_CONFLICT,
                CommandBridgeIssueType.OPERATOR_OR_LITERAL_MODEL,
            )
        } -> CommandMigrationClass.CONTRACT_DECISION
        issues.any { it.type in setOf(CommandBridgeIssueType.MISSING_PROVIDER_METADATA, CommandBridgeIssueType.PLUGIN_OWNER_NOT_PROVIDER) } -> CommandMigrationClass.PROVIDER_METADATA
        issues.any { it.type == CommandBridgeIssueType.LEGACY_ALIAS_REQUIRED } -> CommandMigrationClass.LEGACY_ALIAS
        issues.any { it.type == CommandBridgeIssueType.NON_CANONICAL_NAME } -> CommandMigrationClass.CANONICAL_NORMALIZATION
        else -> CommandMigrationClass.NONE
    }

    private fun providerCandidate(entry: CommandCatalogEntry): ProviderId? = when (entry.pluginOwner) {
        "visualtasker.customtabs" -> ProviderId("provider.customTabs")
        "visualtasker.tasker" -> ProviderId("provider.tasker")
        "visualtasker.shizuku" -> ProviderId("provider.shizuku")
        "visualtasker.termux" -> ProviderId("provider.termux")
        "visualtasker.scrcpy" -> ProviderId("provider.scrcpy")
        "visualtasker.charts" -> ProviderId("provider.charts")
        "visualtasker.vision" -> ProviderId("provider.vision")
        else -> when (entry.runtime?.liveCapabilityGate) {
            CommandCapability.A11Y -> ProviderId("provider.accessibility")
            CommandCapability.SCREEN_CAPTURE -> ProviderId("provider.screenCapture")
            CommandCapability.VISION -> ProviderId("provider.vision")
            else -> null
        }
    }

    private fun targetType(type: CommandArgumentType): LanguageTypeRef = when (type) {
        CommandArgumentType.BOOLEAN -> CoreTypes.BOOL.ref
        CommandArgumentType.NUMBER,
        CommandArgumentType.DURATION_MS,
        CommandArgumentType.FREQUENCY_HZ,
        CommandArgumentType.PERCENT,
        -> CoreTypes.NUMBER.ref
        CommandArgumentType.TEXT -> CoreTypes.STRING.ref
        CommandArgumentType.ANY -> CoreTypes.ANY.ref
        CommandArgumentType.VARIABLE_REF -> LanguageTypeRef.Named(TypeId("emscript.variableRef"))
        CommandArgumentType.IMAGE_TEMPLATE -> LanguageTypeRef.Named(TypeId("emscript.templateRef"))
        CommandArgumentType.REGION -> LanguageTypeRef.Named(TypeId("emscript.region"))
        CommandArgumentType.STATEMENT_BODY -> LanguageTypeRef.Named(TypeId("emscript.statementBody"))
    }

    private data class ConvertedDefault(val value: ContractValue?, val error: String? = null)

    private fun defaultValue(argument: CommandArgument): ConvertedDefault {
        val raw = argument.defaultValue ?: return ConvertedDefault(null)
        return when (argument.type) {
            CommandArgumentType.BOOLEAN -> raw.toBooleanStrictOrNull()
                ?.let { ConvertedDefault(ContractValue.BoolValue(it)) }
                ?: ConvertedDefault(null, "expected true or false")
            CommandArgumentType.NUMBER,
            CommandArgumentType.DURATION_MS,
            CommandArgumentType.FREQUENCY_HZ,
            CommandArgumentType.PERCENT,
            -> raw.toDoubleOrNull()
                ?.takeIf(Double::isFinite)
                ?.let { ConvertedDefault(ContractValue.NumberValue(canonicalNumber(raw))) }
                ?: ConvertedDefault(null, "expected a finite number")
            CommandArgumentType.TEXT,
            CommandArgumentType.VARIABLE_REF,
            CommandArgumentType.IMAGE_TEMPLATE,
            CommandArgumentType.REGION,
            -> ConvertedDefault(ContractValue.StringValue(raw))
            CommandArgumentType.ANY -> ConvertedDefault(inferAnyDefault(raw))
            CommandArgumentType.STATEMENT_BODY -> ConvertedDefault(null, "statement bodies cannot have scalar defaults")
        }
    }

    private fun inferAnyDefault(raw: String): ContractValue = when {
        raw.equals("true", ignoreCase = true) -> ContractValue.BoolValue(true)
        raw.equals("false", ignoreCase = true) -> ContractValue.BoolValue(false)
        raw.toDoubleOrNull()?.isFinite() == true -> ContractValue.NumberValue(canonicalNumber(raw))
        raw.startsWith('"') && raw.endsWith('"') && raw.length >= 2 -> ContractValue.StringValue(raw.drop(1).dropLast(1))
        else -> ContractValue.StringValue(raw)
    }

    private fun defaultAudit(entry: CommandCatalogEntry): List<CommandDefaultAuditEntry> =
        entry.arguments.filter { it.defaultValue != null }.map { argument ->
            val converted = defaultValue(argument)
            CommandDefaultAuditEntry(
                commandId = entry.id,
                parameter = argument.name,
                declaredType = argument.type.name,
                rawDefault = argument.defaultValue.orEmpty(),
                parserInterpretation = parserInterpretation(argument),
                runtimeInterpretation = runtimeInterpretation(entry, argument),
                targetType = renderType(targetType(argument.type)),
                result = converted.error ?: "typed:${converted.value?.let(::renderValue) ?: "none"}",
            )
        }

    private fun queryAudit(results: List<CommandBridgeResult>): List<CommandQueryAuditEntry> =
        results.filter { proposedSemanticClass(it.sourceEntry) == SemanticClass.QUERY }.map { result ->
            val entry = result.sourceEntry
            CommandQueryAuditEntry(
                commandId = entry.id,
                currentKind = entry.kind,
                currentReturn = entry.returnType ?: "Void/unspecified",
                observedRuntimeResult = result.dispatchEvidence.observedResult,
                blockOutput = entry.block?.blockType?.let { "binding:$it" } ?: "none",
                proposedSemanticClass = SemanticClass.QUERY,
                proposedReturnType = entry.returnType ?: "NEEDS_DECISION",
                evidence = queryEvidence(entry),
                confidence = if (entry.returnType != null) BridgeConfidence.HIGH else BridgeConfidence.MEDIUM,
            )
        }

    private fun namingAudit(entries: List<CommandCatalogEntry>): List<CommandNamingAuditEntry> = buildList {
        entries.filter { proposedCanonicalName(it) != it.canonicalName }.forEach { entry ->
            add(CommandNamingAuditEntry(
                family = familyFor(entry),
                currentCommand = entry.canonicalName,
                siblingPattern = when (entry.id) {
                    "action.findTemplate" -> "templateDefine, templateCompare"
                    else -> "lowerCamelCase namespace"
                },
                proposedCanonical = proposedCanonicalName(entry),
                legacyAliasRequired = true,
                confidence = BridgeConfidence.HIGH,
                reason = namingReason(entry),
            ))
        }
    }

    private fun findAliasCollisions(entries: List<CommandCatalogEntry>): List<CommandAliasCollision> {
        val accepted = entries
            .filter { it.role == CommandCatalogRole.LANGUAGE_COMMAND }
            .flatMap { entry ->
            (entry.acceptedAliases + entry.canonicalName).map { name -> name.lowercase(Locale.ROOT) to entry }
        }
        return accepted.groupBy({ it.first }, { it.second })
            .mapValues { (_, candidates) -> candidates.distinctBy { it.id } }
            .filterValues { it.size > 1 }
            .map { (name, candidates) ->
                CommandAliasCollision(
                    inputName = name,
                    candidateIds = candidates.map { it.id },
                    currentWinnerId = candidates.first().id,
                    reason = "Language-command lookup lowercases input and returns the first language-command candidate.",
                )
            }
            .sortedBy { it.inputName }
    }

    private fun isQueryLike(entry: CommandCatalogEntry): Boolean {
        if (entry.id in queryEvidenceById) return true
        return entry.sideEffect == CommandSideEffect.SCREEN_READ && entry.canonicalName.substringAfterLast('.').let { name ->
            name.startsWith("get", true) ||
                name.startsWith("is", true) ||
                name.startsWith("has", true) ||
                name.startsWith("read", true) ||
                name.startsWith("find", true) ||
                name.startsWith("search", true) ||
                name.startsWith("compare", true) ||
                name.startsWith("exists", true)
        }
    }

    private fun queryEvidence(entry: CommandCatalogEntry): String =
        queryEvidenceById[entry.id]
            ?: "Name and SCREEN_READ side effect indicate value semantics; concrete result type is not declared."

    private fun parserInterpretation(argument: CommandArgument): String = when (argument.type) {
        CommandArgumentType.BOOLEAN -> "boolean literal"
        CommandArgumentType.NUMBER,
        CommandArgumentType.DURATION_MS,
        CommandArgumentType.FREQUENCY_HZ,
        CommandArgumentType.PERCENT,
        -> "numeric literal"
        CommandArgumentType.TEXT,
        CommandArgumentType.VARIABLE_REF,
        CommandArgumentType.IMAGE_TEMPLATE,
        CommandArgumentType.REGION,
        -> "legacy string token"
        CommandArgumentType.ANY -> "expression/raw token"
        CommandArgumentType.STATEMENT_BODY -> "structured body"
    }

    private fun runtimeInterpretation(entry: CommandCatalogEntry, argument: CommandArgument): String = when {
        entry.id == "feedback.vibrate" -> "ordered Long pattern; legacy scalar field remains a read fallback"
        entry.id == "debug.log" -> "expression rendered to text"
        argument.type == CommandArgumentType.ANY -> "command-specific raw argument parsing"
        else -> parserInterpretation(argument)
    }

    private fun familyFor(entry: CommandCatalogEntry): String = when {
        entry.id.startsWith("rem.") -> "projection"
        entry.id == "action.findTemplate" || entry.id.startsWith("vision.template") -> "template"
        entry.id.startsWith("vision.marker") -> "marker"
        entry.id.startsWith("scene.") -> "scene"
        entry.canonicalName.contains('.') -> sanitizeId(entry.canonicalName.substringBefore('.'))
        else -> entry.category
    }

    private fun variantFor(entry: CommandCatalogEntry): String = sanitizeId(
        proposedCanonicalName(entry).substringAfterLast('.').ifBlank { "default" },
    )

    private fun sideEffect(effect: CommandSideEffect): V1SideEffect = when (effect) {
        CommandSideEffect.NONE -> V1SideEffect.NONE
        CommandSideEffect.TIMING -> V1SideEffect.TRACE
        CommandSideEffect.UI_INPUT -> V1SideEffect.INPUT
        CommandSideEffect.FEEDBACK -> V1SideEffect.DEVICE
        CommandSideEffect.LOGGING -> V1SideEffect.TRACE
        CommandSideEffect.VARIABLE_WRITE -> V1SideEffect.STATE
        CommandSideEffect.CONTROL_FLOW -> V1SideEffect.STATE
        CommandSideEffect.SCREEN_READ -> V1SideEffect.DEVICE
    }

    private fun issue(type: CommandBridgeIssueType, message: String, evidence: String) =
        CommandBridgeIssue(type, message, evidence)

    private fun isCanonicalQualifiedName(name: String): Boolean =
        name.split('.').all { lowerCamelSegment.matches(it) }

    private fun sanitizeId(value: String): String {
        val cleaned = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return cleaned.ifBlank { "unknown" }.replaceFirstChar { char ->
            if (char.isLetter()) char.lowercaseChar() else 'x'
        }
    }

    private fun canonicalNumber(raw: String): String = raw.toDouble().let { value ->
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    }

    private fun renderType(type: LanguageTypeRef): String =
        de.visualtasker.emscript.contract.LanguageTypeCompatibility.sourceName(type)

    private fun renderValue(value: ContractValue): String = when (value) {
        is ContractValue.BoolValue -> value.value.toString()
        is ContractValue.NumberValue -> value.canonicalValue
        is ContractValue.StringValue -> value.value
        is ContractValue.ListValue -> value.values.joinToString(prefix = "[", postfix = "]", transform = ::renderValue)
    }

    companion object {
        private val lowerCamelSegment = Regex("[a-z][A-Za-z0-9]*")

        private val queryEvidenceById: Map<String, String> = mapOf(
            "action.findTemplate" to "Runtime returns a template match with score; block is currently statement-shaped.",
            "vision.templateCompare" to "WorkspaceBasicRuntime obtains a non-null numeric score from templateCompare; failures use diagnostics.",
            "vision.markerLoad" to "WorkspaceBasicRuntime obtains a nullable saved marker/region.",
            "system.datastoreGet" to "WorkspaceBasicRuntime obtains a nullable datastore value.",
            "file.readText" to "WorkspaceBasicRuntime obtains nullable file text.",
            "clipboard.get" to "WorkspaceBasicRuntime obtains clipboard text.",
            "system.info" to "WorkspaceBasicRuntime obtains system information text.",
            "system.env" to "WorkspaceBasicRuntime obtains an environment value.",
            "logic.screenContains" to "Reporter has Boolean return type and is evaluated as a condition.",
            "chromeTab.isSupported" to "WorkspaceBasicRuntime obtains a typed Bool payload from the Custom Tabs adapter; failures use diagnostics.",
        )
    }
}

object LegacyCommandDispatchEvidence {
    private val directLiveIds = setOf(
        "event.start",
        "action.wait",
        "action.clickText",
        "feedback.beep",
        "feedback.vibrate",
        "debug.log",
        "variable.set",
        "variable.get",
        "control.repeat",
        "control.while",
        "control.if",
        "control.ifElse",
        "control.ifElseIfElse",
        "logic.screenContains",
        "logic.boolean",
        "logic.and",
        "logic.or",
        "logic.operate",
        "logic.compare",
        "literal.number",
        "literal.string",
        "literal.boolean",
        "input.clickPoint",
        "action.swipe",
        "file.readText",
        "file.writeText",
        "clipboard.get",
        "clipboard.set",
        "cache.clear",
        "system.info",
        "system.env",
        "vision.screenshot",
        "action.findTemplate",
        "vision.markerSave",
        "vision.markerLoad",
        "vision.markerDelete",
        "vision.templateDefine",
        "vision.templateCompare",
        "system.datastorePut",
        "system.datastoreGet",
    )

    private val providerOwners = setOf(
        "visualtasker.customtabs",
        "visualtasker.tasker",
        "visualtasker.shizuku",
        "visualtasker.termux",
        "visualtasker.scrcpy",
    )

    fun byCommandId(entries: List<CommandCatalogEntry>): Map<String, CommandDispatchEvidence> =
        entries.associate { entry -> entry.id to evidence(entry) }

    private fun evidence(entry: CommandCatalogEntry): CommandDispatchEvidence = when {
        entry.id.startsWith("rem.") -> CommandDispatchEvidence(
            CommandLiveStatus.NO_DISPATCH,
            emptyList(),
            "projection metadata",
            "REM entries are consumed as visual metadata and have no WorkspaceBasicRuntime dispatch branch.",
        )
        entry.id in directLiveIds -> CommandDispatchEvidence(
            status = if (
                entry.runtime?.liveCapabilityGate in setOf(
                    CommandCapability.A11Y,
                    CommandCapability.SCREEN_CAPTURE,
                    CommandCapability.VISION,
                )
            ) CommandLiveStatus.LIVE_PROVIDER_DEPENDENT else CommandLiveStatus.LIVE_CONFIRMED,
            routes = listOf("WorkspaceDryRunRuntime -> WorkspaceBasicRuntime -> environment"),
            observedResult = observedResult(entry),
            evidence = "A concrete evaluator or WorkspaceBasicRuntime branch exists for ${entry.id}.",
        )
        entry.pluginOwner in providerOwners -> CommandDispatchEvidence(
            CommandLiveStatus.LIVE_PROVIDER_DEPENDENT,
            listOf("WorkspaceBasicRuntime -> ${entry.pluginOwner} adapter"),
            "provider result/error",
            "WorkspaceBasicRuntime dispatches the namespace to an environment adapter; availability is external.",
        )
        entry.pluginOwner == "visualtasker.charts" -> CommandDispatchEvidence(
            CommandLiveStatus.NO_DISPATCH,
            emptyList(),
            "unknown",
            "No Chart namespace branch exists in WorkspaceBasicRuntime.",
        )
        entry.runtime?.liveImplemented == false -> CommandDispatchEvidence(
            CommandLiveStatus.DRY_RUN_ONLY,
            listOfNotNull(entry.runtime?.dryRunBehavior),
            "dry-run diagnostic",
            "Catalog explicitly marks liveImplemented=false.",
        )
        entry.pluginOwner == "visualtasker.vision" -> CommandDispatchEvidence(
            CommandLiveStatus.NO_DISPATCH,
            emptyList(),
            "unknown",
            "No concrete direct branch was found for this vision catalog entry.",
        )
        else -> CommandDispatchEvidence(
            CommandLiveStatus.CATALOG_ONLY,
            listOfNotNull(entry.runtime?.dryRunBehavior),
            "catalog metadata only",
            "Catalog declares runtime metadata, but no explicit live dispatch branch was confirmed.",
        )
    }

    private fun observedResult(entry: CommandCatalogEntry): String = when (entry.id) {
        "file.readText", "clipboard.get", "system.info", "environment.get", "system.datastoreGet" -> "text/value"
        "action.findTemplate" -> "template match with score"
        "vision.markerLoad" -> "saved marker/region"
        "vision.templateCompare" -> "numeric score"
        else -> entry.returnType ?: "execution outcome"
    }
}
