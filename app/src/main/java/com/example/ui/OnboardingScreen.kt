package com.example.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.customization.VersePrefsManager
import com.example.customization.WallpaperVerseRenderer
import com.example.ui.components.BrandIconTile
import com.example.wake.WakePrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

enum class DoodleType {
    CROSS, ICHTHYS, DOVE, SUN, FLAME, OLIVE, CHALICE
}

@Composable
fun DoodleCanvas(
    type: DoodleType,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidthPx = 4.dp.toPx()
        val stroke = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)

        when (type) {
            DoodleType.CROSS -> {
                drawLine(color, Offset(w / 2f, 0f), Offset(w / 2f, h), strokeWidth = strokeWidthPx, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.2f, h * 0.35f), Offset(w * 0.8f, h * 0.35f), strokeWidth = strokeWidthPx, cap = StrokeCap.Round)
            }
            DoodleType.ICHTHYS -> {
                val path = Path().apply {
                    moveTo(0f, h * 0.35f)
                    quadraticTo(w * 0.5f, -h * 0.1f, w, h * 0.75f)
                    moveTo(0f, h * 0.65f)
                    quadraticTo(w * 0.5f, h * 1.1f, w, h * 0.25f)
                }
                drawPath(path, color, style = stroke)
            }
            DoodleType.DOVE -> {
                val path = Path().apply {
                    moveTo(0f, h * 0.5f)
                    quadraticTo(w * 0.35f, 0f, w * 0.65f, h * 0.45f)
                    quadraticTo(w * 0.85f, -h * 0.1f, w, h * 0.15f)
                    quadraticTo(w * 0.75f, h * 0.85f, w * 0.45f, h * 0.75f)
                    quadraticTo(w * 0.2f, h * 0.9f, 0f, h * 0.5f)
                }
                drawPath(path, color, style = stroke)
            }
            DoodleType.SUN -> {
                val center = Offset(w / 2f, h / 2f)
                val radius = w * 0.22f
                drawCircle(color, radius = radius, center = center, style = stroke)
                val numRays = 8
                for (i in 0 until numRays) {
                    val angle = Math.toRadians((i * 360f / numRays).toDouble())
                    val r1 = radius + 3.dp.toPx()
                    val r2 = radius + 8.dp.toPx()
                    val start = Offset(center.x + (r1 * Math.cos(angle)).toFloat(), center.y + (r1 * Math.sin(angle)).toFloat())
                    val end = Offset(center.x + (r2 * Math.cos(angle)).toFloat(), center.y + (r2 * Math.sin(angle)).toFloat())
                    drawLine(color, start, end, strokeWidth = strokeWidthPx, cap = StrokeCap.Round)
                }
            }
            DoodleType.FLAME -> {
                val path = Path().apply {
                    moveTo(w / 2f, 0f)
                    quadraticTo(w * 0.85f, h * 0.35f, w * 0.75f, h * 0.7f)
                    quadraticTo(w * 0.65f, h, w / 2f, h)
                    quadraticTo(w * 0.35f, h, w * 0.25f, h * 0.7f)
                    quadraticTo(w * 0.15f, h * 0.35f, w / 2f, 0f)
                }
                drawPath(path, color, style = stroke)
            }
            DoodleType.OLIVE -> {
                val path = Path().apply {
                    moveTo(0f, h)
                    quadraticTo(w * 0.5f, h * 0.5f, w, 0f)
                }
                drawPath(path, color, style = stroke)
                val leaf1 = Path().apply {
                    moveTo(w * 0.3f, h * 0.7f)
                    quadraticTo(w * 0.15f, h * 0.5f, w * 0.3f, h * 0.5f)
                    quadraticTo(w * 0.35f, h * 0.65f, w * 0.3f, h * 0.7f)
                }
                val leaf2 = Path().apply {
                    moveTo(w * 0.6f, h * 0.4f)
                    quadraticTo(w * 0.75f, h * 0.25f, w * 0.75f, h * 0.4f)
                    quadraticTo(w * 0.65f, h * 0.45f, w * 0.6f, h * 0.4f)
                }
                drawPath(leaf1, color, style = stroke)
                drawPath(leaf2, color, style = stroke)
            }
            DoodleType.CHALICE -> {
                val path = Path().apply {
                    moveTo(w * 0.2f, h * 0.15f)
                    quadraticTo(w / 2f, h * 0.65f, w * 0.8f, h * 0.15f)
                    moveTo(w / 2f, h * 0.45f)
                    lineTo(w / 2f, h * 0.85f)
                    moveTo(w * 0.25f, h * 0.85f)
                    lineTo(w * 0.75f, h * 0.85f)
                }
                drawPath(path, color, style = stroke)
            }
        }
    }
}

