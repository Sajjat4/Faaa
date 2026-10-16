package com.jarvis.assistant.automation.session

import android.graphics.Color

/**
 * Strict Color-Gated Avatar States for Dynamic Overlay Avatar.
 * Each state specifies exact Primary and Secondary HEX colors as strictly dictated.
 */
enum class AvatarState(
    val displayName: String,
    val primaryHex: String,
    val secondaryHex: String
) {
    IDLE("Idle", "#287BFF", "#00F0FF"),
    CONNECTING("Connecting", "#287BFF", "#9D00FF"),
    LISTENING("Listening", "#00F0FF", "#287BFF"),
    THINKING("Thinking", "#9D00FF", "#FF36D1"),
    SPEAKING("Speaking", "#FF36D1", "#00F0FF"),
    EXECUTING("Executing", "#287BFF", "#00F0FF"),
    VERIFYING("Verifying", "#00F0FF", "#9D00FF"),
    SUCCESS("Success", "#00E676", "#00F0FF"),
    RETRYING("Retrying", "#FF6D00", "#FFB020"),
    PAUSED("Paused", "#FFB020", "#FFD166"),
    RECONNECTING("Reconnecting", "#287BFF", "#9D00FF"),
    FAILED("Failed", "#FF1744", "#FF6D00"),
    DISCONNECTED("Disconnected", "#AAB4C3", "#AAB4C3"),
    ENDED("Ended", "#FF1744", "#FF36D1");

    val primaryColor: Int get() = Color.parseColor(primaryHex)
    val secondaryColor: Int get() = Color.parseColor(secondaryHex)

    companion object {
        fun fromVoiceSessionState(voiceState: VoiceSessionState): AvatarState = when (voiceState) {
            VoiceSessionState.IDLE -> IDLE
            VoiceSessionState.CONNECTING -> CONNECTING
            VoiceSessionState.LISTENING -> LISTENING
            VoiceSessionState.THINKING -> THINKING
            VoiceSessionState.SPEAKING -> SPEAKING
            VoiceSessionState.PAUSED -> PAUSED
            VoiceSessionState.ENDED -> ENDED
        }
    }
}
