package de.visualtasker.blockeditor.compose.host

import de.visualtasker.blockeditor.compose.viewmodel.BlockInfoField
import de.visualtasker.blockeditor.compose.viewmodel.BlockInfoSnapshot
import de.visualtasker.blockeditor.compose.viewmodel.DragRenderState
import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.Offset2
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.interaction.ViewportState
import de.visualtasker.blockeditor.layout.LayoutCache
import de.visualtasker.blockeditor.registry.BlockDefinition
import de.visualtasker.blockeditor.registry.BlockRegistry

/**
 * Read-only editor surface state exposed to [BlockEditorHost].
 */
interface BlockEditorControllerState {
    val document: WorkspaceDocument
    val layoutCache: LayoutCache
    val viewport: ViewportState
    val runtimeState: BlockEditorRuntimeState
    val dragRender: DragRenderState?
    val selectedBlockIds: Set<BlockId>
    val showBottomPanel: Boolean
    val expandedCategory: String?
    val showBlockFactory: Boolean
    val codePreview: String
    val registry: BlockRegistry
    val blockRegistry: BlockRegistry

    fun selectedBlockInfo(): BlockInfoSnapshot?
    fun definitionsForExpandedCategory(): List<BlockDefinition>
}
