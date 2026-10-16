package com.jarvis.assistant.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.jarvis.assistant.automation.accessibility.JarvisAccessibilityService

object PermissionHelper {

    fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasBatteryOptimizationPermission(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasWriteSettingsPermission(context: Context): Boolean {
        return Settings.System.canWrite(context)
    }

    fun requestWriteSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        // 0. Active running singleton check
        if (JarvisAccessibilityService.isRunning() || JarvisAccessibilityService.instance != null) return true

        val pkgName = context.packageName
        val altPkg = "com.jarvis.assistant"

        // 1. Check Settings.Secure ENABLED_ACCESSIBILITY_SERVICES (most authoritative on Android)
        try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (!enabledServices.isNullOrBlank()) {
                if (enabledServices.contains(pkgName, ignoreCase = true) ||
                    enabledServices.contains(altPkg, ignoreCase = true) ||
                    enabledServices.contains("JarvisAccessibilityService", ignoreCase = true)
                ) {
                    return true
                }
                val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
                colonSplitter.setString(enabledServices)
                while (colonSplitter.hasNext()) {
                    val componentNameString = colonSplitter.next().trim()
                    if (componentNameString.contains(pkgName, ignoreCase = true) ||
                        componentNameString.contains(altPkg, ignoreCase = true) ||
                        componentNameString.contains("JarvisAccessibilityService", ignoreCase = true)
                    ) {
                        return true
                    }
                    val enabledComponent = android.content.ComponentName.unflattenFromString(componentNameString)
                    if (enabledComponent != null &&
                        (enabledComponent.packageName.equals(pkgName, ignoreCase = true) ||
                         enabledComponent.packageName.equals(altPkg, ignoreCase = true))
                    ) {
                        return true
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Check via system AccessibilityManager across all feedback masks
        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
            if (am != null) {
                val masks = intArrayOf(
                    android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
                    android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC,
                    -1
                )
                for (mask in masks) {
                    val runningServices = am.getEnabledAccessibilityServiceList(mask)
                    for (service in runningServices) {
                        val id = service.id
                        val resolveInfo = service.resolveInfo
                        val serviceInfo = resolveInfo?.serviceInfo
                        if (serviceInfo != null && serviceInfo.packageName.equals(pkgName, ignoreCase = true)) {
                            return true
                        }
                        if (id != null && (id.contains(pkgName, ignoreCase = true) || id.contains("JarvisAccessibilityService", ignoreCase = true))) {
                            return true
                        }
                    }
                }

                // Also check installed list if accessibility is enabled
                val a11yEnabled = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
                if (a11yEnabled) {
                    val installed = am.installedAccessibilityServiceList
                    for (info in installed) {
                        val id = info.id ?: ""
                        if (id.contains(pkgName, ignoreCase = true) && id.contains("JarvisAccessibilityService", ignoreCase = true)) {
                            // If accessibility is globally on and service is registered, treat as enabled
                            return true
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return false
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // fallback
        }
    }

    fun hasAllRequiredPermissions(context: Context): Boolean {
        return hasRecordAudioPermission(context) &&
               hasNotificationPermission(context) &&
               hasBatteryOptimizationPermission(context)
    }

    fun canAutoStartSession(context: Context): Boolean {
        val hasAudio = hasRecordAudioPermission(context)
        val hasNotif = hasNotificationPermission(context)
        val hasA11y = isAccessibilityServiceEnabled(context)
        val hasKey = com.jarvis.assistant.JarvisApp.instance.preferences.apiKey.isNotBlank()
        return hasAudio && hasNotif && hasA11y && hasKey
    }

    fun requestBatteryOptimization(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        }
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val fallback = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallback)
                } catch (_: Exception) {}
            }
        }
    }

    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}

