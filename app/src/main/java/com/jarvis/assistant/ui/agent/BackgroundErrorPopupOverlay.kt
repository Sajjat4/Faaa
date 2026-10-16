package com.jarvis.assistant.ui.agent

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.R
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.background.JarvisLiveError
import com.jarvis.assistant.util.PermissionHelper

/**
 * System Overlay Pop-up that renders floating error alerts over any app in the background.
 * Specifically alerts the user when Accessibility Service is disabled, connection fails,
 * API key errors occur, or permissions are revoked.
 */
class BackgroundErrorPopupOverlay(private val context: Context) {

    companion object {
        private const val TAG = "BackgroundErrorPopup"
        private const val NOTIF_ID_ERROR_POPUP = 9110
        private const val NOTIF_CHANNEL_ID = "jarvis_error_alerts"
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private val root = FrameLayout(context).apply {
        clipChildren = false
        clipToPadding = false
        setBackgroundColor(Color.TRANSPARENT)
    }

    private val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        elevation = dp(16).toFloat()
    }

    // Top Header: Icon + Title + Close Button
    private val headerRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private val ivIcon = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply {
            rightMargin = dp(10)
        }
        setImageResource(R.drawable.ic_warning)
    }

    private val tvTitle = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        textSize = 13.5f
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        setTextColor(Color.WHITE)
        letterSpacing = 0.04f
        isSingleLine = true
    }

    private val btnClose = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
        gravity = Gravity.CENTER
        text = "✕"
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#94A3B8"))
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#1E293B"))
        }
        setOnClickListener {
            hide()
            JarvisConversationService.clearLiveError()
        }
    }

    // Error Message Body
    private val tvMessage = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(8)
            bottomMargin = dp(12)
        }
        textSize = 12f
        setTextColor(Color.parseColor("#CBD5E1"))
        setLineSpacing(dp(2).toFloat(), 1.0f)
        maxLines = 4
    }

    // Action Buttons Row: Fix Now + Dismiss
    private val actionRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
    }

    private val btnDismiss = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            dp(36)
        ).apply {
            rightMargin = dp(8)
        }
        gravity = Gravity.CENTER
        setPadding(dp(14), 0, dp(14), 0)
        text = "DISMISS"
        textSize = 11.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#94A3B8"))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(8).toFloat()
            setColor(Color.parseColor("#1E293B"))
        }
        setOnClickListener {
            hide()
            JarvisConversationService.clearLiveError()
        }
    }

    private val btnAction = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            dp(36)
        )
        gravity = Gravity.CENTER
        setPadding(dp(16), 0, dp(16), 0)
        text = "FIX NOW"
        textSize = 11.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        elevation = dp(4).toFloat()
    }

    private val windowParams = WindowManager.LayoutParams(
        dp(340),
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        x = 0
        y = dp(72)
    }

    private var isAttached = false
    private var currentError: JarvisLiveError? = null

    // Touch & Drag Handling
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var isDragging = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    init {
        headerRow.addView(ivIcon)
        headerRow.addView(tvTitle)
        headerRow.addView(btnClose)

        actionRow.addView(btnDismiss)
        actionRow.addView(btnAction)

        card.addView(headerRow)
        card.addView(tvMessage)
        card.addView(actionRow)

        root.addView(card, FrameLayout.LayoutParams(-1, -2))

        setupDragAndTouch()
    }

    private fun setupDragAndTouch() {
        card.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = windowParams.x
                    startY = windowParams.y
                    isDragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (Math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        isDragging = true
                        windowParams.x = startX + dx
                        windowParams.y = (startY + dy).coerceAtLeast(dp(20))
                        try {
                            if (isAttached) windowManager.updateViewLayout(root, windowParams)
                        } catch (_: Exception) {}
                        true
                    } else false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging
                }
                else -> false
            }
        }
    }

    /**
     * Shows the background error pop-up with appropriate styling and actions based on error type.
     */
    fun show(error: JarvisLiveError) {
        currentError = error
        Log.i(TAG, "Showing background error pop-up: [${error.errorCode}] ${error.title} - ${error.message}")

        val isAccessibility = error.errorCode == "ACCESSIBILITY_DISABLED"
        val isConnection = error.errorCode.contains("CONNECTION") || error.errorCode.contains("FATAL") || error.errorCode == "SESSION_CLOSED"
        val isApiKey = error.errorCode.contains("API_KEY") || error.errorCode == "QUOTA_EXCEEDED"

        val accentColor = when {
            isAccessibility -> Color.parseColor("#F59E0B") // Amber Warning
            isApiKey -> Color.parseColor("#EF4444") // Coral Red
            isConnection -> Color.parseColor("#3B82F6") // Tech Blue
            else -> Color.parseColor("#EF4444")
        }

        // Style Card Background with Glassmorphic Deep Slate and Accent Stroke
        card.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16).toFloat()
            setColor(Color.parseColor("#0F172A")) // Deep OLED Slate
            setStroke(dp(1.8f), accentColor)
        }

        // Set Icon
        ivIcon.setImageResource(
            when {
                isAccessibility -> R.drawable.ic_settings_shield
                isConnection -> R.drawable.ic_warning
                else -> R.drawable.ic_warning
            }
        )
        ivIcon.imageTintList = ColorStateList.valueOf(accentColor)

        // Set Texts
        tvTitle.text = error.title
        tvTitle.setTextColor(accentColor)
        tvMessage.text = error.message
        btnAction.text = if (error.actionText.isNotBlank()) error.actionText else "FIX NOW"

        // Action Button Style
        btnAction.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(8).toFloat()
            setColor(accentColor)
        }

        btnAction.setOnClickListener {
            handleErrorAction(error)
            hide()
        }

        // Fallback or Heads-up notification if overlay permission is missing
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Display over other apps not granted, posting Heads-up Notification for error")
            showHeadsUpNotification(error)
            return
        }

        // Attach to WindowManager
        try {
            if (!isAttached) {
                windowParams.x = 0
                windowParams.y = dp(72)
                windowManager.addView(root, windowParams)
                isAttached = true
                animateEntrance()
                triggerAlertHaptics()
            } else {
                windowManager.updateViewLayout(root, windowParams)
                animateEntrance()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to display background error overlay: ${e.message}")
            showHeadsUpNotification(error)
        }
    }

    /**
     * Hides and detaches the error pop-up overlay.
     */
    fun hide() {
        if (!isAttached) return
        try {
            card.animate()
                .scaleX(0.85f)
                .scaleY(0.85f)
                .alpha(0f)
                .setDuration(160)
                .withEndAction {
                    try {
                        if (isAttached) {
                            windowManager.removeView(root)
                            isAttached = false
                        }
                    } catch (_: Exception) {}
                }
                .start()
        } catch (_: Exception) {
            try {
                if (isAttached) {
                    windowManager.removeView(root)
                    isAttached = false
                }
            } catch (_: Exception) {}
        }
    }

    private fun animateEntrance() {
        card.scaleX = 0.8f
        card.scaleY = 0.8f
        card.alpha = 0f

        val scaleX = ObjectAnimator.ofFloat(card, View.SCALE_X, 0.8f, 1.0f)
        val scaleY = ObjectAnimator.ofFloat(card, View.SCALE_Y, 0.8f, 1.0f)
        val alpha = ObjectAnimator.ofFloat(card, View.ALPHA, 0f, 1.0f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 260
            interpolator = OvershootInterpolator(1.2f)
            start()
        }
    }

    private fun triggerAlertHaptics() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 150), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 100, 80, 150), -1)
            }
        } catch (_: Exception) {}
    }

    private fun handleErrorAction(error: JarvisLiveError) {
        when (error.errorCode) {
            "ACCESSIBILITY_DISABLED" -> {
                PermissionHelper.openAccessibilitySettings(context)
            }
            "API_KEY_INVALID", "API_KEY_MISSING", "QUOTA_EXCEEDED" -> {
                try {
                    val intent = Intent(context, com.jarvis.assistant.ui.settings.ApiConnectionActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch ApiConnectionActivity: ${e.message}")
                }
            }
            "PERMISSIONS_MISSING" -> {
                try {
                    val intent = Intent(context, com.jarvis.assistant.ui.permission.PermissionActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch PermissionActivity: ${e.message}")
                }
            }
            "CONNECTION_ERROR", "FATAL_ERROR", "SESSION_CLOSED" -> {
                // Reconnect or restart conversation session
                try {
                    JarvisConversationService.clearLiveError()
                    val intent = Intent(context, JarvisConversationService::class.java).apply {
                        action = JarvisConversationService.ACTION_START_CONVERSATION
                    }
                    androidx.core.content.ContextCompat.startForegroundService(context, intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to trigger session reconnect: ${e.message}")
                }
            }
            else -> {
                // Default: open Main app
                try {
                    val intent = Intent(context, com.jarvis.assistant.ui.home.MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    private fun showHeadsUpNotification(error: JarvisLiveError) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val pendingIntent = when (error.errorCode) {
                "ACCESSIBILITY_DISABLED" -> PendingIntent.getActivity(
                    context, 101,
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                "API_KEY_INVALID", "API_KEY_MISSING" -> PendingIntent.getActivity(
                    context, 102,
                    Intent(context, com.jarvis.assistant.ui.settings.ApiConnectionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                else -> PendingIntent.getActivity(
                    context, 103,
                    Intent(context, com.jarvis.assistant.ui.home.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            val notif = NotificationCompat.Builder(context, "jarvis_service_channel")
                .setSmallIcon(R.drawable.ic_warning)
                .setContentTitle(error.title)
                .setContentText(error.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(error.message))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_warning, error.actionText, pendingIntent)
                .build()

            nm.notify(NOTIF_ID_ERROR_POPUP, notif)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to display fallback heads up notification: ${e.message}")
        }
    }

    private fun dp(v: Float): Int = (v * context.resources.displayMetrics.density).toInt()
    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
}
