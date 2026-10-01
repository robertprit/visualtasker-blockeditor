package de.visualtasker.blockeditor.registry

import de.visualtasker.emscript.contract.CommandDefinition
import de.visualtasker.emscript.contract.EmscriptV1Commands

object BeepCommandCompatibility {
    val legacyEntry: CommandCatalogEntry = NativeCommandLegacyDefinitions.requireEntry(BlockTypes.FEEDBACK_BEEP)

    fun parityIssues(
        legacy: CommandCatalogEntry,
        native: CommandDefinition = EmscriptV1Commands.BEEP,
    ): List<String> = NativeCommandLegacyDefinitions.parityIssues(legacy, native)
}
