package com.jarvis.assistant.automation.session

import com.jarvis.assistant.data.model.ConversationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionStateManagerTest {

    @Before
    fun setup() {
        SessionStateManager.updateState(VoiceSessionState.IDLE)
    }

    @Test
    fun testSyncFromService_ListeningState() {
        SessionStateManager.syncFromService(
            convState = ConversationState.LISTENING,
            isMuted = false,
            isSessionOn = true,
            status = "CONNECTED"
        )
        assertEquals(VoiceSessionState.LISTENING, SessionStateManager.voiceState.value)
        assertFalse(SessionStateManager.isPaused.value)
    }

    @Test
    fun testSyncFromService_ThinkingState() {
        SessionStateManager.syncFromService(
            convState = ConversationState.THINKING,
            isMuted = false,
            isSessionOn = true,
            status = "CONNECTED"
        )
        assertEquals(VoiceSessionState.THINKING, SessionStateManager.voiceState.value)
        assertFalse(SessionStateManager.isPaused.value)
    }

    @Test
    fun testSyncFromService_SpeakingState() {
        SessionStateManager.syncFromService(
            convState = ConversationState.SPEAKING,
            isMuted = false,
            isSessionOn = true,
            status = "CONNECTED"
        )
        assertEquals(VoiceSessionState.SPEAKING, SessionStateManager.voiceState.value)
        assertFalse(SessionStateManager.isPaused.value)
    }

    @Test
    fun testSyncFromService_MutedIsPaused() {
        SessionStateManager.syncFromService(
            convState = ConversationState.LISTENING,
            isMuted = true,
            isSessionOn = true,
            status = "CONNECTED"
        )
        assertEquals(VoiceSessionState.PAUSED, SessionStateManager.voiceState.value)
        assertTrue(SessionStateManager.isPaused.value)
    }

    @Test
    fun testSyncFromService_SessionOffIsIdle() {
        SessionStateManager.syncFromService(
            convState = ConversationState.LISTENING,
            isMuted = false,
            isSessionOn = false,
            status = "READY"
        )
        assertEquals(VoiceSessionState.IDLE, SessionStateManager.voiceState.value)
        assertFalse(SessionStateManager.isPaused.value)
    }

    @Test
    fun testAudioLevelUpdate() {
        SessionStateManager.updateAudioLevel(0.85f)
        assertEquals(0.85f, SessionStateManager.audioLevel.value, 0.001f)
    }
}
