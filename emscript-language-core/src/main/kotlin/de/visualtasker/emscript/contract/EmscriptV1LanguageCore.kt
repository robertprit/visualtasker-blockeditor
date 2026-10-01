package de.visualtasker.emscript.contract

object EmscriptV1LanguageCore {
    val definition: LanguageCoreDefinition = LanguageCoreDefinition(
        languageVersion = LanguageVersion.V1_0,
        keywords = listOf(
            keyword("let", "LET"),
            keyword("set", "SET"),
            keyword("if", "IF"),
            keyword("elseif", "ELSEIF", "ELSE IF"),
            keyword("else", "ELSE"),
            keyword("end", "END"),
            keyword("repeat", "REPEAT", "LOOP"),
            keyword("while", "WHILE"),
            keyword("for", "FOR"),
            keyword("in", "IN"),
            keyword("break", "BREAK"),
            keyword("continue", "CONTINUE"),
            keyword("function", "FUNCTION"),
            keyword("return", "RETURN"),
            keyword("true", "TRUE"),
            keyword("false", "FALSE"),
        ),
        operators = listOf(
            binary(EmscriptV1OperatorIds.ADD, "+", 4, OperatorFamily.ARITHMETIC),
            binary(EmscriptV1OperatorIds.SUBTRACT, "-", 4, OperatorFamily.ARITHMETIC),
            binary(EmscriptV1OperatorIds.MULTIPLY, "*", 3, OperatorFamily.ARITHMETIC),
            binary(EmscriptV1OperatorIds.DIVIDE, "/", 3, OperatorFamily.ARITHMETIC),
            binary(EmscriptV1OperatorIds.MODULO, "%", 3, OperatorFamily.ARITHMETIC),
            binary(EmscriptV1OperatorIds.LESS, "<", 5, OperatorFamily.COMPARISON),
            binary(EmscriptV1OperatorIds.LESS_OR_EQUAL, "<=", 5, OperatorFamily.COMPARISON),
            binary(EmscriptV1OperatorIds.GREATER, ">", 5, OperatorFamily.COMPARISON),
            binary(EmscriptV1OperatorIds.GREATER_OR_EQUAL, ">=", 5, OperatorFamily.COMPARISON),
            binary(EmscriptV1OperatorIds.EQUAL, "==", 6, OperatorFamily.EQUALITY),
            binary(EmscriptV1OperatorIds.NOT_EQUAL, "!=", 6, OperatorFamily.EQUALITY),
            binary(EmscriptV1OperatorIds.AND, "&&", 7, OperatorFamily.BOOLEAN),
            binary(EmscriptV1OperatorIds.OR, "||", 8, OperatorFamily.BOOLEAN),
            unary(EmscriptV1OperatorIds.NOT, "!", 2, OperatorFamily.BOOLEAN),
            unary(EmscriptV1OperatorIds.NEGATE, "-", 2, OperatorFamily.ARITHMETIC),
        ),
        coreTypes = CoreTypes.ALL,
        casing = CasingPolicy(
            keywords = CanonicalCase.UPPERCASE,
            commands = CanonicalCase.LOWER_CAMEL_CASE,
            types = CanonicalCase.PASCAL_CASE,
            providerNamespaces = CanonicalCase.LOWER_CAMEL_CASE,
            localVariables = CanonicalCase.CASE_SENSITIVE,
        ),
        statementTermination = StatementTerminationPolicy(
            canonical = StatementTerminator.NEWLINE,
            accepted = listOf(StatementTerminator.NEWLINE, StatementTerminator.SEMICOLON),
            serializerOmitsOptionalSemicolons = true,
            newlineTerminatesInsideOpenDelimiters = false,
        ),
    )

    private fun keyword(id: String, canonical: String, vararg aliases: String) =
        KeywordDefinition(KeywordId(id), canonical, aliases.toList())

    private fun binary(id: OperatorId, symbol: String, rank: Int, family: OperatorFamily) =
        OperatorDefinition(
            id = id,
            symbol = symbol,
            precedenceRank = rank,
            associativity = OperatorAssociativity.LEFT,
            arity = OperatorArity.BINARY,
            family = family,
        )

    private fun unary(id: OperatorId, symbol: String, rank: Int, family: OperatorFamily) =
        OperatorDefinition(
            id = id,
            symbol = symbol,
            precedenceRank = rank,
            associativity = OperatorAssociativity.RIGHT,
            arity = OperatorArity.UNARY,
            family = family,
        )
}
