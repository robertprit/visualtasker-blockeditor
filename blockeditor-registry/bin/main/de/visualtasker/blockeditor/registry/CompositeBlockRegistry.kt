package de.visualtasker.blockeditor.registry

class CompositeBlockRegistry(
    private val base: BlockRegistry = DefaultBlockRegistry,
) : BlockRegistry {
    private val custom = linkedMapOf<String, BlockDefinition>()

    fun register(definition: BlockDefinition) {
        custom[definition.id] = definition
    }

    fun unregister(id: String) {
        custom.remove(id)
    }

    fun customDefinitions(): List<BlockDefinition> = custom.values.toList()

    override fun getDefinition(id: String): BlockDefinition? =
        custom[id] ?: base.getDefinition(id)

    override fun allDefinitions(): List<BlockDefinition> =
        base.allDefinitions() + custom.values

    fun definitionsByCategory(category: String): List<BlockDefinition> =
        allDefinitions().filter { it.category == category }
}
