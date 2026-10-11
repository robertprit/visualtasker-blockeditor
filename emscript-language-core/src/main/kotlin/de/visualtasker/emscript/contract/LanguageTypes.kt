package de.visualtasker.emscript.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface LanguageTypeRef {
    @Serializable
    @SerialName("named")
    data class Named(val id: TypeId) : LanguageTypeRef

    @Serializable
    @SerialName("list")
    data class ListOf(val elementType: LanguageTypeRef) : LanguageTypeRef

    @Serializable
    @SerialName("nullable")
    data class Nullable(val baseType: LanguageTypeRef) : LanguageTypeRef {
        init {
            require(baseType !is Nullable) { "Nullable types cannot be nested" }
        }
    }
}

@Serializable
enum class TypeDefinitionKind {
    CORE,
    DOMAIN,
    PROVIDER,
}

@Serializable
data class TypeDefinition(
    val id: TypeId,
    val canonicalName: String,
    val kind: TypeDefinitionKind,
    val storable: Boolean = true,
    val parameterAllowed: Boolean = true,
    val documentation: String = "",
) {
    val ref: LanguageTypeRef.Named get() = LanguageTypeRef.Named(id)
}

object CoreTypeIds {
    val STRING = TypeId("core.string")
    val NUMBER = TypeId("core.number")
    val BOOL = TypeId("core.bool")
    val ANY = TypeId("core.any")
    val VOID = TypeId("core.void")
}

object CoreTypes {
    val STRING = TypeDefinition(CoreTypeIds.STRING, "String", TypeDefinitionKind.CORE)
    val NUMBER = TypeDefinition(CoreTypeIds.NUMBER, "Number", TypeDefinitionKind.CORE)
    val BOOL = TypeDefinition(CoreTypeIds.BOOL, "Bool", TypeDefinitionKind.CORE)
    val ANY = TypeDefinition(CoreTypeIds.ANY, "Any", TypeDefinitionKind.CORE)
    val VOID = TypeDefinition(
        id = CoreTypeIds.VOID,
        canonicalName = "Void",
        kind = TypeDefinitionKind.CORE,
        storable = false,
        parameterAllowed = false,
        documentation = "Return type for functions and actions without a value.",
    )

    val ALL: List<TypeDefinition> = listOf(STRING, NUMBER, BOOL, ANY, VOID)

    fun listOf(elementType: LanguageTypeRef): LanguageTypeRef.ListOf =
        LanguageTypeRef.ListOf(elementType)

    fun nullable(baseType: LanguageTypeRef): LanguageTypeRef.Nullable =
        LanguageTypeRef.Nullable(baseType)
}

object DomainTypeIds {
    val POINT = TypeId("domain.geometry.point")
    val PATH_SEGMENT = TypeId("domain.geometry.path-segment")
    val PATH = TypeId("domain.geometry.path")
    val POINTER_PATH = TypeId("domain.gesture.pointer-path")
    val MULTI_PATH = TypeId("domain.gesture.multi-path")
    val IMAGE_MATCH = TypeId("domain.perception.image-match")
    val TEXT_MATCH = TypeId("domain.perception.text-match")
    val MARKER = TypeId("domain.resource.marker")
    val CHART_SNAPSHOT = TypeId("domain.chart.snapshot")
}

