package de.visualtasker.blockeditor.compose.host

data class BlockEditorHostUiConfig(
    val showBottomPanel: Boolean = true,
    val showBottomPanelToggle: Boolean = true,
    val showBlockFactory: Boolean = true,
    val allowClearWorkspace: Boolean = false,
    val soundEffectsEnabled: Boolean = false,
    val hapticFeedbackEnabled: Boolean = false,
)