@Composable
fun AnimatedDoodleItem(
    type: DoodleType,
    size: Dp,
    rotation: Float,
    color: Color,
    alignment: Alignment,
    padding: PaddingValues,
    delayMs: Long
) {
    val alphaAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        alphaAnim.animateTo(1f, tween(durationMillis = 750, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = alignment
    ) {
        DoodleCanvas(
            type = type,
            color = color,
            modifier = Modifier
                .size(size)
                .rotate(rotation)
                .graphicsLayer { alpha = alphaAnim.value }
        )
    }
}

@Composable
fun DoodleBackgroundLayer() {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedDoodleItem(
            type = DoodleType.CROSS,
            size = 40.dp,
            rotation = -10f,
            color = Color(0x30B4574E),
            alignment = Alignment.TopStart,
            padding = PaddingValues(top = 28.dp, start = 16.dp),
            delayMs = 100L
        )
        AnimatedDoodleItem(
            type = DoodleType.SUN,
            size = 48.dp,
            rotation = 12f,
            color = Color(0x35E8B87A),
            alignment = Alignment.TopEnd,
            padding = PaddingValues(top = 20.dp, end = 60.dp),
            delayMs = 200L
        )
        AnimatedDoodleItem(
            type = DoodleType.DOVE,
            size = 44.dp,
            rotation = -14f,
            color = Color(0x30B4574E),
            alignment = Alignment.TopEnd,
            padding = PaddingValues(top = 36.dp, end = 16.dp),
            delayMs = 150L
        )
        AnimatedDoodleItem(
            type = DoodleType.FLAME,
            size = 32.dp,
            rotation = 8f,
            color = Color(0x268B7E72),
            alignment = Alignment.TopStart,
            padding = PaddingValues(top = 140.dp, start = 20.dp),
            delayMs = 250L
        )
        AnimatedDoodleItem(
            type = DoodleType.ICHTHYS,
            size = 48.dp,
            rotation = -8f,
            color = Color(0x30B4574E),
            alignment = Alignment.TopEnd,
            padding = PaddingValues(top = 150.dp, end = 24.dp),
            delayMs = 300L
        )
        AnimatedDoodleItem(
            type = DoodleType.OLIVE,
            size = 56.dp,
            rotation = 15f,
            color = Color(0x30B4574E),
            alignment = Alignment.CenterStart,
            padding = PaddingValues(bottom = 120.dp, start = 12.dp),
            delayMs = 350L
        )
        AnimatedDoodleItem(
            type = DoodleType.CHALICE,
            size = 42.dp,
            rotation = -12f,
            color = Color(0x30B4574E),
            alignment = Alignment.CenterEnd,
            padding = PaddingValues(bottom = 100.dp, end = 16.dp),
            delayMs = 400L
        )
        AnimatedDoodleItem(
            type = DoodleType.CROSS,
            size = 36.dp,
            rotation = -6f,
            color = Color(0x35E8B87A),
            alignment = Alignment.CenterStart,
            padding = PaddingValues(top = 120.dp, start = 18.dp),
            delayMs = 450L
        )
        AnimatedDoodleItem(
            type = DoodleType.DOVE,
            size = 50.dp,
            rotation = 10f,
            color = Color(0x268B7E72),
            alignment = Alignment.CenterEnd,
            padding = PaddingValues(top = 140.dp, end = 20.dp),
            delayMs = 500L
        )
        AnimatedDoodleItem(
            type = DoodleType.SUN,
            size = 52.dp,
            rotation = -15f,
            color = Color(0x30B4574E),
            alignment = Alignment.BottomStart,
            padding = PaddingValues(bottom = 40.dp, start = 28.dp),
            delayMs = 550L
        )
        AnimatedDoodleItem(
            type = DoodleType.ICHTHYS,
            size = 40.dp,
            rotation = 6f,
            color = Color(0x30B4574E),
            alignment = Alignment.BottomStart,
            padding = PaddingValues(bottom = 20.dp, start = 120.dp),
            delayMs = 600L
        )
        AnimatedDoodleItem(
            type = DoodleType.FLAME,
            size = 38.dp,
            rotation = -10f,
            color = Color(0x30B4574E),
            alignment = Alignment.BottomEnd,
            padding = PaddingValues(bottom = 44.dp, end = 24.dp),
            delayMs = 650L
        )
        AnimatedDoodleItem(
            type = DoodleType.OLIVE,
            size = 44.dp,
            rotation = 12f,
            color = Color(0x35E8B87A),
            alignment = Alignment.BottomEnd,
            padding = PaddingValues(bottom = 16.dp, end = 110.dp),
            delayMs = 700L
        )
        AnimatedDoodleItem(
            type = DoodleType.CHALICE,
            size = 34.dp,
            rotation = 5f,
            color = Color(0x268B7E72),
            alignment = Alignment.TopStart,
            padding = PaddingValues(top = 80.dp, start = 100.dp),
            delayMs = 220L
        )
    }
}

