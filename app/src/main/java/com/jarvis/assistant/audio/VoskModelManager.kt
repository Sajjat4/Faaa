package com.jarvis.assistant.audio

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.vosk.Model
import org.vosk.SpeakerModel
import java.io.File
import java.io.FileOutputStream

object VoskModelManager {
    private const val TAG = "VoskModelManager"

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady: StateFlow<Boolean> = _isModelReady.asStateFlow()

    private val _isSpeakerModelReady = MutableStateFlow(false)
    val isSpeakerModelReady: StateFlow<Boolean> = _isSpeakerModelReady.asStateFlow()

    @Volatile
    var model: Model? = null
        private set

    @Volatile
    var speakerModel: SpeakerModel? = null
        private set

    private val isInitializing = java.util.concurrent.atomic.AtomicBoolean(false)

    fun init(context: Context, scope: CoroutineScope) {
        if (model != null && speakerModel != null) return
        if (!isInitializing.compareAndSet(false, true)) return

        scope.launch(Dispatchers.IO) {
            try {
                // 1. Unpack & load speech recognition model
                val targetDir = File(context.filesDir, "vosk-model")
                val markerFile = File(targetDir, "am/final.mdl")

                if (!markerFile.exists() || markerFile.length() == 0L) {
                    Log.i(TAG, "Unpacking Vosk model to internal storage: ${targetDir.absolutePath}")
                    targetDir.deleteRecursively()
                    targetDir.mkdirs()
                    copyAssetFolder(context.assets, "model-en-us", targetDir)
                    Log.i(TAG, "Vosk model assets unpacked.")
                }

                if (markerFile.exists() && markerFile.length() > 0L) {
                    try {
                        val loadedModel = Model(targetDir.absolutePath)
                        model = loadedModel
                        _isModelReady.value = true
                        Log.i(TAG, "Vosk Model ready in memory!")
                    } catch (e: Throwable) {
                        Log.w(TAG, "Vosk speech model could not be loaded: ${e.message}")
                        model = null
                        _isModelReady.value = false
                    }
                } else {
                    Log.w(TAG, "Vosk acoustic model file (am/final.mdl) is not present. Standby wake-word listener will be inactive; Live Gemini Voice remains fully active.")
                    model = null
                    _isModelReady.value = false
                }

                // 2. Unpack & load speaker recognition model (x-vector extractor)
                try {
                    val spkTargetDir = File(context.filesDir, "vosk-spk-model")
                    val spkMarkerFile = File(spkTargetDir, "final.ext.raw")

                    if (!spkMarkerFile.exists() || spkMarkerFile.length() == 0L) {
                        Log.i(TAG, "Unpacking Vosk Speaker model to: ${spkTargetDir.absolutePath}")
                        spkTargetDir.deleteRecursively()
                        spkTargetDir.mkdirs()
                        copyAssetFolder(context.assets, "model-spk", spkTargetDir)
                        Log.i(TAG, "Vosk Speaker model assets unpacked.")
                    }

                    if (spkMarkerFile.exists() && spkMarkerFile.length() > 0L) {
                        val loadedSpk = SpeakerModel(spkTargetDir.absolutePath)
                        speakerModel = loadedSpk
                        _isSpeakerModelReady.value = true
                        Log.i(TAG, "Vosk SpeakerModel ready in memory!")
                    } else {
                        Log.w(TAG, "Vosk speaker model file (final.ext.raw) not present. Speaker verification in standby.")
                        speakerModel = null
                        _isSpeakerModelReady.value = false
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to load Vosk SpeakerModel: ${e.message}")
                    speakerModel = null
                    _isSpeakerModelReady.value = false
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Vosk init completed with warning: ${e.message}")
            } finally {
                isInitializing.set(false)
            }
        }
    }

    private fun copyAssetFolder(assetManager: AssetManager, fromAssetPath: String, toDir: File) {
        val list = assetManager.list(fromAssetPath)
        if (list.isNullOrEmpty()) {
            try {
                assetManager.open(fromAssetPath).use { input ->
                    toDir.parentFile?.mkdirs()
                    FileOutputStream(toDir).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not copy asset $fromAssetPath: ${e.message}")
            }
        } else {
            toDir.mkdirs()
            for (item in list) {
                val childAsset = if (fromAssetPath.isEmpty()) item else "$fromAssetPath/$item"
                val childTarget = File(toDir, item)
                copyAssetFolder(assetManager, childAsset, childTarget)
            }
        }
    }
}
