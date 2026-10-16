package com.jarvis.assistant.ui.agent

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.audio.BubbleSoundPlayer
import com.jarvis.assistant.automation.agent.AgentTaskSnapshot
import com.jarvis.assistant.automation.agent.TaskStepStatus
import com.jarvis.assistant.automation.session.AvatarState
import com.jarvis.assistant.automation.session.SessionStateManager
import com.jarvis.assistant.data.model.ConversationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Lively autonomous floating companion avatar & task HUD owned by the foreground service.
 * Supports autonomous floating, playful edge hops, bubble-pop sound FX, and Material 3 design.
 */
class TaskProgressOverlay(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val animScope = CoroutineScope(Dispatchers.Main.immediate)
    private var wanderingJob: Job? = null
    private var activeHopAnimator: ValueAnimator? = null

    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()

    private val root = FrameLayout(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        clipChildren = false
        clipToPadding = false
    }

    private val pill = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), dp(7), dp(10), dp(7))
        background = GradientDrawable().apply {
            setColor(Color.argb(146, 8, 9, 12))
            cornerRadius = dp(13).toFloat()
            setStroke(dp(1), Color.argb(65, 225, 230, 238))
        }
        elevation = dp(12).toFloat()
    }

    private val header = TextView(context).apply {
        text = "JARVIS  ⋮⋮"
        setTextColor(Color.rgb(215, 220, 228))
        textSize = 10f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(0, 0, 0, dp(5))
        contentDescription = "Drag JARVIS task overlay anywhere on screen"
    }

    private val title = label(11f, true).apply { maxLines = 1 }
    private val progressText = label(11f, true).apply {
        setTextColor(Color.rgb(190, 196, 206))
        textSize = 9f
        setPadding(0, dp(2), 0, 0)
    }
    private val stepRows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100
        progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(8, 9, 12))
        progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.argb(55, 230, 234, 240))
    }

    private val dynamicAvatar = DynamicOverlayAvatarView(context).apply {
        layoutParams = FrameLayout.LayoutParams(dp(88), dp(88), Gravity.CENTER)
        visibility = View.VISIBLE
    }

    private val avatar = WebView(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
        }
        webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                updateState(lastKnownState)
            }
        }
        visibility = View.GONE
    }

    private val params = WindowManager.LayoutParams(
        dp(88),
        dp(88),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
        x = dp(8)
        y = dp(90)
    }

    private var attached = false
    private var avatarMode = true
    private var loadedAvatar = ""
    private var displayedTaskId: String? = null
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var currentEdge = ""
    private var dragging = false
    private var lastTapAt = 0L
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var lastKnownState = ConversationState.IDLE
    private var currentAvatarState = AvatarState.IDLE
    private var audioJob: Job? = null

    init {
        pill.visibility = View.GONE
        dynamicAvatar.visibility = View.VISIBLE
        avatar.visibility = View.GONE
        pill.addView(header)
        pill.addView(title, matchWrap())
        pill.addView(stepRows, matchWrap())
        pill.addView(progress, LinearLayout.LayoutParams(-1, dp(3)).apply { topMargin = dp(4); bottomMargin = dp(1) })
        pill.addView(progressText, matchWrap())
        root.addView(pill, FrameLayout.LayoutParams(-1, -2))
        root.addView(dynamicAvatar, FrameLayout.LayoutParams(dp(88), dp(88), Gravity.CENTER))
        root.addView(avatar, FrameLayout.LayoutParams(dp(88), dp(88), Gravity.CENTER))
        pill.isClickable = true
        pill.setOnTouchListener { _, event -> handleOverlayTouch(event) }
        dynamicAvatar.setOnTouchListener { _, event -> handleOverlayTouch(event) }
        avatar.setOnTouchListener { _, event -> handleOverlayTouch(event) }
    }

    fun updateState(state: ConversationState) {
        lastKnownState = state
        val avatarState = when (state) {
            ConversationState.LISTENING -> AvatarState.LISTENING
            ConversationState.THINKING -> AvatarState.THINKING
            ConversationState.SPEAKING -> AvatarState.SPEAKING
            ConversationState.IDLE -> {
                if (SessionStateManager.isPaused.value) AvatarState.PAUSED
                else AvatarState.IDLE
            }
        }
        setAvatarState(avatarState)
    }

    fun setAvatarState(state: AvatarState) {
        currentAvatarState = state
        dynamicAvatar.setState(state)
        val stateName = state.displayName.lowercase()
        if (attached && avatarMode && avatar.visibility == View.VISIBLE) {
            avatar.evaluateJavascript("window.setAvatarState && window.setAvatarState('$stateName')", null)
        }
    }

    fun update(snapshot: AgentTaskSnapshot?, showIdleAvatar: Boolean = false): Boolean {
        if (!Settings.canDrawOverlays(context)) {
            remove()
            return false
        }
        val preferences = JarvisApp.instance.preferences
        if (snapshot == null && !showIdleAvatar && preferences.taskOverlayMode != "always") {
            remove()
            displayedTaskId = null
            return true
        }
        if (!preferences.isTaskOverlayEnabled) {
            remove()
            return true
        }
        val selectedAvatar = preferences.taskOverlayAvatar
        if (selectedAvatar != loadedAvatar) {
            loadedAvatar = selectedAvatar
            dynamicAvatar.setAvatarStyle(selectedAvatar)
        }
        val nextTaskId = snapshot?.taskId
        if (displayedTaskId != nextTaskId) {
            displayedTaskId = nextTaskId
            avatarMode = true
        }
        if (!attached) {
            try {
                if (preferences.taskOverlayFreePosition && preferences.taskOverlayX >= 0 && preferences.taskOverlayY >= 0) {
                    params.gravity = Gravity.TOP or Gravity.LEFT
                    params.x = preferences.taskOverlayX
                    params.y = preferences.taskOverlayY
                    clampPosition()
                    currentEdge = preferences.taskOverlayEdge
                }
                windowManager.addView(root, params)
                attached = true
                startAutonomousWandering()
                audioJob?.cancel()
                audioJob = animScope.launch {
                    SessionStateManager.audioLevel.collect { level ->
                        if (attached && avatarMode) {
                            dynamicAvatar.updateAudioLevel(level)
                        }
                    }
                }
            } catch (_: Exception) {
                attached = false
                return false
            }
        }
        if (currentEdge != preferences.taskOverlayEdge) applyEdge(preferences.taskOverlayEdge)
        if (snapshot == null) renderIdle() else render(snapshot)
        applyMode()
        return true
    }

    fun remove() {
        stopAutonomousWandering()
        audioJob?.cancel()
        audioJob = null
        activeHopAnimator?.cancel()
        activeHopAnimator = null
        if (attached) {
            try { windowManager.removeView(root) } catch (_: Exception) {}
            attached = false
        }
    }

    private fun startAutonomousWandering() {
        wanderingJob?.cancel()
        wanderingJob = animScope.launch {
            while (isActive && attached) {
                val delayTime = 13000L + Random.nextLong(9000L) // Every 13-22 seconds
                delay(delayTime)
                val prefs = JarvisApp.instance.preferences
                if (attached && avatarMode && !dragging && prefs.isTaskOverlayMovementEnabled) {
                    triggerHopAnimation(isAutonomous = true)
                }
            }
        }
    }

    private fun stopAutonomousWandering() {
        wanderingJob?.cancel()
        wanderingJob = null
    }

    private fun triggerHopAnimation(isAutonomous: Boolean = false) {
        if (!attached || dragging) return
        if (activeHopAnimator?.isRunning == true) return

        val prefs = JarvisApp.instance.preferences
        // Play bubble pop sound if sound is enabled
        if (prefs.isTaskOverlaySoundEnabled) {
            BubbleSoundPlayer.play(animScope)
        }

        // Trigger in-webview squash and stretch animation
        avatar.evaluateJavascript("window.triggerHop && window.triggerHop()", null)

        // Smooth window jump animation
        val baseY = params.y
        val hopHeight = dp(if (isAutonomous) 22 else 16)
        val metrics = context.resources.displayMetrics
        val minY = dp(24)
        val maxY = metrics.heightPixels - dp(100)

        val targetApexY = (baseY - hopHeight).coerceIn(minY, maxY)
        // If too close to top, jump downwards slightly instead
        val delta = if (baseY - hopHeight < minY) hopHeight else -hopHeight

        val animator = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
            duration = 580L
            interpolator = OvershootInterpolator(1.15f)
            addUpdateListener { va ->
                if (!attached || dragging) {
                    cancel()
                    return@addUpdateListener
                }
                val fraction = va.animatedValue as Float
                params.y = (baseY + (delta * fraction).toInt()).coerceIn(minY, maxY)
                try {
                    windowManager.updateViewLayout(root, params)
                } catch (_: Exception) {}
            }
        }
        activeHopAnimator = animator
        animator.start()
    }

    private fun render(snapshot: AgentTaskSnapshot) {
        progress.visibility = View.VISIBLE
        title.text = snapshot.originalGoal
        progress.progress = snapshot.progressPercentage.coerceIn(0, 100)
        val completed = snapshot.steps.count { it.status == TaskStepStatus.COMPLETED }
        progressText.text = "$completed/${snapshot.steps.size} done  ·  ${snapshot.progressPercentage}%"
        stepRows.removeAllViews()
        val activeIndex = snapshot.steps.indexOfFirst { it.stepId == snapshot.currentStepId }.coerceAtLeast(0)
        val first = (activeIndex - 2).coerceAtLeast(0)
        snapshot.steps.drop(first).take(4).forEach { step ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val active = step.status in setOf(TaskStepStatus.RUNNING, TaskStepStatus.VERIFYING, TaskStepStatus.WAITING_FOR_UI, TaskStepStatus.RECOVERING)
            val marker: View = if (active) {
                ProgressBar(context, null, android.R.attr.progressBarStyleSmall).apply {
                    isIndeterminate = true
                    indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.rgb(205, 210, 218))
                }
            } else TextView(context).apply {
                text = if (step.status == TaskStepStatus.COMPLETED) "✓" else if (step.status in setOf(TaskStepStatus.FAILED, TaskStepStatus.BLOCKED)) "!" else "·"
                setTextColor(if (step.status == TaskStepStatus.COMPLETED) Color.rgb(175, 210, 188) else Color.rgb(165, 171, 181))
                textSize = 10f
                gravity = Gravity.CENTER
            }
            row.addView(marker, LinearLayout.LayoutParams(dp(12), dp(12)))
            row.addView(label(9f, active).apply {
                text = step.title
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                alpha = if (step.status == TaskStepStatus.COMPLETED) 0.72f else 1f
                setPadding(dp(5), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, dp(14), 1f))
            stepRows.addView(row, LinearLayout.LayoutParams(-1, dp(15)))
        }

        // Map task execution engine snapshot to AvatarState
        val isAnyRunning = snapshot.steps.any { it.status == TaskStepStatus.RUNNING || it.status == TaskStepStatus.WAITING_FOR_UI }
        val isAnyVerifying = snapshot.steps.any { it.status == TaskStepStatus.VERIFYING }
        val isAnyRecovering = snapshot.steps.any { it.status == TaskStepStatus.RECOVERING }
        val isAllCompleted = snapshot.steps.isNotEmpty() && snapshot.steps.all { it.status == TaskStepStatus.COMPLETED }
        val isAnyFailed = snapshot.steps.any { it.status == TaskStepStatus.FAILED }

        val taskAvatarState = when {
            isAllCompleted -> AvatarState.SUCCESS
            isAnyFailed -> AvatarState.FAILED
            isAnyRecovering -> AvatarState.RETRYING
            isAnyVerifying -> AvatarState.VERIFYING
            isAnyRunning -> AvatarState.EXECUTING
            else -> AvatarState.EXECUTING
        }
        setAvatarState(taskAvatarState)

        try { windowManager.updateViewLayout(root, params) } catch (_: Exception) {}
    }

    private fun renderIdle() {
        title.text = "JARVIS"
        stepRows.removeAllViews()
        stepRows.addView(label(9f, false).apply { text = "Ready · Double-tap for task status" }, matchWrap())
        progress.visibility = View.GONE
        progressText.text = "Double-tap for status"
        val voiceState = SessionStateManager.voiceState.value
        setAvatarState(AvatarState.fromVoiceSessionState(voiceState))
        try { windowManager.updateViewLayout(root, params) } catch (_: Exception) {}
    }

    private fun handleOverlayTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                activeHopAnimator?.cancel()
                downRawX = event.rawX
                downRawY = event.rawY
                val location = IntArray(2)
                root.getLocationOnScreen(location)
                startX = location[0]
                startY = location[1]
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (dragging || kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop) {
                    dragging = true
                    params.gravity = Gravity.TOP or Gravity.LEFT
                    params.x = startX + dx.toInt()
                    params.y = startY + dy.toInt()
                    clampPosition()
                    try { windowManager.updateViewLayout(root, params) } catch (_: Exception) {}
                    return true
                }
                return false
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    dragging = false
                    clampPosition()
                    val preferences = JarvisApp.instance.preferences
                    preferences.taskOverlayFreePosition = true
                    preferences.taskOverlayX = params.x
                    preferences.taskOverlayY = params.y
                    return true
                }
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastTapAt <= 360L) {
                    lastTapAt = 0L
                    toggleAvatarMode()
                    return true
                }
                lastTapAt = now
                // Playful bounce & bubble sound feedback on tap
                triggerHopAnimation(isAutonomous = false)
                return false
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                return false
            }
        }
        return false
    }

    private fun applyEdge(edge: String) {
        currentEdge = edge
        when (edge) {
            "left" -> { params.gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL; params.x = dp(8); params.y = 0 }
            "top" -> { params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; params.x = 0; params.y = dp(90) }
            "bottom" -> { params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; params.x = 0; params.y = dp(90) }
            else -> { params.gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL; params.x = dp(8); params.y = 0 }
        }
        try { if (attached) windowManager.updateViewLayout(root, params) } catch (_: Exception) {}
    }

    private fun toggleAvatarMode() {
        avatarMode = !avatarMode
        applyMode()
    }

    private fun applyMode() {
        pill.visibility = if (avatarMode) View.GONE else View.VISIBLE
        val selectedAvatar = JarvisApp.instance.preferences.taskOverlayAvatar
        dynamicAvatar.setAvatarStyle(selectedAvatar)

        if (avatarMode) {
            dynamicAvatar.visibility = View.VISIBLE
            avatar.visibility = View.GONE
        } else {
            dynamicAvatar.visibility = View.GONE
            avatar.visibility = View.GONE
        }
        params.width = if (avatarMode) dp(88) else minOf(dp(236), context.resources.displayMetrics.widthPixels - dp(16))
        params.height = if (avatarMode) dp(88) else WindowManager.LayoutParams.WRAP_CONTENT
        if (JarvisApp.instance.preferences.taskOverlayFreePosition) clampPosition()
        try { windowManager.updateViewLayout(root, params) } catch (_: Exception) {}
    }

    private fun clampPosition() {
        val metrics = context.resources.displayMetrics
        val overlayWidth = root.width.takeIf { it > 0 } ?: params.width.takeIf { it > 0 } ?: dp(88)
        val overlayHeight = root.height.takeIf { it > 0 } ?: params.height.takeIf { it > 0 } ?: dp(88)
        params.gravity = Gravity.TOP or Gravity.LEFT
        params.x = params.x.coerceIn(0, (metrics.widthPixels - overlayWidth).coerceAtLeast(0))
        params.y = params.y.coerceIn(dp(24), (metrics.heightPixels - overlayHeight).coerceAtLeast(dp(24)))
    }

    private fun label(size: Float, bold: Boolean) = TextView(context).apply {
        setTextColor(Color.rgb(226, 232, 240))
        textSize = size
        if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
