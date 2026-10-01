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
        return when (normalized.lowercase()) {
        "string", "text" -> CoreTypes.STRING.ref
        "number" -> CoreTypes.NUMBER.ref
        "bool", "boolean" -> CoreTypes.BOOL.ref
        "any" -> CoreTypes.ANY.ref
        "void" -> CoreTypes.VOID.ref
        else -> LanguageTypeRef.Named(TypeId("workspace.${normalized.lowercase()}"))
        }
    }

    fun workspaceName(type: LanguageTypeRef?): String? = when (type) {
        CoreTypes.STRING.ref -> "Text"
        CoreTypes.NUMBER.ref -> "Number"
        CoreTypes.BOOL.ref -> "Boolean"
        CoreTypes.ANY.ref -> "Any"
        CoreTypes.VOID.ref -> "Void"
        is LanguageTypeRef.Named -> type.id.value.removePrefix("workspace.")
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
        is LanguageTypeRef.Named -> type.id.value.removePrefix("workspace.")
        is LanguageTypeRef.ListOf -> "List<${sourceName(type.elementType)}>"
        is LanguageTypeRef.Nullable -> "${sourceName(type.baseType)}?"
    }
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
