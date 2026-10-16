package com.jarvis.assistant.ui.grok

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.jarvis.assistant.automation.session.AvatarState
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * 60 FPS Grok Bot & Cute Chibi Rendering Engine.
 * Provides two ultra-polished avatars:
 * 1. "Grok Bot" (Sleek, futuristic, high-end classy cyber visualizer with orbital particle physics & puffing glow)
 * 2. "Grok Chibi" (Adorable kawaii chibi cyber bot with sparkling anime eyes, cute antennas, blushing cheeks, and soft puffing breathing)
 */
object GrokBotDrawingEngine {

    enum class GrokBotState {
        IDLE, WORKING, SPEAKING;

        companion object {
            fun fromAvatarState(state: AvatarState): GrokBotState {
                return when (state) {
                    AvatarState.IDLE, AvatarState.PAUSED, AvatarState.DISCONNECTED, AvatarState.ENDED -> IDLE
                    AvatarState.THINKING, AvatarState.EXECUTING, AvatarState.VERIFYING,
                    AvatarState.CONNECTING, AvatarState.RECONNECTING, AvatarState.RETRYING -> WORKING
                    AvatarState.SPEAKING, AvatarState.LISTENING, AvatarState.SUCCESS, AvatarState.FAILED -> SPEAKING
                }
            }
        }
    }

    data class BotColorPalette(
        val primaryColor: Int,
        val secondaryColor: Int,
        val glowColor: Int
    )

    fun getPaletteForState(state: GrokBotState): BotColorPalette {
        return when (state) {
            GrokBotState.IDLE -> BotColorPalette(
                primaryColor = 0xFF00F0FF.toInt(),   // Neon Electric Cyan
                secondaryColor = 0xFF9D00FF.toInt(), // Deep Cyber Purple
                glowColor = 0x8000F0FF.toInt()       // 50% Alpha Neon Blue Glow
            )
            GrokBotState.WORKING -> BotColorPalette(
                primaryColor = 0xFFFF007F.toInt(),   // Neon Hot Pink
                secondaryColor = 0xFFFFFFFF.toInt(), // Pure White
                glowColor = 0x80FF007F.toInt()       // 50% Alpha Neon Pink Glow
            )
            GrokBotState.SPEAKING -> BotColorPalette(
                primaryColor = 0xFF00FF66.toInt(),   // Vivid Neon Emerald/Green
                secondaryColor = 0xFF00F0FF.toInt(), // Electric Cyan
                glowColor = 0x8000FF66.toInt()       // 50% Alpha Neon Green Glow
            )
        }
    }

