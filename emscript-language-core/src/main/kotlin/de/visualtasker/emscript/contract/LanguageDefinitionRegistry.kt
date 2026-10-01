package de.visualtasker.emscript.contract

interface LanguageDefinitionRegistry {
    val core: LanguageCoreDefinition

    fun commands(): List<CommandDefinition>
    fun projections(): List<ProjectionDefinition>
    fun types(): List<TypeDefinition>
    fun commandById(id: CommandId): CommandDefinition?
    fun commandByCanonicalName(name: String): CommandDefinition?
    fun commandByAcceptedName(name: String): CommandDefinition?
    fun typeById(id: TypeId): TypeDefinition?
    fun typeByCanonicalName(name: String): TypeDefinition?
    fun operatorById(id: OperatorId): OperatorDefinition?
    fun operatorBySymbol(symbol: String, arity: OperatorArity): OperatorDefinition?
}

class ImmutableLanguageDefinitionRegistry(
    override val core: LanguageCoreDefinition,
    commands: List<CommandDefinition> = emptyList(),
    projections: List<ProjectionDefinition> = emptyList(),
    domainTypes: List<TypeDefinition> = emptyList(),
) : LanguageDefinitionRegistry {
    private val commandDefinitions = commands.toList()
    private val projectionDefinitions = projections.toList()
    private val typeDefinitions = (core.coreTypes + domainTypes).toList()
    private val commandsById = commandDefinitions.associateBy(CommandDefinition::id)
    private val commandsByCanonicalName = commandDefinitions.associateBy(CommandDefinition::canonicalName)
    private val commandsByAlias = commandDefinitions
        .flatMap { command -> command.aliases.map { alias -> alias.name to command } }
        .toMap()
    private val typesById = typeDefinitions.associateBy(TypeDefinition::id)
    private val typesByName = typeDefinitions.associateBy(TypeDefinition::canonicalName)
    private val operatorsById = core.operators.associateBy(OperatorDefinition::id)
    private val operatorsBySymbolAndArity = core.operators.associateBy { it.symbol to it.arity }

    init {
        val validationDefinition = core.copy(coreTypes = typeDefinitions)
        val diagnostics = LanguageContractValidator.validate(validationDefinition, commandDefinitions, projectionDefinitions)
        require(diagnostics.isEmpty()) {
            diagnostics.joinToString(prefix = "Invalid language registry:\n", separator = "\n") {
                "${it.code} at ${it.path}: ${it.message}"
            }
        }
        require(typeDefinitions.map(TypeDefinition::id).distinct().size == typeDefinitions.size) {
            "Type IDs must be unique across core and domain types."
        }
        require(typeDefinitions.map(TypeDefinition::canonicalName).distinct().size == typeDefinitions.size) {
            "Type names must be unique across core and domain types."
        }
    }

    override fun commands(): List<CommandDefinition> = commandDefinitions

    override fun projections(): List<ProjectionDefinition> = projectionDefinitions

    override fun types(): List<TypeDefinition> = typeDefinitions

    override fun commandById(id: CommandId): CommandDefinition? = commandsById[id]

    override fun commandByCanonicalName(name: String): CommandDefinition? = commandsByCanonicalName[name]

    override fun commandByAcceptedName(name: String): CommandDefinition? =
        commandsByCanonicalName[name] ?: commandsByAlias[name]

    override fun typeById(id: TypeId): TypeDefinition? = typesById[id]

    override fun typeByCanonicalName(name: String): TypeDefinition? = typesByName[name]

    override fun operatorById(id: OperatorId): OperatorDefinition? = operatorsById[id]

    override fun operatorBySymbol(symbol: String, arity: OperatorArity): OperatorDefinition? =
        operatorsBySymbolAndArity[symbol to arity]
}