object DomainTypes {
    val POINT = TypeDefinition(
        id = DomainTypeIds.POINT,
        canonicalName = "Point",
        kind = TypeDefinitionKind.DOMAIN,
        documentation = "Immutable absolute display coordinate.",
    )
    val PATH_SEGMENT = TypeDefinition(
        id = DomainTypeIds.PATH_SEGMENT,
        canonicalName = "PathSegment",
        kind = TypeDefinitionKind.DOMAIN,
        documentation = "Immutable line or Bezier segment in timeless Path geometry.",
    )
    val PATH = TypeDefinition(
        id = DomainTypeIds.PATH,
        canonicalName = "Path",
        kind = TypeDefinitionKind.DOMAIN,
        documentation = "Immutable timeless path geometry.",
    )
    val POINTER_PATH = TypeDefinition(
        id = DomainTypeIds.POINTER_PATH,
        canonicalName = "PointerPath",
        kind = TypeDefinitionKind.DOMAIN,
        documentation = "Immutable single-pointer path with execution timing.",
    )
    val MULTI_PATH = TypeDefinition(
        id = DomainTypeIds.MULTI_PATH,
        canonicalName = "MultiPath",
        kind = TypeDefinitionKind.DOMAIN,
        documentation = "Immutable synchronized collection of timed pointer paths.",
    )
    val IMAGE_MATCH = TypeDefinition(
        id = DomainTypeIds.IMAGE_MATCH,
        canonicalName = "ImageMatch",
        kind = TypeDefinitionKind.DOMAIN,
        documentation = "Immutable result of a successful image-template match.",
    )
    val TEXT_MATCH = TypeDefinition(
        id = DomainTypeIds.TEXT_MATCH,
        canonicalName = "TextMatch",
        kind = TypeDefinitionKind.DOMAIN,
        documentation = "Immutable best-match result from textual perception evidence.",
    )
    val MARKER = TypeDefinition(
        id = DomainTypeIds.MARKER,
        canonicalName = "Marker",
        kind = TypeDefinitionKind.DOMAIN,
        documentation = "Immutable snapshot of a persisted visual marker resource.",
    )
    val CHART_SNAPSHOT = TypeDefinition(
        id = DomainTypeIds.CHART_SNAPSHOT,
        canonicalName = "ChartSnapshot",
        kind = TypeDefinitionKind.DOMAIN,
        documentation = "Immutable snapshot of a persisted chart resource.",
    )

    val ALL: List<TypeDefinition> = listOf(
        POINT,
        PATH_SEGMENT,
        PATH,
        POINTER_PATH,
        MULTI_PATH,
        IMAGE_MATCH,
        TEXT_MATCH,
        MARKER,
        CHART_SNAPSHOT,
    )
}

object ProviderTypeIds {
    val TASKER_VARIABLE = TypeId("provider.tasker.variable")
}

object ProviderTypes {
    val TASKER_VARIABLE = TypeDefinition(
        id = ProviderTypeIds.TASKER_VARIABLE,
        canonicalName = "TaskerVariable",
        kind = TypeDefinitionKind.PROVIDER,
        documentation = "Immutable name/value entry from the Tasker variable snapshot.",
    )

    val ALL: List<TypeDefinition> = listOf(TASKER_VARIABLE)
}

object LanguageTypeCompatibility {
    fun isAssignable(actual: LanguageTypeRef, expected: LanguageTypeRef): Boolean = when {
        expected is LanguageTypeRef.Nullable -> {
            val actualBase = (actual as? LanguageTypeRef.Nullable)?.baseType ?: actual
            isAssignableNonNullable(actualBase, expected.baseType)
        }
        actual is LanguageTypeRef.Nullable -> false
        else -> isAssignableNonNullable(actual, expected)
    }

    private fun isAssignableNonNullable(actual: LanguageTypeRef, expected: LanguageTypeRef): Boolean =
        expected == CoreTypes.ANY.ref || actual == expected

