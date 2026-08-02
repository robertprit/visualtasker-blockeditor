package de.visualtasker.blockeditor.compose.host

data class BlockEditorPanelCloseState(
    val showBlockFactory: Boolean,
    val expandedCategory: String?,
    val showBottomPanel: Boolean,
)

enum class BlockEditorPanelCloseTarget {
    BlockFactory,
    CategoryPalette,
    BottomPanel,
}

fun topMostCloseTarget(state: BlockEditorPanelCloseState): BlockEditorPanelCloseTarget? =
    when {
        state.showBlockFactory -> BlockEditorPanelCloseTarget.BlockFactory
        state.expandedCategory != null -> BlockEditorPanelCloseTarget.CategoryPalette
        state.showBottomPanel -> BlockEditorPanelCloseTarget.BottomPanel
        else -> null
    }