    private val glowPaint by lazy { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val fillPaint by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL } }
    private val strokePaint by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE } }
    private val eyePaint by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL } }
    private val particlePaint by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL } }
    private val earPath by lazy { Path() }
    private val tempRect by lazy { RectF() }

    /**
     * Checks whether the specified style name belongs to the Grok Bot family.
     */
    fun isGrokFamilyStyle(style: String): Boolean {
        val s = style.lowercase(Locale.ROOT).trim()
        return s.startsWith("grok")
    }

    /**
     * Master render entrypoint for Grok Bot variants.
     */
    fun drawCuteOrGrokBot(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        state: GrokBotState,
        animTime: Float,
        audioLevel: Float = 0f,
        scaleFactor: Float = 1.0f,
        avatarStyle: String = "Grok Bot"
    ) {
        val s = avatarStyle.lowercase(Locale.ROOT).trim()
        if (s == "grok chibi" || s == "grokchibi") {
            drawGrokChibi(canvas, cx, cy, radius, state, animTime, audioLevel, scaleFactor)
        } else {
            drawGrokBot(canvas, cx, cy, radius, state, animTime, audioLevel, scaleFactor)
        }
    }

    /**
     * High-End Classy Grok Bot:
     * - Multi-layer radial neon glow aura
     * - Outer dynamic energy ring with orbital rotation
     * - Central glossy sphere core with 3D spherical shading and light reflection
     * - 6 orbiting trailing particles with physics-based puffing spring expansion
     */
    fun drawGrokBot(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        state: GrokBotState,
        animTime: Float,
        audioLevel: Float = 0f,
        scaleFactor: Float = 1.0f
    ) {
        val palette = getPaletteForState(state)

        val pulseFrequency = if (state == GrokBotState.WORKING) 7.2 else 2.6
        val pulseSine = (sin(animTime * pulseFrequency).toFloat() + 1f) / 2f
        val minScale = 0.88f
        val targetScale = if (state == GrokBotState.WORKING) 1.25f else 1.06f
        val pulseScale = minScale + (targetScale - minScale) * pulseSine + (audioLevel.coerceIn(0f, 1f) * 0.20f)

        val eyeSpeed = if (state == GrokBotState.WORKING) 4.2 else 2.0
        val eyeMoveOffset = sin(animTime * eyeSpeed).toFloat() * (7f * scaleFactor)

        val glowRadius = (radius * 1.85f * scaleFactor * pulseScale).coerceAtLeast(20f)

        // 1. Multi-Layer Radial Neon Glow Aura
        glowPaint.shader = RadialGradient(
            cx, cy, glowRadius,
            intArrayOf(palette.glowColor, Color.argb(40, Color.red(palette.primaryColor), Color.green(palette.primaryColor), Color.blue(palette.primaryColor)), Color.TRANSPARENT),
            floatArrayOf(0.15f, 0.65f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, glowRadius, glowPaint)

        canvas.save()
        canvas.scale(pulseScale, pulseScale, cx, cy)

        val headCenterX = cx + eyeMoveOffset
        val headCenterY = cy

        // 2. Classy Outer Energy Rings
        val ringRadius = (radius * 0.82f * scaleFactor).coerceAtLeast(14f)
        strokePaint.strokeWidth = 2.4f * scaleFactor
        strokePaint.color = palette.primaryColor
        strokePaint.alpha = 180
        tempRect.set(headCenterX - ringRadius, headCenterY - ringRadius, headCenterX + ringRadius, headCenterY + ringRadius)
        canvas.drawArc(tempRect, animTime * 60f, 280f, false, strokePaint)

        strokePaint.strokeWidth = 1.5f * scaleFactor
        strokePaint.color = palette.secondaryColor
        strokePaint.alpha = 130
        tempRect.set(headCenterX - ringRadius * 1.18f, headCenterY - ringRadius * 1.18f, headCenterX + ringRadius * 1.18f, headCenterY + ringRadius * 1.18f)
        canvas.drawArc(tempRect, -animTime * 45f, 210f, false, strokePaint)

        // 3. Central Core Glass Sphere with 3D gradient
        val centerDotRadius = (radius * 0.44f * scaleFactor).coerceAtLeast(8f)
        fillPaint.shader = RadialGradient(
            headCenterX - centerDotRadius * 0.35f, headCenterY - centerDotRadius * 0.35f,
            centerDotRadius * 1.4f,
            intArrayOf(Color.WHITE, palette.secondaryColor, palette.primaryColor),
            floatArrayOf(0.1f, 0.6f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(headCenterX, headCenterY, centerDotRadius, fillPaint)

        // Specular glass shine
        fillPaint.shader = null
        fillPaint.color = Color.WHITE
        fillPaint.alpha = 240
        canvas.drawCircle(headCenterX - centerDotRadius * 0.32f, headCenterY - centerDotRadius * 0.32f, centerDotRadius * 0.28f, fillPaint)

        // 4. 6 Surrounding Orbiting Puffing Particles with soft trailing glow
        val dotCount = 6
        val baseParticleRadius = (radius * 0.16f * scaleFactor).coerceAtLeast(3.5f)
        val baseDistance = radius * 0.95f * scaleFactor

        for (i in 0 until dotCount) {
            val angle = i * (360f / dotCount) + (animTime * 35f)
            val radian = Math.toRadians(angle.toDouble())

            val puffOffset = sin(pulseScale * 5.2f + i * 1.1f).toFloat() * (8f * scaleFactor)
            val distance = baseDistance + puffOffset

            val dotX = headCenterX + (distance * cos(radian)).toFloat()
            val dotY = headCenterY + (distance * sin(radian)).toFloat()
            val pRadius = baseParticleRadius * (0.85f + (sin(animTime * 5f + i).toFloat() * 0.20f))

            // Particle halo
            particlePaint.color = palette.primaryColor
            particlePaint.alpha = 100
            canvas.drawCircle(dotX, dotY, pRadius * 1.6f, particlePaint)

            // Particle bright core
            particlePaint.color = if (i % 2 == 0) palette.primaryColor else Color.WHITE
            particlePaint.alpha = 255
            canvas.drawCircle(dotX, dotY, pRadius, particlePaint)
        }

        canvas.restore()
    }

    /**
     * Grok Chibi: Ultra-cute kawaii cyber bot with:
     * - Soft rounded porcelain cyber head
     * - Big sparkling anime kawaii eyes with pupil highlights and blinking
     * - Blushing cheeks
     * - Micro-antennas with glowing neon LED bulbs
     * - Soft breathing puffing animation
     */
    fun drawGrokChibi(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        state: GrokBotState,
        animTime: Float,
        audioLevel: Float = 0f,
        scaleFactor: Float = 1.0f
    ) {
        val palette = getPaletteForState(state)
        val pulseScale = 0.92f + (sin(animTime * 3.2).toFloat() * 0.08f) + (audioLevel * 0.16f)
        val r = (radius * 0.76f * scaleFactor * pulseScale).coerceAtLeast(16f)

        // 1. Soft Warm Kawaii Glow Aura
        glowPaint.shader = RadialGradient(
            cx, cy, r * 2.2f,
            intArrayOf(palette.glowColor, Color.argb(45, Color.red(palette.primaryColor), Color.green(palette.primaryColor), Color.blue(palette.primaryColor)), Color.TRANSPARENT),
            floatArrayOf(0.2f, 0.7f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r * 2.2f, glowPaint)

        canvas.save()
        canvas.scale(pulseScale, pulseScale, cx, cy)

        val hx = cx
        val hy = cy + 2f * scaleFactor

        // 2. Cute Micro-Antennas with glowing pastel bulbs
        val antSway = sin(animTime * 3.5).toFloat() * 6f * scaleFactor
        strokePaint.strokeWidth = 2.4f * scaleFactor
        strokePaint.color = Color.parseColor("#94A3B8")
        strokePaint.strokeCap = Paint.Cap.ROUND

        // Left antenna
        val leftAntBaseX = hx - r * 0.40f
        val leftAntBaseY = hy - r * 0.70f
        val leftAntTipX = leftAntBaseX - r * 0.25f + antSway
        val leftAntTipY = leftAntBaseY - r * 0.45f
        canvas.drawLine(leftAntBaseX, leftAntBaseY, leftAntTipX, leftAntTipY, strokePaint)

        // Right antenna
        val rightAntBaseX = hx + r * 0.40f
        val rightAntBaseY = hy - r * 0.70f
        val rightAntTipX = rightAntBaseX + r * 0.25f + antSway
        val rightAntTipY = rightAntBaseY - r * 0.45f
        canvas.drawLine(rightAntBaseX, rightAntBaseY, rightAntTipX, rightAntTipY, strokePaint)

        // Glowing antenna tips
        val bulbR = r * 0.18f + (audioLevel * r * 0.08f)
        fillPaint.shader = null
        fillPaint.color = palette.primaryColor
        fillPaint.alpha = 130
        canvas.drawCircle(leftAntTipX, leftAntTipY, bulbR * 1.5f, fillPaint)
        canvas.drawCircle(rightAntTipX, rightAntTipY, bulbR * 1.5f, fillPaint)

        fillPaint.color = Color.WHITE
        fillPaint.alpha = 255
        canvas.drawCircle(leftAntTipX, leftAntTipY, bulbR, fillPaint)
        canvas.drawCircle(rightAntTipX, rightAntTipY, bulbR, fillPaint)

        // 3. Cute Chibi Head (Rounded pear shape with subtle porcelain shading)
        fillPaint.shader = LinearGradient(
            hx, hy - r, hx, hy + r,
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#E0E7FF"),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(hx, hy, r, fillPaint)

        // Head soft outline
        strokePaint.strokeWidth = 2.0f * scaleFactor
        strokePaint.color = Color.argb(120, Color.red(palette.primaryColor), Color.green(palette.primaryColor), Color.blue(palette.primaryColor))
        canvas.drawCircle(hx, hy, r, strokePaint)

        // 4. Big Sparkling Kawaii Anime Eyes
        val blinkCycle = (animTime * 1.3f) % 4.0f
        val isBlinking = blinkCycle > 3.82f

        val eyeSpacing = r * 0.38f
        val eyeY = hy - r * 0.04f
        val eyeW = r * 0.22f
        val eyeH = r * 0.28f

        if (isBlinking) {
            // Happy closed eye curved lines `^ ^`
            strokePaint.strokeWidth = 2.6f * scaleFactor
            strokePaint.color = Color.parseColor("#1E293B")
            tempRect.set(hx - eyeSpacing - eyeW, eyeY - eyeH * 0.4f, hx - eyeSpacing + eyeW, eyeY + eyeH * 0.4f)
            canvas.drawArc(tempRect, 190f, 160f, false, strokePaint)
            tempRect.set(hx + eyeSpacing - eyeW, eyeY - eyeH * 0.4f, hx + eyeSpacing + eyeW, eyeY + eyeH * 0.4f)
            canvas.drawArc(tempRect, 190f, 160f, false, strokePaint)
        } else {
            // Big anime pupils with cute glossy sparkle reflections
            eyePaint.color = Color.parseColor("#0F172A")
            canvas.drawOval(RectF(hx - eyeSpacing - eyeW, eyeY - eyeH, hx - eyeSpacing + eyeW, eyeY + eyeH), eyePaint)
            canvas.drawOval(RectF(hx + eyeSpacing - eyeW, eyeY - eyeH, hx + eyeSpacing + eyeW, eyeY + eyeH), eyePaint)

            // Neon eye iris rim
            strokePaint.strokeWidth = 1.4f * scaleFactor
            strokePaint.color = palette.primaryColor
            canvas.drawOval(RectF(hx - eyeSpacing - eyeW * 0.85f, eyeY - eyeH * 0.85f, hx - eyeSpacing + eyeW * 0.85f, eyeY + eyeH * 0.85f), strokePaint)
            canvas.drawOval(RectF(hx + eyeSpacing - eyeW * 0.85f, eyeY - eyeH * 0.85f, hx + eyeSpacing + eyeW * 0.85f, eyeY + eyeH * 0.85f), strokePaint)

            // Primary star sparkle highlight (top-left)
            fillPaint.shader = null
            fillPaint.color = Color.WHITE
            fillPaint.alpha = 255
            canvas.drawCircle(hx - eyeSpacing - eyeW * 0.30f, eyeY - eyeH * 0.35f, eyeW * 0.42f, fillPaint)
            canvas.drawCircle(hx + eyeSpacing - eyeW * 0.30f, eyeY - eyeH * 0.35f, eyeW * 0.42f, fillPaint)

            // Secondary sparkle highlight (bottom-right)
            canvas.drawCircle(hx - eyeSpacing + eyeW * 0.35f, eyeY + eyeH * 0.28f, eyeW * 0.22f, fillPaint)
            canvas.drawCircle(hx + eyeSpacing + eyeW * 0.35f, eyeY + eyeH * 0.28f, eyeW * 0.22f, fillPaint)
        }

        // 5. Adorable Blushing Cheeks
        fillPaint.shader = null
        fillPaint.color = Color.parseColor("#FF6B8B")
        fillPaint.alpha = 175
        canvas.drawCircle(hx - eyeSpacing - eyeW * 0.7f, hy + r * 0.34f, r * 0.16f, fillPaint)
        canvas.drawCircle(hx + eyeSpacing + eyeW * 0.7f, hy + r * 0.34f, r * 0.16f, fillPaint)

        // 6. Cute Smiling Mouth (wavy open cute smile)
        strokePaint.strokeWidth = 2.2f * scaleFactor
        strokePaint.color = Color.parseColor("#1E293B")
        val mouthY = hy + r * 0.38f
        val mouthW = r * 0.18f + (audioLevel * r * 0.12f)
        tempRect.set(hx - mouthW, mouthY - r * 0.10f, hx + mouthW, mouthY + r * 0.18f)
        canvas.drawArc(tempRect, 20f, 140f, false, strokePaint)

        // 7. Cute floating orbital sparkles
        val sparkleCount = 4
        for (i in 0 until sparkleCount) {
            val angle = i * (360f / sparkleCount) + (animTime * 28f)
            val rad = Math.toRadians(angle.toDouble())
            val sDist = r * 1.35f + (sin(animTime * 4f + i).toFloat() * 4f * scaleFactor)
            val sx = hx + (cos(rad) * sDist).toFloat()
            val sy = hy + (sin(rad) * sDist).toFloat()

            particlePaint.color = palette.primaryColor
            particlePaint.alpha = 190
            canvas.drawCircle(sx, sy, 3.2f * scaleFactor, particlePaint)
        }

        canvas.restore()
    }
}
