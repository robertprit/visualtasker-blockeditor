package de.visualtasker.emscript.contract

import kotlinx.serialization.Serializable

@Serializable
enum class SemanticClass {
    VALUE,
    EXPRESSION,
    QUERY,
    ACTION,
    CONTROL,
    DEFINITION,
    EVENT,
    PROJECTION,
}

@Serializable
enum class CommandSideEffect {
    NONE,
    INPUT,
    UI,
    OVERLAY,
    FILESYSTEM,
    NETWORK,
    DEVICE,
    EXTERNAL_PROVIDER,
    STATE,
    TRACE,
}

object CommandDomains {
    val CORE = CommandDomainId("core")
    val VARIABLE = CommandDomainId("variable")
    val CONTROL = CommandDomainId("control")
    val INPUT = CommandDomainId("input")
    val GESTURE = CommandDomainId("gesture")
    val GEOMETRY = CommandDomainId("geometry")
    val PERCEPTION = CommandDomainId("perception")
    val DRAW = CommandDomainId("draw")
    val TRANSFORM = CommandDomainId("transform")
    val ANIMATION = CommandDomainId("animation")
    val FEEDBACK = CommandDomainId("feedback")
    val PROVIDER = CommandDomainId("provider")
    val PROJECTION = CommandDomainId("projection")
    val TRACE = CommandDomainId("trace")
}

@Serializable
data class CommandAliasDefinition(
    val name: String,
    val sinceVersion: LanguageVersion,
    val deprecatedSince: LanguageVersion? = null,
    val removedSince: LanguageVersion? = null,
    val legacyOnly: Boolean = true,
)

@Serializable
data class ParameterDefinition(
    val id: ParameterId,
    val type: LanguageTypeRef,
    val required: Boolean,
    val defaultValue: ContractValue? = null,
    val positionalAllowed: Boolean = true,
    val namedAllowed: Boolean = true,
    val variadic: Boolean = false,
    val documentation: String = "",
)

@Serializable
data class CommandDocumentation(
    val summary: String,
    val details: String = "",
    val examples: List<String> = emptyList(),
)

@Serializable
data class CommandDefinition(
    val id: CommandId,
    val canonicalName: String,
    val aliases: List<CommandAliasDefinition> = emptyList(),
    val semanticClass: SemanticClass,
    val domain: CommandDomainId,
    val family: CommandFamilyId? = null,
    val variant: CommandVariantId? = null,
    val parameters: List<ParameterDefinition> = emptyList(),
    val returnType: LanguageTypeRef,
    val sideEffects: List<CommandSideEffect>,
    val requiredCapabilities: List<CapabilityId> = emptyList(),
    val provider: ProviderId? = null,
    val lifecycle: DefinitionLifecycle,
    val documentation: CommandDocumentation,
)

fun CommandDefinition.canBeUsedAsExpression(): Boolean = returnType != CoreTypes.VOID.ref
