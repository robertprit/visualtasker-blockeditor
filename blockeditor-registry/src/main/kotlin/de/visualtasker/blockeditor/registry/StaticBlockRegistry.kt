package de.visualtasker.blockeditor.registry

/** Immutable, stable-order registry suitable for host-supplied complete catalogs. */
class StaticBlockRegistry(definitions: List<BlockDefinition>) : BlockRegistry {
    private val orderedDefinitions = definitions.toList()
    private val definitionsById: Map<String, BlockDefinition>

    init {
        val duplicate = orderedDefinitions.groupingBy(BlockDefinition::id).eachCount()
            .entries.firstOrNull { it.value > 1 }
        require(duplicate == null) { "Duplicate block definition id: ${duplicate?.key}" }
        definitionsById = orderedDefinitions.associateBy(BlockDefinition::id)
    }

    override fun getDefinition(id: String): BlockDefinition? = definitionsById[id]

    override fun allDefinitions(): List<BlockDefinition> = orderedDefinitions.toList()
}