@Composable
fun CandleIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(14.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.3f, h * 0.35f),
            size = Size(w * 0.4f, h * 0.65f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
        drawLine(
            color = tint,
            start = Offset(w / 2f, h * 0.22f),
            end = Offset(w / 2f, h * 0.35f),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )
        val flame = Path().apply {
            moveTo(w / 2f, 0f)
            quadraticTo(w * 0.68f, h * 0.15f, w / 2f, h * 0.22f)
            quadraticTo(w * 0.32f, h * 0.15f, w / 2f, 0f)
        }
        drawPath(flame, color = tint)
    }
}

@Composable
fun BrandFireIcon(
    modifier: Modifier = Modifier.size(20.dp)
) {
    Image(
        painter = painterResource(id = R.drawable.ic_fire_logo),
        contentDescription = null,
        modifier = modifier
    )
}

@Composable
fun StaggeredAnimatedItem(
    index: Int,
    content: @Composable () -> Unit
) {
    val alphaAnim = remember { Animatable(0f) }
    val yOffsetAnim = remember { Animatable(12f) }

    LaunchedEffect(Unit) {
        val delayMs = index * 80L
        delay(delayMs)
        launch {
            alphaAnim.animateTo(1f, tween(durationMillis = 350, easing = FastOutSlowInEasing))
        }
        launch {
            yOffsetAnim.animateTo(0f, tween(durationMillis = 350, easing = FastOutSlowInEasing))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = alphaAnim.value
                translationY = yOffsetAnim.value.dp.toPx()
            }
    ) {
        content()
    }
}

