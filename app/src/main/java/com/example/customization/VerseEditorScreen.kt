package com.example.customization

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun VerseEditorScreen(
    onBack: (isFirstSave: Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    var yPct by remember { mutableFloatStateOf(VersePrefsManager.getEffectiveVerseYPct(context)) }
    var scale by remember { mutableFloatStateOf(VersePrefsManager.getEffectiveVerseScale(context)) }
    val verseText = remember { VersePrefsManager.getVerseText(context) }
    val colorInt = remember { VersePrefsManager.getEffectiveVerseColor(context) }
    val stylePreset = remember { VersePrefsManager.getEffectiveVerseStyle(context) }
    var baseBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showUi by remember { mutableStateOf(true) }
    var isInteracting by remember { mutableStateOf(false) }
    var textHeightPx by remember { mutableFloatStateOf(0f) }
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            baseBitmap = VersePrefsManager.getEffectiveWallpaperBitmap(context)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        if (bitmap != null) {
                            val metrics = context.resources.displayMetrics
                            val cropped = WallpaperVerseRenderer.cropToScreenRatio(bitmap, metrics.widthPixels, metrics.heightPixels)
                            VersePrefsManager.saveDraftWallpaper(context, cropped)
                            baseBitmap = cropped
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Wallpaper draft updated", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("VerseEditor", "Failed loading image: ${e.message}")
                }
            }
        }
    }

    BackHandler { onBack(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                if (!isInteracting) {
                    showUi = !showUi
                }
            }
    ) {
        val screenHeightPx = constraints.maxHeight.toFloat()

        // 1. Background Wallpaper
        baseBitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Wallpaper",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } ?: Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A)))

        // Top Back Button Overlay
        androidx.compose.animation.AnimatedVisibility(
            visible = showUi && !isInteracting,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp, start = 20.dp),
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { onBack(false) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // 2. Lock Screen Overlays (Clock & Fingerprint)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "09:41",
                fontSize = 72.sp,
                fontWeight = FontWeight.Light,
                color = Color.White.copy(alpha = 0.95f),
                style = TextStyle(shadow = Shadow(color = Color.Black.copy(alpha = 0.3f), blurRadius = 8f))
            )
            Text(
                text = "Wed, 12 Aug",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.9f),
                style = TextStyle(shadow = Shadow(color = Color.Black.copy(alpha = 0.3f), blurRadius = 8f))
            )
        }

        if (showUi && !isInteracting) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = (screenHeightPx / density.density * 0.25f).dp)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = "Unlock",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // 3. The Verse Text (Draggable & Zoomable)
        val typeface = when (stylePreset) {
            "Classic", "Serif Bold" -> FontFamily.Serif
            "Monospace" -> FontFamily.Monospace
            "Cursive" -> FontFamily.Cursive
            else -> FontFamily.Default
        }
        val fontWeight = when (stylePreset) {
            "Bold", "Serif Bold" -> FontWeight.Bold
            "Light" -> FontWeight.Light
            else -> FontWeight.Normal
        }
        val fontStyle = if (stylePreset == "Classic") FontStyle.Italic else FontStyle.Normal

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, (yPct * screenHeightPx).roundToInt() - (textHeightPx / 2f).roundToInt()) }
                .padding(horizontal = 32.dp)
                .onGloballyPositioned { coordinates ->
                    textHeightPx = coordinates.size.height.toFloat()
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 3.0f)
                        
                        val proposedY = (yPct * screenHeightPx) + pan.y
                        // Strictly bound to screen height to prevent going off edges
                        val minY = textHeightPx / 2f
                        val maxY = screenHeightPx - (textHeightPx / 2f)
                        yPct = proposedY.coerceIn(minY, maxY) / screenHeightPx

                        VersePrefsManager.setDraftScale(context, scale)
                        VersePrefsManager.setDraftYPct(context, yPct)
                    }
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            isInteracting = event.changes.any { it.pressed }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = verseText,
                fontSize = (24 * scale).sp,
                fontFamily = typeface,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                color = Color(colorInt),
                textAlign = TextAlign.Center,
                lineHeight = (32 * scale).sp,
                style = TextStyle(
                    shadow = Shadow(
                        color = if (colorInt == android.graphics.Color.BLACK) Color.White.copy(alpha=0.6f) else Color.Black.copy(alpha = 0.6f),
                        offset = Offset(2f, 4f),
                        blurRadius = 12f
                    )
                )
            )
        }

        // 4. Edit Instructions Overlay (above buttons)
        androidx.compose.animation.AnimatedVisibility(
            visible = showUi && !isInteracting,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp),
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            Text(
                text = "Pinch to resize • Drag to move",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(shadow = Shadow(color = Color.Black, blurRadius = 8f))
            )
        }

        // 5. Bottom Action Bar (Done / Background)
        androidx.compose.animation.AnimatedVisibility(
            visible = showUi && !isInteracting,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(Icons.Default.Image, contentDescription = "Background", tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Background", color = Color.White)
                }

                Button(
                    onClick = {
                        isProcessing = true
                        val isFirstSave = !VersePrefsManager.isVerseSetupComplete(context)
                        // Save exact position and scale to DRAFT
                        VersePrefsManager.setDraftYPct(context, yPct)
                        VersePrefsManager.setDraftScale(context, scale)
                        if (!isFirstSave) {
                            coroutineScope.launch(Dispatchers.IO) {
                                VersePrefsManager.commitDraft(context)
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    onBack(false)
                                }
                            }
                        } else {
                            isProcessing = false
                            onBack(true)
                        }
                    },
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB4574E),
                        disabledContainerColor = Color(0xFFB4574E).copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Done", tint = Color(0xFFFDFCF8))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Done",
                        color = Color(0xFFFDFCF8),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
