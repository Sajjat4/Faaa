package com.jarvis.assistant.ui.agent

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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.automation.session.AvatarState
import com.jarvis.assistant.automation.session.SessionStateManager
import com.jarvis.assistant.automation.session.VoiceSessionState
import com.jarvis.assistant.ui.home.MainActivity

/**
 * WindowManager-based Fixed Dynamic Island Voice Controller.
 * Anchored permanently at the TOP CENTER of the display, aligned directly
 * below the Android front camera punch hole cutout.
 *
 * Implements the exact layout, color rules, and controls from the MetaMuse screenshot:
 * - Compact View (~172dp x 38dp): MetaMuse character face badge, glowing state indicator,
 *   state text, and multi-bar reactive audio waveform.
 * - Expanded View (~284dp x 112dp): Top row companion info + Bottom action buttons
 *   (Pause/Resume, End, More) with labels underneath, and status description.
 */
class FloatingVoiceController(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var attached = false
    private var isExpanded = false
    private var currentColor = Color.parseColor("#00F0FF")
    private var colorAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    private val autoCollapseRunnable = Runnable {
        if (isExpanded) {
            setExpanded(false)
        }
    }

    private val root = FrameLayout(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        clipChildren = false
        clipToPadding = false
    }

    // Dynamic Island Capsule Background (OLED Jet Black with smooth rounded pill border)
    private val islandBackground = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(25).toFloat()
        setColor(Color.parseColor("#070B14"))
        setStroke(dp(1.8f), currentColor)
    }

    // Master Container
    private val islandContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        background = islandBackground
        elevation = dp(18).toFloat()
        setPadding(dp(10), dp(5), dp(10), dp(7))
    }

    // Top Row: Avatar Badge + State Dot + Label + Waveform
    private val topRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(40)
        )
    }

    // Left Expressive Droid Bot (60 FPS LED face, color emotion changes, nod & shake gestures)
    private val droidBot = com.jarvis.assistant.ui.grok.DynamicIslandDroidView(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
            marginStart = dp(4)
            marginEnd = dp(6)
        }
    }

    // Right Multi-Bar Audio Waveform (6 reactive bars)
    private val waveBars = (1..6).map { createWaveBar() }

    private val waveContainer = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(14)).apply {
            marginEnd = dp(6)
        }
        waveBars.forEach { addView(it) }
    }

    // Expanded Controls Section
    private val expandedSection = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        visibility = View.GONE
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(4)
        }
    }

    // Divider line between top row and actions
    private val expandedDivider = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(0.8f)).apply {
            bottomMargin = dp(6)
        }
        setBackgroundColor(Color.parseColor("#1E293B"))
    }

    // Action Buttons Row (Pause, End, More)
    private val actionButtonsRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    // Button 1: Pause / Resume with Label
    private val btnPauseResumeIcon = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
        setPadding(dp(8), dp(8), dp(8), dp(8))
        background = makeMaterialCircleButton(Color.parseColor("#141B2D"))
        setImageResource(R.drawable.ic_voice_pause)
        imageTintList = ColorStateList.valueOf(Color.WHITE)
    }
    private val tvPauseResumeLabel = TextView(context).apply {
        text = "Pause"
        textSize = 9.5f
        setTextColor(Color.parseColor("#94A3B8"))
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(2) }
    }
    private val pauseButtonGroup = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        addView(btnPauseResumeIcon)
        addView(tvPauseResumeLabel)
        setOnClickListener {
            val paused = SessionStateManager.togglePauseResume()
            updatePauseButtonState(paused)
            resetAutoCollapse()
        }
    }

    // Button 2: End Conversation with Label
    private val btnEndIcon = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
        setPadding(dp(8), dp(8), dp(8), dp(8))
        background = makeMaterialCircleButton(Color.parseColor("#DC2626"))
        setImageResource(R.drawable.ic_voice_hangup)
        imageTintList = ColorStateList.valueOf(Color.WHITE)
    }
    private val tvEndLabel = TextView(context).apply {
        text = "End"
        textSize = 9.5f
        setTextColor(Color.parseColor("#EF4444"))
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(2) }
    }
    private val endButtonGroup = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        addView(btnEndIcon)
        addView(tvEndLabel)
        setOnClickListener {
            SessionStateManager.requestHangUp()
            hide()
        }
    }

    // Button 3: More (Opens full app) with Label
    private val btnMoreIcon = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
        setPadding(dp(8), dp(8), dp(8), dp(8))
        background = makeMaterialCircleButton(Color.parseColor("#141B2D"))
        setImageResource(R.drawable.ic_more_dots)
        imageTintList = ColorStateList.valueOf(Color.WHITE)
    }
    private val tvMoreLabel = TextView(context).apply {
        text = "More"
        textSize = 9.5f
        setTextColor(Color.parseColor("#94A3B8"))
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(2) }
    }
    private val moreButtonGroup = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        addView(btnMoreIcon)
        addView(tvMoreLabel)
        setOnClickListener {
            openMainActivity()
        }
    }

    // Subtitle Footer
    private val tvFooterSubtitle = TextView(context).apply {
        text = "Controls work with session state (Pause / Resume / End / More)"
        textSize = 8.5f
        setTextColor(Color.parseColor("#64748B"))
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(6)
            bottomMargin = dp(2)
        }
    }

    // WindowManager LayoutParams: Strictly locked at TOP CENTER, aligned below the camera
    private val params = WindowManager.LayoutParams(
        dp(148),
        dp(46),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        x = 0
        y = getTopCameraCutoutOffset()
    }

    init {
        // Assemble Top Row: Expressive Droid Bot + Reactive Multi-bar Waveform (NO text labels)
        topRow.addView(droidBot)
        topRow.addView(waveContainer)

        // Assemble Expanded Actions
        actionButtonsRow.addView(pauseButtonGroup)
        actionButtonsRow.addView(endButtonGroup)
        actionButtonsRow.addView(moreButtonGroup)

        expandedSection.addView(expandedDivider)
        expandedSection.addView(actionButtonsRow)
        expandedSection.addView(tvFooterSubtitle)

        // Assemble Island Container
        islandContainer.addView(topRow)
        islandContainer.addView(expandedSection)

        root.addView(islandContainer, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        // Direct tap on droid opens full app
        droidBot.setOnClickListener {
            openMainActivity()
        }

        // Tap on top row or island capsule toggles expansion
        topRow.setOnClickListener {
            toggleExpanded()
        }
        islandContainer.setOnClickListener {
            toggleExpanded()
        }

        // Click and Hold (Long Press): Hang up voice session or open AI app with haptic feedback
        val onHoldListener = View.OnLongClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            val currentState = SessionStateManager.voiceState.value
            if (currentState != VoiceSessionState.IDLE && currentState != VoiceSessionState.ENDED) {
                SessionStateManager.requestHangUp()
                hide()
            } else {
                openMainActivity()
            }
            true
        }
        topRow.setOnLongClickListener(onHoldListener)
        islandContainer.setOnLongClickListener(onHoldListener)
        droidBot.setOnLongClickListener(onHoldListener)

        startAmbientPulse()
    }

    fun show(): Boolean {
        if (!Settings.canDrawOverlays(context)) return false
        if (attached) return true

        return try {
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.x = 0
            params.y = getTopCameraCutoutOffset()
            windowManager.addView(root, params)
            attached = true
            updateState(SessionStateManager.voiceState.value)
            true
        } catch (_: Exception) {
            attached = false
            false
        }
    }

    fun hide() {
        if (attached) {
            try {
                mainHandler.removeCallbacks(autoCollapseRunnable)
                windowManager.removeView(root)
            } catch (_: Exception) {}
            attached = false
            isExpanded = false
            applyExpandedView(false)
        }
    }

    fun isShowing(): Boolean = attached

    /**
     * Updates Droid bot emotion colors & gestures, pause button, and animates glowing border.
     * Conveys emotions PURELY visually without text labels.
     */
    fun updateState(state: VoiceSessionState) {
        val avatarState = AvatarState.fromVoiceSessionState(state)
        val isPaused = state == VoiceSessionState.PAUSED
        updatePauseButtonState(isPaused)

        // Update prominent expressive Droid bot face, LED eyes, and emotion colors
        droidBot.updateState(avatarState)

        // Strict Color Gating transition
        animateBorderColor(avatarState.primaryColor)
    }

    /**
     * Responds to audio levels by dynamically scaling waveform bars and avatar face.
     */
    fun updateAudioLevel(level: Float) {
        if (!attached) return
        val clamped = level.coerceIn(0f, 1f)
        droidBot.audioLevel = clamped

        // Waveform bars dynamic height scaling
        val baseHeights = listOf(4f, 7f, 10f, 8f, 6f, 4f)
        waveBars.forEachIndexed { index, bar ->
            val factor = baseHeights.getOrElse(index) { 5f }
            val h = dp(2f + (clamped * factor * 1.5f))
            bar.layoutParams = (bar.layoutParams as LinearLayout.LayoutParams).apply { height = h }
        }
    }

    private fun toggleExpanded() {
        setExpanded(!isExpanded)
    }

    private fun setExpanded(expanded: Boolean) {
        if (isExpanded == expanded) return
        isExpanded = expanded
        applyExpandedView(expanded)

        mainHandler.removeCallbacks(autoCollapseRunnable)
        if (isExpanded) {
            mainHandler.postDelayed(autoCollapseRunnable, 5500L)
        }
    }

    private fun resetAutoCollapse() {
        mainHandler.removeCallbacks(autoCollapseRunnable)
        if (isExpanded) {
            mainHandler.postDelayed(autoCollapseRunnable, 5500L)
        }
    }

    private fun applyExpandedView(expanded: Boolean) {
        if (expanded) {
            expandedSection.visibility = View.VISIBLE
            params.width = dp(304)
            params.height = dp(138)
            islandBackground.cornerRadius = dp(26).toFloat()
        } else {
            expandedSection.visibility = View.GONE
            params.width = dp(148)
            params.height = dp(46)
            islandBackground.cornerRadius = dp(23).toFloat()
        }

        try {
            if (attached) {
                params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                params.x = 0
                params.y = getTopCameraCutoutOffset()
                windowManager.updateViewLayout(root, params)
            }
        } catch (_: Exception) {}
    }

    private fun animateBorderColor(targetColor: Int) {
        if (currentColor == targetColor) return
        colorAnimator?.cancel()
        val animator = ValueAnimator.ofObject(ArgbEvaluator(), currentColor, targetColor).apply {
            duration = 280L
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                val c = va.animatedValue as Int
                currentColor = c
                islandBackground.setStroke(dp(1.8f), c)
                waveBars.forEach { it.backgroundTintList = ColorStateList.valueOf(c) }
            }
        }
        colorAnimator = animator
        animator.start()
    }

    private fun startAmbientPulse() {
        pulseAnimator = ValueAnimator.ofFloat(0.85f, 1.15f).apply {
            duration = 1300L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                if (attached && !isExpanded) {
                    val s = va.animatedValue as Float
                    droidBot.scaleX = s
                    droidBot.scaleY = s
                }
            }
        }
        pulseAnimator?.start()
    }

    private fun updatePauseButtonState(isPaused: Boolean) {
        if (isPaused) {
            btnPauseResumeIcon.setImageResource(R.drawable.ic_voice_play)
            btnPauseResumeIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#00E676"))
            tvPauseResumeLabel.text = "Resume"
        } else {
            btnPauseResumeIcon.setImageResource(R.drawable.ic_voice_pause)
            btnPauseResumeIcon.imageTintList = ColorStateList.valueOf(Color.WHITE)
            tvPauseResumeLabel.text = "Pause"
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
            layoutParams = LinearLayout.LayoutParams(dp(2.4f), dp(5f)).apply {
                marginStart = dp(1.5f)
                marginEnd = dp(1.5f)
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
     * Resolves the top offset so the Dynamic Island rests right below / around
     * the front camera punch hole cutout.
     */
    private fun getTopCameraCutoutOffset(): Int {
        var statusBarHeight = 0
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resId > 0) {
            statusBarHeight = context.resources.getDimensionPixelSize(resId)
        }
        if (statusBarHeight <= 0) {
            statusBarHeight = dp(24)
        }
        return (statusBarHeight / 3).coerceAtLeast(dp(10))
    }

    private fun dp(value: Number): Int = (value.toFloat() * context.resources.displayMetrics.density).toInt()
}
