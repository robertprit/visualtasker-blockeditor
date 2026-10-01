package de.visualtasker.blockeditor.registry

data class LegacyCommandSpelling(
    val name: String,
    val readableThrough: String = "V1.x",
    val earliestRemoval: String = "V2",
)

data class CommandNamingNormalization(
    val stableId: String,
    val canonicalName: String,
    val legacySpellings: List<LegacyCommandSpelling>,
) {
    init {
        require(stableId.isNotBlank())
        require(canonicalName.isNotBlank())
        require(legacySpellings.isNotEmpty())
        require(legacySpellings.map { it.name.lowercase() }.distinct().size == legacySpellings.size)
    }

    val legacyAliases: List<String> = legacySpellings.map(LegacyCommandSpelling::name)
}

object EmscriptV1NamingNormalizations {
    val FILE_WRITE_TEXT = CommandNamingNormalization(
        stableId = "file.writeText",
        canonicalName = "file.writeText",
        legacySpellings = listOf(LegacyCommandSpelling("File.writeText")),
    )
    val CLIPBOARD_SET = CommandNamingNormalization(
        stableId = "clipboard.set",
        canonicalName = "clipboard.set",
        legacySpellings = listOf(LegacyCommandSpelling("Clipboard.set")),
    )
    val CACHE_CLEAR = CommandNamingNormalization(
        stableId = "cache.clear",
        canonicalName = "cache.clear",
        legacySpellings = listOf(LegacyCommandSpelling("Cache.clear")),
    )

    val ALL: List<CommandNamingNormalization> = listOf(
        FILE_WRITE_TEXT,
        CLIPBOARD_SET,
        CACHE_CLEAR,
    )

    private val byId = ALL.associateBy(CommandNamingNormalization::stableId)

    fun byStableId(id: String): CommandNamingNormalization? = byId[id]
}
