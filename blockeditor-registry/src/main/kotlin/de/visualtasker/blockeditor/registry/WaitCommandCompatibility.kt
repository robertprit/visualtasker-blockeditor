package de.visualtasker.blockeditor.registry

import de.visualtasker.emscript.contract.CommandDefinition

/** Compatibility facade retained for focused wait regression tests. */
object WaitCommandCompatibility {
    val legacyEntry: CommandCatalogEntry = NativeCommandLegacyDefinitions.requireEntry(BlockTypes.ACTION_WAIT)

    fun parityIssues(
        legacy: CommandCatalogEntry,
        native: CommandDefinition = de.visualtasker.emscript.contract.EmscriptV1Commands.WAIT,
    ): List<String> = NativeCommandLegacyDefinitions.parityIssues(legacy, native)
}
