package de.visualtasker.blockeditor.compose.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.JoinFull
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.ui.graphics.vector.ImageVector
import de.visualtasker.blockeditor.registry.BlockTypes

object BlockIcons {
    fun forBlockType(type: String): ImageVector = when (type) {
        BlockTypes.EVENT_START -> Icons.Filled.PlayArrow
        BlockTypes.ACTION_CLICK_TEXT -> Icons.Filled.AdsClick
        BlockTypes.ACTION_WAIT -> Icons.Filled.HourglassTop
        BlockTypes.DEBUG_LOG -> Icons.Filled.BugReport
        BlockTypes.CONTROL_REPEAT -> Icons.Filled.Repeat
        BlockTypes.CONTROL_WHILE -> Icons.Filled.Loop
        BlockTypes.CONTROL_IF -> Icons.AutoMirrored.Filled.CallSplit
        BlockTypes.CONTROL_IF_ELSE -> Icons.AutoMirrored.Filled.CallSplit
        BlockTypes.CONTROL_IF_ELSEIF_ELSE -> Icons.Filled.AccountTree
        BlockTypes.LOGIC_SCREEN_CONTAINS -> Icons.Filled.Search
        BlockTypes.LOGIC_BOOLEAN -> Icons.Filled.ToggleOn
        BlockTypes.LOGIC_AND -> Icons.Filled.JoinFull
        BlockTypes.LOGIC_OR -> Icons.AutoMirrored.Filled.CallSplit
        BlockTypes.LOGIC_OPERATE -> Icons.Filled.Functions
        BlockTypes.VARIABLE_GET -> Icons.Filled.Functions
        BlockTypes.VARIABLE_SET -> Icons.Filled.Edit
        else -> when {
            type.startsWith(BlockTypes.VARIABLE_REPORTER_PREFIX) -> Icons.Filled.Functions
            type.startsWith(BlockTypes.CUSTOM_PREFIX) -> Icons.Filled.Build
            else -> Icons.Filled.SubdirectoryArrowRight
        }
    }

    fun forSlotName(name: String): ImageVector = when (name) {
        BlockTypes.SLOT_DO -> Icons.Filled.Loop
        BlockTypes.SLOT_THEN -> Icons.Filled.Check
        BlockTypes.SLOT_ELIF -> Icons.AutoMirrored.Filled.CallSplit
        BlockTypes.SLOT_ELSE -> Icons.Filled.Close
        BlockTypes.SLOT_BODY -> Icons.Filled.Loop
        else -> Icons.Filled.SubdirectoryArrowRight
    }
}