@Composable
fun CustomSelectorField(
    value: String,
    placeholder: String,
    icon: @Composable (tint: Color) -> Unit,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isFocused) Color(0xFFB4574E) else Color(0xFFE8E0D4)
    val iconTint = if (isFocused) Color(0xFFB4574E) else Color(0xFF8B7E72)

    Surface(
        modifier = modifier
            .height(56.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFDFCF8), // SOLID 100% opacity, no doodle bleed
        border = BorderStroke(1.5.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            icon(iconTint)

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = value.ifEmpty { placeholder },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (value.isNotEmpty()) Color(0xFF2C2420) else Color(0xFF8B7E72),
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Color(0xFF8B7E72),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    onOpenVerseEditor: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val yearFocusRequester = remember { FocusRequester() }

    var isNameFocused by remember { mutableStateOf(false) }
    var isMonthFocused by remember { mutableStateOf(false) }
    var isDayFocused by remember { mutableStateOf(false) }
    var isYearFocused by remember { mutableStateOf(false) }

    var currentStep by rememberSaveable { 
        val saved = WakePrefsManager.getOnboardingStep(context)
        mutableIntStateOf(if (saved in 1..4) saved else 1)
    }

    LaunchedEffect(currentStep) {
        WakePrefsManager.setOnboardingStep(context, currentStep)
    }
    var userNameInput by remember { mutableStateOf(WakePrefsManager.getUserName(context)) }

    val existingDob = WakePrefsManager.getUserDob(context)
    var selectedMonth by remember {
        mutableStateOf(existingDob.split(" ").getOrNull(0) ?: "")
    }
    var selectedDay by remember {
        mutableStateOf(existingDob.split(" ").getOrNull(1)?.removeSuffix(",") ?: "")
    }
    var yearInput by remember {
        mutableStateOf(existingDob.split(" ").getOrNull(2) ?: "")
    }

    var showMonthDialog by remember { mutableStateOf(false) }
    var showDayDialog by remember { mutableStateOf(false) }
    var showSetupSheet by remember { mutableStateOf(false) }

    // Photo picker for lock screen setup during onboarding (Option B)
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
                            withContext(Dispatchers.Main) {
                                showSetupSheet = false
                                onOpenVerseEditor()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("OnboardingScreen", "Failed picking photo: ${e.message}")
                }
            }
        }
    }

    val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    val days = (1..31).map { it.toString() }

    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val yearInt = yearInput.toIntOrNull()

    val isDobAllEmpty = selectedMonth.isEmpty() && selectedDay.isEmpty() && yearInput.isEmpty()
    val isDobAllFilled = selectedMonth.isNotEmpty() && selectedDay.isNotEmpty() && yearInput.isNotEmpty() && (yearInt != null && yearInt in 1900..currentYear && yearInput.length == 4)

    val isDobValid = isDobAllEmpty || isDobAllFilled
    val isNameValid = userNameInput.trim().isNotEmpty()
    val isCanBegin = isNameValid && isDobValid

    val unifiedTextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color(0xFF2C2420),
        unfocusedTextColor = Color(0xFF2C2420),
        focusedPlaceholderColor = Color(0xFF8B7E72),
        unfocusedPlaceholderColor = Color(0xFF8B7E72),
        cursorColor = Color(0xFFB4574E),
        focusedBorderColor = Color(0xFFB4574E),
        unfocusedBorderColor = Color(0xFFE8E0D4),
        focusedLabelColor = Color(0xFFB4574E),
        unfocusedLabelColor = Color(0xFF8B7E72),
        focusedContainerColor = Color(0xFFFDFCF8), // SOLID 100% opacity
        unfocusedContainerColor = Color(0xFFFDFCF8) // SOLID 100% opacity
    )

    val buttonBgColor by animateColorAsState(
        targetValue = if (isCanBegin) Color(0xFFB4574E) else Color(0xFFC8BFB3),
        animationSpec = tween(durationMillis = 180),
        label = "button_bg"
    )
    val buttonTextColor by animateColorAsState(
        targetValue = if (isCanBegin) Color(0xFFFDFCF8) else Color(0xFFF2EFE6),
        animationSpec = tween(durationMillis = 180),
        label = "button_text"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2EFE6)),
        contentAlignment = Alignment.Center
    ) {
        // FULL-SCREEN BACKGROUND DOODLE LAYER (Behind content on page 2 only)
        if (currentStep == 2) {
            DoodleBackgroundLayer()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "onboarding_step"
            ) { step ->
                if (step == 1) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        BrandIconTile()

                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            text = "Begin your day in His presence.",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            color = Color(0xFF1C1917),
                            textAlign = TextAlign.Center,
                            lineHeight = 36.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "A quiet morning prayer, spoken with you — before the world gets loud.",
                            fontSize = 16.sp,
                            color = Color(0xFF57534E),
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp
                        )

                        Spacer(modifier = Modifier.height(48.dp))

                        Button(
                            onClick = { currentStep = 2 },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFB4574E),
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = "Continue",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else if (step == 2) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Index 0: Centered Brand Icon Tile above title
                        StaggeredAnimatedItem(index = 0) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                BrandIconTile()
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Index 1: Serif Title
                        StaggeredAnimatedItem(index = 1) {
                            Text(
                                text = "How should we call you?",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif,
                                color = Color(0xFF1C1917),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Index 2: Quiet Subtitle
                        StaggeredAnimatedItem(index = 2) {
                            Text(
                                text = "A quiet morning prayer, spoken with you — before the world gets loud.",
                                fontSize = 14.sp,
                                color = Color(0xFF8B7E72),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Index 3: Name Input Field
                        StaggeredAnimatedItem(index = 3) {
                            OutlinedTextField(
                                value = userNameInput,
                                onValueChange = { userNameInput = it },
                                placeholder = {
                                    Text(
                                        text = "Your name",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = Color(0xFF8B7E72)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (isNameFocused) Color(0xFFB4574E) else Color(0xFF8B7E72),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF2C2420)
                                ),
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = {
                                        keyboardController?.hide()
                                        isNameFocused = false
                                        isMonthFocused = true
                                        isDayFocused = false
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .onFocusChanged { isNameFocused = it.isFocused },
                                shape = RoundedCornerShape(14.dp),
                                colors = unifiedTextFieldColors
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Index 4: Date of Birth Header + Row (Month 1.35 / Day 0.75 / Year 1.0)
                        StaggeredAnimatedItem(index = 4) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Date of birth (optional)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color(0xFF8B7E72),
                                    letterSpacing = 0.3.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 4.dp, bottom = 8.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Month selector (weight 1.35)
                                    CustomSelectorField(
                                        value = selectedMonth,
                                        placeholder = "Month",
                                        icon = { tint ->
                                            Icon(
                                                imageVector = Icons.Default.DateRange,
                                                contentDescription = null,
                                                tint = tint,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        },
                                        isFocused = isMonthFocused,
                                        onClick = {
                                            isMonthFocused = true
                                            isDayFocused = false
                                            showMonthDialog = true
                                        },
                                        modifier = Modifier.weight(1.35f)
                                    )

                                    // Day selector (weight 0.75)
                                    CustomSelectorField(
                                        value = selectedDay,
                                        placeholder = "Day",
                                        icon = { tint ->
                                            Icon(
                                                imageVector = Icons.Default.WbSunny,
                                                contentDescription = null,
                                                tint = tint,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        },
                                        isFocused = isDayFocused,
                                        onClick = {
                                            isMonthFocused = false
                                            isDayFocused = true
                                            showDayDialog = true
                                        },
                                        modifier = Modifier.weight(0.75f)
                                    )

                                    // Year input (weight 1.0)
                                    OutlinedTextField(
                                        value = yearInput,
                                        onValueChange = { input ->
                                            if (input.length <= 4 && input.all { it.isDigit() }) {
                                                yearInput = input
                                            }
                                        },
                                        placeholder = {
                                            Text(
                                                text = "YYYY",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = Color(0xFF8B7E72)
                                            )
                                        },
                                        leadingIcon = {
                                            CandleIcon(
                                                tint = if (isYearFocused) Color(0xFFB4574E) else Color(0xFF8B7E72),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF2C2420)
                                        ),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                keyboardController?.hide()
                                                focusManager.clearFocus()
                                                isYearFocused = false
                                            }
                                        ),
                                        modifier = Modifier
                                            .weight(1.0f)
                                            .height(56.dp)
                                            .focusRequester(yearFocusRequester)
                                            .onFocusChanged { isYearFocused = it.isFocused },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = unifiedTextFieldColors
                                    )
                                }

                                if (!isDobValid) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val hintText = if (selectedMonth.isEmpty() || selectedDay.isEmpty() || yearInput.isEmpty()) {
                                        "Please complete Month, Day, and Year, or leave all blank."
                                    } else {
                                        "Please enter a valid 4-digit year (1900–$currentYear)."
                                    }
                                    Text(
                                        text = hintText,
                                        fontSize = 12.sp,
                                        color = Color(0xFFB4574E),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(36.dp))

                        // Index 5: Continue to Lock Screen Offer Button
                        StaggeredAnimatedItem(index = 5) {
                            Button(
                                onClick = {
                                    if (isCanBegin) {
                                        val finalName = userNameInput.trim().ifBlank { "friend" }
                                        val finalDob = if (isDobAllFilled) "$selectedMonth $selectedDay, $yearInput" else ""
                                        WakePrefsManager.setUserName(context, finalName)
                                        WakePrefsManager.setUserDob(context, finalDob)
                                        currentStep = 3
                                    }
                                },
                                enabled = isCanBegin,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .shadow(
                                        elevation = 8.dp,
                                        shape = RoundedCornerShape(26.dp),
                                        ambientColor = Color(0x4DB4574E),
                                        spotColor = Color(0x4DB4574E)
                                    ),
                                shape = RoundedCornerShape(26.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = buttonBgColor,
                                    contentColor = buttonTextColor,
                                    disabledContainerColor = buttonBgColor,
                                    disabledContentColor = buttonTextColor
                                )
                            ) {
                                Text(
                                    text = "Continue",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        // Index 6: Footer "hope • love"
                        StaggeredAnimatedItem(index = 6) {
                            Text(
                                text = "hope • love",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                color = Color(0xFF8B7E72),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else if (step == 3) {
                    // STEP 3: API KEY SETUP
                    com.example.ui.key.KeySetupContent(
                        onSuccess = {
                            currentStep = 4
                        },
                        onSkip = {
                            currentStep = 4
                        }
                    )
                } else if (step == 4) {
                    // STEP 4: THE LOCK SCREEN PRAYER / VERSE SETUP (FINAL STEP)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Icon Pill
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFB4574E).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFFB4574E),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Title
                        Text(
                            text = "Daily Verse on Your Lock Screen",
                            style = TextStyle(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                color = Color(0xFF2C2420)
                            ),
                            textAlign = TextAlign.Center,
                            lineHeight = 32.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Subtitle with clear value proposition
                        Text(
                            text = "Carry God's word with you throughout your day. A quiet reminder every time you pick up your phone.",
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFF8B7E72),
                                lineHeight = 20.sp
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Immersive Lock Screen Preview Card
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFCF8)),
                            border = BorderStroke(1.dp, Color(0xFFE8E0D4)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    ) {
                                        Text(
                                            text = "06:30",
                                            fontFamily = FontFamily.SansSerif,
                                            fontWeight = FontWeight.Light,
                                            fontSize = 26.sp,
                                            color = Color(0xFFFDFCF8).copy(alpha = 0.85f),
                                            letterSpacing = 1.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "“This is the day the Lord has made; let us rejoice and be glad in it.”",
                                            style = TextStyle(
                                                fontFamily = FontFamily.Serif,
                                                fontStyle = FontStyle.Italic,
                                                fontSize = 12.sp,
                                                color = Color(0xFFFDFCF8),
                                                textAlign = TextAlign.Center,
                                                lineHeight = 16.sp
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Micro-benefit trust badges (evaluative ease)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.Shield,
                                            contentDescription = null,
                                            tint = Color(0xFF6B8F5A),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Your photo or dark theme",
                                            fontSize = 11.sp,
                                            color = Color(0xFF8B7E72)
                                        )
                                    }

                                    Text(text = "•", color = Color(0xFFE8E0D4))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.Visibility,
                                            contentDescription = null,
                                            tint = Color(0xFFB4574E),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Custom styling",
                                            fontSize = 11.sp,
                                            color = Color(0xFF8B7E72)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Primary CTA: [Set Up Lock Screen Verse] (Option B)
                        Button(
                            onClick = {
                                showSetupSheet = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .shadow(
                                    elevation = 6.dp,
                                    shape = RoundedCornerShape(26.dp),
                                    ambientColor = Color(0x4DB4574E),
                                    spotColor = Color(0x4DB4574E)
                                ),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFB4574E),
                                contentColor = Color(0xFFFDFCF8)
                            )
                        ) {
                            Text(
                                text = "Set Up Lock Screen Verse",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Secondary Skip CTA: [Maybe later] -> Completes Onboarding and proceeds to Home
                        TextButton(
                            onClick = {
                                WakePrefsManager.setOnboardingComplete(context, true)
                                onOnboardingComplete()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Text(
                                text = "Maybe later",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF8B7E72)
                            )
                        }
                    }
                }
            }
        }
    }

    // Lock Screen Setup Sheet during Onboarding
    if (showSetupSheet) {
        LockScreenSetupSheet(
            onDismiss = {
                showSetupSheet = false
                currentStep = 4
            },
            onChoosePhoto = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onUseDarkDefault = {
                coroutineScope.launch(Dispatchers.IO) {
                    val darkDefault = WallpaperVerseRenderer.createFallbackBackgroundBitmap()
                    VersePrefsManager.saveDraftWallpaper(context, darkDefault)
                    withContext(Dispatchers.Main) {
                        showSetupSheet = false
                        onOpenVerseEditor()
                    }
                }
            }
        )
    }

    // Month Selection Dialog
    if (showMonthDialog) {
        CustomPickerDialog(
            title = "Select Month",
            items = months,
            selectedItem = selectedMonth,
            onItemSelected = { month ->
                selectedMonth = month
            },
            onConfirmAdvance = {
                showMonthDialog = false
                isMonthFocused = false
                isDayFocused = true
            },
            onDismissRequest = {
                showMonthDialog = false
            }
        )
    }

    // Day Selection Dialog
    if (showDayDialog) {
        CustomPickerDialog(
            title = "Select Day",
            items = days,
            selectedItem = selectedDay,
            onItemSelected = { day ->
                selectedDay = day
            },
            onConfirmAdvance = {
                showDayDialog = false
                isDayFocused = false
                yearFocusRequester.requestFocus()
                keyboardController?.hide()
            },
            onDismissRequest = {
                showDayDialog = false
            }
        )
    }
}

@Composable
fun CustomPickerDialog(
    title: String,
    items: List<String>,
    selectedItem: String,
    onItemSelected: (String) -> Unit,
    onConfirmAdvance: () -> Unit,
    onDismissRequest: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    val hasSelection = selectedItem.isNotEmpty()

    Dialog(
        onDismissRequest = {
            if (hasSelection) onConfirmAdvance() else onDismissRequest()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x662C2420))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (hasSelection) onConfirmAdvance() else onDismissRequest()
                },
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(120))
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFF2EFE6),
                    shadowElevation = 12.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Header area
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF2EFE6))
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = Color(0xFF2C2420),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Serif,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        HorizontalDivider(
                            color = Color(0xFFE8E0D4),
                            thickness = 1.dp
                        )

                        // Scrollable list
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 290.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(items) { _, item ->
                                val isSelected = item == selectedItem

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (isSelected) Color(0xFFB4574E) else Color(0xFFF3E1DE)
                                        )
                                        .clickable {
                                            onItemSelected(item)
                                        }
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = item,
                                        fontSize = 15.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (isSelected) Color(0xFFFDFCF8) else Color(0xFF2C2420),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(Color(0xFFFDFCF8), CircleShape)
                                                .align(Alignment.CenterEnd)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            color = Color(0xFFE8E0D4),
                            thickness = 1.dp
                        )

                        // Footer with 40dp Circular Down-Arrow Button at bottom-right
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (hasSelection) Color(0xFFB4574E) else Color(0xFFE8E0D4))
                                    .clickable(enabled = hasSelection) {
                                        onConfirmAdvance()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Confirm selection",
                                    tint = if (hasSelection) Color(0xFFFDFCF8) else Color(0xFF8B7E72),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
