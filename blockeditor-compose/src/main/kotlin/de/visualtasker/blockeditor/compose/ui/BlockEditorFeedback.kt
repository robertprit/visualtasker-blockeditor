package de.visualtasker.blockeditor.compose.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.view.SoundEffectConstants
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

internal enum class BlockEditorFeedbackEvent {
    DragStarted,
    SnapEntered,
    SnapChanged,
    SnapLost,
    Connected,
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
    }
    if (!soundEnabled) return
    platformView.playSoundEffect(SoundEffectConstants.CLICK)
    val (tone, durationMs, volume) = when (event) {
        BlockEditorFeedbackEvent.SnapEntered -> Triple(ToneGenerator.TONE_PROP_ACK, 24, 18)
        BlockEditorFeedbackEvent.SnapChanged -> Triple(ToneGenerator.TONE_PROP_ACK, 18, 16)
        BlockEditorFeedbackEvent.SnapLost -> Triple(ToneGenerator.TONE_PROP_NACK, 28, 16)
        BlockEditorFeedbackEvent.Connected -> Triple(ToneGenerator.TONE_PROP_BEEP, 44, 32)
        BlockEditorFeedbackEvent.RejectedDrop -> Triple(ToneGenerator.TONE_PROP_NACK, 48, 28)
        BlockEditorFeedbackEvent.Deleted -> Triple(ToneGenerator.TONE_PROP_NACK, 54, 32)
        BlockEditorFeedbackEvent.DragStarted,
        BlockEditorFeedbackEvent.Command,
        -> Triple(ToneGenerator.TONE_PROP_BEEP, 30, 24)
    }
    runCatching {
        val generator = ToneGenerator(AudioManager.STREAM_SYSTEM, volume)
        generator.startTone(tone, durationMs)
        platformView.postDelayed({ generator.release() }, (durationMs + 40).toLong())
    }
}

