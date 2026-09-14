package de.visualtasker.blockeditor.compose.ui

import android.media.MediaPlayer
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import de.visualtasker.blockeditor.compose.R

internal enum class BlockEditorFeedbackEvent {
    DragStarted,
    SnapEntered,
    SnapChanged,
    SnapLost,
    Docked,
    Undocked,
    Dropped,
    Collapsed,
    Expanded,
    RejectedDrop,
    Deleted,
    Command,
}

internal fun playEditorFeedback(
    platformView: android.view.View,
    haptic: HapticFeedback,
    event: BlockEditorFeedbackEvent,
    soundEnabled: Boolean,
    hapticEnabled: Boolean,
) {
    if (hapticEnabled) {
        val hapticType = when (event) {
            BlockEditorFeedbackEvent.SnapEntered,
            BlockEditorFeedbackEvent.SnapChanged,
            -> HapticFeedbackType.TextHandleMove
            else -> HapticFeedbackType.LongPress
        }
        haptic.performHapticFeedback(hapticType)
        val platformHaptic = when (event) {
            BlockEditorFeedbackEvent.SnapEntered,
            BlockEditorFeedbackEvent.SnapChanged,
            -> HapticFeedbackConstants.TEXT_HANDLE_MOVE
            BlockEditorFeedbackEvent.SnapLost,
            -> HapticFeedbackConstants.LONG_PRESS
            BlockEditorFeedbackEvent.Docked,
            BlockEditorFeedbackEvent.Dropped,
            BlockEditorFeedbackEvent.Collapsed,
            BlockEditorFeedbackEvent.Expanded,
            -> HapticFeedbackConstants.VIRTUAL_KEY
            BlockEditorFeedbackEvent.Undocked,
            BlockEditorFeedbackEvent.RejectedDrop,
            BlockEditorFeedbackEvent.Deleted,
            -> HapticFeedbackConstants.LONG_PRESS
            BlockEditorFeedbackEvent.DragStarted,
            BlockEditorFeedbackEvent.Command,
            -> HapticFeedbackConstants.KEYBOARD_TAP
        }
        platformView.performHapticFeedback(
            platformHaptic,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
        )
    }
    if (!soundEnabled) return
    platformView.playSoundEffect(SoundEffectConstants.CLICK)
    val rawResId = when (event) {
        BlockEditorFeedbackEvent.Deleted -> R.raw.flow_trashbin
        BlockEditorFeedbackEvent.SnapEntered,
        BlockEditorFeedbackEvent.SnapChanged,
        BlockEditorFeedbackEvent.Docked,
        -> R.raw.flow_snap
        BlockEditorFeedbackEvent.SnapLost,
        BlockEditorFeedbackEvent.Undocked,
        BlockEditorFeedbackEvent.Dropped,
        BlockEditorFeedbackEvent.Collapsed,
        BlockEditorFeedbackEvent.Expanded,
        BlockEditorFeedbackEvent.RejectedDrop,
        BlockEditorFeedbackEvent.DragStarted,
        BlockEditorFeedbackEvent.Command,
        -> null
    }
    if (rawResId != null) {
        runCatching {
            MediaPlayer.create(platformView.context.applicationContext, rawResId)?.apply {
                setVolume(1f, 1f)
                setOnCompletionListener { player -> player.release() }
                setOnErrorListener { player, _, _ ->
                    player.release()
                    true
                }
                start()
            }
        }
    }
}
