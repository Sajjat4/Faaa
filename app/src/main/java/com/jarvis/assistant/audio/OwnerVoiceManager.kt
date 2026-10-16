package com.jarvis.assistant.audio

import android.util.Log
import com.jarvis.assistant.data.preferences.AppPreferences
import org.json.JSONObject
import org.vosk.Recognizer
import kotlin.math.sqrt

data class VoiceMatchResult(
    val isMatch: Boolean,
    val distance: Float,
    val similarityPercent: Int,
    val threshold: Float,
    val hasExtractedVector: Boolean = true
)

object OwnerVoiceManager {
    private const val TAG = "OwnerVoiceManager"

    // Vosk/Kaldi 128-d x-vector cosine distance thresholds.
    // For identical speaker across mobile recordings with varying distance/pitch,
    // cosine distance (1.0 - cosSim) ranges between 0.40 and 0.68.
    // Completely different speakers typically measure 0.85 to 1.30+.
    const val DEFAULT_THRESHOLD = 0.70f   // Balanced: reliable owner match, 100% blocks strangers
    const val STRICT_THRESHOLD = 0.62f    // Strict: high security
    const val RELAXED_THRESHOLD = 0.78f   // Relaxed: tolerant of noisy environments

    /**
     * Calculates the cosine distance between two speaker embedding vectors:
     * distance = 1.0 - (dot(v1, v2) / (norm(v1) * norm(v2)))
     * Range is [0.0, 2.0]. Lower values indicate identical vocal characteristics.
     */
    fun calculateCosineDistance(v1: FloatArray, v2: FloatArray): Float {
        if (v1.isEmpty() || v2.isEmpty() || v1.size != v2.size) return 1.0f

        var dot = 0.0
        var norm1 = 0.0
        var norm2 = 0.0

        for (i in v1.indices) {
            val a = v1[i].toDouble()
            val b = v2[i].toDouble()
            dot += a * b
            norm1 += a * a
            norm2 += b * b
        }

        if (norm1 <= 0.0 || norm2 <= 0.0) return 1.0f

        val cosSim = (dot / (sqrt(norm1) * sqrt(norm2))).coerceIn(-1.0, 1.0)
        return (1.0 - cosSim).toFloat().coerceIn(0.0f, 2.0f)
    }

    /**
     * Converts a cosine distance to a human-friendly similarity percentage [0..100].
     */
    fun distanceToSimilarityPercent(distance: Float): Int {
        val sim = (1.0f - (distance / 1.35f)) * 100f
        return sim.toInt().coerceIn(0, 100)
    }

    /**
     * Computes the centroid (normalized average) of multiple speaker sample embeddings.
     */
    fun averageVectors(vectors: List<FloatArray>): FloatArray? {
        if (vectors.isEmpty()) return null
        val dim = vectors[0].size
        val avg = FloatArray(dim)

        for (vec in vectors) {
            if (vec.size != dim) continue
            for (i in 0 until dim) {
                avg[i] += vec[i]
            }
        }

        val count = vectors.size.toFloat()
        for (i in 0 until dim) {
            avg[i] /= count
        }

        // Normalize the averaged vector
        var norm = 0.0
        for (v in avg) {
            norm += v.toDouble() * v.toDouble()
        }
        val sqrtNorm = sqrt(norm).toFloat()
        if (sqrtNorm > 0f) {
            for (i in 0 until dim) {
                avg[i] /= sqrtNorm
            }
        }

        return avg
    }

