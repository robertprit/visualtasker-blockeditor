package de.visualtasker.blockeditor.compose.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

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
    val (tone, durationMs, volume) = when (event) {
        BlockEditorFeedbackEvent.SnapEntered -> Triple(ToneGenerator.TONE_PROP_ACK, 24, 18)
        BlockEditorFeedbackEvent.SnapChanged -> Triple(ToneGenerator.TONE_PROP_ACK, 18, 16)
        BlockEditorFeedbackEvent.SnapLost -> Triple(ToneGenerator.TONE_PROP_NACK, 28, 16)
        BlockEditorFeedbackEvent.Docked -> Triple(ToneGenerator.TONE_PROP_ACK, 44, 32)
        BlockEditorFeedbackEvent.Undocked -> Triple(ToneGenerator.TONE_PROP_NACK, 34, 22)
        BlockEditorFeedbackEvent.Dropped -> Triple(ToneGenerator.TONE_PROP_BEEP, 30, 20)
        BlockEditorFeedbackEvent.Collapsed -> Triple(ToneGenerator.TONE_PROP_BEEP, 24, 18)
        BlockEditorFeedbackEvent.Expanded -> Triple(ToneGenerator.TONE_PROP_ACK, 28, 18)
        BlockEditorFeedbackEvent.RejectedDrop -> Triple(ToneGenerator.TONE_PROP_NACK, 48, 28)
        BlockEditorFeedbackEvent.Deleted -> Triple(ToneGenerator.TONE_PROP_NACK, 54, 32)
        BlockEditorFeedbackEvent.DragStarted,
        BlockEditorFeedbackEvent.Command,
        -> Triple(ToneGenerator.TONE_PROP_BEEP, 30, 24)
    }
    runCatching {
        val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, volume)
        generator.startTone(tone, durationMs)
        platformView.postDelayed({ generator.release() }, (durationMs + 40).toLong())
    }
}
