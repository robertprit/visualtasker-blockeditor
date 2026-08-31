package de.visualtasker.blockeditor.compose.viewmodel

import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.Offset2

data class BlockTypeOption(
    val typeId: String,
    val label: String,
    val categoryLabel: String,
)

data class BlockContextMenuRequest(
    val blockId: BlockId,
    val screenPoint: Offset2,
)