    fun fromWorkspaceName(name: String?): LanguageTypeRef? {
        val normalized = name?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (normalized.endsWith('?')) {
            val base = fromWorkspaceName(normalized.dropLast(1)) ?: return null
            return LanguageTypeRef.Nullable(base)
        }
        if (normalized.startsWith("List<", ignoreCase = true) && normalized.endsWith('>')) {
            val elementName = normalized.substringAfter('<').dropLast(1).trim()
            val elementType = fromWorkspaceName(elementName) ?: return null
            return LanguageTypeRef.ListOf(elementType)
        }
        return when (normalized.lowercase()) {
        "string", "text" -> CoreTypes.STRING.ref
        "number" -> CoreTypes.NUMBER.ref
        "bool", "boolean" -> CoreTypes.BOOL.ref
        "any" -> CoreTypes.ANY.ref
        "void" -> CoreTypes.VOID.ref
        DomainTypes.POINT.canonicalName.lowercase() -> DomainTypes.POINT.ref
        DomainTypes.PATH_SEGMENT.canonicalName.lowercase() -> DomainTypes.PATH_SEGMENT.ref
        DomainTypes.PATH.canonicalName.lowercase() -> DomainTypes.PATH.ref
        DomainTypes.POINTER_PATH.canonicalName.lowercase() -> DomainTypes.POINTER_PATH.ref
        DomainTypes.MULTI_PATH.canonicalName.lowercase() -> DomainTypes.MULTI_PATH.ref
        DomainTypes.IMAGE_MATCH.canonicalName.lowercase() -> DomainTypes.IMAGE_MATCH.ref
        DomainTypes.TEXT_MATCH.canonicalName.lowercase() -> DomainTypes.TEXT_MATCH.ref
        DomainTypes.MARKER.canonicalName.lowercase() -> DomainTypes.MARKER.ref
        DomainTypes.CHART_SNAPSHOT.canonicalName.lowercase() -> DomainTypes.CHART_SNAPSHOT.ref
        ProviderTypes.TASKER_VARIABLE.canonicalName.lowercase() -> ProviderTypes.TASKER_VARIABLE.ref
        else -> LanguageTypeRef.Named(TypeId("workspace.${normalized.lowercase()}"))
        }
    }

    fun workspaceName(type: LanguageTypeRef?): String? = when (type) {
        CoreTypes.STRING.ref -> "Text"
        CoreTypes.NUMBER.ref -> "Number"
        CoreTypes.BOOL.ref -> "Boolean"
        CoreTypes.ANY.ref -> "Any"
        CoreTypes.VOID.ref -> "Void"
        is LanguageTypeRef.Named -> canonicalNamedType(type) ?: type.id.value.removePrefix("workspace.")
        is LanguageTypeRef.ListOf -> "List<${workspaceName(type.elementType)}>"
        is LanguageTypeRef.Nullable -> "${workspaceName(type.baseType)}?"
        null -> null
    }

    fun sourceName(type: LanguageTypeRef): String = when (type) {
        CoreTypes.STRING.ref -> "String"
        CoreTypes.NUMBER.ref -> "Number"
        CoreTypes.BOOL.ref -> "Bool"
        CoreTypes.ANY.ref -> "Any"
        CoreTypes.VOID.ref -> "Void"
        is LanguageTypeRef.Named -> canonicalNamedType(type) ?: type.id.value.removePrefix("workspace.")
        is LanguageTypeRef.ListOf -> "List<${sourceName(type.elementType)}>"
        is LanguageTypeRef.Nullable -> "${sourceName(type.baseType)}?"
    }

    private fun canonicalNamedType(type: LanguageTypeRef.Named): String? =
        (DomainTypes.ALL + ProviderTypes.ALL).firstOrNull { it.id == type.id }?.canonicalName
}

@Serializable
sealed interface ContractValue {
    val type: LanguageTypeRef

    @Serializable
    @SerialName("string")
    data class StringValue(val value: String) : ContractValue {
        override val type: LanguageTypeRef = CoreTypes.STRING.ref
    }

    @Serializable
    @SerialName("number")
    data class NumberValue(val canonicalValue: String) : ContractValue {
        init {
            require(canonicalValue.toDoubleOrNull()?.isFinite() == true) {
                "NumberValue must use a finite decimal representation."
            }
        }

        override val type: LanguageTypeRef = CoreTypes.NUMBER.ref
    }

    @Serializable
    @SerialName("bool")
    data class BoolValue(val value: Boolean) : ContractValue {
        override val type: LanguageTypeRef = CoreTypes.BOOL.ref
    }

    @Serializable
    @SerialName("list")
    data class ListValue(
        val elementType: LanguageTypeRef,
        val values: List<ContractValue>,
    ) : ContractValue {
        override val type: LanguageTypeRef = LanguageTypeRef.ListOf(elementType)
    }
}
