package com.jarvis.assistant.ui.grok

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.jarvis.assistant.automation.session.AvatarState
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-performance 60 FPS expressive Droid Bot View designed specifically for the Dynamic Island.
 * Conveys emotions purely through face and visor color, LED expressions, and physics-based head gestures:
 * - Affirmative Head Nod (bobbing up and down) for SUCCESS
 * - Refusal Head Shake (side to side) for FAILED / ERROR
 * - Head Tilt & Scanner beam for THINKING
 * - Alert eyes & antenna glow for LISTENING
 * - Audio-reactive speaking eyes for SPEAKING
 * - Sleepy eyes for PAUSED / IDLE
 */
class DynamicIslandDroidView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var currentState: AvatarState = AvatarState.IDLE
        private set

    var audioLevel: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    // Colors
    private var primaryColor: Int = Color.parseColor("#00F0FF")
    private var secondaryColor: Int = Color.parseColor("#9D00FF")
    private var targetColor: Int = primaryColor
    private val argbEvaluator = ArgbEvaluator()

    // Gesture & Motion States
    private var nodOffset: Float = 0f
    private var shakeOffset: Float = 0f
    private var tiltAngle: Float = 0f
    private var nodAnimator: ValueAnimator? = null
    private var shakeAnimator: ValueAnimator? = null
    private var tiltAnimator: ValueAnimator? = null

    // Paints
    private val domePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val visorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val eyeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val antennaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val domePath = Path()
    private val visorRect = RectF()
    private val checkPath = Path()

    init {
        updateColorsForState(currentState)
    }

    fun updateState(state: AvatarState) {
        if (currentState == state) return
        val previousState = currentState
        currentState = state
        updateColorsForState(state)

        // Trigger physical head gestures based on emotion
        when (state) {
            AvatarState.SUCCESS -> {
                triggerHeadNod()
            }
            AvatarState.FAILED -> {
                triggerHeadShake()
            }
            AvatarState.THINKING, AvatarState.EXECUTING, AvatarState.VERIFYING -> {
                triggerHeadTilt(-7f) // Curious head tilt
            }
            else -> {
                if (previousState == AvatarState.THINKING || tiltAngle != 0f) {
                    triggerHeadTilt(0f)
                }
            }
        }
        invalidate()
    }

    private fun updateColorsForState(state: AvatarState) {
        when (state) {
            AvatarState.IDLE, AvatarState.DISCONNECTED, AvatarState.ENDED -> {
                primaryColor = Color.parseColor("#00D4FF")   // Calm Cyan
                secondaryColor = Color.parseColor("#3B82F6") // Blue
            }
            AvatarState.LISTENING -> {
                primaryColor = Color.parseColor("#00F0FF")   // Electric Cyan
                secondaryColor = Color.parseColor("#06B6D4") // Teal
            }
            AvatarState.THINKING, AvatarState.EXECUTING, AvatarState.VERIFYING,
            AvatarState.CONNECTING, AvatarState.RECONNECTING, AvatarState.RETRYING -> {
                primaryColor = Color.parseColor("#A855F7")   // Cyber Purple
                secondaryColor = Color.parseColor("#EC4899") // Pink
            }
            AvatarState.SPEAKING -> {
                primaryColor = Color.parseColor("#00FF66")   // Neon Green
                secondaryColor = Color.parseColor("#00F0FF") // Cyan
            }
            AvatarState.SUCCESS -> {
                primaryColor = Color.parseColor("#10B981")   // Emerald Green
                secondaryColor = Color.parseColor("#34D399") // Light Green
            }
            AvatarState.FAILED -> {
                primaryColor = Color.parseColor("#EF4444")   // Vivid Crimson Red
                secondaryColor = Color.parseColor("#F87171") // Red Alert
            }
            AvatarState.PAUSED -> {
                primaryColor = Color.parseColor("#F59E0B")   // Amber Orange
                secondaryColor = Color.parseColor("#D97706") // Deep Amber
            }
        }
    }

    /**
     * Affirmative head nod (bobbing up and down) for successful operations ("হা").
     */
    fun triggerHeadNod() {
        nodAnimator?.cancel()
        nodAnimator = ValueAnimator.ofFloat(0f, 4f, -2f, 3f, -1f, 0f).apply {
            duration = 550L
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                nodOffset = va.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /**
     * Refusal head shake (side to side) for error, cancellation, or failure ("না").
     */
    fun triggerHeadShake() {
        shakeAnimator?.cancel()
        shakeAnimator = ValueAnimator.ofFloat(0f, -5f, 5f, -4f, 4f, -2f, 2f, 0f).apply {
            duration = 600L
            interpolator = OvershootInterpolator(1.4f)
            addUpdateListener { va ->
                shakeOffset = va.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /**
     * Inquisitive head tilt for thinking/curious state.
     */
    private fun triggerHeadTilt(targetAngle: Float) {
        tiltAnimator?.cancel()
        tiltAnimator = ValueAnimator.ofFloat(tiltAngle, targetAngle).apply {
            duration = 350L
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                tiltAngle = va.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val animTime = SystemClock.uptimeMillis() / 1000f

        val cx = w / 2f
        val cy = h / 2f
        val baseRadius = minOf(w, h) * 0.44f

        canvas.save()

        // Apply physical head nod and shake translations
        canvas.translate(shakeOffset, nodOffset)
        // Apply inquisitive tilt
        canvas.rotate(tiltAngle, cx, cy)

        // 1. Dynamic background glow
        val glowRadius = baseRadius * 1.5f
        glowPaint.shader = RadialGradient(
            cx, cy, glowRadius,
            intArrayOf(Color.argb(80, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor)), Color.TRANSPARENT),
            floatArrayOf(0.2f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, glowRadius, glowPaint)

        // 2. Droid Antennas
        val headY = cy + baseRadius * 0.12f
        val domeRadius = baseRadius * 0.78f

        antennaPaint.strokeWidth = baseRadius * 0.11f
        antennaPaint.color = Color.parseColor("#94A3B8")

        val antSpacing = domeRadius * 0.36f
        val antTipOffsetY = baseRadius * 0.55f
        val antTipSpread = baseRadius * 0.28f

        // Antenna wiggle on audio level
        val wiggle = sin(animTime * 12.0).toFloat() * (audioLevel * baseRadius * 0.08f)

        val leftBaseX = cx - antSpacing
        val leftBaseY = headY - domeRadius * 0.65f
        val leftTipX = leftBaseX - antTipSpread + wiggle
        val leftTipY = leftBaseY - antTipOffsetY

        val rightBaseX = cx + antSpacing
        val rightBaseY = headY - domeRadius * 0.65f
        val rightTipX = rightBaseX + antTipSpread + wiggle
        val rightTipY = rightBaseY - antTipOffsetY

        canvas.drawLine(leftBaseX, leftBaseY, leftTipX, leftTipY, antennaPaint)
        canvas.drawLine(rightBaseX, rightBaseY, rightTipX, rightTipY, antennaPaint)

        // Antenna glowing LED tips
        val tipRadius = baseRadius * 0.14f + (audioLevel * baseRadius * 0.06f)
        eyeGlowPaint.color = primaryColor
        eyeGlowPaint.alpha = 120
        canvas.drawCircle(leftTipX, leftTipY, tipRadius * 1.6f, eyeGlowPaint)
        canvas.drawCircle(rightTipX, rightTipY, tipRadius * 1.6f, eyeGlowPaint)

        eyePaint.color = primaryColor
        eyePaint.alpha = 255
        canvas.drawCircle(leftTipX, leftTipY, tipRadius, eyePaint)
        canvas.drawCircle(rightTipX, rightTipY, tipRadius, eyePaint)

        // 3. Droid Dome Head (Porcelain metallic dome with subtle gradient)
        domePath.reset()
        val domeBottom = headY + domeRadius * 0.42f
        val domeTop = headY - domeRadius * 0.72f
        val domeLeft = cx - domeRadius
        val domeRight = cx + domeRadius

        domePath.moveTo(domeLeft, domeBottom)
        domePath.lineTo(domeLeft, headY - domeRadius * 0.10f)
        domePath.arcTo(RectF(domeLeft, domeTop, domeRight, domeBottom), 180f, 180f, false)
        domePath.lineTo(domeRight, domeBottom)
        domePath.close()

        domePaint.shader = LinearGradient(
            cx, domeTop, cx, domeBottom,
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#D9E8FF"),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(domePath, domePaint)

        // 4. Curved Glass Visor Screen
        val visorW = domeRadius * 1.55f
        val visorH = domeRadius * 0.70f
        visorRect.set(cx - visorW / 2f, headY - visorH * 0.40f, cx + visorW / 2f, headY + visorH * 0.60f)

        visorPaint.shader = LinearGradient(
            cx, visorRect.top, cx, visorRect.bottom,
            Color.parseColor("#0F172A"),
            Color.parseColor("#020617"),
            Shader.TileMode.CLAMP
        )
        val visorCorner = visorH * 0.45f
        canvas.drawRoundRect(visorRect, visorCorner, visorCorner, visorPaint)

        // 5. Expressive LED Eyes & Emotion Graphics
        drawEmotionFace(canvas, cx, headY, domeRadius, animTime)

        canvas.restore()

        postInvalidateOnAnimation()
    }

    private fun drawEmotionFace(canvas: Canvas, cx: Float, headY: Float, domeR: Float, time: Float) {
        val eyeSpacing = domeR * 0.38f
        val eyeY = headY + domeR * 0.08f
        val eyeW = domeR * 0.16f
        val eyeH = domeR * 0.22f

        eyePaint.color = primaryColor
        eyeGlowPaint.color = primaryColor

        when (currentState) {
            AvatarState.SUCCESS -> {
                // Vivid Emerald Green Checkmark or Happy Curved Arches `^ ^`
                checkPaint.color = primaryColor
                checkPaint.strokeWidth = domeR * 0.14f

                // Draw centered glowing Checkmark inside visor
                checkPath.reset()
                val checkX = cx
                val checkY = eyeY
                val checkScale = domeR * 0.28f

                checkPath.moveTo(checkX - checkScale * 0.65f, checkY)
                checkPath.lineTo(checkX - checkScale * 0.15f, checkY + checkScale * 0.45f)
                checkPath.lineTo(checkX + checkScale * 0.70f, checkY - checkScale * 0.50f)
                canvas.drawPath(checkPath, checkPaint)
            }

            AvatarState.FAILED -> {
                // Bright Crimson Red `X X` error eyes
                checkPaint.color = primaryColor
                checkPaint.strokeWidth = domeR * 0.12f

                val crossSize = domeR * 0.16f
                // Left X
                val leftX = cx - eyeSpacing
                canvas.drawLine(leftX - crossSize, eyeY - crossSize, leftX + crossSize, eyeY + crossSize, checkPaint)
                canvas.drawLine(leftX + crossSize, eyeY - crossSize, leftX - crossSize, eyeY + crossSize, checkPaint)

                // Right X
                val rightX = cx + eyeSpacing
                canvas.drawLine(rightX - crossSize, eyeY - crossSize, rightX + crossSize, eyeY + crossSize, checkPaint)
                canvas.drawLine(rightX + crossSize, eyeY - crossSize, rightX - crossSize, eyeY + crossSize, checkPaint)
            }

            AvatarState.THINKING, AvatarState.EXECUTING, AvatarState.VERIFYING,
            AvatarState.CONNECTING, AvatarState.RECONNECTING, AvatarState.RETRYING -> {
                // Scanning visor beam + inquisitive eyes looking upward/side
                val scanX = cx + sin(time * 6.0).toFloat() * (eyeSpacing * 1.2f)
                val scanWidth = domeR * 0.35f

                // Beam sweep glow
                eyeGlowPaint.alpha = 140
                canvas.drawRoundRect(
                    scanX - scanWidth / 2f, eyeY - eyeH * 0.6f,
                    scanX + scanWidth / 2f, eyeY + eyeH * 0.6f,
                    eyeW, eyeW, eyeGlowPaint
                )

                // Curious dot eyes following beam
                eyePaint.alpha = 255
                canvas.drawCircle(cx - eyeSpacing + sin(time * 4.0).toFloat() * 2f, eyeY - domeR * 0.05f, eyeW * 0.9f, eyePaint)
                canvas.drawCircle(cx + eyeSpacing + sin(time * 4.0).toFloat() * 2f, eyeY - domeR * 0.05f, eyeW * 0.9f, eyePaint)
            }

            AvatarState.SPEAKING -> {
                // Dynamic reactive speaking eyes / waveform bars syncing to live voice audio
                val speechScale = 0.6f + (audioLevel * 0.9f)
                val dynamicEyeH = eyeH * speechScale

                eyeGlowPaint.alpha = 120
                canvas.drawRoundRect(
                    cx - eyeSpacing - eyeW, eyeY - dynamicEyeH / 2f,
                    cx - eyeSpacing + eyeW, eyeY + dynamicEyeH / 2f,
                    eyeW, eyeW, eyeGlowPaint
                )
                canvas.drawRoundRect(
                    cx + eyeSpacing - eyeW, eyeY - dynamicEyeH / 2f,
                    cx + eyeSpacing + eyeW, eyeY + dynamicEyeH / 2f,
                    eyeW, eyeW, eyeGlowPaint
                )

                eyePaint.alpha = 255
                canvas.drawRoundRect(
                    cx - eyeSpacing - eyeW * 0.75f, eyeY - dynamicEyeH / 2f,
                    cx - eyeSpacing + eyeW * 0.75f, eyeY + dynamicEyeH / 2f,
                    eyeW * 0.75f, eyeW * 0.75f, eyePaint
                )
                canvas.drawRoundRect(
                    cx + eyeSpacing - eyeW * 0.75f, eyeY - dynamicEyeH / 2f,
                    cx + eyeSpacing + eyeW * 0.75f, eyeY + dynamicEyeH / 2f,
                    eyeW * 0.75f, eyeW * 0.75f, eyePaint
                )
            }

            AvatarState.PAUSED -> {
                // Sleepy line eyes `- -`
                checkPaint.color = primaryColor
                checkPaint.strokeWidth = domeR * 0.10f
                canvas.drawLine(cx - eyeSpacing - eyeW, eyeY, cx - eyeSpacing + eyeW, eyeY, checkPaint)
                canvas.drawLine(cx + eyeSpacing - eyeW, eyeY, cx + eyeSpacing + eyeW, eyeY, checkPaint)
            }

            else -> {
                // IDLE / LISTENING: Calm blinking cyan eyes
                val blinkCycle = (time * 1.4f) % 4.0f
                val isBlinking = blinkCycle > 3.82f
                val eyeScaleY = if (isBlinking) 0.12f else 1.0f

                val curH = eyeH * eyeScaleY

                eyeGlowPaint.alpha = 90
                canvas.drawRoundRect(
                    cx - eyeSpacing - eyeW * 1.1f, eyeY - curH * 0.6f,
                    cx - eyeSpacing + eyeW * 1.1f, eyeY + curH * 0.6f,
                    eyeW, eyeW, eyeGlowPaint
                )
                canvas.drawRoundRect(
                    cx + eyeSpacing - eyeW * 1.1f, eyeY - curH * 0.6f,
                    cx + eyeSpacing + eyeW * 1.1f, eyeY + curH * 0.6f,
                    eyeW, eyeW, eyeGlowPaint
                )

                eyePaint.alpha = 255
                canvas.drawRoundRect(
                    cx - eyeSpacing - eyeW * 0.8f, eyeY - curH * 0.5f,
                    cx - eyeSpacing + eyeW * 0.8f, eyeY + curH * 0.5f,
                    eyeW * 0.8f, eyeW * 0.8f, eyePaint
                )
                canvas.drawRoundRect(
                    cx + eyeSpacing - eyeW * 0.8f, eyeY - curH * 0.5f,
                    cx + eyeSpacing + eyeW * 0.8f, eyeY + curH * 0.5f,
                    eyeW * 0.8f, eyeW * 0.8f, eyePaint
                )
            }
        }
    }
}
