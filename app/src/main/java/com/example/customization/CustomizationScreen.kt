package com.example.customization

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun CustomizationScreen(
    onBack: () -> Unit,
    onOpenVerseEditor: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isVerseEnabled by remember {
        mutableStateOf(VersePrefsManager.isVerseEnabled(context))
    }
    var baseBitmap by remember {
        mutableStateOf<Bitmap?>(null)
    }

    var yPct by remember { mutableFloatStateOf(VersePrefsManager.getVerseYPct(context)) }
    var scale by remember { mutableFloatStateOf(VersePrefsManager.getVerseScale(context)) }
    var style by remember { mutableStateOf(VersePrefsManager.getVerseStyle(context)) }
    var colorInt by remember { mutableStateOf(VersePrefsManager.getVerseColor(context)) }
    val verseText = remember { VersePrefsManager.getVerseText(context) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                yPct = VersePrefsManager.getVerseYPct(context)
                scale = VersePrefsManager.getVerseScale(context)
                style = VersePrefsManager.getVerseStyle(context)
                colorInt = VersePrefsManager.getVerseColor(context)
                coroutineScope.launch(Dispatchers.IO) {
                    val bmp = WallpaperVerseRenderer.getBaseWallpaperBitmap(context)
                    withContext(Dispatchers.Main) { baseBitmap = bmp }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler {
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2EFE6))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 40.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF1C1714),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                Text(
                    text = "Customization",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1714)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Card 1: Memory verse on lock screen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF7F4EC))
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Memory verse lock screen",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1C1714)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Displays your daily memory prayer directly on your lock screen wallpaper",
                        fontSize = 12.sp,
                        color = Color(0xFF8B7E72)
                    )
                }

                Switch(
                    checked = isVerseEnabled,
                    onCheckedChange = { newState ->
                        isVerseEnabled = newState
                        VersePrefsManager.setVerseEnabled(context, newState)
                        coroutineScope.launch {
                            WallpaperVerseRenderer.applyWallpaper(context)
                            val msg = if (newState) "Lock screen verse enabled" else "Lock screen verse disabled"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFC2714F),
                        uncheckedThumbColor = Color(0xFF8B7E72),
                        uncheckedTrackColor = Color(0xFFE2DED2)
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // PREVIEW CONTAINER showing wallpaper with verse
            Text(
                text = "Lock Screen Preview (Tap to customize)",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF8B7E72),
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0F172A))
                    .shadow(4.dp, RoundedCornerShape(24.dp))
                    .clickable { onOpenVerseEditor() }
            ) {
                val boxHeight = maxHeight

                // Display Real/Saved Wallpaper as Background
                baseBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Lockscreen Wallpaper",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Clock Placeholder
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "09:34",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.95f),
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.6f),
                                offset = Offset(0f, 2f),
                                blurRadius = 6f
                            )
                        )
                    )
                    Text(
                        text = "Tuesday, August 12",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                // Rendered Memory Verse Text at scaled Y%
                val previewScale = 260f / 800f // Rough ratio of preview height to actual screen height
                val fontSizeSp = (24 * scale * previewScale).sp

                val fontFamily = when (style) {
                    "Classic", "Serif Bold" -> FontFamily.Serif
                    "Monospace" -> FontFamily.Monospace
                    "Cursive" -> FontFamily.Cursive
                    else -> FontFamily.Default
                }

                val fontWeight = when (style) {
                    "Bold", "Serif Bold" -> FontWeight.Bold
                    "Light" -> FontWeight.Light
                    else -> FontWeight.Normal
                }
                
                // Track height of text to center it at yPct
                var textHeightPx by remember { mutableFloatStateOf(0f) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .offset {
                            val centerY = (boxHeight.toPx() * yPct).roundToInt()
                            val topY = centerY - (textHeightPx / 2f).roundToInt()
                            IntOffset(0, topY)
                        }
                        .onGloballyPositioned { coordinates ->
                            textHeightPx = coordinates.size.height.toFloat()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = verseText,
                        fontSize = fontSizeSp,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight,
                        color = Color(colorInt),
                        textAlign = TextAlign.Center,
                        lineHeight = (32 * scale * previewScale).sp,
                        style = TextStyle(
                            shadow = Shadow(
                                color = if (colorInt == android.graphics.Color.BLACK) Color.White.copy(alpha=0.6f) else Color.Black.copy(alpha = 0.9f),
                                offset = Offset(2f, 2f),
                                blurRadius = 6f
                            )
                        )
                    )
                }

                // Fingerprint Icon Placeholder (25% from bottom)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 65.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Unlock",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // Text Color Picker
            Text(
                text = "Text Color",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF8B7E72)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val colors = listOf(
                    0xFFFFFFFF.toInt(), // White
                    0xFF000000.toInt(), // Black
                    0xFFFFF2CC.toInt(), // Soft Yellow
                    0xFFFFD700.toInt(), // Warm Gold
                    0xFFA8E6CF.toInt(), // Mint Green
                    0xFF87CEEB.toInt(), // Sky Blue
                    0xFFFFB6C1.toInt()  // Rose Pink
                )
                colors.forEach { c ->
                    val isSelected = c == colorInt
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .clickable {
                                colorInt = c
                                VersePrefsManager.setVerseColor(context, c)
                                coroutineScope.launch { WallpaperVerseRenderer.applyWallpaper(context) }
                            }
                            .then(
                                if (isSelected) Modifier.background(Color.Black.copy(alpha = 0.2f)) // Selected state overlay
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Color.Transparent) // Use a subtle outline or just let the overlay show it's selected
                            Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(if (c == 0xFFFFFFFF.toInt()) Color.Black else Color.White))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // Size & Style Settings
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Size Picker
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Size",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF8B7E72)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val sizes = listOf("Small" to 0.7f, "Medium" to 1.0f, "Large" to 1.4f)
                    Column(
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White)
                    ) {
                        sizes.forEachIndexed { index, pair ->
                            val (label, valScale) = pair
                            val isSelected = Math.abs(scale - valScale) < 0.1f
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFFC2714F) else Color(0xFF1C1714),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scale = valScale
                                        VersePrefsManager.setVerseScale(context, valScale)
                                        coroutineScope.launch { WallpaperVerseRenderer.applyWallpaper(context) }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }
                }

                // Font Style Picker
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Font Style",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF8B7E72)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val styles = listOf("Modern", "Classic", "Bold", "Light", "Monospace", "Cursive", "Serif Bold")
                    Column(
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White)
                    ) {
                        styles.forEach { label ->
                            val isSelected = style == label
                            val itemFamily = when (label) {
                                "Classic", "Serif Bold" -> FontFamily.Serif
                                "Monospace" -> FontFamily.Monospace
                                "Cursive" -> FontFamily.Cursive
                                else -> FontFamily.Default
                            }
                            val itemWeight = when (label) {
                                "Bold", "Serif Bold" -> FontWeight.Bold
                                "Light" -> FontWeight.Light
                                else -> FontWeight.Normal
                            }
                            Text(
                                text = label,
                                fontSize = 16.sp,
                                fontFamily = itemFamily,
                                fontWeight = itemWeight,
                                color = if (isSelected) Color(0xFFC2714F) else Color(0xFF1C1714),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        style = label
                                        VersePrefsManager.setVerseStyle(context, label)
                                        coroutineScope.launch { WallpaperVerseRenderer.applyWallpaper(context) }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