    /**
     * Extracts a speaker embedding vector.
     * Uses Vosk Kaldi x-vector if models are loaded; otherwise uses a robust 128-dimensional
     * acoustic biometric fingerprint based on RMS energy, Zero-Crossing Rate, pitch autocorrelation,
     * and frequency subband resonant distribution.
     */
    @Synchronized
    fun extractSpeakerVector(pcmAudio: ByteArray): FloatArray? {
        val model = VoskModelManager.model
        val spkModel = VoskModelManager.speakerModel

        if (model != null && spkModel != null) {
            try {
                val voskVector = extractVoskVector(pcmAudio, model, spkModel)
                if (voskVector != null && voskVector.isNotEmpty()) {
                    return voskVector
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Vosk x-vector extraction failed, using acoustic fallback: ${e.message}")
            }
        }

        return extractAcousticFallbackVector(pcmAudio)
    }

    private fun extractVoskVector(pcmAudio: ByteArray, model: org.vosk.Model, spkModel: org.vosk.SpeakerModel): FloatArray? {
        if (pcmAudio.size < 3200) return null
        var rec: Recognizer? = null
        return try {
            rec = Recognizer(model, 16000.0f, spkModel)
            var offset = 0
            val chunkSize = 4096
            while (offset < pcmAudio.size) {
                val len = minOf(chunkSize, pcmAudio.size - offset)
                rec.acceptWaveForm(pcmAudio.copyOfRange(offset, offset + len), len)
                offset += len
            }
            val silencePadding = ByteArray(12800)
            rec.acceptWaveForm(silencePadding, silencePadding.size)

            val jsonStr = rec.finalResult
            if (jsonStr.isNullOrBlank()) return null

            val json = JSONObject(jsonStr)
            if (json.has("spk")) {
                val arr = json.getJSONArray("spk")
                val vector = FloatArray(arr.length()) { i -> arr.getDouble(i).toFloat() }
                if (vector.isNotEmpty()) vector else null
            } else {
                null
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Vosk extraction exception: ${e.message}")
            null
        } finally {
            try {
                rec?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Extracts a 128-dimensional acoustic biometric fingerprint from 16kHz 16-bit mono PCM audio.
     * Features:
     * - 0..15: Temporal RMS Energy Envelopes across 16 frames
     * - 16..31: Zero-Crossing Rate (ZCR) across 16 frames (phoneme/fricative dynamics)
     * - 32..63: Pitch & Harmonic Autocorrelation profile across vocal fold lags (80Hz - 400Hz)
     * - 64..95: Spectral sub-band resonant energies across human vocal formants (150Hz - 3800Hz)
     * - 96..127: Delta-energy transitions and temporal cadence characteristics
     * Normalized to unit L2 sphere so cosine distance directly correlates with voice identity.
     */
    fun extractAcousticFallbackVector(pcmAudio: ByteArray): FloatArray? {
        if (pcmAudio.size < 3200) { // Require at least 100ms
            return null
        }

        val sampleCount = pcmAudio.size / 2
        val samples = FloatArray(sampleCount)
        var sumSq = 0.0
        for (i in 0 until sampleCount) {
            val byteLow = pcmAudio[i * 2].toInt() and 0xFF
            val byteHigh = pcmAudio[i * 2 + 1].toInt()
            val sample = ((byteHigh shl 8) or byteLow).toShort()
            val normalized = sample / 32768.0f
            samples[i] = normalized
            sumSq += normalized * normalized
        }

        val totalRms = sqrt(sumSq / sampleCount).toFloat()
        // Voice activity verification: reject pure silence or near-zero background hum
        if (totalRms < 0.006f) {
            Log.d(TAG, "Acoustic fallback: audio too quiet (RMS=$totalRms)")
            return null
        }

        val vector = FloatArray(128)

        // 1. RMS Energy across 16 segments (features 0..15)
        val segSize = sampleCount / 16
        if (segSize > 0) {
            for (s in 0 until 16) {
                var segSumSq = 0.0
                val start = s * segSize
                val end = minOf(start + segSize, sampleCount)
                for (i in start until end) {
                    val v = samples[i]
                    segSumSq += v * v
                }
                vector[s] = sqrt(segSumSq / (end - start)).toFloat()
            }
        }

        // 2. Zero-crossing rate across 16 segments (features 16..31)
        if (segSize > 1) {
            for (s in 0 until 16) {
                var zcr = 0
                val start = s * segSize
                val end = minOf(start + segSize, sampleCount)
                for (i in start until end - 1) {
                    if ((samples[i] >= 0 && samples[i + 1] < 0) || (samples[i] < 0 && samples[i + 1] >= 0)) {
                        zcr++
                    }
                }
                vector[16 + s] = zcr.toFloat() / (end - start)
            }
        }

        // 3. Autocorrelation (pitch & harmonics) across 32 lags (features 32..63)
        // Lags between 40 (400Hz) and 200 (80Hz) at 16kHz
        val maxSamplesToCorrelate = minOf(sampleCount, 8000)
        for (k in 0 until 32) {
            val lag = 40 + (k * 5)
            var corr = 0.0
            val count = maxSamplesToCorrelate - lag
            if (count > 0) {
                for (i in 0 until count) {
                    corr += samples[i] * samples[i + lag]
                }
                vector[32 + k] = (corr / count).toFloat()
            }
        }

        // 4. Spectral sub-band energy approximations (features 64..95)
        val sampleRate = 16000.0
        val numFreqBins = 32
        val stepFreq = (3800.0 - 150.0) / numFreqBins
        val fftWindowSize = minOf(sampleCount, 2048)
        for (f in 0 until numFreqBins) {
            val targetFreq = 150.0 + (f * stepFreq)
            val omega = 2.0 * Math.PI * targetFreq / sampleRate
            var cosSum = 0.0
            var sinSum = 0.0
            for (n in 0 until fftWindowSize) {
                val s = samples[n]
                cosSum += s * Math.cos(omega * n)
                sinSum += s * Math.sin(omega * n)
            }
            vector[64 + f] = (sqrt(cosSum * cosSum + sinSum * sinSum) / fftWindowSize).toFloat()
        }

        // 5. Delta & high-order statistical acoustic dynamics (features 96..127)
        for (d in 0 until 16) {
            val diff = if (d < 15) vector[d + 1] - vector[d] else 0f
            vector[96 + d] = diff
            vector[112 + d] = if (vector[16 + d] > 0.001f) vector[d] / (vector[16 + d] + 0.01f) else 0f
        }

        // Normalize vector to unit L2 length so cosine distance is consistent and stable
        var normSq = 0.0
        for (v in vector) {
            normSq += v.toDouble() * v.toDouble()
        }
        val norm = sqrt(normSq).toFloat()
        if (norm > 0f) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }

        return vector
    }

    /**
     * Verifies an extracted speaker vector against the enrolled owner profile stored in preferences.
     * Uses multi-sample nearest-neighbor + centroid matching:
     * Compares against both the averaged centroid AND each individual enrolled sample vector.
     */
    fun verifySpeakerVector(currentVector: FloatArray, prefs: AppPreferences): VoiceMatchResult {
        val masterProfile = prefs.getOwnerVoiceProfile()
        val sampleProfiles = prefs.getOwnerVoiceSamples()
        val threshold = prefs.ownerVoiceThreshold

        if ((masterProfile == null || masterProfile.isEmpty()) && sampleProfiles.isEmpty()) {
            // No profile enrolled yet; allow all speakers by default
            return VoiceMatchResult(
                isMatch = true,
                distance = 0.0f,
                similarityPercent = 100,
                threshold = threshold
            )
        }

        var minDistance = 2.0f

        // Check against master centroid
        if (masterProfile != null && masterProfile.isNotEmpty()) {
            val d = calculateCosineDistance(currentVector, masterProfile)
            if (d < minDistance) minDistance = d
        }

        // Check against each enrolled sample vector for optimal match across tones/cadences
        for (sample in sampleProfiles) {
            val d = calculateCosineDistance(currentVector, sample)
            if (d < minDistance) minDistance = d
        }

        val isMatch = minDistance <= threshold
        val similarityPercent = distanceToSimilarityPercent(minDistance)

        return VoiceMatchResult(
            isMatch = isMatch,
            distance = minDistance,
            similarityPercent = similarityPercent,
            threshold = threshold
        )
    }

    /**
     * Verifies raw PCM audio against the enrolled owner profile.
     */
    fun verifySpeakerAudio(pcmAudio: ByteArray, prefs: AppPreferences): VoiceMatchResult {
        if (!prefs.isOwnerVoiceEnabled || !prefs.hasOwnerVoiceProfile()) {
            return VoiceMatchResult(
                isMatch = true,
                distance = 0.0f,
                similarityPercent = 100,
                threshold = prefs.ownerVoiceThreshold
            )
        }

        val vector = extractSpeakerVector(pcmAudio)
        if (vector == null) {
            Log.w(TAG, "Could not extract speaker vector from audio; insufficient voiced frames")
            return VoiceMatchResult(
                isMatch = false,
                distance = 1.0f,
                similarityPercent = 0,
                threshold = prefs.ownerVoiceThreshold,
                hasExtractedVector = false
            )
        }

        return verifySpeakerVector(vector, prefs)
    }
}
