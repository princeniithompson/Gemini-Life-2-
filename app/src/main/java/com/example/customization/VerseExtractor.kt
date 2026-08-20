package com.example.customization

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VerseExtractor {
    private const val TAG = "VerseExtractor"

    fun extractVerse(fullAiText: String): String? {
        val trimmedFull = fullAiText.trim()
        if (trimmedFull.isBlank()) return null

        // 1. Prefer text after "Carry this with you today:" (case-insensitive)
        val markerKeywords = listOf(
            "carry this with you today:",
            "carry this with you today",
            "carry this with you:",
            "carry this with you"
        )

        val lowerText = trimmedFull.lowercase()
        for (marker in markerKeywords) {
            val idx = lowerText.indexOf(marker)
            if (idx != -1) {
                val extracted = trimmedFull.substring(idx + marker.length)
                    .trim()
                    .removePrefix(":")
                    .removePrefix("-")
                    .removePrefix("—")
                    .removePrefix("\"")
                    .removeSuffix("\"")
                    .trim()
                if (extracted.isNotBlank()) {
                    return extracted
                }
            }
        }

        // 2. Fallback: last non-empty spoken sentence
        val sentences = trimmedFull.split(Regex("[.!?\n]+"))
            .map { it.trim().removePrefix("\"").removeSuffix("\"").trim() }
            .filter { it.isNotBlank() }

        if (sentences.isNotEmpty()) {
            return sentences.last()
        }

        return null
    }

    suspend fun onPrayerCompleted(context: Context, fullAiText: String) = withContext(Dispatchers.IO) {
        try {
            val extractedVerse = extractVerse(fullAiText)
            if (!extractedVerse.isNullOrBlank()) {
                VersePrefsManager.setVerseText(context, extractedVerse)
                VersePrefsManager.setVerseTimestamp(context, System.currentTimeMillis())
                Log.i(TAG, "Extracted new memory verse on prayer completion: \"$extractedVerse\"")

                // Immediately re-composite & apply wallpaper if verse enabled
                WallpaperVerseRenderer.applyWallpaper(context)
            } else {
                Log.i(TAG, "Output transcription empty or no verse found; keeping previous verse.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onPrayerCompleted verse extraction: ${e.message}", e)
        }
    }
}
