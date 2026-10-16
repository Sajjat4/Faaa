package com.jarvis.assistant.ui.grok

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.jarvis.assistant.R
import com.jarvis.assistant.automation.session.AvatarState
import com.jarvis.assistant.automation.session.SessionStateManager
import com.jarvis.assistant.automation.session.VoiceSessionState
import com.jarvis.assistant.ui.home.MainActivity

/**
 * Fixed Native Dynamic Island Overlay anchored directly at the top center
 * below / surrounding the Android front camera punch hole.
 *
 * Implements strict color gating, animated state orb, responsive audio waveform bars,
 * interactive expansion for pause/resume & hangup, and completely removes any
 * floating microphone icon clutter.
 */
class GrokDynamicIslandOverlay(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var attached = false
    private var isExpanded = false
    private var currentColor = Color.parseColor("#00F0FF")
    private var colorAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    private val collapseRunnable = Runnable {
        if (isExpanded) {
            setExpanded(false)
        }
    }

    private val root = FrameLayout(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        clipChildren = false
        clipToPadding = false
    }

    // Dynamic Island Jet Black Capsule Background
    private val islandBackground = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(18).toFloat()
        setColor(Color.parseColor("#050811"))
        setStroke(dp(1.8f), currentColor)
    }

    private val islandContainer = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = islandBackground
        elevation = dp(18).toFloat()
    }

    // Left Animated Expressive Droid Bot (60 FPS LED face, emotion color transitions, and head gestures)
    private val droidBot = DynamicIslandDroidView(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply {
            marginStart = dp(6)
            marginEnd = dp(6)
        }
    }

    // Hidden State Label (Dynamic Island conveys emotion purely via Droid bot face & colors, NO text labels)
    private val stateLabel = TextView(context).apply {
        visibility = View.GONE
    }

    // Right Mini Audio Waveform Visualizer (3 bars reacting to live speech)
    private val waveBar1 = createWaveBar()
    private val waveBar2 = createWaveBar()
    private val waveBar3 = createWaveBar()

    private val waveContainer = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(14)).apply {
            marginEnd = dp(10)
        }
        addView(waveBar1)
        addView(waveBar2)
        addView(waveBar3)
    }

    // Expanded Controls Layout
    private val expandedControls = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(4)
            marginEnd = dp(8)
        }
    }

    // Action 1: Pause / Resume
    private val btnPauseResume = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(6) }
        setPadding(dp(8), dp(8), dp(8), dp(8))
        background = makeMaterialCircleButton(Color.parseColor("#1E293B"))
        setImageResource(R.drawable.ic_voice_pause)
        imageTintList = ColorStateList.valueOf(Color.WHITE)
        contentDescription = "Pause or resume voice conversation"
        setOnClickListener {
            val paused = SessionStateManager.togglePauseResume()
            updatePauseButtonState(paused)
            resetAutoCollapse()
        }
    }

    // Action 2: Hang Up
    private val btnHangUp = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(6) }
        setPadding(dp(8), dp(8), dp(8), dp(8))
        background = makeMaterialCircleButton(Color.parseColor("#DC2626"))
        setImageResource(R.drawable.ic_voice_hangup)
        imageTintList = ColorStateList.valueOf(Color.WHITE)
        contentDescription = "Hang up voice conversation"
        setOnClickListener {
            SessionStateManager.requestHangUp()
            hide()
        }
    }

    // Action 3: Open App
    private val btnOpenApp = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
        setPadding(dp(8), dp(8), dp(8), dp(8))
        background = makeMaterialCircleButton(Color.parseColor("#0F172A"))
        setImageResource(R.drawable.ic_voice_expand)
        imageTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
        contentDescription = "Open JARVIS full screen"
        setOnClickListener {
            openMainActivity()
        }
    }

    // WindowManager LayoutParams: Fixed at TOP CENTER, anchored under camera notch
    private val params = WindowManager.LayoutParams(
        dp(132),
        dp(36),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        x = 0 // Centered
        y = getCameraNotchOffset()
    }

    init {
        expandedControls.addView(btnPauseResume)
        expandedControls.addView(btnHangUp)
        expandedControls.addView(btnOpenApp)

        islandContainer.addView(droidBot)
        islandContainer.addView(waveContainer)
        islandContainer.addView(expandedControls)

        root.addView(islandContainer, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        islandContainer.setOnClickListener {
            toggleExpanded()
        }

        startAmbientPulse()
    }

    fun show(): Boolean {
        if (!Settings.canDrawOverlays(context)) return false
        if (attached) return true

        return try {
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.x = 0
            params.y = getCameraNotchOffset()
            windowManager.addView(root, params)
            attached = true
            updateFromSessionState(SessionStateManager.voiceState.value)
            true
        } catch (_: Exception) {
            attached = false
            false
        }
    }

    fun hide() {
        if (attached) {
            try {
                mainHandler.removeCallbacks(collapseRunnable)
                windowManager.removeView(root)
            } catch (_: Exception) {}
            attached = false
            isExpanded = false
            applyExpandedView(false)
        }
    }

    fun isShowing(): Boolean = attached

    fun updateFromSessionState(state: VoiceSessionState) {
        val avatarState = AvatarState.fromVoiceSessionState(state)
        updateFromAvatarState(avatarState, state == VoiceSessionState.PAUSED)
    }

    fun updateFromAvatarState(avatarState: AvatarState, isPausedOverride: Boolean? = null) {
        val isPaused = isPausedOverride ?: (avatarState == AvatarState.PAUSED)
        updatePauseButtonState(isPaused)

        // Droid Bot LED face & emotion update
        droidBot.updateState(avatarState)

        // Trigger SoundPool micro-interaction based on state
        val grokBotState = GrokBotDrawingEngine.GrokBotState.fromAvatarState(avatarState)
        GrokBotSoundManager.getInstance(context).playSoundForState(grokBotState)

        // Strict Dynamic Island Color Gating matching Droid emotion
        val targetColor = when (avatarState) {
            AvatarState.SUCCESS -> Color.parseColor("#10B981") // Emerald Green
            AvatarState.FAILED -> Color.parseColor("#EF4444")  // Crimson Red
            AvatarState.SPEAKING -> Color.parseColor("#00FF66")// Neon Green
            AvatarState.LISTENING -> Color.parseColor("#00F0FF")// Electric Cyan
            AvatarState.THINKING, AvatarState.EXECUTING, AvatarState.VERIFYING -> Color.parseColor("#A855F7") // Cyber Purple
            AvatarState.PAUSED -> Color.parseColor("#F59E0B")  // Amber
            else -> Color.parseColor("#00D4FF")                // Calm Blue
        }
        animateBorderColor(targetColor)
    }

    fun updateAudioLevel(level: Float) {
        if (!attached) return
        val clamped = level.coerceIn(0f, 1f)

        // Waveform bars dynamic scaling
        val h1 = dp(4f + clamped * 12f)
        val h2 = dp(4f + clamped * 16f)
        val h3 = dp(4f + clamped * 10f)

        waveBar1.layoutParams = (waveBar1.layoutParams as LinearLayout.LayoutParams).apply { height = h1 }
        waveBar2.layoutParams = (waveBar2.layoutParams as LinearLayout.LayoutParams).apply { height = h2 }
        waveBar3.layoutParams = (waveBar3.layoutParams as LinearLayout.LayoutParams).apply { height = h3 }

        droidBot.audioLevel = clamped
    }

    private fun toggleExpanded() {
        setExpanded(!isExpanded)
    }

    private fun setExpanded(expanded: Boolean) {
        if (isExpanded == expanded) return
        isExpanded = expanded
        applyExpandedView(expanded)

        mainHandler.removeCallbacks(collapseRunnable)
        if (isExpanded) {
            mainHandler.postDelayed(collapseRunnable, 4500L) // Auto collapse
        }
    }

    private fun resetAutoCollapse() {
        mainHandler.removeCallbacks(collapseRunnable)
        if (isExpanded) {
            mainHandler.postDelayed(collapseRunnable, 4500L)
        }
    }

    private fun applyExpandedView(expanded: Boolean) {
        if (expanded) {
            expandedControls.visibility = View.VISIBLE
            waveContainer.visibility = View.GONE
            params.width = dp(270)
            params.height = dp(46)
            islandBackground.cornerRadius = dp(23).toFloat()
            droidBot.layoutParams = (droidBot.layoutParams as LinearLayout.LayoutParams).apply {
                width = dp(32)
                height = dp(32)
                marginStart = dp(10)
                marginEnd = dp(8)
            }
        } else {
            expandedControls.visibility = View.GONE
            waveContainer.visibility = View.VISIBLE
            params.width = dp(96)
            params.height = dp(36)
            islandBackground.cornerRadius = dp(18).toFloat()
            droidBot.layoutParams = (droidBot.layoutParams as LinearLayout.LayoutParams).apply {
                width = dp(26)
                height = dp(26)
                marginStart = dp(6)
                marginEnd = dp(6)
            }
        }

        try {
            if (attached) {
                params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                params.x = 0
                params.y = getCameraNotchOffset()
                windowManager.updateViewLayout(root, params)
            }
        } catch (_: Exception) {}
    }

    private fun animateBorderColor(targetColor: Int) {
        if (currentColor == targetColor) return
        colorAnimator?.cancel()
        val animator = ValueAnimator.ofObject(ArgbEvaluator(), currentColor, targetColor).apply {
            duration = 260L
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                val c = va.animatedValue as Int
                currentColor = c
                islandBackground.setStroke(dp(1.8f), c)
                waveBar1.backgroundTintList = ColorStateList.valueOf(c)
                waveBar2.backgroundTintList = ColorStateList.valueOf(c)
                waveBar3.backgroundTintList = ColorStateList.valueOf(c)
            }
        }
        colorAnimator = animator
        animator.start()
    }

    private fun startAmbientPulse() {
        // GrokBotCanvasView handles its internal 60 FPS physics-based continuous pulsing
    }

    private fun updatePauseButtonState(isPaused: Boolean) {
        if (isPaused) {
            btnPauseResume.setImageResource(R.drawable.ic_voice_play)
            btnPauseResume.imageTintList = ColorStateList.valueOf(Color.parseColor("#00E676"))
        } else {
            btnPauseResume.setImageResource(R.drawable.ic_voice_pause)
            btnPauseResume.imageTintList = ColorStateList.valueOf(Color.WHITE)
        }
    }

    private fun openMainActivity() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        context.startActivity(intent)
        setExpanded(false)
    }

    private fun createWaveBar(): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(2.4f), dp(6f)).apply {
                marginStart = dp(1.8f)
                marginEnd = dp(1.8f)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(2f).toFloat()
                setColor(currentColor)
            }
        }
    }

    private fun makeMaterialCircleButton(color: Int): RippleDrawable {
        val shape = GradientDrawable().apply {
            this.shape = GradientDrawable.OVAL
            setColor(color)
        }
        return RippleDrawable(ColorStateList.valueOf(Color.argb(80, 255, 255, 255)), shape, null)
    }

    /**
     * Resolves the top offset so the Dynamic Island fits seamlessly under / surrounding
     * the camera punch hole cutout at the top of the display.
     */
    private fun getCameraNotchOffset(): Int {
        var statusBarHeight = 0
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resId > 0) {
            statusBarHeight = context.resources.getDimensionPixelSize(resId)
        }
        if (statusBarHeight <= 0) {
            statusBarHeight = dp(24)
        }
        // Anchored comfortably at the top camera punch hole line
        return (statusBarHeight / 3).coerceAtLeast(dp(10))
    }

    private fun dp(value: Number): Int = (value.toFloat() * context.resources.displayMetrics.density).toInt()
}
