package com.example.ui.key

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.MainActivity
import com.example.api.ApiKeyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.ContextWrapper
import java.io.File

private fun Context.findMainActivity(): MainActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is MainActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private const val AI_STUDIO_KEY_URL = "https://aistudio.google.com/app/apikey"

private enum class KeySetupStep {
    CHOICES,
    VIDEO,
    PASTE
}

@Composable
private fun PoppedOutKeyOrb(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(96.dp)
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                ambientColor = Color(0x4DB4574E),
                spotColor = Color(0x40B4574E)
            )
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFFFFDF8),
                        Color(0xFFF7ECE0),
                        Color(0xFFEBD7C5)
                    ),
                    center = Offset(0.35f, 0.3f)
                )
            )
            .border(
                width = 2.5.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color(0xFFFFFDFC),
                        Color(0xFFF2DFD2),
                        Color(0xFFD9B9A6)
                    )
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        // Specular highlight gloss at top-left
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color(0x66FFFFFF),
                radius = size.minDimension * 0.28f,
                center = Offset(size.width * 0.35f, size.height * 0.3f)
            )
        }

        // Key icon in rich terracotta
        Icon(
            imageVector = Icons.Filled.Key,
            contentDescription = null,
            tint = Color(0xFFB4574E),
            modifier = Modifier.size(42.dp)
        )

        // Sparkle star ✦ at top-right of the key
        Text(
            text = "✦",
            color = Color(0xFFE8B87A),
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 20.dp, end = 22.dp)
        )
    }
}

@Composable
private fun WheatSprigDecorative(
    modifier: Modifier = Modifier,
    tint: Color = Color(0x38B4574E)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stemPath = Path().apply {
            moveTo(w * 0.2f, h * 0.95f)
            quadraticBezierTo(w * 0.45f, h * 0.5f, w * 0.8f, h * 0.08f)
        }
        drawPath(
            path = stemPath,
            color = tint,
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        )

        // Leaf pairs
        val leafTValues = listOf(
            Pair(0.32f, -1),
            Pair(0.44f, 1),
            Pair(0.58f, -1),
            Pair(0.70f, 1),
            Pair(0.82f, 0)
        )
        for ((t, side) in leafTValues) {
            val lx = w * (0.2f + 0.6f * t)
            val ly = h * (0.95f - 0.87f * t)
            val leafPath = Path().apply {
                moveTo(lx, ly)
                val tipX = if (side == 0) lx + 3.dp.toPx() else lx + (side * 10.dp.toPx())
                val tipY = ly - 9.dp.toPx()
                val ctrlX = if (side == 0) lx - 2.dp.toPx() else lx + (side * 12.dp.toPx())
                val ctrlY = ly - 3.dp.toPx()
                quadraticBezierTo(ctrlX, ctrlY, tipX, tipY)
                quadraticBezierTo(lx, ly - 3.dp.toPx(), lx, ly)
                close()
            }
            drawPath(path = leafPath, color = tint)
        }
    }
}

