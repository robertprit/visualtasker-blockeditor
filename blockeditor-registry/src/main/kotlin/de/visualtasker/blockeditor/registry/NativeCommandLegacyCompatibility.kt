package de.visualtasker.blockeditor.registry

import de.visualtasker.emscript.contract.CapabilityId
import de.visualtasker.emscript.contract.CommandDefinition
import de.visualtasker.emscript.contract.CommandSideEffect as V1SideEffect
import de.visualtasker.emscript.contract.ContractValue
import de.visualtasker.emscript.contract.CoreTypes
import de.visualtasker.emscript.contract.DefinitionLifecycle
import de.visualtasker.emscript.contract.EmscriptV1Commands
import de.visualtasker.emscript.contract.LanguageVersion
import de.visualtasker.emscript.contract.ProviderId

data class LegacyCommandProjectionSpec(
    val kind: CommandCatalogKind,
    val category: String,
    val blockType: String,
    val argumentTypes: Map<String, CommandArgumentType>,
    val sideEffect: CommandSideEffect,
    val capabilities: Set<CommandCapability>,
    val runtime: CommandRuntimeBinding,
    val pluginOwner: String = "visualtasker.core",
    val provider: ProviderId? = null,
    val flowNodeKind: String = category,
    val lifecycle: DefinitionLifecycle = DefinitionLifecycle(LanguageVersion.V1_0),
)

object NativeCommandLegacyCompatibility {
    fun project(definition: CommandDefinition, spec: LegacyCommandProjectionSpec): CommandCatalogEntry =
        CommandCatalogEntry(
            id = definition.id.value,
            canonicalName = definition.canonicalName,
            acceptedAliases = definition.aliases.map { it.name },
            kind = spec.kind,
            category = spec.category,
            arguments = definition.parameters.map { parameter ->
                CommandArgument(
                    name = parameter.id.value,
                    type = requireNotNull(spec.argumentTypes[parameter.id.value]) {
                        "Missing legacy argument type for ${definition.id.value}.${parameter.id.value}"
                    },
                    required = parameter.required,
                    defaultValue = parameter.defaultValue.toLegacyString(),
                )
            },
            returnType = definition.returnType.toLegacyReturnType(),
            sideEffect = spec.sideEffect,
            capabilities = spec.capabilities,
            pluginOwner = spec.pluginOwner,
            block = CommandBlockBinding(spec.blockType),
            flowchart = CommandFlowchartBinding(spec.flowNodeKind),
            runtime = spec.runtime,
        ).also { projected ->
            val issues = parityIssues(projected, definition, spec)
            require(issues.isEmpty()) { issues.joinToString(prefix = "Invalid native compatibility projection: ") }
        }

    fun parityIssues(
        legacy: CommandCatalogEntry,
        native: CommandDefinition,
        spec: LegacyCommandProjectionSpec,
    ): List<String> = buildList {
        compare("id", legacy.id, native.id.value)
        compare("canonicalName", legacy.canonicalName, native.canonicalName)
        compare("aliases", legacy.acceptedAliases, native.aliases.map { it.name })
        compare("parameter count", legacy.arguments.size, native.parameters.size)
        legacy.arguments.zip(native.parameters).forEachIndexed { index, (legacyParameter, nativeParameter) ->
            compare("parameter[$index].name", legacyParameter.name, nativeParameter.id.value)
            compare("parameter[$index].type", legacyParameter.type, spec.argumentTypes[nativeParameter.id.value])
            compare("parameter[$index].required", legacyParameter.required, nativeParameter.required)
            compare("parameter[$index].default", legacyParameter.defaultValue, nativeParameter.defaultValue.toLegacyString())
        }
        compare("return", legacy.returnType, native.returnType.toLegacyReturnType())
        compare("side effect", legacy.sideEffect, spec.sideEffect)
        compare("V1 side effect", native.sideEffects, spec.sideEffect.toV1SideEffects())
        compare("capabilities", legacy.capabilities, spec.capabilities)
        compare("V1 capabilities", native.requiredCapabilities.toSet(), spec.capabilities.toV1Capabilities())
        compare("provider", native.provider, spec.provider)
        compare("lifecycle", native.lifecycle, spec.lifecycle)
        compare("plugin owner", legacy.pluginOwner, spec.pluginOwner)
        compare("dispatchability", legacy.runtime?.liveImplemented == true, spec.runtime.liveImplemented)
        compare("runtime capability", legacy.runtime?.liveCapabilityGate, spec.runtime.liveCapabilityGate)
        compare(
            "V1 dispatch capability",
            CapabilityId("capability.${spec.runtime.liveCapabilityGate.name.lowercase()}") in native.requiredCapabilities,
            true,
        )
    }

