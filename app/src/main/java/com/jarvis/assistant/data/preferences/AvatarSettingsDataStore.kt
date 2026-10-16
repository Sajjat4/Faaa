package com.jarvis.assistant.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jarvis.assistant.JarvisApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.avatarDataStore: DataStore<Preferences> by preferencesDataStore(name = "avatar_settings_prefs")

data class AvatarSettings(
    val avatarStyle: String = "MetaMuse",
    val soundProfile: String = "Cyber Neon",
    val animationSpeed: String = "Normal",
    val colorTheme: String = "Blue",
    val particleAnimation: Boolean = true,
    val audioWaveform: Boolean = true,
    val audioFeedback: Boolean = true,
    val hapticFeedback: Boolean = true,
    val showStatusText: Boolean = true,
    val reduceMotion: Boolean = false,
    val dynamicIslandEnabled: Boolean = true,
    val overlayEnabled: Boolean = true,
    val avatarScalePercent: Int = 100
)

class AvatarSettingsDataStore(private val context: Context) {

    companion object {
        val KEY_AVATAR_STYLE = stringPreferencesKey("avatar_style")
        val KEY_SOUND_PROFILE = stringPreferencesKey("sound_profile")
        val KEY_ANIMATION_SPEED = stringPreferencesKey("animation_speed")
        val KEY_COLOR_THEME = stringPreferencesKey("color_theme")
        val KEY_PARTICLE_ANIMATION = booleanPreferencesKey("particle_animation")
        val KEY_AUDIO_WAVEFORM = booleanPreferencesKey("audio_waveform")
        val KEY_AUDIO_FEEDBACK = booleanPreferencesKey("audio_feedback")
        val KEY_HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val KEY_SHOW_STATUS_TEXT = booleanPreferencesKey("show_status_text")
        val KEY_REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val KEY_DYNAMIC_ISLAND = booleanPreferencesKey("dynamic_island")
        val KEY_OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val KEY_AVATAR_SCALE_PERCENT = intPreferencesKey("avatar_scale_percent")
    }

    val settingsFlow: Flow<AvatarSettings> = context.avatarDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val appPrefs = JarvisApp.instance.preferences
            AvatarSettings(
                avatarStyle = prefs[KEY_AVATAR_STYLE] ?: appPrefs.avatarStyle,
                soundProfile = prefs[KEY_SOUND_PROFILE] ?: appPrefs.avatarSoundProfile,
                animationSpeed = prefs[KEY_ANIMATION_SPEED] ?: appPrefs.avatarAnimationSpeed,
                colorTheme = prefs[KEY_COLOR_THEME] ?: appPrefs.avatarColorTheme,
                particleAnimation = prefs[KEY_PARTICLE_ANIMATION] ?: appPrefs.isParticleAnimationEnabled,
                audioWaveform = prefs[KEY_AUDIO_WAVEFORM] ?: appPrefs.isAudioWaveformEnabled,
                audioFeedback = prefs[KEY_AUDIO_FEEDBACK] ?: appPrefs.isAudioFeedbackEnabled,
                hapticFeedback = prefs[KEY_HAPTIC_FEEDBACK] ?: appPrefs.isHapticFeedbackEnabled,
                showStatusText = prefs[KEY_SHOW_STATUS_TEXT] ?: appPrefs.isShowStatusTextEnabled,
                reduceMotion = prefs[KEY_REDUCE_MOTION] ?: appPrefs.isReduceMotionEnabled,
                dynamicIslandEnabled = prefs[KEY_DYNAMIC_ISLAND] ?: appPrefs.isDynamicIslandEnabled,
                overlayEnabled = prefs[KEY_OVERLAY_ENABLED] ?: appPrefs.isTaskOverlayEnabled,
                avatarScalePercent = prefs[KEY_AVATAR_SCALE_PERCENT] ?: appPrefs.avatarScalePercent
            )
        }

    suspend fun loadCurrentSettings(): AvatarSettings {
        return try {
            settingsFlow.first()
        } catch (_: Exception) {
            val appPrefs = JarvisApp.instance.preferences
            AvatarSettings(
                avatarStyle = appPrefs.avatarStyle,
                soundProfile = appPrefs.avatarSoundProfile,
                animationSpeed = appPrefs.avatarAnimationSpeed,
                colorTheme = appPrefs.avatarColorTheme,
                particleAnimation = appPrefs.isParticleAnimationEnabled,
                audioWaveform = appPrefs.isAudioWaveformEnabled,
                audioFeedback = appPrefs.isAudioFeedbackEnabled,
                hapticFeedback = appPrefs.isHapticFeedbackEnabled,
                showStatusText = appPrefs.isShowStatusTextEnabled,
                reduceMotion = appPrefs.isReduceMotionEnabled,
                dynamicIslandEnabled = appPrefs.isDynamicIslandEnabled,
                overlayEnabled = appPrefs.isTaskOverlayEnabled,
                avatarScalePercent = appPrefs.avatarScalePercent
            )
        }
    }

    suspend fun saveSettings(settings: AvatarSettings): Result<Unit> {
        return try {
            context.avatarDataStore.edit { prefs ->
                prefs[KEY_AVATAR_STYLE] = settings.avatarStyle
                prefs[KEY_SOUND_PROFILE] = settings.soundProfile
                prefs[KEY_ANIMATION_SPEED] = settings.animationSpeed
                prefs[KEY_COLOR_THEME] = settings.colorTheme
                prefs[KEY_PARTICLE_ANIMATION] = settings.particleAnimation
                prefs[KEY_AUDIO_WAVEFORM] = settings.audioWaveform
                prefs[KEY_AUDIO_FEEDBACK] = settings.audioFeedback
                prefs[KEY_HAPTIC_FEEDBACK] = settings.hapticFeedback
                prefs[KEY_SHOW_STATUS_TEXT] = settings.showStatusText
                prefs[KEY_REDUCE_MOTION] = settings.reduceMotion
                prefs[KEY_DYNAMIC_ISLAND] = settings.dynamicIslandEnabled
                prefs[KEY_OVERLAY_ENABLED] = settings.overlayEnabled
                prefs[KEY_AVATAR_SCALE_PERCENT] = settings.avatarScalePercent
            }

            // Sync with AppPreferences for immediate synchronous queries across the app
            val appPrefs = JarvisApp.instance.preferences
            appPrefs.avatarStyle = settings.avatarStyle
            appPrefs.avatarSoundProfile = settings.soundProfile
            appPrefs.avatarAnimationSpeed = settings.animationSpeed
            appPrefs.avatarColorTheme = settings.colorTheme
            appPrefs.isParticleAnimationEnabled = settings.particleAnimation
            appPrefs.isAudioWaveformEnabled = settings.audioWaveform
            appPrefs.isAudioFeedbackEnabled = settings.audioFeedback
            appPrefs.isHapticFeedbackEnabled = settings.hapticFeedback
            appPrefs.isShowStatusTextEnabled = settings.showStatusText
            appPrefs.isReduceMotionEnabled = settings.reduceMotion
            appPrefs.isDynamicIslandEnabled = settings.dynamicIslandEnabled
            appPrefs.isTaskOverlayEnabled = settings.overlayEnabled
            appPrefs.avatarScalePercent = settings.avatarScalePercent

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
