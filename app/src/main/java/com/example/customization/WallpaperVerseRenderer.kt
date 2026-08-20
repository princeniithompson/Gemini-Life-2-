package com.example.customization

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import android.util.TypedValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object WallpaperVerseRenderer {
    private const val TAG = "WallpaperVerseRenderer"

    @Volatile
    var isSelfUpdatingWallpaper: Boolean = false

    fun saveBaseWallpaper(context: Context, bitmap: Bitmap) {
        try {
            val file = File(context.filesDir, "base_wallpaper.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Log.i(TAG, "Saved base_wallpaper.png (${bitmap.width}x${bitmap.height})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving base_wallpaper.png: ${e.message}", e)
        }
    }

    suspend fun getBaseWallpaperBitmap(context: Context): Bitmap = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "base_wallpaper.png")
        if (file.exists() && file.length() > 0) {
            try {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) return@withContext bmp
            } catch (e: Exception) {
                Log.w(TAG, "Error decoding saved base_wallpaper.png: ${e.message}")
            }
        }
        val wallpaperManager = WallpaperManager.getInstance(context)
        val drawable = readTrueCurrentWallpaper(wallpaperManager)
        if (drawable != null) {
            val bmp = drawableToBitmap(drawable)
            saveBaseWallpaper(context, bmp)
            return@withContext bmp
        }
        createFallbackBackgroundBitmap()
    }

    suspend fun applyWallpaper(context: Context): Boolean = withContext(NonCancellable + Dispatchers.IO) {
        try {
            val enabled = VersePrefsManager.isVerseEnabled(context)
            val wallpaperManager = WallpaperManager.getInstance(context)

            if (!enabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    try {
                        wallpaperManager.clear(WallpaperManager.FLAG_LOCK)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error clearing FLAG_LOCK: ${e.message}")
                    }
                } else {
                    try {
                        wallpaperManager.clear()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error clearing wallpaper: ${e.message}")
                    }
                }
                Log.i(TAG, "[WALLPAPER] Lock wallpaper cleared - original restored")
                return@withContext true
            }

            val text = VersePrefsManager.getVerseText(context)
            val yPct = VersePrefsManager.getVerseYPct(context)
            val scale = VersePrefsManager.getVerseScale(context)
            val style = VersePrefsManager.getVerseStyle(context)
            val colorInt = VersePrefsManager.getVerseColor(context)

            val baseBitmap = getBaseWallpaperBitmap(context)

            val renderedBitmap = renderVerseOnBitmap(
                context = context,
                baseBitmap = baseBitmap,
                verseText = text,
                yPct = yPct,
                scale = scale,
                stylePreset = style,
                colorInt = colorInt
            )

            // Apply directly to Lock Screen ONLY
            isSelfUpdatingWallpaper = true
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    wallpaperManager.setBitmap(renderedBitmap, null, false, WallpaperManager.FLAG_LOCK)
                } else {
                    wallpaperManager.setBitmap(renderedBitmap)
                }
                Log.i(TAG, "Successfully rendered prayer onto lock screen wallpaper")
            } finally {
                delay(1000)
                isSelfUpdatingWallpaper = false
            }

            true
        } catch (e: CancellationException) {
            isSelfUpdatingWallpaper = false
            false
        } catch (e: Exception) {
            isSelfUpdatingWallpaper = false
            Log.e(TAG, "Error applying wallpaper: ${e.message}", e)
            false
        }
    }

    private fun readTrueCurrentWallpaper(wallpaperManager: WallpaperManager): Drawable? {
        // 1. Try lock screen drawable if available
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val pfd = wallpaperManager.getWallpaperFile(WallpaperManager.FLAG_LOCK)
                if (pfd != null) {
                    pfd.use {
                        val bmp = BitmapFactory.decodeFileDescriptor(it.fileDescriptor)
                        if (bmp != null) return BitmapDrawable(null, bmp)
                    }
                }
                val d = wallpaperManager.getBuiltInDrawable(WallpaperManager.FLAG_LOCK)
                if (d != null) return d
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reading LOCK wallpaper file: ${e.message}")
        }

        // 2. Try system wallpaper drawable
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val pfd = wallpaperManager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)
                if (pfd != null) {
                    pfd.use {
                        val bmp = BitmapFactory.decodeFileDescriptor(it.fileDescriptor)
                        if (bmp != null) return BitmapDrawable(null, bmp)
                    }
                }
            }
            val d = wallpaperManager.drawable
            if (d != null) return d
        } catch (e: Exception) {
            Log.w(TAG, "Error reading SYSTEM wallpaper drawable: ${e.message}")
        }

        // 3. Try peekDrawable
        try {
            val d = wallpaperManager.peekDrawable()
            if (d != null) return d
        } catch (e: Exception) {
            Log.w(TAG, "Error peeking wallpaper drawable: ${e.message}")
        }

        return null
    }

    fun hasBaseWallpaper(context: Context): Boolean {
        val file = File(context.filesDir, "base_wallpaper.png")
        return file.exists() && file.length() > 0
    }

    fun cropToScreenRatio(original: Bitmap, screenWidth: Int, screenHeight: Int): Bitmap {
        val originalRatio = original.width.toFloat() / original.height.toFloat()
        val targetRatio = screenWidth.toFloat() / screenHeight.toFloat()
        
        var x = 0
        var y = 0
        var width = original.width
        var height = original.height

        if (originalRatio > targetRatio) {
            // Image is wider than screen
            width = (original.height * targetRatio).toInt().coerceAtLeast(1)
            x = ((original.width - width) / 2).coerceAtLeast(0)
        } else {
            // Image is taller than screen
            height = (original.width / targetRatio).toInt().coerceAtLeast(1)
            y = ((original.height - height) / 2).coerceAtLeast(0)
        }
        return Bitmap.createBitmap(original, x, y, width.coerceAtMost(original.width - x), height.coerceAtMost(original.height - y))
    }

    fun createFallbackBackgroundBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.shader = LinearGradient(
            0f, 0f, 0f, 2400f,
            intArrayOf(Color.parseColor("#0F172A"), Color.parseColor("#1E293B"), Color.parseColor("#090D16")),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, 1080f, 2400f, paint)
        return bitmap
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = drawable.intrinsicWidth.coerceAtLeast(1080)
        val height = drawable.intrinsicHeight.coerceAtLeast(2160)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    fun renderVerseOnBitmap(
        context: Context,
        baseBitmap: Bitmap,
        verseText: String,
        yPct: Float,
        scale: Float,
        stylePreset: String,
        colorInt: Int
    ): Bitmap {
        val width = baseBitmap.width
        val height = baseBitmap.height
        val mutableBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)

        // Ensure we scale properly relative to a baseline 1080p screen density
        val scaleFactor = width / 1080f
        
        // Exact same margin logic as UI to match bounds
        val marginPx = 32f * context.resources.displayMetrics.density * scaleFactor
        val maxTextWidth = (width - (marginPx * 2)).coerceAtLeast(300f)

        // Base text size 24sp equivalent * scale
        val baseTextSizeSp = 24f * scale
        val textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            baseTextSizeSp,
            context.resources.displayMetrics
        ) * scaleFactor

        val typeface = when (stylePreset) {
            "Classic" -> Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            "Bold" -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            "Light" -> Typeface.create("sans-serif-light", Typeface.NORMAL)
            "Monospace" -> Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            "Cursive" -> Typeface.create("cursive", Typeface.NORMAL)
            "Serif Bold" -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
            else -> Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) // Modern
        }

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorInt
            this.textSize = textSizePx
            this.typeface = typeface
            
            // Adjust shadow based on text color for better contrast
            val shadowAlpha = if (colorInt == Color.BLACK) 80 else 150
            val shadowColor = if (colorInt == Color.BLACK) Color.WHITE else Color.BLACK
            
            setShadowLayer(
                12f * scaleFactor,
                2f * scaleFactor,
                4f * scaleFactor,
                Color.argb(shadowAlpha, Color.red(shadowColor), Color.green(shadowColor), Color.blue(shadowColor))
            )
        }

        fun buildLayout(px: Float): StaticLayout {
            textPaint.textSize = px
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(verseText, 0, verseText.length, textPaint, maxTextWidth.toInt())
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setLineSpacing(0f, 1.33f) // 32sp line height / 24sp text size = ~1.33
                    .setIncludePad(true)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(verseText, textPaint, maxTextWidth.toInt(), Layout.Alignment.ALIGN_CENTER, 1.33f, 0f, true)
            }
        }

        val layout = buildLayout(textSizePx)

        // Y-Position comes exactly from user gesture state (yPct is the center of the text)
        val targetCenterY = height * yPct
        val layoutHeight = layout.height.toFloat()
        
        // Prevent going off edges (enforce boundaries)
        val startY = (targetCenterY - (layoutHeight / 2f)).coerceIn(0f, height - layoutHeight)

        canvas.save()
        canvas.translate((width - maxTextWidth) / 2f, startY)
        layout.draw(canvas)
        canvas.restore()

        return mutableBitmap
    }
}