@Composable
private fun SunburstAndAtmosphereCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = 64.dp.toPx()

        // Radial warm sunburst glow behind orb
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x52F9E6C8),
                    Color(0x29F4DEC2),
                    Color(0x00FDFCF8)
                ),
                center = Offset(cx, cy),
                radius = 160.dp.toPx()
            ),
            radius = 160.dp.toPx(),
            center = Offset(cx, cy)
        )

        // Subtle sunburst rays emanating upward and around
        val rayCount = 13
        for (i in 0 until rayCount) {
            val angleDeg = -160f + (140f / (rayCount - 1)) * i
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val startR = 48.dp.toPx()
            val endR = 145.dp.toPx()
            val sx = cx + (startR * Math.cos(angleRad)).toFloat()
            val sy = cy + (startR * Math.sin(angleRad)).toFloat()
            val ex = cx + (endR * Math.cos(angleRad)).toFloat()
            val ey = cy + (endR * Math.sin(angleRad)).toFloat()
            drawLine(
                color = Color(0x30E8B87A),
                start = Offset(sx, sy),
                end = Offset(ex, ey),
                strokeWidth = 1.2.dp.toPx()
            )
        }

        // Dreamy cloud puffs behind orb
        drawOval(
            color = Color(0x33F2E6D6),
            topLeft = Offset(cx - 130.dp.toPx(), cy - 8.dp.toPx()),
            size = Size(85.dp.toPx(), 42.dp.toPx())
        )
        drawOval(
            color = Color(0x2BF2E6D6),
            topLeft = Offset(cx + 45.dp.toPx(), cy - 12.dp.toPx()),
            size = Size(90.dp.toPx(), 45.dp.toPx())
        )

        // Sparkle stars ✦
        fun drawSparkle(x: Float, y: Float, starSize: Float, color: Color) {
            val path = Path().apply {
                moveTo(x, y - starSize)
                quadraticBezierTo(x, y, x + starSize, y)
                quadraticBezierTo(x, y, x, y + starSize)
                quadraticBezierTo(x, y, x - starSize, y)
                quadraticBezierTo(x, y, x, y - starSize)
                close()
            }
            drawPath(path = path, color = color)
        }

        drawSparkle(cx - 78.dp.toPx(), cy - 25.dp.toPx(), 7.dp.toPx(), Color(0xCCE8B87A))
        drawSparkle(cx + 82.dp.toPx(), cy - 20.dp.toPx(), 6.5.dp.toPx(), Color(0xCCD98A84))
        drawSparkle(cx + 105.dp.toPx(), cy + 24.dp.toPx(), 5.dp.toPx(), Color(0xB3E8B87A))
        drawSparkle(cx - 95.dp.toPx(), cy + 20.dp.toPx(), 4.5.dp.toPx(), Color(0x99D98A84))

        // Bottom organic landscape hills / waves
        val wavePath1 = Path().apply {
            moveTo(0f, h - 36.dp.toPx())
            cubicTo(
                w * 0.35f, h - 52.dp.toPx(),
                w * 0.7f, h - 20.dp.toPx(),
                w, h - 40.dp.toPx()
            )
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = wavePath1,
            color = Color(0x24B4574E)
        )

        val wavePath2 = Path().apply {
            moveTo(0f, h - 18.dp.toPx())
            cubicTo(
                w * 0.4f, h - 30.dp.toPx(),
                w * 0.75f, h - 8.dp.toPx(),
                w, h - 22.dp.toPx()
            )
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = wavePath2,
            color = Color(0x40B4574E)
        )
    }
}

