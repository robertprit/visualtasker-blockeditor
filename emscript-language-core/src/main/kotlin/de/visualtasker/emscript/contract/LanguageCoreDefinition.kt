package de.visualtasker.emscript.contract

import kotlinx.serialization.Serializable

@Serializable
data class KeywordDefinition(
    val id: KeywordId,
    val canonical: String,
    val legacyAliases: List<String> = emptyList(),
)

@Serializable
enum class OperatorAssociativity {
    LEFT,
    RIGHT,
}

@Serializable
enum class OperatorArity {
    UNARY,
    BINARY,
}

@Serializable
enum class OperatorFamily {
    ARITHMETIC,
    COMPARISON,
    EQUALITY,
    BOOLEAN,
}

@Serializable
data class OperatorDefinition(
    val id: OperatorId,
    val symbol: String,
    val precedenceRank: Int,
    val associativity: OperatorAssociativity,
    val arity: OperatorArity,
    val family: OperatorFamily,
)

@Serializable
enum class CanonicalCase {
    UPPERCASE,
    LOWER_CAMEL_CASE,
    PASCAL_CASE,
    CASE_SENSITIVE,
}

@Serializable
data class CasingPolicy(
    val keywords: CanonicalCase,
    val commands: CanonicalCase,
    val types: CanonicalCase,
    val providerNamespaces: CanonicalCase,
    val localVariables: CanonicalCase,
)

@Serializable
enum class StatementTerminator {
    NEWLINE,
    SEMICOLON,
}

@Serializable
data class StatementTerminationPolicy(
    val canonical: StatementTerminator,
    val accepted: List<StatementTerminator>,
    val serializerOmitsOptionalSemicolons: Boolean,
    val newlineTerminatesInsideOpenDelimiters: Boolean,
)

@Serializable
data class LanguageCoreDefinition(
    val languageVersion: LanguageVersion,
    val keywords: List<KeywordDefinition>,
    val operators: List<OperatorDefinition>,
    val coreTypes: List<TypeDefinition>,
    val casing: CasingPolicy,
    val statementTermination: StatementTerminationPolicy,
)
