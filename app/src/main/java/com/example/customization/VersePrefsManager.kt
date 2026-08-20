package com.example.customization

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object VersePrefsManager {
    private const val TAG = "VersePrefsManager"
    private const val PREFS_NAME = "verse_customization_prefs"

    const val KEY_VERSE_ENABLED = "verse_enabled"
    const val KEY_VERSE_POSITION = "verse_position"
    const val KEY_VERSE_SIZE = "verse_size"
    const val KEY_VERSE_STYLE = "verse_style"
    const val KEY_VERSE_TEXT = "verse_text"
    const val KEY_VERSE_TIMESTAMP = "verse_timestamp"
    const val KEY_VERSE_Y_PCT = "verse_y_pct"
    const val KEY_VERSE_SCALE = "verse_scale"
    const val KEY_VERSE_COLOR = "verse_color"
    const val KEY_VERSE_SETUP_COMPLETE = "verse_setup_complete"

    // Draft Keys
    const val KEY_DRAFT_EXISTS = "draft_exists"
    const val KEY_DRAFT_COLOR = "draft_color"
    const val KEY_DRAFT_SIZE = "draft_size"
    const val KEY_DRAFT_STYLE = "draft_style"
    const val KEY_DRAFT_SCALE = "draft_scale"
    const val KEY_DRAFT_Y_PCT = "draft_y_pct"
    const val KEY_DRAFT_POSITION = "draft_position"

    const val DRAFT_WALLPAPER_FILE = "draft_wallpaper.png"
    const val BASE_WALLPAPER_FILE = "base_wallpaper.png"

    const val DEFAULT_POSITION = 62
    const val DEFAULT_SIZE = "Medium"
    const val DEFAULT_STYLE = "Classic"
    const val DEFAULT_VERSE_TEXT = "I can do all things through Christ who strengthens me. — Philippians 4:13"
    const val DEFAULT_Y_PCT = 0.62f
    const val DEFAULT_SCALE = 1.0f
    const val DEFAULT_COLOR = 0xFFFFFFFF.toInt() // White

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ─── SAVED SETTINGS ───

    fun isVerseEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_VERSE_ENABLED, false)
    }

    fun setVerseEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_VERSE_ENABLED, enabled).apply()
    }

    fun getVerseYPct(context: Context): Float {
        return getPrefs(context).getFloat(KEY_VERSE_Y_PCT, DEFAULT_Y_PCT)
    }

    fun setVerseYPct(context: Context, yPct: Float) {
        getPrefs(context).edit().putFloat(KEY_VERSE_Y_PCT, yPct).apply()
    }

    fun getVerseScale(context: Context): Float {
        return getPrefs(context).getFloat(KEY_VERSE_SCALE, DEFAULT_SCALE)
    }

    fun setVerseScale(context: Context, scale: Float) {
        getPrefs(context).edit().putFloat(KEY_VERSE_SCALE, scale).apply()
    }

    fun getVerseColor(context: Context): Int {
        return getPrefs(context).getInt(KEY_VERSE_COLOR, DEFAULT_COLOR)
    }

    fun setVerseColor(context: Context, color: Int) {
        getPrefs(context).edit().putInt(KEY_VERSE_COLOR, color).apply()
    }

    fun getVersePosition(context: Context): Int {
        return getPrefs(context).getInt(KEY_VERSE_POSITION, DEFAULT_POSITION)
    }

    fun setVersePosition(context: Context, position: Int) {
        getPrefs(context).edit().putInt(KEY_VERSE_POSITION, position.coerceIn(0, 100)).apply()
    }

    fun getVerseSize(context: Context): String {
        return getPrefs(context).getString(KEY_VERSE_SIZE, DEFAULT_SIZE) ?: DEFAULT_SIZE
    }

    fun setVerseSize(context: Context, size: String) {
        getPrefs(context).edit().putString(KEY_VERSE_SIZE, size).apply()
    }

    fun getVerseStyle(context: Context): String {
        return getPrefs(context).getString(KEY_VERSE_STYLE, DEFAULT_STYLE) ?: DEFAULT_STYLE
    }

    fun setVerseStyle(context: Context, style: String) {
        getPrefs(context).edit().putString(KEY_VERSE_STYLE, style).apply()
    }

    fun getVerseText(context: Context): String {
        return getPrefs(context).getString(KEY_VERSE_TEXT, DEFAULT_VERSE_TEXT) ?: DEFAULT_VERSE_TEXT
    }

    fun setVerseText(context: Context, text: String) {
        getPrefs(context).edit().putString(KEY_VERSE_TEXT, text).apply()
    }

    fun getVerseTimestamp(context: Context): Long {
        return getPrefs(context).getLong(KEY_VERSE_TIMESTAMP, 0L)
    }

    fun setVerseTimestamp(context: Context, timestamp: Long) {
        getPrefs(context).edit().putLong(KEY_VERSE_TIMESTAMP, timestamp).apply()
    }

    fun isVerseSetupComplete(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_VERSE_SETUP_COMPLETE, false)
    }

    fun setVerseSetupComplete(context: Context, complete: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_VERSE_SETUP_COMPLETE, complete).apply()
    }

    // ─── DRAFT MANAGEMENT ───

    fun hasDraft(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DRAFT_EXISTS, false)
    }

    fun setDraftExists(context: Context, exists: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DRAFT_EXISTS, exists).apply()
    }

    fun getDraftColor(context: Context): Int {
        val prefs = getPrefs(context)
        return if (prefs.contains(KEY_DRAFT_COLOR)) {
            prefs.getInt(KEY_DRAFT_COLOR, getVerseColor(context))
        } else {
            getVerseColor(context)
        }
    }

    fun setDraftColor(context: Context, color: Int) {
        getPrefs(context).edit()
            .putInt(KEY_DRAFT_COLOR, color)
            .putBoolean(KEY_DRAFT_EXISTS, true)
            .apply()
    }

    fun getDraftSize(context: Context): String {
        val prefs = getPrefs(context)
        return if (prefs.contains(KEY_DRAFT_SIZE)) {
            prefs.getString(KEY_DRAFT_SIZE, getVerseSize(context)) ?: getVerseSize(context)
        } else {
            getVerseSize(context)
        }
    }

    fun setDraftSize(context: Context, size: String) {
        getPrefs(context).edit()
            .putString(KEY_DRAFT_SIZE, size)
            .putBoolean(KEY_DRAFT_EXISTS, true)
            .apply()
    }

    fun getDraftStyle(context: Context): String {
        val prefs = getPrefs(context)
        return if (prefs.contains(KEY_DRAFT_STYLE)) {
            prefs.getString(KEY_DRAFT_STYLE, getVerseStyle(context)) ?: getVerseStyle(context)
        } else {
            getVerseStyle(context)
        }
    }

    fun setDraftStyle(context: Context, style: String) {
        getPrefs(context).edit()
            .putString(KEY_DRAFT_STYLE, style)
            .putBoolean(KEY_DRAFT_EXISTS, true)
            .apply()
    }

    fun getDraftScale(context: Context): Float {
        val prefs = getPrefs(context)
        return if (prefs.contains(KEY_DRAFT_SCALE)) {
            prefs.getFloat(KEY_DRAFT_SCALE, getVerseScale(context))
        } else {
            getVerseScale(context)
        }
    }

    fun setDraftScale(context: Context, scale: Float) {
        getPrefs(context).edit()
            .putFloat(KEY_DRAFT_SCALE, scale)
            .putBoolean(KEY_DRAFT_EXISTS, true)
            .apply()
    }

    fun getDraftYPct(context: Context): Float {
        val prefs = getPrefs(context)
        return if (prefs.contains(KEY_DRAFT_Y_PCT)) {
            prefs.getFloat(KEY_DRAFT_Y_PCT, getVerseYPct(context))
        } else {
            getVerseYPct(context)
        }
    }

    fun setDraftYPct(context: Context, yPct: Float) {
        getPrefs(context).edit()
            .putFloat(KEY_DRAFT_Y_PCT, yPct)
            .putBoolean(KEY_DRAFT_EXISTS, true)
            .apply()
    }

    // ─── DRAFT WALLPAPER FILE HANDLING ───

    fun hasDraftWallpaper(context: Context): Boolean {
        val file = File(context.filesDir, DRAFT_WALLPAPER_FILE)
        return file.exists() && file.length() > 0
    }

    fun saveDraftWallpaper(context: Context, bitmap: Bitmap) {
        try {
            val file = File(context.filesDir, DRAFT_WALLPAPER_FILE)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            setDraftExists(context, true)
            Log.i(TAG, "Saved draft_wallpaper.png (${bitmap.width}x${bitmap.height})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving draft_wallpaper.png: ${e.message}", e)
        }
    }

    fun getDraftWallpaperBitmap(context: Context): Bitmap? {
        val file = File(context.filesDir, DRAFT_WALLPAPER_FILE)
        if (file.exists() && file.length() > 0) {
            try {
                return BitmapFactory.decodeFile(file.absolutePath)
            } catch (e: Exception) {
                Log.w(TAG, "Error decoding draft_wallpaper.png: ${e.message}")
            }
        }
        return null
    }

    fun deleteDraftWallpaper(context: Context) {
        try {
            val file = File(context.filesDir, DRAFT_WALLPAPER_FILE)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting draft_wallpaper.png: ${e.message}")
        }
    }

    // ─── EFFECTIVE VALUES (DRAFT IF ACTIVE, ELSE SAVED) ───

    fun getEffectiveVerseColor(context: Context): Int {
        return if (hasDraft(context)) getDraftColor(context) else getVerseColor(context)
    }

    fun getEffectiveVerseSize(context: Context): String {
        return if (hasDraft(context)) getDraftSize(context) else getVerseSize(context)
    }

    fun getEffectiveVerseStyle(context: Context): String {
        return if (hasDraft(context)) getDraftStyle(context) else getVerseStyle(context)
    }

    fun getEffectiveVerseScale(context: Context): Float {
        return if (hasDraft(context)) getDraftScale(context) else getVerseScale(context)
    }

    fun getEffectiveVerseYPct(context: Context): Float {
        return if (hasDraft(context)) getDraftYPct(context) else getVerseYPct(context)
    }

    suspend fun getEffectiveWallpaperBitmap(context: Context): Bitmap = withContext(Dispatchers.IO) {
        val draftBmp = getDraftWallpaperBitmap(context)
        if (draftBmp != null) {
            return@withContext draftBmp
        }
        WallpaperVerseRenderer.getBaseWallpaperBitmap(context)
    }

    // ─── COMMIT & DISCARD ───

    suspend fun commitDraft(context: Context) = withContext(Dispatchers.IO) {
        val draftFile = File(context.filesDir, DRAFT_WALLPAPER_FILE)
        if (draftFile.exists() && draftFile.length() > 0) {
            val baseFile = File(context.filesDir, BASE_WALLPAPER_FILE)
            draftFile.copyTo(baseFile, overwrite = true)
            draftFile.delete()
            Log.i(TAG, "Committed draft_wallpaper.png to base_wallpaper.png")
        }

        val finalColor = getDraftColor(context)
        val finalSize = getDraftSize(context)
        val finalStyle = getDraftStyle(context)
        val finalScale = getDraftScale(context)
        val finalYPct = getDraftYPct(context)

        getPrefs(context).edit()
            .putInt(KEY_VERSE_COLOR, finalColor)
            .putString(KEY_VERSE_SIZE, finalSize)
            .putString(KEY_VERSE_STYLE, finalStyle)
            .putFloat(KEY_VERSE_SCALE, finalScale)
            .putFloat(KEY_VERSE_Y_PCT, finalYPct)
            .putBoolean(KEY_VERSE_SETUP_COMPLETE, true)
            .putBoolean(KEY_VERSE_ENABLED, true)
            .putBoolean(KEY_DRAFT_EXISTS, false)
            .remove(KEY_DRAFT_COLOR)
            .remove(KEY_DRAFT_SIZE)
            .remove(KEY_DRAFT_STYLE)
            .remove(KEY_DRAFT_SCALE)
            .remove(KEY_DRAFT_Y_PCT)
            .apply()

        WallpaperVerseRenderer.applyWallpaper(context)
        Log.i(TAG, "[VERSE] Saved")
    }

    fun discardDraft(context: Context) {
        deleteDraftWallpaper(context)
        getPrefs(context).edit()
            .putBoolean(KEY_DRAFT_EXISTS, false)
            .remove(KEY_DRAFT_COLOR)
            .remove(KEY_DRAFT_SIZE)
            .remove(KEY_DRAFT_STYLE)
            .remove(KEY_DRAFT_SCALE)
            .remove(KEY_DRAFT_Y_PCT)
            .apply()
        Log.i(TAG, "[VERSE] Draft discarded")
    }
}

