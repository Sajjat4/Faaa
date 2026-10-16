package com.jarvis.assistant.ui.agent

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.automation.session.SessionStateManager
import com.jarvis.assistant.data.model.ConversationState
import com.jarvis.assistant.ui.widget.EdgeLightingView
import com.jarvis.assistant.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * System-level WindowManager overlay that displays dual-edge RGB border illumination
 * exclusively when the user is outside the application.
 *
 * Configured with FLAG_NOT_TOUCHABLE so all touches, swipes, and taps pass through 100%
 * transparently to the underlying apps/home screen.
 */
class SystemEdgeLightingOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val edgeView = EdgeLightingView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    }

    private val root = FrameLayout(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        clipChildren = false
        clipToPadding = false
        addView(edgeView)
    }

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    )

    private var attached = false
    private var fadeAnimator: ValueAnimator? = null
    private var syncJob: Job? = null
    private var audioJob: Job? = null

    fun isShowing(): Boolean = attached

    /**
     * Shows the system-wide edge lighting overlay.
     */
    @Synchronized
    fun show(): Boolean {
        if (attached) return true
        if (!PermissionHelper.hasOverlayPermission(context)) return false

        val prefs = try { JarvisApp.instance.preferences } catch (_: Exception) { null }
        if (prefs?.isEdgeLightingEnabled == false) return false

        edgeView.refreshPaletteFromTheme()

        try {
            root.alpha = 0f
            windowManager.addView(root, params)
            attached = true

            // Smooth fade in
            fadeAnimator?.cancel()
            fadeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 320L
                addUpdateListener { va ->
                    root.alpha = va.animatedValue as Float
                }
                start()
            }

            startStateListeners()
            return true
        } catch (e: Exception) {
            attached = false
            return false
        }
    }

    /**
     * Hides and safely removes the system edge lighting overlay.
     */
    @Synchronized
    fun hide() {
        if (!attached) return

        stopStateListeners()
        fadeAnimator?.cancel()
        fadeAnimator = ValueAnimator.ofFloat(root.alpha, 0f).apply {
            duration = 240L
            addUpdateListener { va ->
                root.alpha = va.animatedValue as Float
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    safeRemoveView()
                }
            })
            start()
        }
    }

    @Synchronized
    private fun safeRemoveView() {
        if (!attached) return
        try {
            windowManager.removeViewImmediate(root)
        } catch (_: Exception) {
            try {
                windowManager.removeView(root)
            } catch (_: Exception) {}
        } finally {
            attached = false
            root.alpha = 1f
        }
    }

    fun updateAudioLevel(level: Float) {
        if (attached) {
            edgeView.updateAudioLevel(level)
        }
    }

    fun updateState(state: ConversationState) {
        if (attached) {
            edgeView.updateState(state)
        }
    }

    fun triggerNotificationSweep() {
        if (attached) {
            edgeView.triggerNotificationPulse()
        }
    }

    fun refreshTheme() {
        if (attached) {
            edgeView.refreshPaletteFromTheme()
        }
    }

    private fun startStateListeners() {
        stopStateListeners()

        syncJob = scope.launch {
            SessionStateManager.voiceState.collect { vState ->
                val convState = when (vState) {
                    com.jarvis.assistant.automation.session.VoiceSessionState.LISTENING -> ConversationState.LISTENING
                    com.jarvis.assistant.automation.session.VoiceSessionState.SPEAKING -> ConversationState.SPEAKING
                    com.jarvis.assistant.automation.session.VoiceSessionState.THINKING -> ConversationState.THINKING
                    com.jarvis.assistant.automation.session.VoiceSessionState.CONNECTING -> ConversationState.THINKING
                    com.jarvis.assistant.automation.session.VoiceSessionState.PAUSED,
                    com.jarvis.assistant.automation.session.VoiceSessionState.IDLE,
                    com.jarvis.assistant.automation.session.VoiceSessionState.ENDED -> ConversationState.IDLE
                }
                edgeView.updateState(convState)
            }
        }

        audioJob = scope.launch {
            SessionStateManager.audioLevel.collect { level ->
                edgeView.updateAudioLevel(level)
            }
        }
    }

    private fun stopStateListeners() {
        syncJob?.cancel()
        syncJob = null
        audioJob?.cancel()
        audioJob = null
    }

    fun destroy() {
        stopStateListeners()
        safeRemoveView()
        scope.cancel()
    }
}
