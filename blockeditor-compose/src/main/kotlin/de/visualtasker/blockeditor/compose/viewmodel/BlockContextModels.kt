package de.visualtasker.blockeditor.compose.viewmodel

import de.visualtasker.workflow.core.BlockId
import de.visualtasker.workflow.core.Offset2

data class BlockTypeOption(
    val typeId: String,
    val label: String,
    val categoryLabel: String,
)

data class BlockContextMenuRequest(
    val blockId: BlockId,
    val screenPoint: Offset2,
)
