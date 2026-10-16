package com.jarvis.assistant.automation.session

import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.background.VoiceSessionService
import com.jarvis.assistant.data.model.ConversationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Voice interaction states synchronized across main UI and floating overlay.
 */
enum class VoiceSessionState {
    IDLE,
    CONNECTING,
    LISTENING,
    THINKING,
    SPEAKING,
    PAUSED,
    ENDED
}

/**
 * Central state coordinator for voice sessions.
 * Synchronizes voice interaction states between MainActivity/HomeFragment
 * and FloatingVoiceController, ensuring consistent Pause/Resume and Hang Up actions.
 */
object SessionStateManager {

    private val _voiceState = MutableStateFlow(VoiceSessionState.IDLE)
    val voiceState: StateFlow<VoiceSessionState> = _voiceState.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _connectionStatus = MutableStateFlow("READY")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    /**
     * Updates the current voice state and handles internal paused status.
     */
    fun updateState(state: VoiceSessionState) {
        _voiceState.value = state
        if (state == VoiceSessionState.PAUSED) {
            _isPaused.value = true
        } else if (state != VoiceSessionState.IDLE && state != VoiceSessionState.ENDED) {
            _isPaused.value = false
        }
    }

    /**
     * Bridges raw ConversationState + mute flag to VoiceSessionState.
     */
    fun syncFromService(convState: ConversationState, isMuted: Boolean, isSessionOn: Boolean, status: String) {
        _connectionStatus.value = status
        if (!isSessionOn) {
            _voiceState.value = VoiceSessionState.IDLE
            _isPaused.value = false
            return
        }

        if (isMuted) {
            _voiceState.value = VoiceSessionState.PAUSED
            _isPaused.value = true
            return
        }

        _isPaused.value = false
        _voiceState.value = when (convState) {
            ConversationState.LISTENING -> VoiceSessionState.LISTENING
            ConversationState.THINKING -> VoiceSessionState.THINKING
            ConversationState.SPEAKING -> VoiceSessionState.SPEAKING
            ConversationState.IDLE -> if (status == "CONNECTING") VoiceSessionState.CONNECTING else VoiceSessionState.LISTENING
        }
    }

    fun updateAudioLevel(level: Float) {
        _audioLevel.value = level
    }

    /**
     * Pause action: Mutes audio input stream and pauses audio playback
     * without severing the Gemini Live connection.
     */
    fun requestPause() {
        JarvisConversationService.setMicMuted(true)
        JarvisConversationService.interruptPlayback()
        updateState(VoiceSessionState.PAUSED)
    }

    /**
     * Resume action: Unmutes audio input stream and returns to LISTENING.
     */
    fun requestResume() {
        JarvisConversationService.setMicMuted(false)
        updateState(VoiceSessionState.LISTENING)
    }

    /**
     * Toggles Pause/Resume.
     * @return true if now paused, false if resumed.
     */
    fun togglePauseResume(): Boolean {
        if (_isPaused.value || _voiceState.value == VoiceSessionState.PAUSED) {
            requestResume()
            return false
        } else {
            requestPause()
            return true
        }
    }

    /**
     * Hang Up action: Fully terminates the voice session and background connection.
     */
    fun requestHangUp() {
        JarvisConversationService.stopAll()
        try {
            VoiceSessionService.stop(JarvisApp.instance)
        } catch (_: Exception) {}
        updateState(VoiceSessionState.ENDED)
        _isPaused.value = false
    }
}
