package de.visualtasker.blockeditor.emscript

import de.visualtasker.emscript.contract.EmscriptSourceAnchorContract
import de.visualtasker.workflow.core.BlockId
import de.visualtasker.workflow.core.BlockNode
import de.visualtasker.workflow.core.CanonicalWorkspaceMigration
import de.visualtasker.workflow.core.Connection
import de.visualtasker.workflow.core.SemanticEntityKind
import de.visualtasker.workflow.core.SemanticRelation
import de.visualtasker.workflow.core.WorkspaceDocument
import de.visualtasker.workflow.core.WorkspaceGraph
import de.visualtasker.workflow.core.allConnections
import de.visualtasker.workflow.semantics.WorkflowElementTypes

internal data class ProjectedSourceEntityAnchor(
    val locator: String,
    val entityId: String,
    val entityKind: SemanticEntityKind,
)

internal data class ProjectedSourceRelationAnchor(
    val relation: SemanticRelation,
)

internal data class ProjectedStatementSourceAnchors(
    val entities: List<ProjectedSourceEntityAnchor>,
)

internal data class WorkspaceSourceAnchorProjection(
    val statements: List<ProjectedStatementSourceAnchors>,
    val relations: List<ProjectedSourceRelationAnchor>,
)

/** Read-only projection from canonical identity to runtime-inert EMScript directives. */
internal object WorkspaceSourceAnchorProjector {
    fun project(document: WorkspaceDocument): WorkspaceSourceAnchorProjection? {
        val migrated = CanonicalWorkspaceMigration.toCurrent(document)
        if (!migrated.isValid) return null
        val canonicalDocument = migrated.document
        val canonical = requireNotNull(canonicalDocument.canonical)
        val entitiesByLegacySource = canonical.entities
            .filter { !it.legacySourceId.isNullOrBlank() }
            .associateBy { requireNotNull(it.legacySourceId) }
        val connectionOwners = canonicalDocument.blocks.values
            .flatMap(BlockNode::allConnections)
            .associate { it.id to it.owner }
        val statementAnchors = mutableListOf<ProjectedStatementSourceAnchors>()
        val projectedEntityIds = linkedSetOf<String>()
        val visitedStatements = mutableSetOf<BlockId>()
        lateinit var appendChain: (BlockId) -> Unit

        fun target(connection: Connection?): BlockId? =
            connection?.connectedTo?.let(connectionOwners::get)

        fun collectExpression(
            blockId: BlockId,
            path: List<String>,
            destination: MutableList<ProjectedSourceEntityAnchor>,
            visited: MutableSet<BlockId>,
        ) {
            if (!visited.add(blockId)) return
            val block = canonicalDocument.blocks[blockId] ?: return
            entitiesByLegacySource[blockId.value]
                ?.takeIf { it.ref.kind == SemanticEntityKind.Expression }
                ?.let { entity ->
                    destination += ProjectedSourceEntityAnchor(
                        locator = EmscriptSourceAnchorContract.inputLocator(path),
                        entityId = entity.ref.id.value,
                        entityKind = entity.ref.kind,
                    )
                    projectedEntityIds += entity.ref.id.value
                }
            block.valueInputs.forEach { input ->
                target(input.connection)?.let { child ->
                    collectExpression(child, path + input.name, destination, visited)
                }
            }
        }

        fun appendStatement(blockId: BlockId) {
            if (!visitedStatements.add(blockId)) return
            val block = canonicalDocument.blocks[blockId] ?: return
            val anchors = mutableListOf<ProjectedSourceEntityAnchor>()
            entitiesByLegacySource[blockId.value]
                ?.takeIf { it.ref.kind == SemanticEntityKind.Statement }
                ?.let { entity ->
                    anchors += ProjectedSourceEntityAnchor(
                        locator = EmscriptSourceAnchorContract.BLOCK_LOCATOR,
                        entityId = entity.ref.id.value,
                        entityKind = entity.ref.kind,
                    )
                    projectedEntityIds += entity.ref.id.value
                }
            block.valueInputs.forEach { input ->
                target(input.connection)?.let { child ->
                    collectExpression(child, listOf(input.name), anchors, mutableSetOf())
                }
            }
            block.statementInputs.forEach { input ->
                entitiesByLegacySource["${blockId.value}:${input.name}"]
                    ?.takeIf { it.ref.kind == SemanticEntityKind.Branch }
                    ?.let { entity ->
                        anchors += ProjectedSourceEntityAnchor(
                            locator = EmscriptSourceAnchorContract.branchLocator(input.name),
                            entityId = entity.ref.id.value,
                            entityKind = entity.ref.kind,
                        )
                        projectedEntityIds += entity.ref.id.value
                    }
            }
            statementAnchors += ProjectedStatementSourceAnchors(
                entities = anchors.sortedWith(compareBy(ProjectedSourceEntityAnchor::locator, ProjectedSourceEntityAnchor::entityId)),
            )

            statementSlotsInEmissionOrder(block).forEach { slotName ->
                val input = block.statementInputs.firstOrNull { it.name == slotName } ?: return@forEach
                target(input.connection)?.let(appendChain)
            }
        }

        appendChain = { headId ->
            var current: BlockId? = headId
            val localVisited = mutableSetOf<BlockId>()
            while (current != null && localVisited.add(current)) {
                appendStatement(current)
                current = WorkspaceGraph.nextChain(canonicalDocument, current)
            }
        }

        canonicalDocument.rootBlocks
            .mapNotNull { id -> canonicalDocument.blocks[id]?.takeIf { it.type == WorkflowElementTypes.EVENT_START } }
            .forEach { start -> WorkspaceGraph.nextChain(canonicalDocument, start.id)?.let(appendChain) }

        val relationAnchors = canonical.relations
            .filter { relation ->
                relation.source.id.value in projectedEntityIds && relation.target.id.value in projectedEntityIds
            }
            .sortedBy { it.id.value }
            .map(::ProjectedSourceRelationAnchor)
        return WorkspaceSourceAnchorProjection(statementAnchors, relationAnchors)
    }

    private fun statementSlotsInEmissionOrder(block: BlockNode): List<String> = when (block.type) {
        WorkflowElementTypes.CONTROL_REPEAT -> listOf(WorkflowElementTypes.SLOT_DO)
        WorkflowElementTypes.CONTROL_WHILE -> listOf(WorkflowElementTypes.SLOT_BODY)
        WorkflowElementTypes.CONTROL_IF -> listOf(WorkflowElementTypes.SLOT_THEN)
        WorkflowElementTypes.CONTROL_IF_ELSE -> listOf(WorkflowElementTypes.SLOT_THEN, WorkflowElementTypes.SLOT_ELSE)
        WorkflowElementTypes.CONTROL_IF_ELSEIF_ELSE -> buildList {
            add(WorkflowElementTypes.SLOT_THEN)
            addAll(
                block.statementInputs
                    .map { it.name }
                    .filter { it == WorkflowElementTypes.SLOT_ELIF || it.startsWith("ELIF_") }
                    .sortedWith(compareBy(::elseIfSlotIndex)),
            )
            add(WorkflowElementTypes.SLOT_ELSE)
        }
        else -> emptyList()
    }

    private fun elseIfSlotIndex(slot: String): Int =
        if (slot == WorkflowElementTypes.SLOT_ELIF) 0 else slot.removePrefix("ELIF_").toIntOrNull() ?: Int.MAX_VALUE

}
