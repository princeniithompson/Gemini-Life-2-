// Runtime-downloaded guide images. Never bundle as drawables. Never delete this loader.
package com.example.ui.key

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

enum class GuideImageType(
    val filename: String,
    val url: String,
    val fallbackCaption: String
) {
    TOS(
        filename = "guide_tos.png",
        url = "https://files.catbox.moe/o0c4pm.png",
        fallbackCaption = "1-2. Tick the box, then tap Continue. The emails box is optional."
    ),
    KEYS(
        filename = "guide_keys.jpg",
        url = "https://files.catbox.moe/b8wcyq.jpg",
        fallbackCaption = "3. Tap the copy icon to copy your key. If the page is empty, tap Create API key first. Free tier means you never pay."
    ),
    JESUS_LAMB(
        filename = "jesus_lamb.png",
        url = "https://files.catbox.moe/fbrlan.png",
        fallbackCaption = "Jesus with Lamb"
    )
}

object GuideImageLoader {
    private const val TAG = "GuideImageLoader"
    private const val MAX_WIDTH = 1080

    fun preload(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "Starting preload for guide images and assets...")
                for (type in GuideImageType.values()) {
                    loadGuideBitmap(context, type)
                }
                Log.i(TAG, "Preload completed successfully.")
            } catch (e: Throwable) {
                Log.w(TAG, "Preload error: ${e.message}")
            }
        }
    }

    suspend fun loadGuideBitmap(context: Context, type: GuideImageType): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "guides")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val targetFile = File(cacheDir, type.filename)

            if (!targetFile.exists() || targetFile.length() == 0L) {
                Log.i(TAG, "[IMAGE] Cache miss for ${type.filename}. Downloading from ${type.url}...")
                val tempFile = File(cacheDir, "${type.filename}.tmp")
                val url = URL(type.url)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 15000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "FirstLight-Android")
                }
                connection.connect()
                if (connection.responseCode in 200..299) {
                    connection.inputStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tempFile.exists() && tempFile.length() > 0) {
                        tempFile.renameTo(targetFile)
                        Log.i(TAG, "[IMAGE] Successfully downloaded and cached ${type.filename} (${targetFile.length()} bytes)")
                    }
                } else {
                    Log.e(TAG, "[IMAGE] Failed downloading ${type.filename}, HTTP response: ${connection.responseCode}")
                }
                connection.disconnect()
            } else {
                Log.i(TAG, "[IMAGE] Cache hit for ${type.filename} (${targetFile.length()} bytes)")
            }

            if (targetFile.exists() && targetFile.length() > 0) {
                Log.i(TAG, "[IMAGE] Decoding ${type.filename} (max width: $MAX_WIDTH)...")
                val boundsOptions = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(targetFile.absolutePath, boundsOptions)

                var sampleSize = 1
                if (boundsOptions.outWidth > MAX_WIDTH) {
                    sampleSize = (boundsOptions.outWidth + MAX_WIDTH - 1) / MAX_WIDTH
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize.coerceAtLeast(1)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = BitmapFactory.decodeFile(targetFile.absolutePath, decodeOptions)
                if (bitmap != null) {
                    Log.i(TAG, "[IMAGE] Successfully decoded ${type.filename} (${bitmap.width}x${bitmap.height})")
                } else {
                    Log.w(TAG, "[IMAGE] Failed to decode bitmap from ${targetFile.absolutePath}")
                }
                return@withContext bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading image ${type.filename}: ${e.message}", e)
        }
        null
    }
}

@Composable
fun ZoomableImageDialog(
    bitmap: ImageBitmap,
    contentDescription: String,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF01A1412))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        if (scale <= 1f) {
                            offset = Offset.Zero
                        } else {
                            val maxOffsetX = (size.width * (scale - 1f)) / 2f
                            val maxOffsetY = (size.height * (scale - 1f)) / 2f
                            offset = Offset(
                                x = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                                y = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                            )
                        }
                    }
                }
        ) {
            // Centered Image with scale & offset
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )

            // Top action bar with Zoom toggle and Close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0x99000000),
                    modifier = Modifier.clickable {
                        if (scale > 1.1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2f
                        }
                    }
                ) {
                    Text(
                        text = if (scale > 1.1f) "${"%.1f".format(scale)}x (Reset)" else "1x (Tap 2x)",
                        color = Color(0xFFFDFCF8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0x99000000), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close full view",
                        tint = Color(0xFFFDFCF8),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GuideImageCard(
    type: GuideImageType,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }
    var showZoomDialog by remember { mutableStateOf(false) }

    LaunchedEffect(type) {
        val decoded = GuideImageLoader.loadGuideBitmap(context, type)
        if (decoded != null) {
            bitmap = decoded.asImageBitmap()
            isError = false
        } else {
            isError = true
        }
        isLoading = false
    }

    if (showZoomDialog && bitmap != null) {
        ZoomableImageDialog(
            bitmap = bitmap!!,
            contentDescription = contentDescription,
            onDismiss = { showZoomDialog = false }
        )
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFE8E0D4)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F4EC)),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = bitmap != null) {
                showZoomDialog = true
            }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = Color(0xFFB4574E),
                        strokeWidth = 2.5.dp
                    )
                }
                bitmap != null -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            bitmap = bitmap!!,
                            contentDescription = contentDescription,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0x88000000),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Tap to zoom",
                                color = Color(0xFFFDFCF8),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                else -> {
                    // Fallback Text Card on any failure
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = type.fallbackCaption,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF2C2420),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

