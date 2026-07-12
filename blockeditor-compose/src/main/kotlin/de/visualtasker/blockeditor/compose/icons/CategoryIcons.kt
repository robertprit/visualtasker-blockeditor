package de.visualtasker.blockeditor.compose.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.ui.graphics.vector.ImageVector
import de.visualtasker.blockeditor.registry.BlockCategories

object CategoryIcons {
    fun forCategory(category: String): ImageVector = when (category) {
        BlockCategories.EVENT -> Icons.Filled.PlayArrow
        BlockCategories.ACTION -> Icons.Filled.AdsClick
        BlockCategories.CONTROL -> Icons.Filled.Repeat
        BlockCategories.LOGIC -> Icons.Filled.ToggleOn
        BlockCategories.DEBUG -> Icons.Filled.BugReport
        BlockCategories.VARIABLE -> Icons.Filled.Functions
        BlockCategories.CUSTOM -> Icons.Filled.Build
        else -> Icons.AutoMirrored.Filled.CallSplit
    }
}
