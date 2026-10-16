package com.jarvis.assistant.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.automation.session.AvatarState
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.data.model.ConversationState

/**
 * Manages micro-interaction sound effects and haptic feedback for Overlay Avatar transitions.
 * Strictly adheres to user preferences: completely silent if audio feedback is off,
 * and never interrupts active Gemini Live voice conversation audio output.
 */
object AvatarSoundEffectManager {
    private const val TAG = "AvatarSoundEffect"
    private var activePlayer: MediaPlayer? = null

    fun playStateFeedback(context: Context, state: AvatarState) {
        val prefs = JarvisApp.instance.preferences

        // 1. Haptic Feedback
        if (prefs.isHapticFeedbackEnabled) {
            triggerHaptic(context, state)
        }

        // 2. Audio Feedback Check
        if (!prefs.isAudioFeedbackEnabled) {
            return
        }

        // Route to zero-latency SoundPool for Grok Bot and cute variants
        val activeAvatar = prefs.avatarStyle
        if (com.jarvis.assistant.ui.grok.GrokBotDrawingEngine.isGrokFamilyStyle(activeAvatar)) {
            val grokState = com.jarvis.assistant.ui.grok.GrokBotDrawingEngine.GrokBotState.fromAvatarState(state)
            com.jarvis.assistant.ui.grok.GrokBotSoundManager.getInstance(context).playSoundForState(grokState)
            return
        }

        val soundRes = when (state) {
            AvatarState.IDLE -> R.raw.sound_idle
            AvatarState.EXECUTING, AvatarState.THINKING, AvatarState.VERIFYING -> R.raw.sound_working
            AvatarState.SPEAKING -> R.raw.sound_speaking
            AvatarState.PAUSED -> R.raw.sound_paused
            AvatarState.SUCCESS -> R.raw.sound_success
            AvatarState.FAILED, AvatarState.RETRYING -> R.raw.sound_error
            AvatarState.RECONNECTING, AvatarState.CONNECTING -> R.raw.sound_reconnecting
            AvatarState.LISTENING, AvatarState.DISCONNECTED, AvatarState.ENDED -> null
        } ?: return

        try {
            activePlayer?.run {
                if (isPlaying) stop()
                release()
            }
            activePlayer = null

            val player = MediaPlayer.create(context.applicationContext, soundRes)?.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .build()
                )
                setVolume(0.35f, 0.35f) // gentle, non-intrusive volume
                setOnCompletionListener { mp ->
                    mp.release()
                    if (activePlayer == mp) activePlayer = null
                }
                start()
            }
            activePlayer = player
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play sound for state $state: ${e.message}")
        }
    }

    private fun triggerHaptic(context: Context, state: AvatarState) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (!vibrator.hasVibrator()) return

            val effect = when (state) {
                AvatarState.SUCCESS -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    } else {
                        VibrationEffect.createOneShot(25, 120)
                    }
                }
                AvatarState.FAILED -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                    } else {
                        VibrationEffect.createOneShot(45, 200)
                    }
                }
                AvatarState.RETRYING, AvatarState.RECONNECTING -> {
                    VibrationEffect.createOneShot(18, 80)
                }
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    } else {
                        VibrationEffect.createOneShot(12, 60)
                    }
                }
            }
            vibrator.vibrate(effect)
        } catch (_: Exception) {}
    }
}