@Composable
fun KeySetupContent(
    onSuccess: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    isModalOrSheet: Boolean = false
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val mainActivity = context.findMainActivity()
    val isPipMode = mainActivity?.isInPipMode ?: false
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentStep by rememberSaveable {
        val saved = try {
            KeySetupStep.valueOf(com.example.wake.WakePrefsManager.getKeySetupStep(context))
        } catch (_: Exception) {
            KeySetupStep.CHOICES
        }
        mutableStateOf(saved)
    }
    var keyInput by rememberSaveable { mutableStateOf("") }
    var launchTimestamp by rememberSaveable { mutableLongStateOf(0L) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var secondsElapsed by remember { mutableIntStateOf(0) }

    // Video download / caching state
    var videoFile by remember { mutableStateOf<File?>(null) }
    var isVideoDownloading by remember { mutableStateOf(true) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var videoWidth by remember { mutableIntStateOf(9) }
    var videoHeight by remember { mutableIntStateOf(16) }

    // Breathing pulse animation for paste action
    val infiniteTransition = rememberInfiniteTransition(label = "paste_breathing")
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe_scale"
    )
    val breatheAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe_alpha"
    )

    LaunchedEffect(currentStep, videoFile) {
        com.example.wake.WakePrefsManager.setKeySetupStep(context, currentStep.name)
        if (currentStep == KeySetupStep.VIDEO) {
            mainActivity?.isVideoGuideActive = true
            mainActivity?.updatePipParams(videoFile, autoEnter = true)
        } else {
            if (mainActivity?.isInPipMode != true) {
                mainActivity?.isVideoGuideActive = false
            }
        }
    }

    // Auto-detect and paste API key from clipboard when returning to the app
    DisposableEffect(lifecycleOwner, currentStep) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && currentStep == KeySetupStep.PASTE && keyInput.isBlank()) {
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = clipboard?.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val pasted = clip.getItemAt(0).coerceToText(context).toString().trim()
                        if (pasted.startsWith("AIza") && ApiKeyProvider.isKeyValidFormat(pasted)) {
                            keyInput = pasted
                            errorMessage = null
                            Toast.makeText(context, "API Key detected & pasted!", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("KeySetup", "Auto-paste error: ${e.message}")
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val cached = GuideVideoLoader.getCachedVideoFile(context)
            if (cached.exists() && cached.length() > 0) {
                videoFile = cached
                isVideoDownloading = false
            } else {
                GuideVideoLoader.downloadVideo(context) { progress ->
                    downloadProgress = progress
                }?.let { downloaded ->
                    videoFile = downloaded
                }
                isVideoDownloading = false
            }
        }
    }

    // 9-second timer effect when user leaves to get key
    LaunchedEffect(launchTimestamp) {
        if (launchTimestamp > 0L) {
            while (true) {
                val now = System.currentTimeMillis()
                val diff = ((now - launchTimestamp) / 1000).toInt()
                secondsElapsed = diff
                if (diff >= 9) break
                delay(500)
            }
        }
    }

    val isTimerActive = launchTimestamp > 0L && secondsElapsed < 9

    // If in PiP mode, show ONLY the video player full frame with NO container box
    if (isPipMode) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    videoFile?.let { file ->
                        setVideoPath(file.absolutePath)
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            start()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (!isModalOrSheet) Modifier.verticalScroll(scrollState) else Modifier),
        contentAlignment = Alignment.TopCenter
    ) {
        // Decorative atmospheric background (sunburst rays, clouds, sparkle stars, bottom waves)
        SunburstAndAtmosphereCanvas(modifier = Modifier.matchParentSize())

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top Header 3D Key Badge (8% Pop Effect)
            PoppedOutKeyOrb(
                modifier = Modifier.padding(top = 10.dp, bottom = 12.dp)
            )

            // 2. Typography & Header Hierarchy
            Text(
                text = "Unlock First Light",
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = Color(0xFF2C2420)
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "YOUR FREE KEY",
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 3.sp,
                    color = Color(0xFFB4574E)
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "✦",
                color = Color(0xFFE8B87A),
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "First Light is 100% free. It uses your own free Google AI key so your prayers remain private and secure.",
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF6E6259),
                    lineHeight = 20.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 14.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

        if (currentStep == KeySetupStep.VIDEO) {
            // Zero-bezel video presentation directly in full width
            VideoPlayerContainer(
                videoFile = videoFile,
                isDownloading = isVideoDownloading,
                downloadProgress = downloadProgress,
                onVideoPrepared = { w, h ->
                    videoWidth = w
                    videoHeight = h
                },
                onBackClick = {
                    currentStep = KeySetupStep.CHOICES
                },
                onVideoEnded = {},
                onRewatch = {},
                onContinueInBrowser = {
                    com.example.wake.WakePrefsManager.setKeySetupStep(context, KeySetupStep.PASTE.name)
                    currentStep = KeySetupStep.PASTE
                    launchTimestamp = System.currentTimeMillis()
                    secondsElapsed = 0
                    if (com.example.wake.PermissionHelper.hasPipPermission(context)) {
                        mainActivity?.requestPipMode(videoFile)
                    }
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AI_STUDIO_KEY_URL)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } else {
            // Main Container Card (2% Pop Effect) for CHOICES & PASTE
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = Color(0x1F2C2420),
                        spotColor = Color(0x1F2C2420)
                    ),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFCF8)),
                border = BorderStroke(1.dp, Color(0xFFE8E0D4))
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Subtle botanical wheat sprig accent in the top-right
                    WheatSprigDecorative(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 12.dp)
                            .size(38.dp, 50.dp),
                        tint = Color(0x38B4574E)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (currentStep) {
                            KeySetupStep.CHOICES -> {
                                Text(
                                    text = "How would you like to get your key?",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Serif,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF2C2420)
                                    ),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                // Button 1: Terracotta pill container ("I already have my key")
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                        .shadow(
                                            elevation = 4.dp,
                                            shape = RoundedCornerShape(32.dp),
                                            ambientColor = Color(0x40B4574E),
                                            spotColor = Color(0x40B4574E)
                                        )
                                        .clip(RoundedCornerShape(32.dp))
                                        .background(
                                            brush = Brush.verticalGradient(
                                                listOf(
                                                    Color(0xFFBA5C53),
                                                    Color(0xFFA74D45)
                                                )
                                            )
                                        )
                                        .clickable {
                                            currentStep = KeySetupStep.PASTE
                                            launchTimestamp = System.currentTimeMillis()
                                            secondsElapsed = 0
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AI_STUDIO_KEY_URL))
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(horizontal = 12.dp)
                                        .testTag("already_have_key_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        // Left White Circular Badge with Terracotta Key Icon
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .shadow(2.dp, CircleShape)
                                                .background(Color.White, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Key,
                                                contentDescription = null,
                                                tint = Color(0xFFB4574E),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "I already have my key",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFDFCF8)
                                            )
                                            Text(
                                                text = "Enter your existing Google AI key",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = Color(0xFFFADCD8)
                                            )
                                        }

                                        Icon(
                                            imageVector = Icons.Default.ArrowOutward,
                                            contentDescription = null,
                                            tint = Color(0xFFFDFCF8),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Button 2: Cream pill container ("Show me how")
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                        .shadow(
                                            elevation = 2.dp,
                                            shape = RoundedCornerShape(32.dp),
                                            ambientColor = Color(0x142C2420),
                                            spotColor = Color(0x142C2420)
                                        )
                                        .clip(RoundedCornerShape(32.dp))
                                        .background(Color(0xFFF7F4EC))
                                        .border(1.dp, Color(0xFFE8E0D4), RoundedCornerShape(32.dp))
                                        .clickable {
                                            currentStep = KeySetupStep.VIDEO
                                        }
                                        .padding(horizontal = 12.dp)
                                        .testTag("watch_video_guide_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        // Left White Circular Badge with Terracotta Play Icon
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .shadow(1.dp, CircleShape)
                                                .background(Color.White, CircleShape)
                                                .border(1.dp, Color(0xFFE8E0D4), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.PlayArrow,
                                                contentDescription = null,
                                                tint = Color(0xFFB4574E),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "Show me how",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2C2420)
                                            )
                                            Text(
                                                text = "Watch the quick guide (1 min)",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = Color(0xFF8B7E72)
                                            )
                                        }

                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = Color(0xFF8B7E72),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            KeySetupStep.VIDEO -> {
                                // Handled above in zero-bezel view
                            }

                KeySetupStep.PASTE -> {
                    // Live Paste Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            width = 1.5.dp,
                            color = if (keyInput.isNotBlank()) Color(0xFF6B8F5A) else Color(0xFFB4574E)
                        ),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F4EC)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Status Header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(
                                            if (keyInput.isNotBlank()) Color(0xFF6B8F5A) else Color(0xFFB4574E),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (keyInput.isNotBlank()) "✓" else "🔑",
                                        color = Color(0xFFFDFCF8),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when {
                                        keyInput.isNotBlank() -> "Key entered — ready to connect"
                                        isTimerActive -> "Opening Google AI Studio... (${9 - secondsElapsed}s)"
                                        else -> "Paste your copied key here"
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when {
                                        keyInput.isNotBlank() -> Color(0xFF6B8F5A)
                                        isTimerActive -> Color(0xFFB4574E)
                                        else -> Color(0xFF2C2420)
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Input Field
                            OutlinedTextField(
                                value = keyInput,
                                onValueChange = {
                                    keyInput = it
                                    errorMessage = null
                                },
                                placeholder = {
                                    Text(
                                        text = "AIzaSy...",
                                        fontSize = 13.sp,
                                        color = Color(0xFF8B7E72),
                                        fontFamily = FontFamily.Monospace
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        tint = if (keyInput.isNotBlank()) Color(0xFF6B8F5A) else Color(0xFF8B7E72),
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        if (keyInput.isNotBlank()) {
                                            IconButton(
                                                onClick = {
                                                    keyInput = ""
                                                    errorMessage = null
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "Clear",
                                                    tint = Color(0xFF8B7E72),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else {
                                            TextButton(
                                                onClick = {
                                                    try {
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                        val clip = clipboard?.primaryClip
                                                        if (clip != null && clip.itemCount > 0) {
                                                            val pasted = clip.getItemAt(0).coerceToText(context).toString().trim()
                                                            if (pasted.isNotBlank()) {
                                                                keyInput = pasted
                                                                errorMessage = null
                                                                Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                Toast.makeText(context, "Clipboard is empty. Please copy your key from Google AI Studio.", Toast.LENGTH_LONG).show()
                                                            }
                                                        } else {
                                                            Toast.makeText(context, "Clipboard is empty. Please copy your key from Google AI Studio.", Toast.LENGTH_LONG).show()
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e("KeySetup", "Clipboard error: ${e.message}")
                                                    }
                                                },
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentPaste,
                                                    contentDescription = "Paste",
                                                    tint = Color(0xFFB4574E),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Paste",
                                                    color = Color(0xFFB4574E),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { focusManager.clearFocus() }
                                ),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color(0xFF2C2420),
                                    unfocusedTextColor = Color(0xFF2C2420),
                                    focusedContainerColor = Color(0xFFFDFCF8),
                                    unfocusedContainerColor = Color(0xFFFDFCF8),
                                    cursorColor = Color(0xFFB4574E),
                                    focusedBorderColor = Color(0xFFB4574E),
                                    unfocusedBorderColor = Color(0xFFE8E0D4)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("api_key_input_field")
                            )

                            // Prominent Breathing "Tap to Paste Key" Action Button
                            if (keyInput.isBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .graphicsLayer {
                                            scaleX = breatheScale
                                            scaleY = breatheScale
                                        }
                                        .shadow(
                                            elevation = (5 * breatheScale).dp,
                                            shape = RoundedCornerShape(24.dp),
                                            ambientColor = Color(0xFFB4574E).copy(alpha = breatheAlpha),
                                            spotColor = Color(0xFFB4574E).copy(alpha = breatheAlpha)
                                        )
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(
                                            brush = Brush.horizontalGradient(
                                                listOf(
                                                    Color(0xFFBA5C53),
                                                    Color(0xFFA74D45)
                                                )
                                            )
                                        )
                                        .clickable {
                                            try {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                val clip = clipboard?.primaryClip
                                                if (clip != null && clip.itemCount > 0) {
                                                    val pasted = clip.getItemAt(0).coerceToText(context).toString().trim()
                                                    if (pasted.isNotBlank()) {
                                                        keyInput = pasted
                                                        errorMessage = null
                                                        Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Clipboard is empty. Please copy your key from Google AI Studio.", Toast.LENGTH_LONG).show()
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Clipboard is empty. Please copy your key from Google AI Studio.", Toast.LENGTH_LONG).show()
                                                }
                                            } catch (e: Exception) {
                                                Log.e("KeySetup", "Clipboard error: ${e.message}")
                                            }
                                        }
                                        .padding(horizontal = 16.dp)
                                        .testTag("breathing_paste_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = "Paste from Clipboard",
                                            tint = Color(0xFFFDFCF8),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Tap to Paste Key",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFDFCF8)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        launchTimestamp = System.currentTimeMillis()
                                        secondsElapsed = 0
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AI_STUDIO_KEY_URL))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Need your key? Open Google AI Studio",
                                    fontSize = 11.sp,
                                    color = Color(0xFFB4574E),
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowOutward,
                                    contentDescription = null,
                                    tint = Color(0xFFB4574E),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    // Error message
                    AnimatedVisibility(
                        visible = errorMessage != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        errorMessage?.let { msg ->
                            Text(
                                text = msg,
                                color = Color(0xFFB4574E),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp, start = 6.dp, end = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Buttons: Back + Connect & Finish
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { currentStep = KeySetupStep.CHOICES },
                            modifier = Modifier
                                .weight(0.35f)
                                .height(46.dp),
                            shape = RoundedCornerShape(23.dp),
                            border = BorderStroke(1.dp, Color(0xFFE8E0D4)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8B7E72))
                        ) {
                            Text("Back", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                val trimmed = keyInput.trim()
                                if (trimmed.isEmpty()) {
                                    errorMessage = "Please paste your key above, then tap connect."
                                    return@Button
                                }
                                if (!ApiKeyProvider.isKeyValidFormat(trimmed)) {
                                    errorMessage = "We couldn't connect with this key. Please check that you copied the entire key and try again."
                                    return@Button
                                }

                                isLoading = true
                                errorMessage = null

                                coroutineScope.launch {
                                    val result = ApiKeyProvider.validateKeyLive(trimmed)
                                    isLoading = false
                                    if (result.isSuccess) {
                                        ApiKeyProvider.setUserApiKey(context, trimmed)
                                        onSuccess()
                                    } else {
                                        errorMessage = "We couldn't connect with this key. Please check that you copied the entire key and try again."
                                    }
                                }
                            },
                            enabled = !isLoading && keyInput.trim().isNotBlank(),
                            modifier = Modifier
                                .weight(0.65f)
                                .height(46.dp)
                                .shadow(
                                    elevation = if (keyInput.isNotBlank()) 2.dp else 0.dp,
                                    shape = RoundedCornerShape(23.dp)
                                )
                                .testTag("connect_finish_button"),
                            shape = RoundedCornerShape(23.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2C2420),
                                contentColor = Color(0xFFFDFCF8),
                                disabledContainerColor = Color(0xFFE8E0D4),
                                disabledContentColor = Color(0xFF8B7E72)
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = Color(0xFFFDFCF8),
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Connecting...", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            } else {
                                Text("Connect & Finish", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
}

        Spacer(modifier = Modifier.height(20.dp))

        // 5. Footer: Clock icon + "I'll do this later" and sub-caption
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusManager.clearFocus()
                    onSkip()
                }
                .padding(vertical = 8.dp)
                .testTag("skip_key_setup_button")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = Color(0xFF8B7E72),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "I'll do this later",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF5E5248)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "You can set it up anytime in Settings",
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF8C7E72),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
}

@Composable
private fun VideoPlayerContainer(
    videoFile: File?,
    isDownloading: Boolean,
    downloadProgress: Float,
    onVideoPrepared: (width: Int, height: Int) -> Unit,
    onBackClick: () -> Unit,
    onVideoEnded: () -> Unit,
    onRewatch: () -> Unit,
    onContinueInBrowser: () -> Unit
) {
    val context = LocalContext.current
    val mainActivity = remember(context) { context.findMainActivity() }
    var isEnded by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var videoWidth by remember { mutableIntStateOf(9) }
    var videoHeight by remember { mutableIntStateOf(16) }

    DisposableEffect(videoFile) {
        mainActivity?.isVideoGuideActive = true
        mainActivity?.updatePipParams(videoFile, autoEnter = true)
        onDispose {
            if (mainActivity?.isInPipMode != true) {
                mainActivity?.isVideoGuideActive = false
                mainActivity?.updatePipParams(videoFile, autoEnter = false)
            }
        }
    }

    val aspect = if (videoWidth > 0 && videoHeight > 0) videoWidth.toFloat() / videoHeight.toFloat() else (9f / 16f)

    // Auto-hide controls after 3.5 seconds if video is playing
    LaunchedEffect(showControls, isPlaying, isEnded) {
        if (showControls && isPlaying && !isEnded) {
            delay(3500)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!isEnded) {
                    showControls = !showControls
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isDownloading || videoFile == null || !videoFile.exists()) {
            // Downloading / Progress State or Fallback Text Caption
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF2C2420))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        progress = { if (downloadProgress > 0f) downloadProgress else 0.1f },
                        color = Color(0xFFB4574E),
                        trackColor = Color(0xFFE8E0D4),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (downloadProgress > 0f) "Loading video tutorial (${(downloadProgress * 100).toInt()}%)..." else "Preparing video tutorial...",
                        color = Color(0xFFFDFCF8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                } else {
                    // Fallback Text Caption Cards if video file is unavailable
                    Text(
                        text = "1. Tap 'I already have my key' to open Google AI Studio.\n2. Sign in with Google and tap 'Create API key'.\n3. Copy your key and paste it here.",
                        color = Color(0xFFFDFCF8),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Start
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onContinueInBrowser,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB4574E))
                    ) {
                        Text("Open Google AI Studio & Paste", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            // Video View
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoPath(videoFile.absolutePath)
                        setOnPreparedListener { mp ->
                            if (mp.videoWidth > 0 && mp.videoHeight > 0) {
                                videoWidth = mp.videoWidth
                                videoHeight = mp.videoHeight
                                onVideoPrepared(mp.videoWidth, mp.videoHeight)
                            }
                            mp.isLooping = false
                            start()
                            isPlaying = true
                        }
                        setOnCompletionListener {
                            isEnded = true
                            isPlaying = false
                            onVideoEnded()
                        }
                        videoViewRef = this
                    }
                },
                update = { vView ->
                    videoViewRef = vView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Overlaid Player Controls (tap screen to toggle)
            if (showControls && !isEnded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x66000000))
                        .padding(12.dp)
                ) {
                    // Top Left Back Arrow Button (<-)
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(40.dp)
                            .background(Color(0x99000000), CircleShape)
                            .testTag("video_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Top Right Picture-in-Picture Toggle Button
                    IconButton(
                        onClick = {
                            if (!com.example.wake.PermissionHelper.hasPipPermission(context)) {
                                Toast.makeText(context, "Please allow Picture-in-Picture for First Light in Settings", Toast.LENGTH_LONG).show()
                                com.example.wake.PermissionHelper.openPipSettings(context)
                            } else {
                                mainActivity?.lastVideoPosition = videoViewRef?.currentPosition ?: 0
                                mainActivity?.requestPipMode(videoFile)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(40.dp)
                            .background(Color(0x99000000), CircleShape)
                            .testTag("video_pip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = "Picture in Picture",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Center Seek Controls: -5s | Play/Pause | +5s
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind 5s Button
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0x992C2420), CircleShape)
                                .clip(CircleShape)
                                .clickable {
                                    videoViewRef?.let { vv ->
                                        val newPos = (vv.currentPosition - 5000).coerceAtLeast(0)
                                        vv.seekTo(newPos)
                                        mainActivity?.lastVideoPosition = newPos
                                        showControls = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "-5s",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Play / Pause Toggle
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .background(Color(0xFFB4574E), CircleShape)
                                .clip(CircleShape)
                                .clickable {
                                    videoViewRef?.let { vv ->
                                        if (vv.isPlaying) {
                                            vv.pause()
                                            isPlaying = false
                                        } else {
                                            vv.start()
                                            isPlaying = true
                                        }
                                        mainActivity?.lastVideoPosition = vv.currentPosition
                                        showControls = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Fast Forward 5s Button
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0x992C2420), CircleShape)
                                .clip(CircleShape)
                                .clickable {
                                    videoViewRef?.let { vv ->
                                        val duration = vv.duration
                                        val newPos = (vv.currentPosition + 5000).coerceAtMost(if (duration > 0) duration else Int.MAX_VALUE)
                                        vv.seekTo(newPos)
                                        mainActivity?.lastVideoPosition = newPos
                                        showControls = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+5s",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Bottom "Continue in Browser" button
                    Button(
                        onClick = {
                            mainActivity?.lastVideoPosition = videoViewRef?.currentPosition ?: 0
                            onContinueInBrowser()
                        },
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFB4574E),
                            contentColor = Color(0xFFFDFCF8)
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(0.9f)
                            .height(48.dp)
                            .testTag("video_continue_in_browser_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Continue in Browser",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowOutward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Overlaid Controls when Video ENDS
            if (isEnded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x99000000))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Pop-out terracotta lollipop button: "Continue in Browser"
                        Button(
                            onClick = {
                                mainActivity?.lastVideoPosition = videoViewRef?.currentPosition ?: 0
                                onContinueInBrowser()
                            },
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFB4574E),
                                contentColor = Color(0xFFFDFCF8)
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.88f)
                                .height(54.dp)
                                .testTag("continue_in_browser_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Continue in Browser",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowOutward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Small "Rewatch" text button
                        TextButton(
                            onClick = {
                                isEnded = false
                                isPlaying = true
                                videoViewRef?.seekTo(0)
                                videoViewRef?.start()
                                onRewatch()
                            }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color(0xFFFDFCF8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Rewatch",
                                    color = Color(0xFFFDFCF8),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
