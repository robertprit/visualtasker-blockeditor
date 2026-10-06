package de.visualtasker.blockeditor.registry

import de.visualtasker.workflow.semantics.CommandCatalog
import de.visualtasker.workflow.semantics.VisualTaskerCommandCatalog
import de.visualtasker.workflow.semantics.shortDisplayName

fun BlockDefinition.withCommandCatalogMetadata(
    catalog: CommandCatalog = VisualTaskerCommandCatalog,
): BlockDefinition {
    val entry = catalog.findByBlockType(id) ?: return this
    return copy(
        metadata = metadata + mapOf(
            VisualTaskerCommandCatalog.METADATA_COMMAND_ID to entry.id,
            VisualTaskerCommandCatalog.METADATA_CANONICAL_NAME to entry.canonicalName,
            VisualTaskerCommandCatalog.METADATA_COMMAND_KIND to entry.kind.name,
            VisualTaskerCommandCatalog.METADATA_PLUGIN_OWNER to entry.pluginOwner,
            VisualTaskerCommandCatalog.METADATA_RUNTIME_CAPABILITIES to entry.capabilities.joinToString(",") { it.name },
            VisualTaskerCommandCatalog.METADATA_DISPLAY_NAME to entry.canonicalName,
            VisualTaskerCommandCatalog.METADATA_SHORT_NAME to entry.shortDisplayName(),
            VisualTaskerCommandCatalog.METADATA_RUNTIME_STATUS to (entry.runtime?.dryRunBehavior ?: "unknown"),
        ),
    )
}
