package com.jarvis.assistant.ui.grok

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.data.model.ConversationState

/**
 * Low-latency SoundPool audio manager for Grok Bot and cute avatars.
 * Supports multiple sound profiles (Cyber Neon, Cute Chibi, Deep Mech, Hyper Pulse, Soft Ambient, Retro 8-Bit).
 * Includes interactive preview audio auditioning for Idle, Working, Speaking, and Success triggers.
 */
class GrokBotSoundManager(private val context: Context) {

    private val soundPool: SoundPool
    private val soundMap = mutableMapOf<GrokBotDrawingEngine.GrokBotState, Int>()
    private val previewSoundMap = mutableMapOf<String, Int>()
    private val loadedSoundIds = mutableSetOf<Int>()
    private var lastPlayedState: GrokBotDrawingEngine.GrokBotState? = null

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedSoundIds.add(sampleId)
            }
        }

        try {
            val idleId = soundPool.load(context, R.raw.sound_idle, 1)
            val workId = soundPool.load(context, R.raw.sound_working, 1)
            val speakId = soundPool.load(context, R.raw.sound_speaking, 1)
            val successId = soundPool.load(context, R.raw.sound_success, 1)

            soundMap[GrokBotDrawingEngine.GrokBotState.IDLE] = idleId
            soundMap[GrokBotDrawingEngine.GrokBotState.WORKING] = workId
            soundMap[GrokBotDrawingEngine.GrokBotState.SPEAKING] = speakId

            previewSoundMap["idle"] = idleId
            previewSoundMap["working"] = workId
            previewSoundMap["speaking"] = speakId
            previewSoundMap["success"] = successId
        } catch (e: Exception) {
            Log.w("GrokBotSoundManager", "Error loading sound effects: ${e.message}")
        }
    }

    /**
     * Calculates the playback rate (pitch) and volume corresponding to the selected sound profile.
     */
    fun getRateAndVolumeForProfile(profile: String): Pair<Float, Float> {
        return when (profile) {
            "Cute Chibi" -> Pair(1.42f, 0.45f) // High-pitch, playful kawaii chirps
            "Deep Mech" -> Pair(0.78f, 0.48f)  // Low-pitch, resonant robotic bass
            "Hyper Pulse" -> Pair(1.22f, 0.42f) // Upbeat energetic pace
            "Soft Ambient" -> Pair(0.92f, 0.24f) // Gentle, subdued whisper
            "Retro 8-Bit" -> Pair(1.55f, 0.42f) // Fast arcade chip sound
            else -> Pair(1.0f, 0.40f)           // "Cyber Neon" standard
        }
    }

    /**
     * Plays sound effect on state transition according to current profile.
     */
    fun playSoundForState(state: GrokBotDrawingEngine.GrokBotState, soundProfile: String? = null) {
        val prefs = JarvisApp.instance.preferences
        if (!prefs.isAudioFeedbackEnabled) return

        if (lastPlayedState == state) return
        lastPlayedState = state

        if (JarvisConversationService.conversationState.value == ConversationState.SPEAKING &&
            state != GrokBotDrawingEngine.GrokBotState.SPEAKING) {
            return
        }

        val soundId = soundMap[state] ?: return
        if (loadedSoundIds.contains(soundId)) {
            val activeProfile = soundProfile ?: prefs.avatarSoundProfile
            val (rate, volume) = getRateAndVolumeForProfile(activeProfile)
            try {
                soundPool.play(soundId, volume, volume, 1, 0, rate)
            } catch (e: Exception) {
                Log.w("GrokBotSoundManager", "Failed to play sound for $state: ${e.message}")
            }
        }
    }

    /**
     * Interactively auditions a specific sound effect for live user preview.
     */
    fun playPreviewSound(soundType: String, soundProfile: String) {
        val key = soundType.lowercase()
        val soundId = previewSoundMap[key] ?: return
        val (rate, volume) = getRateAndVolumeForProfile(soundProfile)

        try {
            soundPool.play(soundId, volume, volume, 2, 0, rate)
        } catch (e: Exception) {
            Log.w("GrokBotSoundManager", "Failed to play preview sound ($key): ${e.message}")
        }
    }

    fun release() {
        try {
            soundPool.release()
            loadedSoundIds.clear()
        } catch (_: Exception) {}
    }

    companion object {
        @Volatile
        private var instance: GrokBotSoundManager? = null

        fun getInstance(context: Context): GrokBotSoundManager {
            return instance ?: synchronized(this) {
                instance ?: GrokBotSoundManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
