package de.visualtasker.emscript.contract

import kotlinx.serialization.Serializable

private val stableIdPattern = Regex("[A-Za-z][A-Za-z0-9]*(?:[._-][A-Za-z0-9]+)*")

private fun requireStableId(kind: String, value: String) {
    require(stableIdPattern.matches(value)) {
        "$kind must be a stable ASCII identifier, but was '$value'."
    }
}

@JvmInline
@Serializable
value class KeywordId(val value: String) {
    init { requireStableId("KeywordId", value) }
}

@JvmInline
@Serializable
value class OperatorId(val value: String) {
    init { requireStableId("OperatorId", value) }
}

@JvmInline
@Serializable
value class TypeId(val value: String) {
    init { requireStableId("TypeId", value) }
}

@JvmInline
@Serializable
value class CommandId(val value: String) {
    init { requireStableId("CommandId", value) }
}

@JvmInline
@Serializable
value class ParameterId(val value: String) {
    init { requireStableId("ParameterId", value) }
}

@JvmInline
@Serializable
value class CommandDomainId(val value: String) {
    init { requireStableId("CommandDomainId", value) }
}

@JvmInline
@Serializable
value class CommandFamilyId(val value: String) {
    init { requireStableId("CommandFamilyId", value) }
}

@JvmInline
@Serializable
value class CommandVariantId(val value: String) {
    init { requireStableId("CommandVariantId", value) }
}

@JvmInline
@Serializable
value class CapabilityId(val value: String) {
    init { requireStableId("CapabilityId", value) }
}

@JvmInline
@Serializable
value class ProviderId(val value: String) {
    init { requireStableId("ProviderId", value) }
}

@JvmInline
@Serializable
value class ProjectionId(val value: String) {
    init { requireStableId("ProjectionId", value) }
}

@Serializable
data class LanguageVersion(
    val major: Int,
    val minor: Int,
    val patch: Int = 0,
) : Comparable<LanguageVersion> {
    init {
        require(major >= 0 && minor >= 0 && patch >= 0) {
            "LanguageVersion components must be non-negative."
        }
    }

    override fun compareTo(other: LanguageVersion): Int =
        compareValuesBy(this, other, LanguageVersion::major, LanguageVersion::minor, LanguageVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        val V1_0: LanguageVersion = LanguageVersion(1, 0, 0)
        val V2_0: LanguageVersion = LanguageVersion(2, 0, 0)
    }
}

@Serializable
data class DefinitionLifecycle(
    val sinceVersion: LanguageVersion,
    val deprecatedSince: LanguageVersion? = null,
    val removedSince: LanguageVersion? = null,
    val replacementCommandId: CommandId? = null,
)