    private fun ContractValue?.toLegacyString(): String? = when (this) {
        null -> null
        is ContractValue.NumberValue -> canonicalValue
        is ContractValue.StringValue -> value
        is ContractValue.BoolValue -> value.toString()
        is ContractValue.ListValue -> error("Legacy scalar command defaults cannot represent List values")
    }

    private fun de.visualtasker.emscript.contract.LanguageTypeRef.toLegacyReturnType(): String? = when (this) {
        CoreTypes.VOID.ref -> null
        CoreTypes.STRING.ref -> "String"
        CoreTypes.NUMBER.ref -> "Number"
        CoreTypes.BOOL.ref -> "Boolean"
        CoreTypes.ANY.ref -> "Any"
        is de.visualtasker.emscript.contract.LanguageTypeRef.Nullable ->
            de.visualtasker.emscript.contract.LanguageTypeCompatibility.sourceName(this)
        else -> toString()
    }

    private fun CommandSideEffect.toV1SideEffects(): List<V1SideEffect> = when (this) {
        CommandSideEffect.NONE -> listOf(V1SideEffect.NONE)
        CommandSideEffect.TIMING -> listOf(V1SideEffect.TRACE)
        CommandSideEffect.UI_INPUT -> listOf(V1SideEffect.INPUT)
        CommandSideEffect.FEEDBACK -> listOf(V1SideEffect.DEVICE)
        CommandSideEffect.LOGGING -> listOf(V1SideEffect.TRACE)
        CommandSideEffect.VARIABLE_WRITE -> listOf(V1SideEffect.STATE)
        CommandSideEffect.CONTROL_FLOW -> listOf(V1SideEffect.STATE)
        CommandSideEffect.SCREEN_READ -> listOf(V1SideEffect.DEVICE)
    }

    private fun Set<CommandCapability>.toV1Capabilities(): Set<CapabilityId> =
        mapTo(linkedSetOf()) { CapabilityId("capability.${it.name.lowercase()}") }

    private fun MutableList<String>.compare(label: String, legacy: Any?, native: Any?) {
        if (legacy != native) add("$label: legacy=$legacy native=$native")
    }
}

object NativeCommandLegacyDefinitions {
    private val eventStartSpec = LegacyCommandProjectionSpec(
        kind = CommandCatalogKind.EVENT,
        category = BlockCategories.EVENT,
        blockType = BlockTypes.EVENT_START,
        argumentTypes = emptyMap(),
        sideEffect = CommandSideEffect.CONTROL_FLOW,
        capabilities = setOf(CommandCapability.CORE),
        runtime = CommandRuntimeBinding("entrypoint", CommandCapability.CORE),
        flowNodeKind = "event",
    )
    private val waitSpec = LegacyCommandProjectionSpec(
        kind = CommandCatalogKind.STATEMENT,
        category = BlockCategories.ACTION,
        blockType = BlockTypes.ACTION_WAIT,
        argumentTypes = mapOf("ms" to CommandArgumentType.DURATION_MS),
        sideEffect = CommandSideEffect.TIMING,
        capabilities = setOf(CommandCapability.CORE, CommandCapability.TIMING),
        runtime = CommandRuntimeBinding("simulate", CommandCapability.TIMING),
    )
    private val beepSpec = LegacyCommandProjectionSpec(
        kind = CommandCatalogKind.STATEMENT,
        category = BlockCategories.FEEDBACK,
        blockType = BlockTypes.FEEDBACK_BEEP,
        argumentTypes = mapOf(
            "frequency" to CommandArgumentType.FREQUENCY_HZ,
            "durationMs" to CommandArgumentType.DURATION_MS,
            "volume" to CommandArgumentType.PERCENT,
        ),
        sideEffect = CommandSideEffect.FEEDBACK,
        capabilities = setOf(CommandCapability.CORE, CommandCapability.FEEDBACK),
        runtime = CommandRuntimeBinding("simulate", CommandCapability.FEEDBACK),
    )
    private val definitionsById = EmscriptV1Commands.ALL.associateBy { it.id.value }
    private val specsById = mapOf(
        EmscriptV1Commands.EVENT_START.id.value to eventStartSpec,
        EmscriptV1Commands.WAIT.id.value to waitSpec,
        EmscriptV1Commands.BEEP.id.value to beepSpec,
    )
    private val entriesById = specsById.mapValues { (id, spec) ->
        NativeCommandLegacyCompatibility.project(requireNotNull(definitionsById[id]), spec)
    }

    fun requireEntry(commandId: String): CommandCatalogEntry =
        requireNotNull(entriesById[commandId]) { "No native legacy projection for $commandId" }

    fun parityIssues(legacy: CommandCatalogEntry, native: CommandDefinition): List<String> =
        NativeCommandLegacyCompatibility.parityIssues(
            legacy = legacy,
            native = native,
            spec = requireNotNull(specsById[native.id.value]) {
                "No native legacy projection spec for ${native.id.value}"
            },
        )
}
