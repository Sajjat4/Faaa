package com.jarvis.assistant.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.data.model.ConversationState
import com.jarvis.assistant.util.ThemeManager
import kotlin.math.max
import kotlin.math.sin

/**
 * Ultra-smooth, hardware-accelerated dual-edge RGB luminous lighting view.
 * Renders flowing multi-stop gradient streams on the left and right borders of the screen.
 * Dynamically adapts to app theme palette and pulses with voice/audio amplitude.
 */
class EdgeLightingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density
    private fun dp(v: Int): Float = v * density

    // Gradient colors dynamically bound to theme
    private var colorPalette: IntArray = intArrayOf(
        Color.parseColor("#00F0FF"),
        Color.parseColor("#7928CA"),
        Color.parseColor("#FF007F"),
        Color.parseColor("#00F0FF")
    )

    // Drawing paints & shaders
    private val leftGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rightGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val leftCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rightCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val leftRect = RectF()
    private val rightRect = RectF()
    private val leftCoreRect = RectF()
    private val rightCoreRect = RectF()

    // Animation & phase
    private var phase: Float = 0f
    private var flowAnimator: ValueAnimator? = null

    // Audio & voice responsiveness
    private var targetAudioLevel: Float = 0f
    private var smoothedAudioLevel: Float = 0f
    private var currentState: ConversationState = ConversationState.IDLE
    private var baseWidthPx = dp(12)
    private var maxBonusWidthPx = dp(14)
    private var currentAlpha = 0.85f

    // Notification sweep pulse
    private var isNotificationPulsing = false
    private var notificationSweepProgress = 0f
    private var notificationPulseAnimator: ValueAnimator? = null

    init {
        setWillNotDraw(false)
        refreshPaletteFromTheme()
    }

    fun refreshPaletteFromTheme() {
        val prefs = try { JarvisApp.instance.preferences } catch (_: Exception) { null }
        val themeKey = prefs?.appTheme ?: "sunset"
        val customHex = prefs?.customThemeColor
        colorPalette = ThemeManager.getEdgeLightingColors(themeKey, customHex)
        invalidateGradients()
        invalidate()
    }

    fun setPalette(colors: IntArray) {
        if (colors.isNotEmpty()) {
            colorPalette = colors
            invalidateGradients()
            invalidate()
        }
    }

    fun updateAudioLevel(level: Float) {
        targetAudioLevel = level.coerceIn(0f, 1f)
        invalidate()
    }

    fun updateState(state: ConversationState) {
        currentState = state
        when (state) {
            ConversationState.LISTENING -> {
                currentAlpha = 1.0f
            }
            ConversationState.SPEAKING -> {
                currentAlpha = 0.95f
            }
            ConversationState.THINKING -> {
                currentAlpha = 0.80f
            }
            ConversationState.IDLE -> {
                currentAlpha = 0.65f
            }
        }
        invalidate()
    }

    fun triggerNotificationPulse() {
        notificationPulseAnimator?.cancel()
        isNotificationPulsing = true
        notificationPulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100L
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                notificationSweepProgress = va.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    isNotificationPulsing = false
                    invalidate()
                }
            })
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startFlowAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopFlowAnimation()
        notificationPulseAnimator?.cancel()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            startFlowAnimation()
        } else {
            stopFlowAnimation()
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            startFlowAnimation()
        } else {
            stopFlowAnimation()
        }
    }

    private fun startFlowAnimation() {
        if (flowAnimator?.isRunning == true) return
        flowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4200L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                phase = va.animatedFraction
                invalidateGradients()
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    private fun stopFlowAnimation() {
        flowAnimator?.cancel()
        flowAnimator = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidateGradients()
    }

    private fun invalidateGradients() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0 || colorPalette.isEmpty()) return

        // Shift color stops according to animation phase
        val count = colorPalette.size
        val shiftedColors = IntArray(count + 1)
        val stops = FloatArray(count + 1)

        val offset = phase
        for (i in 0 until count) {
            val idx = (i + (offset * count).toInt()) % count
            shiftedColors[i] = colorPalette[idx]
            stops[i] = i.toFloat() / count.toFloat()
        }
        shiftedColors[count] = shiftedColors[0]
        stops[count] = 1.0f

        val verticalShader = LinearGradient(
            0f, 0f, 0f, h,
            shiftedColors,
            stops,
            Shader.TileMode.REPEAT
        )

        leftGlowPaint.shader = verticalShader
        rightGlowPaint.shader = verticalShader
        leftCorePaint.shader = verticalShader
        rightCorePaint.shader = verticalShader
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // Smooth audio response interpolation
        smoothedAudioLevel += (targetAudioLevel - smoothedAudioLevel) * 0.28f

        // Compute active glow dimensions
        val prefs = try { JarvisApp.instance.preferences } catch (_: Exception) { null }
        val userBaseWidth = dp(prefs?.edgeLightingWidthDp ?: 12)
        val voicePulseEnabled = prefs?.isEdgeLightingVoicePulse ?: true

        val bonusWidth = if (voicePulseEnabled) smoothedAudioLevel * maxBonusWidthPx else 0f
        val pulseBreathing = if (currentState == ConversationState.LISTENING || currentState == ConversationState.SPEAKING) {
            (sin(System.currentTimeMillis() / 180.0) * dp(2)).toFloat()
        } else {
            0f
        }

        val totalGlowWidth = (userBaseWidth + bonusWidth + pulseBreathing).coerceAtLeast(dp(6))
        val coreWidth = (totalGlowWidth * 0.35f).coerceAtLeast(dp(2.5f))

        val baseAlphaInt = (currentAlpha * 255).toInt().coerceIn(0, 255)
        val audioBoost = (smoothedAudioLevel * 70).toInt()
        val finalGlowAlpha = (baseAlphaInt + audioBoost).coerceIn(40, 255)
        val finalCoreAlpha = (finalGlowAlpha + 45).coerceIn(100, 255)

        // Draw Left Border Glow
        leftRect.set(0f, 0f, totalGlowWidth, h)
        leftGlowPaint.alpha = (finalGlowAlpha * 0.55f).toInt()
        canvas.drawRect(leftRect, leftGlowPaint)

        // Draw Left Border Solid/Core Light
        leftCoreRect.set(0f, 0f, coreWidth, h)
        leftCorePaint.alpha = finalCoreAlpha
        canvas.drawRect(leftCoreRect, leftCorePaint)

        // Draw Right Border Glow
        rightRect.set(w - totalGlowWidth, 0f, w, h)
        rightGlowPaint.alpha = (finalGlowAlpha * 0.55f).toInt()
        canvas.drawRect(rightRect, rightGlowPaint)

        // Draw Right Border Solid/Core Light
        rightCoreRect.set(w - coreWidth, 0f, w, h)
        rightCorePaint.alpha = finalCoreAlpha
        canvas.drawRect(rightCoreRect, rightCorePaint)

        // Draw Notification Sweep Wave if active
        if (isNotificationPulsing) {
            val sweepY = notificationSweepProgress * h
            val sweepHeight = dp(140)
            val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = ((1f - notificationSweepProgress) * 200).toInt().coerceIn(0, 255)
            }
            canvas.drawRect(0f, max(0f, sweepY - sweepHeight), totalGlowWidth * 1.5f, sweepY, sweepPaint)
            canvas.drawRect(w - (totalGlowWidth * 1.5f), max(0f, sweepY - sweepHeight), w, sweepY, sweepPaint)
        }
    }
}
