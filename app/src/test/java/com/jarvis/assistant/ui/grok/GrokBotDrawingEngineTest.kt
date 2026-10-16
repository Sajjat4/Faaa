package com.jarvis.assistant.ui.grok

import com.jarvis.assistant.automation.session.AvatarState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrokBotDrawingEngineTest {

    @Test
    fun testGrokBotStateMapping() {
        assertEquals(
            GrokBotDrawingEngine.GrokBotState.IDLE,
            GrokBotDrawingEngine.GrokBotState.fromAvatarState(AvatarState.IDLE)
        )
        assertEquals(
            GrokBotDrawingEngine.GrokBotState.IDLE,
            GrokBotDrawingEngine.GrokBotState.fromAvatarState(AvatarState.PAUSED)
        )
        assertEquals(
            GrokBotDrawingEngine.GrokBotState.WORKING,
            GrokBotDrawingEngine.GrokBotState.fromAvatarState(AvatarState.THINKING)
        )
        assertEquals(
            GrokBotDrawingEngine.GrokBotState.WORKING,
            GrokBotDrawingEngine.GrokBotState.fromAvatarState(AvatarState.EXECUTING)
        )
        assertEquals(
            GrokBotDrawingEngine.GrokBotState.WORKING,
            GrokBotDrawingEngine.GrokBotState.fromAvatarState(AvatarState.CONNECTING)
        )
        assertEquals(
            GrokBotDrawingEngine.GrokBotState.SPEAKING,
            GrokBotDrawingEngine.GrokBotState.fromAvatarState(AvatarState.SPEAKING)
        )
        assertEquals(
            GrokBotDrawingEngine.GrokBotState.SPEAKING,
            GrokBotDrawingEngine.GrokBotState.fromAvatarState(AvatarState.LISTENING)
        )
    }

    @Test
    fun testPaletteCreation() {
        val idlePalette = GrokBotDrawingEngine.BotColorPalette(0x00F0FF, 0x9D00FF, 0x8000F0FF.toInt())
        assertNotNull(idlePalette)
        assertEquals(0x00F0FF, idlePalette.primaryColor)
        assertEquals(0x9D00FF, idlePalette.secondaryColor)
    }

    @Test
    fun testGrokFamilyStyles() {
        assertTrue(GrokBotDrawingEngine.isGrokFamilyStyle("Grok Bot"))
        assertTrue(GrokBotDrawingEngine.isGrokFamilyStyle("Grok Neko"))
        assertTrue(GrokBotDrawingEngine.isGrokFamilyStyle("Grok Chibi"))
        assertTrue(GrokBotDrawingEngine.isGrokFamilyStyle("Grok Astro"))
        assertTrue(GrokBotDrawingEngine.isGrokFamilyStyle("Grok Bunny"))
        assertTrue(GrokBotDrawingEngine.isGrokFamilyStyle("Grok Fox"))
        assertTrue(GrokBotDrawingEngine.isGrokFamilyStyle("Grok Panda"))
        assertTrue(GrokBotDrawingEngine.isGrokFamilyStyle("Grok Sparkle"))
        assertTrue(GrokBotDrawingEngine.isGrokFamilyStyle("Grok Nova"))
        assertTrue(GrokBotDrawingEngine.isGrokFamilyStyle("Grok Bubble"))
        assertTrue(GrokBotDrawingEngine.isGrokFamilyStyle("Grok Droid Cute"))

        assertFalse(GrokBotDrawingEngine.isGrokFamilyStyle("MetaMuse"))
        assertFalse(GrokBotDrawingEngine.isGrokFamilyStyle("Classic"))
        assertFalse(GrokBotDrawingEngine.isGrokFamilyStyle("Pixel"))
    }
}
