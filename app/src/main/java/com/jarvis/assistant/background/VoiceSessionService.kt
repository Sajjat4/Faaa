package com.jarvis.assistant.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.automation.session.SessionStateManager
import com.jarvis.assistant.ui.agent.FloatingVoiceController
import com.jarvis.assistant.ui.home.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Dedicated Foreground Service handling the persistent connection for Gemini Live voice.
 * Ensures microphone capture remains active and stable when the app enters the background,
 * holds wake and wifi locks to avoid socket disconnections, and coordinates
 * the WindowManager FloatingVoiceController.
 */
class VoiceSessionService : Service() {

    companion object {
        const val TAG = "VoiceSessionService"
        const val ACTION_START_VOICE = "com.jarvis.assistant.START_VOICE_SESSION"
        const val ACTION_STOP_VOICE = "com.jarvis.assistant.STOP_VOICE_SESSION"
        const val ACTION_PAUSE_VOICE = "com.jarvis.assistant.PAUSE_VOICE_SESSION"
        const val ACTION_RESUME_VOICE = "com.jarvis.assistant.RESUME_VOICE_SESSION"

        private const val CHANNEL_ID = "voice_session_channel"
        private const val NOTIFICATION_ID = 42

        @Volatile
        private var instance: VoiceSessionService? = null

        fun isRunning(): Boolean = instance != null

        fun start(context: Context) {
            val intent = Intent(context, VoiceSessionService::class.java).apply {
                action = ACTION_START_VOICE
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start VoiceSessionService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, VoiceSessionService::class.java).apply {
                action = ACTION_STOP_VOICE
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop VoiceSessionService: ${e.message}")
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var floatingVoiceController: FloatingVoiceController? = null
    private var syncJob: Job? = null
    private var audioLevelJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        acquireLocks()
        floatingVoiceController = FloatingVoiceController(this)

        // Observe SessionStateManager and app foreground state to control fixed Dynamic Island
        syncJob = serviceScope.launch {
            combine(
                SessionStateManager.voiceState,
                JarvisApp.isAppForeground,
                JarvisConversationService.isSessionOn
            ) { state, isForeground, sessionOn ->
                val prefs = (application as JarvisApp).preferences
                val showIsland = sessionOn && !isForeground && (prefs.isDynamicIslandEnabled || prefs.isFloatingVoiceControlEnabled)
                Pair(showIsland, state)
            }.collect { (showIsland, state) ->
                if (showIsland) {
                    floatingVoiceController?.show()
                    floatingVoiceController?.updateState(state)
                } else {
                    floatingVoiceController?.hide()
                }
            }
        }

        // Forward audio levels for dynamic soundwave / breathing animations
        audioLevelJob = serviceScope.launch {
            SessionStateManager.audioLevel.collect { level ->
                floatingVoiceController?.updateAudioLevel(level)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForegroundNotification()

        when (intent?.action) {
            ACTION_START_VOICE -> {
                Log.i(TAG, "Persistent background voice session confirmed")
            }
            ACTION_PAUSE_VOICE -> {
                SessionStateManager.requestPause()
            }
            ACTION_RESUME_VOICE -> {
                SessionStateManager.requestResume()
            }
            ACTION_STOP_VOICE -> {
                SessionStateManager.requestHangUp()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        return START_STICKY
    }

    private fun ensureForegroundNotification() {
        val notification = buildPersistentNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            try {
                startForeground(NOTIFICATION_ID, notification, types)
            } catch (e: Exception) {
                Log.w(TAG, "Combined foreground service start failed, falling back: ${e.message}")
                try {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                } catch (_: Exception) {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildPersistentNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS Voice Session Active")
            .setContentText("Persistent voice connection active in background")
            .setSmallIcon(R.drawable.ic_sparkle)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Session Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps voice conversation streaming active in background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun acquireLocks() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Jarvis::VoiceSessionWakeLock").apply {
                acquire(60 * 60 * 1000L) // 1 hour safety limit
            }
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock acquisition failed: ${e.message}")
        }

        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wm?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Jarvis::VoiceSessionWifiLock")?.apply {
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "WifiLock acquisition failed: ${e.message}")
        }
    }

    override fun onDestroy() {
        instance = null
        syncJob?.cancel()
        audioLevelJob?.cancel()
        floatingVoiceController?.hide()
        floatingVoiceController = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        wifiLock?.takeIf { it.isHeld }?.release()
        wifiLock = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
