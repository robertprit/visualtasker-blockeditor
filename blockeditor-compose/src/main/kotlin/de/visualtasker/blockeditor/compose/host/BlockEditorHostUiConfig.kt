package de.visualtasker.blockeditor.compose.host

import de.visualtasker.blockeditor.registry.BlockCategories

data class BlockEditorHostUiConfig(
    val showBottomPanel: Boolean = true,
    val showBottomPanelToggle: Boolean = true,
    val showBlockFactory: Boolean = true,
    val showBlockFactoryEntry: Boolean = true,
    val showToolbox: Boolean = true,
    val allowClearWorkspace: Boolean = false,
    val soundEffectsEnabled: Boolean = false,
    val hapticFeedbackEnabled: Boolean = false,
    val gridEnabled: Boolean = true,
    val extraCategories: List<BlockCategories.CategoryMeta> = emptyList(),
)
