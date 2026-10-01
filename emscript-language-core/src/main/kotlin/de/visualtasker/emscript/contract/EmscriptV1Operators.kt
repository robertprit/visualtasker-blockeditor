package de.visualtasker.emscript.contract

object EmscriptV1OperatorIds {
    val ADD = OperatorId("add")
    val SUBTRACT = OperatorId("subtract")
    val MULTIPLY = OperatorId("multiply")
    val DIVIDE = OperatorId("divide")
    val MODULO = OperatorId("modulo")
    val LESS = OperatorId("less")
    val LESS_OR_EQUAL = OperatorId("lessOrEqual")
    val GREATER = OperatorId("greater")
    val GREATER_OR_EQUAL = OperatorId("greaterOrEqual")
    val EQUAL = OperatorId("equal")
    val NOT_EQUAL = OperatorId("notEqual")
    val AND = OperatorId("and")
    val OR = OperatorId("or")
    val NOT = OperatorId("not")
    val NEGATE = OperatorId("negate")

    val ALL: Set<OperatorId> = linkedSetOf(
        ADD,
        SUBTRACT,
        MULTIPLY,
        DIVIDE,
        MODULO,
        LESS,
        LESS_OR_EQUAL,
        GREATER,
        GREATER_OR_EQUAL,
        EQUAL,
        NOT_EQUAL,
        AND,
        OR,
        NOT,
        NEGATE,
    )
}

object EmscriptV1Operators {
    private val byId: Map<OperatorId, OperatorDefinition> by lazy {
        EmscriptV1LanguageCore.definition.operators.associateBy(OperatorDefinition::id)
    }

    private val bySymbolAndArity: Map<Pair<String, OperatorArity>, OperatorDefinition> by lazy {
        EmscriptV1LanguageCore.definition.operators.associateBy { it.symbol to it.arity }
    }

    fun definition(id: OperatorId): OperatorDefinition? = byId[id]

    fun requireDefinition(id: OperatorId): OperatorDefinition =
        requireNotNull(definition(id)) { "Unknown EMScript v1 operator ID: ${id.value}" }

    fun definition(symbol: String, arity: OperatorArity): OperatorDefinition? =
        bySymbolAndArity[symbol to arity]
}
