package com.jarvis.assistant.ui.agent

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
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.audio.AvatarSoundEffectManager
import com.jarvis.assistant.automation.session.AvatarState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import java.util.Locale

/**
 * 60+ FPS High-Performance Dynamic Overlay Avatar View.
 * Renders state-based animations, strict color-gated palettes, MetaMuse 3D character face,
 * particle physics, dynamic radial glow, and synchronized voice audio waveforms.
 */
class DynamicOverlayAvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val argbEvaluator = ArgbEvaluator()

    var currentState: AvatarState = AvatarState.IDLE
        private set

    private var currentPrimaryColor: Int = AvatarState.IDLE.primaryColor
    private var targetPrimaryColor: Int = AvatarState.IDLE.primaryColor

    private var currentSecondaryColor: Int = AvatarState.IDLE.secondaryColor
    private var targetSecondaryColor: Int = AvatarState.IDLE.secondaryColor

    private var colorAnimator: ValueAnimator? = null
    private var successPulse = 0f
    private var failureShake = 0f
    private var currentAudioLevel = 0f
    private var smoothedAudioLevel = 0f

    /** Live runtime scaling factor (adjusted smoothly via avatar size slider) */
    var avatarScaleFactor: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.5f, 1.5f)
            invalidate()
        }

    /** Explicit avatar style override (e.g., 'Droid', 'MetaMuse', 'Classic') */
    var customAvatarStyle: String? = null
        set(value) {
            field = value
            invalidate()
        }

    fun setAvatarStyle(style: String) {
        customAvatarStyle = style
        invalidate()
    }

    // Particle system
    private class Particle(
        var angle: Float,
        var radius: Float,
        var speed: Float,
        var size: Float,
        var alpha: Float
    )
    private val particles = ArrayList<Particle>()
    private val particleCount = 14

    // Paints
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val waveformPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
    }
    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(190, 8, 12, 22)
    }

    // MetaMuse Character Paints
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val visorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val eyeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val badgeIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val earLeftPath = Path()
    private val earRightPath = Path()
    private val innerEarLeftPath = Path()
    private val innerEarRightPath = Path()

    private var startTime = SystemClock.uptimeMillis()
    private val density = context.resources.displayMetrics.density

    init {
        // Initialize particle distribution
        for (i in 0 until particleCount) {
            particles.add(
                Particle(
                    angle = (i * (360f / particleCount)),
                    radius = dp(18f) + Random.nextFloat() * dp(16f),
                    speed = (0.7f + Random.nextFloat() * 1.1f) * (if (Random.nextBoolean()) 1 else -1),
                    size = dp(1.8f) + Random.nextFloat() * dp(2.2f),
                    alpha = 0.45f + Random.nextFloat() * 0.45f
                )
            )
        }
    }

    fun setState(newState: AvatarState) {
        if (currentState == newState) return
        currentState = newState

        targetPrimaryColor = newState.primaryColor
        targetSecondaryColor = newState.secondaryColor

        colorAnimator?.cancel()
        val fromPrim = currentPrimaryColor
        val toPrim = targetPrimaryColor
        val fromSec = currentSecondaryColor
        val toSec = targetSecondaryColor

        colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320L
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                val fraction = va.animatedFraction
                currentPrimaryColor = argbEvaluator.evaluate(fraction, fromPrim, toPrim) as Int
                currentSecondaryColor = argbEvaluator.evaluate(fraction, fromSec, toSec) as Int
                invalidate()
            }
        }
        colorAnimator?.start()

        // One-shot effects
        if (newState == AvatarState.SUCCESS) {
            triggerSuccessPulse()
        } else if (newState == AvatarState.FAILED) {
            triggerFailureShake()
        }

        // Sound effect & Haptic feedback
        AvatarSoundEffectManager.playStateFeedback(context, newState)
        invalidate()
    }

    fun updateAudioLevel(level: Float) {
        currentAudioLevel = level.coerceIn(0f, 1f)
        invalidate()
    }

    private fun triggerSuccessPulse() {
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 600L
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                successPulse = va.animatedValue as Float
                invalidate()
            }
        }.start()
    }

    private fun triggerFailureShake() {
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 450L
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                failureShake = va.animatedValue as Float
                invalidate()
            }
        }.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val cx = w / 2f
        val cy = (h / 2f) - (if (JarvisApp.instance.preferences.isShowStatusTextEnabled) dp(6f) else 0f)
        val elapsed = (SystemClock.uptimeMillis() - startTime) / 1000f

        val prefs = JarvisApp.instance.preferences
        val reduceMotion = prefs.isReduceMotionEnabled
        val speedMultiplier = when (prefs.avatarAnimationSpeed) {
            "Slow" -> 0.55f
            "Fast" -> 1.75f
            else -> 1.0f
        } * (if (reduceMotion) 0.35f else 1.0f)

        // State-based animation rate
        val stateRate = when (currentState) {
            AvatarState.IDLE -> 0.8f
            AvatarState.PAUSED -> 0.4f
            AvatarState.THINKING, AvatarState.EXECUTING -> 2.2f
            AvatarState.SPEAKING, AvatarState.LISTENING -> 1.6f
            AvatarState.RECONNECTING -> 1.4f
            AvatarState.RETRYING -> 2.0f
            AvatarState.FAILED, AvatarState.ENDED -> 0.5f
            else -> 1.0f
        }

        val animTime = elapsed * speedMultiplier * stateRate

        // Audio level smoothing
        smoothedAudioLevel += (currentAudioLevel - smoothedAudioLevel) * 0.28f

        // Dynamic scale from preferences & live slider
        val configuredScale = (prefs.avatarScalePercent / 100f).coerceIn(0.5f, 1.5f)
        val activeScale = configuredScale * avatarScaleFactor

        canvas.save()
        canvas.scale(activeScale, activeScale, cx, cy)

        // Shake offset for failure
        val shakeX = if (failureShake > 0.01f) (sin(animTime * 35.0).toFloat() * dp(4f) * failureShake) else 0f
        val drawCx = cx + shakeX
        val drawCy = cy

        // Dynamic Glow
        val baseRadius = dp(24f)
        val pulseScale = 1.0f + (sin(animTime * 2.8).toFloat() * 0.08f) + (smoothedAudioLevel * 0.22f) + (successPulse * 0.35f)
        val coreRadius = baseRadius * pulseScale

        // Avatar Style specific rendering
        val activeStyle = customAvatarStyle ?: if (prefs.taskOverlayAvatar.isNotBlank() &&
            !prefs.taskOverlayAvatar.equals("metamuse", ignoreCase = true) &&
            !prefs.taskOverlayAvatar.equals("none", ignoreCase = true)
        ) {
            prefs.taskOverlayAvatar
        } else {
            prefs.avatarStyle
        }
        val isGrokBot = com.jarvis.assistant.ui.grok.GrokBotDrawingEngine.isGrokFamilyStyle(activeStyle)

        if (!isGrokBot) {
            glowPaint.shader = RadialGradient(
                drawCx, drawCy, coreRadius * 2.2f,
                intArrayOf(
                    Color.argb(140, Color.red(currentPrimaryColor), Color.green(currentPrimaryColor), Color.blue(currentPrimaryColor)),
                    Color.argb(55, Color.red(currentSecondaryColor), Color.green(currentSecondaryColor), Color.blue(currentSecondaryColor)),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.65f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(drawCx, drawCy, coreRadius * 2.2f, glowPaint)

            // Orbiting Particles
            if (prefs.isParticleAnimationEnabled) {
                drawParticles(canvas, drawCx, drawCy, coreRadius, animTime, reduceMotion)
            }

            // Reactive Audio Waveform
            if (prefs.isAudioWaveformEnabled && (currentState == AvatarState.LISTENING || currentState == AvatarState.SPEAKING || smoothedAudioLevel > 0.05f)) {
                drawAudioWaveform(canvas, drawCx, drawCy, coreRadius, animTime)
            }
        }

        drawAvatarStyle(canvas, drawCx, drawCy, coreRadius, animTime, activeStyle)

        canvas.restore()

        // Status Text Badge (rendered at unscaled bottom for crisp text readability)
        if (prefs.isShowStatusTextEnabled) {
            drawStatusBadge(canvas, cx, h - dp(6f))
        }

        postInvalidateOnAnimation()
    }

    private fun drawParticles(canvas: Canvas, cx: Float, cy: Float, coreR: Float, time: Float, reduceMotion: Boolean) {
        val count = particles.size
        for (i in 0 until count) {
            val p = particles[i]
            val motionFactor = if (reduceMotion) 0.1f else 1.0f
            val currentAngle = (p.angle + (time * 50f * p.speed * motionFactor)) % 360f
            val rad = Math.toRadians(currentAngle.toDouble())

            val r = (coreR * 1.15f) + (p.radius * 0.45f)
            val px = cx + (cos(rad) * r).toFloat()
            val py = cy + (sin(rad) * r).toFloat()

            val pColor = if (i % 2 == 0) currentPrimaryColor else currentSecondaryColor
            particlePaint.color = pColor
            particlePaint.alpha = ((p.alpha * 255).toInt()).coerceIn(40, 240)

            if (JarvisApp.instance.preferences.avatarStyle == "Pixel") {
                val half = p.size / 2f
                canvas.drawRect(px - half, py - half, px + half, py + half, particlePaint)
            } else {
                canvas.drawCircle(px, py, p.size, particlePaint)
            }
        }
    }

    private fun drawAudioWaveform(canvas: Canvas, cx: Float, cy: Float, coreR: Float, time: Float) {
        val bars = 16
        val barRadius = coreR * 1.35f
        waveformPaint.strokeWidth = dp(2.2f)

        for (i in 0 until bars) {
            val angleDeg = (i * (360f / bars)) + (time * 20f)
            val rad = Math.toRadians(angleDeg.toDouble())

            val harmonic = sin(time * 6.0 + i * 0.8).toFloat() * 0.5f + 0.5f
            val barHeight = dp(2f) + (smoothedAudioLevel * dp(12f)) + (harmonic * dp(3f))

            val startX = cx + (cos(rad) * barRadius).toFloat()
            val startY = cy + (sin(rad) * barRadius).toFloat()

            val endX = cx + (cos(rad) * (barRadius + barHeight)).toFloat()
            val endY = cy + (sin(rad) * (barRadius + barHeight)).toFloat()

            waveformPaint.color = if (i % 2 == 0) currentPrimaryColor else currentSecondaryColor
            waveformPaint.alpha = ((140 + (smoothedAudioLevel * 115)).toInt()).coerceIn(60, 255)
            canvas.drawLine(startX, startY, endX, endY, waveformPaint)
        }
    }

    private fun drawAvatarStyle(canvas: Canvas, cx: Float, cy: Float, radius: Float, time: Float, style: String) {
        if (com.jarvis.assistant.ui.grok.GrokBotDrawingEngine.isGrokFamilyStyle(style)) {
            val grokState = com.jarvis.assistant.ui.grok.GrokBotDrawingEngine.GrokBotState.fromAvatarState(currentState)
            com.jarvis.assistant.ui.grok.GrokBotDrawingEngine.drawCuteOrGrokBot(
                canvas = canvas,
                cx = cx,
                cy = cy,
                radius = radius,
                state = grokState,
                animTime = time,
                audioLevel = smoothedAudioLevel,
                scaleFactor = 1.0f,
                avatarStyle = style
            )
            return
        }

        when (style.lowercase(Locale.ROOT)) {
            "droid", "robot" -> {
                drawDroidAvatar(canvas, cx, cy, radius, time)
            }
            "mech" -> {
                drawMechAvatar(canvas, cx, cy, radius, time)
            }
            "cat" -> {
                drawCatAvatar(canvas, cx, cy, radius, time)
            }
            "ghost" -> {
                drawGhostAvatar(canvas, cx, cy, radius, time)
            }
            "alien" -> {
                drawAlienAvatar(canvas, cx, cy, radius, time)
            }
            "flower" -> {
                drawFlowerAvatar(canvas, cx, cy, radius, time)
            }
            "star" -> {
                drawStarAvatar(canvas, cx, cy, radius, time)
            }
            "cloud" -> {
                drawCloudAvatar(canvas, cx, cy, radius, time)
            }
            "drop" -> {
                drawDropAvatar(canvas, cx, cy, radius, time)
            }
            "blob" -> {
                drawBlobAvatar(canvas, cx, cy, radius, time)
            }
            "pebble" -> {
                drawPebbleAvatar(canvas, cx, cy, radius, time)
            }
            "pill" -> {
                drawPillAvatar(canvas, cx, cy, radius, time)
            }
            "puddle" -> {
                drawPuddleAvatar(canvas, cx, cy, radius, time)
            }
            "hexagon" -> {
                drawHexagonAvatar(canvas, cx, cy, radius, time)
            }
            "triangle" -> {
                drawTriangleAvatar(canvas, cx, cy, radius, time)
            }
            "circle" -> {
                drawCircleAvatar(canvas, cx, cy, radius, time)
            }
            "square" -> {
                drawSquareAvatar(canvas, cx, cy, radius, time)
            }
            "neon" -> {
                ringPaint.strokeWidth = dp(3.2f)
                ringPaint.color = currentPrimaryColor
                canvas.drawCircle(cx, cy, radius * 0.95f, ringPaint)

                corePaint.color = currentSecondaryColor
                corePaint.alpha = 230
                canvas.drawCircle(cx, cy, radius * 0.55f, corePaint)

                corePaint.color = Color.WHITE
                corePaint.alpha = 245
                canvas.drawCircle(cx, cy, radius * 0.25f, corePaint)
            }
            "cosmic" -> {
                ringPaint.strokeWidth = dp(2.0f)
                ringPaint.color = currentSecondaryColor
                val cosmicRect = RectF(cx - radius * 0.88f, cy - radius * 0.88f, cx + radius * 0.88f, cy + radius * 0.88f)
                canvas.drawArc(cosmicRect, time * 70f, 240f, false, ringPaint)

                corePaint.color = currentPrimaryColor
                corePaint.alpha = 210
                canvas.drawCircle(cx, cy, radius * 0.65f, corePaint)

                corePaint.color = Color.WHITE
                corePaint.alpha = 255
                canvas.drawCircle(cx, cy, radius * 0.22f, corePaint)
            }
            "minimal" -> {
                ringPaint.strokeWidth = dp(2.4f)
                ringPaint.color = currentPrimaryColor
                canvas.drawCircle(cx, cy, radius * 0.9f, ringPaint)

                ringPaint.strokeWidth = dp(1.8f)
                ringPaint.color = currentSecondaryColor
                canvas.drawCircle(cx, cy, radius * 0.6f, ringPaint)

                corePaint.color = currentPrimaryColor
                corePaint.alpha = 240
                canvas.drawCircle(cx, cy, radius * 0.28f, corePaint)
            }
            "pixel" -> {
                val step = dp(4f)
                corePaint.color = currentPrimaryColor
                val halfSteps = 4
                for (x in -halfSteps..halfSteps) {
                    for (y in -halfSteps..halfSteps) {
                        val distSq = x * x + y * y
                        if (distSq <= 14) {
                            corePaint.color = if (distSq <= 4) currentSecondaryColor else currentPrimaryColor
                            val px = cx + x * step
                            val py = cy + y * step
                            canvas.drawRect(px - step * 0.42f, py - step * 0.42f, px + step * 0.42f, py + step * 0.42f, corePaint)
                        }
                    }
                }
            }
            "classic" -> {
                corePaint.color = currentPrimaryColor
                corePaint.alpha = 220
                canvas.drawCircle(cx, cy, radius * 0.72f, corePaint)

                ringPaint.strokeWidth = dp(2.6f)
                ringPaint.color = currentSecondaryColor
                val ringRect = RectF(cx - radius * 0.96f, cy - radius * 0.96f, cx + radius * 0.96f, cy + radius * 0.96f)
                canvas.drawArc(ringRect, time * 45f, 270f, false, ringPaint)

                corePaint.color = Color.WHITE
                corePaint.alpha = 240
                canvas.drawCircle(cx, cy, radius * 0.28f, corePaint)
            }
            else -> {
                // "MetaMuse" - Official 3D Cyber-Cat Character Avatar (Default)
                drawMetaMuseAvatar(canvas, cx, cy, radius, time)
            }
        }
    }

    /**
     * Renders the Material Droid Character (Cute Android Bot with Antennas & LED eyes)
     */
    private fun drawDroidAvatar(canvas: Canvas, cx: Float, cy: Float, r: Float, time: Float) {
        val headY = cy + dp(2f)
        val domeRadius = r * 0.72f

        // 1. Antennas with glowing tips
        strokePaint.strokeWidth = dp(2.6f)
        strokePaint.color = Color.parseColor("#94A3B8")
        strokePaint.strokeCap = Paint.Cap.ROUND

        val leftAntBaseX = cx - r * 0.30f
        val leftAntBaseY = headY - domeRadius * 0.72f
        val leftAntTipX = leftAntBaseX - dp(9f)
        val leftAntTipY = leftAntBaseY - dp(13f)
        canvas.drawLine(leftAntBaseX, leftAntBaseY, leftAntTipX, leftAntTipY, strokePaint)

        val rightAntBaseX = cx + r * 0.30f
        val rightAntBaseY = headY - domeRadius * 0.72f
        val rightAntTipX = rightAntBaseX + dp(9f)
        val rightAntTipY = rightAntBaseY - dp(13f)
        canvas.drawLine(rightAntBaseX, rightAntBaseY, rightAntTipX, rightAntTipY, strokePaint)

        // Antenna glowing tips (pulsing with state color)
        accentPaint.color = currentPrimaryColor
        accentPaint.alpha = 255
        val tipR = dp(3.2f) + (smoothedAudioLevel * dp(2f))
        canvas.drawCircle(leftAntTipX, leftAntTipY, tipR, accentPaint)
        canvas.drawCircle(rightAntTipX, rightAntTipY, tipR, accentPaint)

        accentPaint.alpha = 110
        canvas.drawCircle(leftAntTipX, leftAntTipY, tipR * 1.8f, accentPaint)
        canvas.drawCircle(rightAntTipX, rightAntTipY, tipR * 1.8f, accentPaint)

        // 2. Droid Dome Head
        val domePath = Path().apply {
            moveTo(cx - domeRadius, headY + domeRadius * 0.50f)
            lineTo(cx - domeRadius, headY - domeRadius * 0.10f)
            arcTo(RectF(cx - domeRadius, headY - domeRadius, cx + domeRadius, headY + domeRadius), 180f, 180f, false)
            lineTo(cx + domeRadius, headY + domeRadius * 0.50f)
            close()
        }

        headPaint.color = Color.parseColor("#D9E8FF") // Porcelain Blue-White Droid
        canvas.drawPath(domePath, headPaint)

        // 3. Curved Glass Visor Screen
        val visorRect = RectF(cx - domeRadius * 0.82f, headY - domeRadius * 0.30f, cx + domeRadius * 0.82f, headY + domeRadius * 0.35f)
        visorPaint.color = Color.parseColor("#0B132B")
        canvas.drawRoundRect(visorRect, dp(11f), dp(11f), visorPaint)

        strokePaint.strokeWidth = dp(1.2f)
        strokePaint.color = Color.argb(120, 148, 163, 184)
        canvas.drawRoundRect(visorRect, dp(11f), dp(11f), strokePaint)

        // 4. Expressive LED Eyes
        val eyeSpacing = domeRadius * 0.40f
        val eyeY = headY - dp(2f)
        val baseEyeW = dp(4.5f)
        val baseEyeH = dp(6.5f)

        val blinkCycle = (time * 1.5f) % 4.0f
        val isBlinking = blinkCycle > 3.82f
        val eyeScaleY = if (isBlinking) 0.12f else 1.0f

        eyePaint.color = currentPrimaryColor
        eyePaint.style = Paint.Style.FILL

        eyeGlowPaint.color = currentPrimaryColor
        eyeGlowPaint.alpha = 130

        when (currentState) {
            AvatarState.SUCCESS -> {
                mouthPaint.color = currentPrimaryColor
                mouthPaint.strokeWidth = dp(2.8f)
                val leftEyeArc = RectF(cx - eyeSpacing - baseEyeW, eyeY - baseEyeH, cx - eyeSpacing + baseEyeW, eyeY + baseEyeH)
                val rightEyeArc = RectF(cx + eyeSpacing - baseEyeW, eyeY - baseEyeH, cx + eyeSpacing + baseEyeW, eyeY + baseEyeH)
                canvas.drawArc(leftEyeArc, 180f, 180f, false, mouthPaint)
                canvas.drawArc(rightEyeArc, 180f, 180f, false, mouthPaint)
            }
            AvatarState.FAILED -> {
                val leftEyeRect = RectF(cx - eyeSpacing - baseEyeW, eyeY - baseEyeH * 0.5f, cx - eyeSpacing + baseEyeW, eyeY + baseEyeH * 0.5f)
                val rightEyeRect = RectF(cx + eyeSpacing - baseEyeW, eyeY - baseEyeH * 0.5f, cx + eyeSpacing + baseEyeW, eyeY + baseEyeH * 0.5f)
                canvas.drawRoundRect(leftEyeRect, dp(2f), dp(2f), eyePaint)
                canvas.drawRoundRect(rightEyeRect, dp(2f), dp(2f), eyePaint)
            }
            AvatarState.PAUSED -> {
                val leftEyeRect = RectF(cx - eyeSpacing - baseEyeW * 1.1f, eyeY - dp(1f), cx - eyeSpacing + baseEyeW * 1.1f, eyeY + dp(1f))
                val rightEyeRect = RectF(cx + eyeSpacing - baseEyeW * 1.1f, eyeY - dp(1f), cx + eyeSpacing + baseEyeW * 1.1f, eyeY + dp(1f))
                canvas.drawRoundRect(leftEyeRect, dp(1f), dp(1f), eyePaint)
                canvas.drawRoundRect(rightEyeRect, dp(1f), dp(1f), eyePaint)
            }
            else -> {
                val currentEyeH = (baseEyeH * eyeScaleY) + (if (currentState == AvatarState.SPEAKING) smoothedAudioLevel * dp(4f) else 0f)
                val leftEyeRect = RectF(cx - eyeSpacing - baseEyeW, eyeY - currentEyeH, cx - eyeSpacing + baseEyeW, eyeY + currentEyeH)
                val rightEyeRect = RectF(cx + eyeSpacing - baseEyeW, eyeY - currentEyeH, cx + eyeSpacing + baseEyeW, eyeY + currentEyeH)

                canvas.drawRoundRect(RectF(leftEyeRect.left - dp(2f), leftEyeRect.top - dp(2f), leftEyeRect.right + dp(2f), leftEyeRect.bottom + dp(2f)), dp(4f), dp(4f), eyeGlowPaint)
                canvas.drawRoundRect(RectF(rightEyeRect.left - dp(2f), rightEyeRect.top - dp(2f), rightEyeRect.right + dp(2f), rightEyeRect.bottom + dp(2f)), dp(4f), dp(4f), eyeGlowPaint)

                canvas.drawRoundRect(leftEyeRect, dp(3f), dp(3f), eyePaint)
                canvas.drawRoundRect(rightEyeRect, dp(3f), dp(3f), eyePaint)

                if (!isBlinking) {
                    eyePaint.color = Color.WHITE
                    eyePaint.alpha = 230
                    canvas.drawCircle(cx - eyeSpacing + dp(1.2f), eyeY - dp(1.5f), dp(1.2f), eyePaint)
                    canvas.drawCircle(cx + eyeSpacing + dp(1.2f), eyeY - dp(1.5f), dp(1.2f), eyePaint)
                    eyePaint.alpha = 255
                }
            }
        }

        // 5. Smile Mouth
        mouthPaint.color = Color.parseColor("#475569")
        mouthPaint.strokeWidth = dp(1.8f)
        val mouthRect = RectF(cx - dp(6.5f), headY + domeRadius * 0.12f, cx + dp(6.5f), headY + domeRadius * 0.32f)
        canvas.drawArc(mouthRect, 20f, 140f, false, mouthPaint)
    }

    private fun drawMechAvatar(canvas: Canvas, cx: Float, cy: Float, r: Float, time: Float) {
        drawDroidAvatar(canvas, cx, cy, r, time)
        // Add armored plating shoulders and visor grid
        strokePaint.color = Color.parseColor("#475569")
        strokePaint.strokeWidth = dp(2f)
        canvas.drawLine(cx - r * 0.8f, cy + r * 0.4f, cx - r * 0.4f, cy + r * 0.4f, strokePaint)
        canvas.drawLine(cx + r * 0.4f, cy + r * 0.4f, cx + r * 0.8f, cy + r * 0.4f, strokePaint)
    }

    private fun drawCatAvatar(canvas: Canvas, cx: Float, cy: Float, r: Float, time: Float) {
        val bobY = cy + sin(time * 3.5).toFloat() * dp(2f)
        val headR = r * 0.72f

        // Ears
        val earPath = Path().apply {
            moveTo(cx - headR * 0.8f, bobY - headR * 0.2f)
            lineTo(cx - headR * 0.85f, bobY - headR * 0.95f)
            lineTo(cx - headR * 0.25f, bobY - headR * 0.6f)
            close()
            moveTo(cx + headR * 0.8f, bobY - headR * 0.2f)
            lineTo(cx + headR * 0.85f, bobY - headR * 0.95f)
            lineTo(cx + headR * 0.25f, bobY - headR * 0.6f)
            close()
        }
        headPaint.color = Color.parseColor("#F47B24")
        canvas.drawPath(earPath, headPaint)

        // Inner pink ears
        val innerEar = Path().apply {
            moveTo(cx - headR * 0.72f, bobY - headR * 0.25f)
            lineTo(cx - headR * 0.78f, bobY - headR * 0.82f)
            lineTo(cx - headR * 0.32f, bobY - headR * 0.55f)
            close()
            moveTo(cx + headR * 0.72f, bobY - headR * 0.25f)
            lineTo(cx + headR * 0.78f, bobY - headR * 0.82f)
            lineTo(cx + headR * 0.32f, bobY - headR * 0.55f)
            close()
        }
        accentPaint.color = Color.parseColor("#FFC462")
        canvas.drawPath(innerEar, accentPaint)

        // Head
        headPaint.color = Color.parseColor("#F47B24")
        canvas.drawCircle(cx, bobY, headR, headPaint)

        // Cheeks
        accentPaint.color = Color.argb(120, 255, 100, 150)
        canvas.drawCircle(cx - headR * 0.55f, bobY + dp(4f), dp(4.5f), accentPaint)
        canvas.drawCircle(cx + headR * 0.55f, bobY + dp(4f), dp(4.5f), accentPaint)

        // Whiskers
        strokePaint.color = Color.parseColor("#1E293B")
        strokePaint.strokeWidth = dp(1.4f)
        canvas.drawLine(cx - headR * 0.5f, bobY + dp(2f), cx - headR * 0.95f, bobY + dp(1f), strokePaint)
        canvas.drawLine(cx - headR * 0.5f, bobY + dp(5f), cx - headR * 0.95f, bobY + dp(7f), strokePaint)
        canvas.drawLine(cx + headR * 0.5f, bobY + dp(2f), cx + headR * 0.95f, bobY + dp(1f), strokePaint)
        canvas.drawLine(cx + headR * 0.5f, bobY + dp(5f), cx + headR * 0.95f, bobY + dp(7f), strokePaint)

        // Cute animated eyes & mouth
        drawCuteFace(canvas, cx, bobY - dp(1f), headR * 0.42f, time)
    }

    private fun drawGhostAvatar(canvas: Canvas, cx: Float, cy: Float, r: Float, time: Float) {
        val bobY = cy + sin(time * 3.0).toFloat() * dp(3.5f)
        val ghostR = r * 0.75f

        val ghostPath = Path().apply {
            moveTo(cx - ghostR, bobY + ghostR * 0.5f)
            arcTo(RectF(cx - ghostR, bobY - ghostR, cx + ghostR, bobY + ghostR), 180f, 180f, false)
            lineTo(cx + ghostR, bobY + ghostR * 0.6f)
            // Undulating wavy hem
            val wave = sin(time * 6.0).toFloat() * dp(2f)
            quadTo(cx + ghostR * 0.6f, bobY + ghostR * 0.8f + wave, cx + ghostR * 0.3f, bobY + ghostR * 0.55f)
            quadTo(cx, bobY + ghostR * 0.85f - wave, cx - ghostR * 0.3f, bobY + ghostR * 0.55f)
            quadTo(cx - ghostR * 0.6f, bobY + ghostR * 0.8f + wave, cx - ghostR, bobY + ghostR * 0.6f)
            close()
        }

        headPaint.color = Color.parseColor("#F1F5F9")
        canvas.drawPath(ghostPath, headPaint)

        // Glowing aura
        strokePaint.color = Color.argb(80, 200, 225, 255)
        strokePaint.strokeWidth = dp(2.5f)
        canvas.drawPath(ghostPath, strokePaint)

        // Pink blush
        accentPaint.color = Color.argb(140, 255, 140, 180)
        canvas.drawCircle(cx - ghostR * 0.45f, bobY + dp(4f), dp(4f), accentPaint)
        canvas.drawCircle(cx + ghostR * 0.45f, bobY + dp(4f), dp(4f), accentPaint)

        drawCuteFace(canvas, cx, bobY - dp(3f), ghostR * 0.38f, time)
    }

    private fun drawAlienAvatar(canvas: Canvas, cx: Float, cy: Float, r: Float, time: Float) {
        val bobY = cy + sin(time * 3.2).toFloat() * dp(2.2f)
        val headR = r * 0.72f

        // Antenna
        val antX = cx + sin(time * 4.0).toFloat() * dp(3f)
        strokePaint.color = Color.parseColor("#5A9E1E")
        strokePaint.strokeWidth = dp(2.2f)
        canvas.drawLine(cx, bobY - headR * 0.8f, antX, bobY - headR * 1.35f, strokePaint)

        // Glowing tip
        accentPaint.color = currentPrimaryColor
        canvas.drawCircle(antX, bobY - headR * 1.35f, dp(4f) + smoothedAudioLevel * dp(3f), accentPaint)

        // Alien Head (inverted teardrop / oval)
        val headPath = Path().apply {
            val oval = RectF(cx - headR, bobY - headR * 0.85f, cx + headR, bobY + headR * 0.95f)
            addOval(oval, Path.Direction.CW)
        }
        headPaint.color = Color.parseColor("#70D920")
        canvas.drawPath(headPath, headPaint)

        // Huge Glossy Alien Eyes
        val eyeSpacing = headR * 0.45f
        val eyeY = bobY - dp(1f)
        val eyeW = dp(6.5f)
        val eyeH = dp(9.5f)

        canvas.save()
        canvas.rotate(-12f, cx - eyeSpacing, eyeY)
        visorPaint.color = Color.parseColor("#0F172A")
        canvas.drawOval(RectF(cx - eyeSpacing - eyeW, eyeY - eyeH, cx - eyeSpacing + eyeW, eyeY + eyeH), visorPaint)
        accentPaint.color = Color.WHITE
        canvas.drawCircle(cx - eyeSpacing + dp(1.5f), eyeY - dp(3f), dp(2f), accentPaint)
        canvas.restore()

        canvas.save()
        canvas.rotate(12f, cx + eyeSpacing, eyeY)
        canvas.drawOval(RectF(cx + eyeSpacing - eyeW, eyeY - eyeH, cx + eyeSpacing + eyeW, eyeY + eyeH), visorPaint)
        canvas.drawCircle(cx + eyeSpacing - dp(1.5f), eyeY - dp(3f), dp(2f), accentPaint)
        canvas.restore()

        drawCharacterMouth(canvas, cx, bobY + headR * 0.42f, headR)
    }

    private fun drawFlowerAvatar(canvas: Canvas, cx: Float, cy: Float, r: Float, time: Float) {
        val rot = time * 20f
        val flowerR = r * 0.72f
        val petalR = flowerR * 0.42f

        canvas.save()
        canvas.rotate(rot, cx, cy)
        headPaint.color = Color.parseColor("#A6FF72")
        for (i in 0 until 8) {
            val angle = Math.toRadians((i * 45.0))
            val px = cx + (cos(angle) * flowerR * 0.65f).toFloat()
            val py = cy + (sin(angle) * flowerR * 0.65f).toFloat()
            canvas.drawCircle(px, py, petalR, headPaint)
        }
        canvas.restore()

        // Flower Center
        accentPaint.color = Color.parseColor("#20CF70")
        canvas.drawCircle(cx, cy, flowerR * 0.55f, accentPaint)

        drawCuteFace(canvas, cx, cy, flowerR * 0.32f, time)
    }

    private fun drawStarAvatar(canvas: Canvas, cx: Float, cy: Float, r: Float, time: Float) {
        val pulse = 1f + sin(time * 3.5).toFloat() * 0.06f
        val starR = r * 0.78f * pulse

        val path = Path()
        val points = 5
        val innerR = starR * 0.45f
        for (i in 0 until points * 2) {
            val radiusPoint = if (i % 2 == 0) starR else innerR
            val angle = Math.toRadians((i * 36.0) - 90.0)
            val px = cx + (cos(angle) * radiusPoint).toFloat()
            val py = cy + (sin(angle) * radiusPoint).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()

        headPaint.color = Color.parseColor("#F1CE48")
        canvas.drawPath(path, headPaint)

        strokePaint.color = Color.parseColor("#FFF39A")
        strokePaint.strokeWidth = dp(2f)
        canvas.drawPath(path, strokePaint)

        drawCuteFace(canvas, cx, cy + dp(1f), starR * 0.3f, time)
    }

    private fun drawCloudAvatar(canvas: Canvas, cx: Float, cy: Float, r: Float, time: Float) {
        val bobY = cy + sin(time * 2.8).toFloat() * dp(2.5f)
        val cloudR = r * 0.68f

        headPaint.color = Color.parseColor("#EDF5FF")
        canvas.drawCircle(cx, bobY, cloudR * 0.75f, headPaint)
        canvas.drawCircle(cx - cloudR * 0.55f, bobY + dp(2f), cloudR * 0.55f, headPaint)
        canvas.drawCircle(cx + cloudR * 0.55f, bobY + dp(2f), cloudR * 0.55f, headPaint)
        canvas.drawRoundRect(RectF(cx - cloudR * 0.8f, bobY + cloudR * 0.2f, cx + cloudR * 0.8f, bobY + cloudR * 0.8f), dp(8f), dp(8f), headPaint)

        accentPaint.color = Color.argb(110, 169, 189, 219)
        canvas.drawCircle(cx - cloudR * 0.45f, bobY + dp(6f), dp(3.5f), accentPaint)
        canvas.drawCircle(cx + cloudR * 0.45f, bobY + dp(6f), dp(3.5f), accentPaint)

        drawCuteFace(canvas, cx, bobY + dp(1f), cloudR * 0.36f, time)
    }

    private fun drawDropAvatar(canvas: Canvas, cx: Float, cy: Float, r: Float, time: Float) {
        val bobY = cy + sin(time * 3.4).toFloat() * dp(2f)
        val dropR = r * 0.72f

        val dropPath = Path().apply {
            moveTo(cx, bobY - dropR * 1.05f)
            quadTo(cx + dropR * 0.9f, bobY, cx + dropR * 0.75f, bobY + dropR * 0.65f)
            arcTo(RectF(cx - dropR * 0.75f, bobY - dropR * 0.1f, cx + dropR * 0.75f, bobY + dropR * 0.9f), 0f, 180f, false)
            quadTo(cx - dropR * 0.9f, bobY, cx, bobY - dropR * 1.05f)
            close()
        }

        headPaint.color = Color.parseColor("#08C7C0")
        canvas.drawPath(dropPath, headPaint)

        // Gloss highlight
        accentPaint.color = Color.argb(180, 255, 255, 255)
        canvas.drawCircle(cx - dropR * 0.28f, bobY - dropR * 0.15f, dp(3f), accentPaint)

        drawCuteFace(canvas, cx, bobY + dp(4f), dropR * 0.35f, time)
    }

    private fun drawBlobAvatar(canvas: Canvas, cx: Float, cy: Float, r: Float, time: Float) {
        val bobY = cy + sin(time * 3.6).toFloat() * dp(2f)
        val blobR = r * 0.72f

        val path = Path()
        val points = 8
        for (i in 0 until points) {
            val angle = Math.toRadians(i * (360.0 / points))
            val wobble = sin(time * 4.0 + i * 1.5).toFloat() * dp(3.5f)
            val rad = blobR + wobble
            val px = cx + (cos(angle) * rad).toFloat()
            val py = bobY + (sin(angle) * rad).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()

        headPaint.color = Color.parseColor("#04BD70")
        canvas.drawPath(path, headPaint)

        drawCuteFace(canvas, cx, bobY, blobR * 0.36f, time)
    }

    private fun drawPebbleAvatar(canvas: Canvas, cx: Float, cy: Float, r: Float, time: Float) {
        val pebbleR = r * 0.72f
        val rect = RectF(cx - pebbleR * 1.05f, cy - pebbleR * 0.8f, cx + pebbleR * 1.05f, cy + pebbleR * 0.8f)
        headPaint.color = Color.parseColor("#9DCF69")
        canvas.drawRoundRect(rect, dp(18f), dp(18f), headPaint)

        drawCuteFace(canvas, cx, cy, pebbleR * 0.38f, time)
    }

    private fun drawPillAvatar(canvas: Canvas, cx: Float, cy: Float, r: Float, time: Float) {
        val pillW = r * 0.58f
        val pillH = r * 0.88f
        val rect = RectF(cx - pillW, cy - pillH, cx + pillW, cy + pillH)

        headPaint.color = Color.parseColor("#8885D8")
        canvas.drawRoundRect(rect, pillW, pillW, headPaint)

        // Split top color
        val topRect = RectF(cx - pillW, cy - pillH, cx + pillW, cy)
        canvas.save()
        canvas.clipRect(topRect)
        accentPaint.color = Color.parseColor("#D0C9FF")
        canvas.drawRoundRect(rect, pillW, pillW, accentPaint)
        canvas.restore()

        drawCuteFace(canvas, cx, cy, pillW * 0.6f, time)
    }

    private fun drawPuddleAvatar(canvas: Canvas, cx: Float, cy: Float, r: Float, time: Float) {
        val puddleR = r * 0.82f
        val rect = RectF(cx - puddleR, cy - puddleR * 0.45f, cx + puddleR, cy + puddleR * 0.45f)
        headPaint.color = Color.parseColor("#ED69BB")
        canvas.drawOval(rect, headPaint)

        // Center droplet
        accentPaint.color = Color.parseColor("#FFA7DC")
        canvas.drawCircle(cx, cy - dp(4f), dp(12f) + smoothedAudioLevel * dp(4f), accentPaint)

        drawCuteFace(canvas, cx, cy - dp(4f), dp(8f), time)
    }

    private fun drawHexagonAvatar(canvas: Canvas, cx: Float, cy: Float, r: Float, time: Float) {
        val hexR = r * 0.75f
        val path = Path()
        for (i in 0 until 6) {
            val angle = Math.toRadians((i * 60.0) + 30.0)
            val px = cx + (cos(angle) * hexR).toFloat()
            val py = cy + (sin(angle) * hexR).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()

        headPaint.color = Color.parseColor("#ED4847")
        canvas.drawPath(path, headPaint)

        drawCuteFace(canvas, cx, cy, hexR * 0.35f, time)
    }

    private fun drawTriangleAvatar(canvas: Canvas, cx: Float, cy: Float, r: Float, time: Float) {
        val triR = r * 0.78f
        val path = Path()
        for (i in 0 until 3) {
            val angle = Math.toRadians((i * 120.0) - 90.0)
            val px = cx + (cos(angle) * triR).toFloat()
            val py = cy + (sin(angle) * triR).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()

        headPaint.color = Color.parseColor("#B438D4")
        canvas.drawPath(path, headPaint)

        drawCuteFace(canvas, cx, cy + dp(4f), triR * 0.3f, time)
    }

    private fun drawCircleAvatar(canvas: Canvas, cx: Float, cy: Float, r: Float, time: Float) {
        val circR = r * 0.75f
        headPaint.color = Color.parseColor("#862DEB")
        canvas.drawCircle(cx, cy, circR, headPaint)

        drawCuteFace(canvas, cx, cy, circR * 0.38f, time)
    }

    private fun drawSquareAvatar(canvas: Canvas, cx: Float, cy: Float, r: Float, time: Float) {
        val sqR = r * 0.7f
        val rect = RectF(cx - sqR, cy - sqR, cx + sqR, cy + sqR)
        headPaint.color = Color.parseColor("#21A9DC")
        canvas.drawRoundRect(rect, dp(12f), dp(12f), headPaint)

        drawCuteFace(canvas, cx, cy, sqR * 0.42f, time)
    }

    private fun drawCuteFace(canvas: Canvas, cx: Float, cy: Float, eyeSpacing: Float, time: Float) {
        val blinkCycle = (time * 1.4f) % 4.0f
        val isBlinking = blinkCycle > 3.82f

        val eyeY = cy - dp(2f)
        val eyeW = dp(3.2f)
        val eyeH = if (isBlinking) dp(0.6f) else dp(4.5f)

        eyePaint.color = Color.parseColor("#1E293B")
        eyePaint.style = Paint.Style.FILL

        when (currentState) {
            AvatarState.SUCCESS -> {
                // Happy curved arcs (^_^)
                mouthPaint.color = Color.parseColor("#1E293B")
                mouthPaint.strokeWidth = dp(2.2f)
                canvas.drawArc(RectF(cx - eyeSpacing - eyeW, eyeY - dp(3f), cx - eyeSpacing + eyeW, eyeY + dp(3f)), 180f, 180f, false, mouthPaint)
                canvas.drawArc(RectF(cx + eyeSpacing - eyeW, eyeY - dp(3f), cx + eyeSpacing + eyeW, eyeY + dp(3f)), 180f, 180f, false, mouthPaint)
            }
            AvatarState.FAILED -> {
                // X X eyes
                strokePaint.color = Color.parseColor("#FF1744")
                strokePaint.strokeWidth = dp(2f)
                val s = dp(3f)
                canvas.drawLine(cx - eyeSpacing - s, eyeY - s, cx - eyeSpacing + s, eyeY + s, strokePaint)
                canvas.drawLine(cx - eyeSpacing - s, eyeY + s, cx - eyeSpacing + s, eyeY - s, strokePaint)
                canvas.drawLine(cx + eyeSpacing - s, eyeY - s, cx + eyeSpacing + s, eyeY + s, strokePaint)
                canvas.drawLine(cx + eyeSpacing - s, eyeY + s, cx + eyeSpacing + s, eyeY - s, strokePaint)
            }
            else -> {
                canvas.drawOval(RectF(cx - eyeSpacing - eyeW, eyeY - eyeH, cx - eyeSpacing + eyeW, eyeY + eyeH), eyePaint)
                canvas.drawOval(RectF(cx + eyeSpacing - eyeW, eyeY - eyeH, cx + eyeSpacing + eyeW, eyeY + eyeH), eyePaint)

                if (!isBlinking) {
                    eyePaint.color = Color.WHITE
                    canvas.drawCircle(cx - eyeSpacing + dp(0.9f), eyeY - dp(1.2f), dp(1.1f), eyePaint)
                    canvas.drawCircle(cx + eyeSpacing + dp(0.9f), eyeY - dp(1.2f), dp(1.1f), eyePaint)
                }
            }
        }

        drawCharacterMouth(canvas, cx, cy + dp(7f), eyeSpacing)
    }

    /**
     * Renders the MetaMuse 3D Cyber-Cat Character:
     * - Pearlescent ceramic head & ears with neon glowing accent pads
     * - Dark glass visor screen with reflective rim
     * - Expressive LED eyes (pill/oval with pupil, blinking & state expressions)
     * - Dynamic mouth synced with voice speech
     * - Orbiting energy rings (reconnecting/executing) & status badges (success, paused, failed)
     */
    private fun drawMetaMuseAvatar(canvas: Canvas, cx: Float, cy: Float, r: Float, time: Float) {
        val earW = r * 0.42f
        val earH = r * 0.52f
        val headY = cy + dp(1f)

        // 1. Cyber-Cat Outer Ears
        earLeftPath.reset()
        earLeftPath.moveTo(cx - r * 0.65f, headY - r * 0.25f)
        earLeftPath.lineTo(cx - r * 0.72f, headY - r * 0.85f)
        earLeftPath.lineTo(cx - r * 0.22f, headY - r * 0.55f)
        earLeftPath.close()

        earRightPath.reset()
        earRightPath.moveTo(cx + r * 0.65f, headY - r * 0.25f)
        earRightPath.lineTo(cx + r * 0.72f, headY - r * 0.85f)
        earRightPath.lineTo(cx + r * 0.22f, headY - r * 0.55f)
        earRightPath.close()

        headPaint.color = Color.parseColor("#E6EDF5")
        canvas.drawPath(earLeftPath, headPaint)
        canvas.drawPath(earRightPath, headPaint)

        // Glowing inner ear accent pads (tinted with Primary HEX)
        innerEarLeftPath.reset()
        innerEarLeftPath.moveTo(cx - r * 0.58f, headY - r * 0.32f)
        innerEarLeftPath.lineTo(cx - r * 0.64f, headY - r * 0.75f)
        innerEarLeftPath.lineTo(cx - r * 0.28f, headY - r * 0.52f)
        innerEarLeftPath.close()

        innerEarRightPath.reset()
        innerEarRightPath.moveTo(cx + r * 0.58f, headY - r * 0.32f)
        innerEarRightPath.lineTo(cx + r * 0.64f, headY - r * 0.75f)
        innerEarRightPath.lineTo(cx + r * 0.28f, headY - r * 0.52f)
        innerEarRightPath.close()

        accentPaint.color = currentPrimaryColor
        accentPaint.alpha = 210
        canvas.drawPath(innerEarLeftPath, accentPaint)
        canvas.drawPath(innerEarRightPath, accentPaint)

        // 2. Ceramic Head (Smooth rounded capsule / squircle)
        val headRect = RectF(cx - r * 0.78f, headY - r * 0.62f, cx + r * 0.78f, headY + r * 0.68f)
        headPaint.shader = LinearGradient(
            cx, headY - r * 0.62f, cx, headY + r * 0.68f,
            Color.WHITE, Color.parseColor("#CBD5E1"),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(headRect, r * 0.48f, r * 0.48f, headPaint)
        headPaint.shader = null

        // Ceramic head border
        strokePaint.color = Color.parseColor("#94A3B8")
        strokePaint.strokeWidth = dp(1.2f)
        canvas.drawRoundRect(headRect, r * 0.48f, r * 0.48f, strokePaint)

        // 3. Dark Curved Glass Visor Screen
        val visorRect = RectF(cx - r * 0.58f, headY - r * 0.28f, cx + r * 0.58f, headY + r * 0.25f)
        visorPaint.color = Color.parseColor("#070B14")
        canvas.drawRoundRect(visorRect, r * 0.26f, r * 0.26f, visorPaint)

        // Visor border rim
        strokePaint.color = Color.argb(120, Color.red(currentSecondaryColor), Color.green(currentSecondaryColor), Color.blue(currentSecondaryColor))
        strokePaint.strokeWidth = dp(1.4f)
        canvas.drawRoundRect(visorRect, r * 0.26f, r * 0.26f, strokePaint)

        // Visor top reflection arc
        strokePaint.color = Color.argb(60, 255, 255, 255)
        strokePaint.strokeWidth = dp(1.0f)
        canvas.drawArc(
            RectF(visorRect.left + dp(4f), visorRect.top + dp(2f), visorRect.right - dp(4f), visorRect.top + dp(10f)),
            190f, 160f, false, strokePaint
        )

        // 4. Expressive LED Eyes inside Visor
        drawVisorEyes(canvas, cx, headY - r * 0.02f, r, time)

        // 5. Expressive Mouth below Visor
        drawCharacterMouth(canvas, cx, headY + r * 0.42f, r)

        // 6. Orbital Rings during Reconnecting or Executing
        if (currentState == AvatarState.RECONNECTING || currentState == AvatarState.EXECUTING) {
            ringPaint.strokeWidth = dp(1.8f)
            ringPaint.color = currentPrimaryColor
            val orbitRect = RectF(cx - r * 1.15f, headY - r * 0.45f, cx + r * 1.15f, headY + r * 0.45f)
            canvas.save()
            canvas.rotate(time * 65f, cx, headY)
            canvas.drawOval(orbitRect, ringPaint)

            // Orbiting particle on the ring
            particlePaint.color = currentSecondaryColor
            particlePaint.alpha = 255
            canvas.drawCircle(cx + r * 1.15f, headY, dp(3f), particlePaint)
            canvas.restore()
        }

        // 7. Mini Status Corner Badge (Success checkmark, Paused bars, Failed exclamation)
        drawCornerBadge(canvas, cx + r * 0.65f, headY - r * 0.55f, dp(8f))
    }

    private fun drawVisorEyes(canvas: Canvas, cx: Float, eyeCenterY: Float, r: Float, time: Float) {
        val eyeSpacing = r * 0.28f
        val leftEyeX = cx - eyeSpacing
        val rightEyeX = cx + eyeSpacing

        // Blink calculation: periodic 180ms blink every 4 seconds
        val blinkPeriod = 4.0
        val blinkTime = time % blinkPeriod
        val isBlinking = blinkTime < 0.18

        eyePaint.color = currentPrimaryColor
        eyeGlowPaint.color = Color.WHITE

        when (currentState) {
            AvatarState.PAUSED, AvatarState.ENDED -> {
                // Sleeping / Resting horizontal LED slit
                strokePaint.color = currentPrimaryColor
                strokePaint.strokeWidth = dp(2.2f)
                val slitW = r * 0.12f
                canvas.drawLine(leftEyeX - slitW, eyeCenterY, leftEyeX + slitW, eyeCenterY, strokePaint)
                canvas.drawLine(rightEyeX - slitW, eyeCenterY, rightEyeX + slitW, eyeCenterY, strokePaint)
            }
            AvatarState.SUCCESS -> {
                // Happy curved arch eyes ^ ^
                strokePaint.color = currentPrimaryColor
                strokePaint.strokeWidth = dp(2.4f)
                val archRectL = RectF(leftEyeX - r * 0.11f, eyeCenterY - r * 0.10f, leftEyeX + r * 0.11f, eyeCenterY + r * 0.10f)
                val archRectR = RectF(rightEyeX - r * 0.11f, eyeCenterY - r * 0.10f, rightEyeX + r * 0.11f, eyeCenterY + r * 0.10f)
                canvas.drawArc(archRectL, 180f, 180f, false, strokePaint)
                canvas.drawArc(archRectR, 180f, 180f, false, strokePaint)
            }
            AvatarState.FAILED -> {
                // Alert diagonal cross / distressed slits \ /
                strokePaint.color = currentPrimaryColor
                strokePaint.strokeWidth = dp(2.4f)
                val xW = r * 0.10f
                canvas.drawLine(leftEyeX - xW, eyeCenterY - xW, leftEyeX + xW, eyeCenterY + xW, strokePaint)
                canvas.drawLine(leftEyeX + xW, eyeCenterY - xW, leftEyeX - xW, eyeCenterY + xW, strokePaint)
                canvas.drawLine(rightEyeX - xW, eyeCenterY - xW, rightEyeX + xW, eyeCenterY + xW, strokePaint)
                canvas.drawLine(rightEyeX + xW, eyeCenterY - xW, rightEyeX - xW, eyeCenterY + xW, strokePaint)
            }
            AvatarState.THINKING -> {
                // Shifting horizontal scanning gaze
                val gazeShift = sin(time * 6.0).toFloat() * (r * 0.08f)
                val eyeW = r * 0.13f
                val eyeH = r * 0.18f
                val rectL = RectF(leftEyeX + gazeShift - eyeW, eyeCenterY - eyeH, leftEyeX + gazeShift + eyeW, eyeCenterY + eyeH)
                val rectR = RectF(rightEyeX + gazeShift - eyeW, eyeCenterY - eyeH, rightEyeX + gazeShift + eyeW, eyeCenterY + eyeH)
                canvas.drawRoundRect(rectL, eyeW, eyeW, eyePaint)
                canvas.drawRoundRect(rectR, eyeW, eyeW, eyePaint)
                // Center core
                canvas.drawCircle(leftEyeX + gazeShift, eyeCenterY, eyeW * 0.45f, eyeGlowPaint)
                canvas.drawCircle(rightEyeX + gazeShift, eyeCenterY, eyeW * 0.45f, eyeGlowPaint)
            }
            else -> {
                // Normal / Idle / Listening / Speaking / Executing pill LED eyes
                val baseEyeW = r * 0.12f
                val baseEyeH = if (isBlinking) dp(1f) else (r * 0.20f + (smoothedAudioLevel * r * 0.08f))

                val rectL = RectF(leftEyeX - baseEyeW, eyeCenterY - baseEyeH, leftEyeX + baseEyeW, eyeCenterY + baseEyeH)
                val rectR = RectF(rightEyeX - baseEyeW, eyeCenterY - baseEyeH, rightEyeX + baseEyeW, eyeCenterY + baseEyeH)

                canvas.drawRoundRect(rectL, baseEyeW, baseEyeW, eyePaint)
                canvas.drawRoundRect(rectR, baseEyeW, baseEyeW, eyePaint)

                if (!isBlinking) {
                    canvas.drawCircle(leftEyeX, eyeCenterY - baseEyeH * 0.2f, baseEyeW * 0.45f, eyeGlowPaint)
                    canvas.drawCircle(rightEyeX, eyeCenterY - baseEyeH * 0.2f, baseEyeW * 0.45f, eyeGlowPaint)
                }
            }
        }
    }

    private fun drawCharacterMouth(canvas: Canvas, cx: Float, mouthY: Float, r: Float) {
        mouthPaint.color = currentPrimaryColor
        mouthPaint.strokeWidth = dp(1.8f)

        when {
            currentState == AvatarState.SPEAKING || smoothedAudioLevel > 0.08f -> {
                // Reactive opening oval mouth synced with voice
                val mouthW = dp(4f) + (smoothedAudioLevel * dp(5f))
                val mouthH = dp(2f) + (smoothedAudioLevel * dp(7f))
                corePaint.color = currentPrimaryColor
                corePaint.alpha = 230
                canvas.drawOval(RectF(cx - mouthW, mouthY - mouthH, cx + mouthW, mouthY + mouthH), corePaint)
            }
            currentState == AvatarState.FAILED -> {
                // Downturned sad arc
                val sadRect = RectF(cx - dp(5f), mouthY - dp(1f), cx + dp(5f), mouthY + dp(6f))
                canvas.drawArc(sadRect, 180f, 180f, false, mouthPaint)
            }
            currentState == AvatarState.PAUSED || currentState == AvatarState.THINKING -> {
                // Neutral small straight line
                canvas.drawLine(cx - dp(3.5f), mouthY, cx + dp(3.5f), mouthY, mouthPaint)
            }
            else -> {
                // Cheerful smiling arc
                val smileRect = RectF(cx - dp(5.5f), mouthY - dp(5f), cx + dp(5.5f), mouthY + dp(3.5f))
                canvas.drawArc(smileRect, 10f, 160f, false, mouthPaint)
            }
        }
    }

    private fun drawCornerBadge(canvas: Canvas, badgeX: Float, badgeY: Float, radius: Float) {
        when (currentState) {
            AvatarState.SUCCESS -> {
                badgeIndicatorPaint.color = Color.parseColor("#00E676")
                canvas.drawCircle(badgeX, badgeY, radius, badgeIndicatorPaint)
                strokePaint.color = Color.WHITE
                strokePaint.strokeWidth = dp(1.6f)
                canvas.drawLine(badgeX - radius * 0.4f, badgeY, badgeX - radius * 0.1f, badgeY + radius * 0.35f, strokePaint)
                canvas.drawLine(badgeX - radius * 0.1f, badgeY + radius * 0.35f, badgeX + radius * 0.45f, badgeY - radius * 0.35f, strokePaint)
            }
            AvatarState.PAUSED -> {
                badgeIndicatorPaint.color = Color.parseColor("#FFB020")
                canvas.drawCircle(badgeX, badgeY, radius, badgeIndicatorPaint)
                strokePaint.color = Color.WHITE
                strokePaint.strokeWidth = dp(1.5f)
                canvas.drawLine(badgeX - dp(1.8f), badgeY - dp(3f), badgeX - dp(1.8f), badgeY + dp(3f), strokePaint)
                canvas.drawLine(badgeX + dp(1.8f), badgeY - dp(3f), badgeX + dp(1.8f), badgeY + dp(3f), strokePaint)
            }
            AvatarState.FAILED -> {
                badgeIndicatorPaint.color = Color.parseColor("#FF1744")
                canvas.drawCircle(badgeX, badgeY, radius, badgeIndicatorPaint)
                strokePaint.color = Color.WHITE
                strokePaint.strokeWidth = dp(1.8f)
                canvas.drawLine(badgeX, badgeY - dp(3.5f), badgeX, badgeY + dp(1f), strokePaint)
                canvas.drawCircle(badgeX, badgeY + dp(3f), dp(0.9f), strokePaint)
            }
            else -> Unit
        }
    }

    private fun drawStatusBadge(canvas: Canvas, cx: Float, bottomY: Float) {
        val text = currentState.displayName
        statusPaint.textSize = dp(9.5f)
        statusPaint.color = currentPrimaryColor

        val textWidth = statusPaint.measureText(text)
        val badgeW = textWidth + dp(14f)
        val badgeH = dp(15f)
        val badgeRect = RectF(cx - badgeW / 2f, bottomY - badgeH, cx + badgeW / 2f, bottomY)

        canvas.drawRoundRect(badgeRect, dp(7.5f), dp(7.5f), badgeBgPaint)
        canvas.drawText(text, cx, bottomY - dp(3.8f), statusPaint)
    }

    private fun dp(value: Float): Float = value * density
}
