package com.jarvis.assistant.ui.grok

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.automation.session.AvatarState

/**
 * High-performance 60 FPS View rendering GrokBot and its cute variants.
 * Can be placed directly into layouts or dynamic overlay capsules.
 */
class GrokBotCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var botState: GrokBotDrawingEngine.GrokBotState = GrokBotDrawingEngine.GrokBotState.IDLE
        set(value) {
            if (field != value) {
                field = value
                GrokBotSoundManager.getInstance(context).playSoundForState(value)
                invalidate()
            }
        }

    var avatarStyle: String = "Grok Bot"
        set(value) {
            field = value
            invalidate()
        }

    var audioLevel: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val startTime = SystemClock.uptimeMillis()

    fun updateFromAvatarState(state: AvatarState) {
        botState = GrokBotDrawingEngine.GrokBotState.fromAvatarState(state)
        // Refresh active style from user preferences if not explicitly overridden
        val prefs = JarvisApp.instance.preferences
        val active = if (prefs.taskOverlayAvatar.isNotBlank() &&
            !prefs.taskOverlayAvatar.equals("metamuse", ignoreCase = true) &&
            !prefs.taskOverlayAvatar.equals("none", ignoreCase = true)
        ) {
            prefs.taskOverlayAvatar
        } else {
            prefs.avatarStyle
        }
        avatarStyle = active
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val radius = (Math.min(w, h) / 2f) * 0.75f
        val animTime = (SystemClock.uptimeMillis() - startTime) / 1000f

        // Calculate dynamic scale factor based on view dimensions
        val scaleFactor = (Math.min(w, h) / 96f).coerceIn(0.35f, 1.5f)

        val activeStyle = if (avatarStyle.isNotBlank()) avatarStyle else JarvisApp.instance.preferences.avatarStyle

        GrokBotDrawingEngine.drawCuteOrGrokBot(
            canvas = canvas,
            cx = cx,
            cy = cy,
            radius = radius,
            state = botState,
            animTime = animTime,
            audioLevel = audioLevel,
            scaleFactor = scaleFactor,
            avatarStyle = activeStyle
        )

        postInvalidateOnAnimation()
    }
}
