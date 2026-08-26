package de.visualtasker.blockeditor.domain

sealed interface WorkspaceAction {
    data class InstantiateBlock(val definitionId: String, val x: Float, val y: Float) : WorkspaceAction
    data class DeleteBlock(val blockId: BlockId) : WorkspaceAction
    data class MoveRoot(val blockId: BlockId, val x: Float, val y: Float) : WorkspaceAction
    data class Connect(val source: ConnectionId, val target: ConnectionId) : WorkspaceAction
    data class Disconnect(val connection: ConnectionId) : WorkspaceAction
    data class DetachBlock(val blockId: BlockId) : WorkspaceAction
    data class Collapse(val blockId: BlockId) : WorkspaceAction
    data class Expand(val blockId: BlockId) : WorkspaceAction
    data class UpdateField(val blockId: BlockId, val key: String, val value: FieldValue) : WorkspaceAction
    data class CreateVariable(val variable: VariableDefinition) : WorkspaceAction
    data class RenameVariable(val variableId: String, val name: String) : WorkspaceAction
    data class DeleteVariable(val variableId: String) : WorkspaceAction
}
