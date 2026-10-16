package com.jarvis.assistant.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Lightweight, zero-asset bubble-pop sound generator for the floating companion avatar.
 * Generates a cute, soft upward-chirping liquid bubble sound.
 */
object BubbleSoundPlayer {
    private const val TAG = "BubbleSoundPlayer"
    private const val SAMPLE_RATE = 44100
    private const val DURATION_SEC = 0.085 // 85ms

    private val pcmBuffer: ShortArray by lazy { generateBubblePcm() }

    private fun generateBubblePcm(): ShortArray {
        val totalSamples = (SAMPLE_RATE * DURATION_SEC).toInt()
        val buffer = ShortArray(totalSamples)

        val startFreq = 420.0
        val endFreq = 960.0

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / DURATION_SEC
            // Upward frequency sweep (chirp)
            val currentFreq = startFreq + (endFreq - startFreq) * (progress * progress)
            val phase = 2.0 * PI * currentFreq * t

            // Exponential decay envelope with soft attack
            val attack = (progress / 0.12).coerceAtMost(1.0)
            val decay = exp(-progress * 4.8)
            val amplitude = attack * decay * 0.42 // Soft, pleasant volume

            val sampleVal = (sin(phase) * amplitude * Short.MAX_VALUE).toInt()
            buffer[i] = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    fun play(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                val samples = pcmBuffer
                val minBufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = maxOf(minBufferSize, samples.size * 2)

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(samples, 0, samples.size)
                audioTrack.play()

                // Release track after playback completes
                launch {
                    kotlinx.coroutines.delay(120L)
                    try {
                        audioTrack.stop()
                        audioTrack.release()
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.w(TAG, "Bubble sound playback failed: ${e.message}")
            }
        }
    }
}
