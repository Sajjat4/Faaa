package com.jarvis.assistant.util

import java.util.Locale
import kotlin.math.min

/**
 * Centralized string similarity and text processing utilities.
 * Eliminates duplicate Levenshtein distance calculations across ContactResolver and other modules.
 */
object StringSimilarityUtils {

    /**
     * Calculate Levenshtein distance between two strings.
     * Used for fuzzy string matching in contact resolution and command parsing.
     */
    fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        var cost = IntArray(lhs.length + 1) { it }
        var newCost = IntArray(lhs.length + 1) { 0 }

        for (i in 1..rhs.length) {
            newCost[0] = i
            for (j in 1..lhs.length) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = min(min(costInsert, costDelete), costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhs.length]
    }

    /**
     * Calculate similarity score (0.0 to 1.0) between two strings using Levenshtein distance.
     */
    fun calculateSimilarity(s1: String, s2: String): Float {
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1
        if (longer.isEmpty()) return 1.0f

        val dist = levenshteinDistance(longer, shorter)
        return (longer.length - dist).toFloat() / longer.length.toFloat()
    }

    /**
     * Clean and normalize text for fingerprinting.
     * Removes punctuation, converts to lowercase, and handles whitespace.
     */
    fun normalizeText(text: String): String {
        return text.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N} ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Generate a fingerprint from text by filtering stopwords and sorting unique tokens.
     * Useful for detecting duplicate or similar updates in speech.
     */
    fun generateFingerprint(text: String, stopwords: Set<String> = DEFAULT_STOPWORDS): String {
        val clean = normalizeText(text)
        if (clean.isBlank()) return ""
        return clean.split(' ')
            .filter { it.length > 2 && it !in stopwords }
            .distinct()
            .sorted()
            .joinToString(" ")
    }

    /**
     * Check if two fingerprints are similar (by word overlap).
     */
    fun areSimilarFingerprints(first: String, second: String, threshold: Float = 0.62f): Boolean {
        if (first == second) return true
        val a = first.split(' ').filter(String::isNotBlank).toSet()
        val b = second.split(' ').filter(String::isNotBlank).toSet()
        if (a.isEmpty() || b.isEmpty()) return false
        val overlap = a.intersect(b).size.toFloat() / a.union(b).size.toFloat()
        return overlap >= threshold
    }

    private val DEFAULT_STOPWORDS = setOf(
        "the", "a", "an", "i", "am", "is", "are", "it", "to", "and",
        "now", "just", "please", "your", "this", "that", "on", "in", "for", "with"
    )
}
