package com.example.ui

import android.Manifest
import android.app.TimePickerDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import com.example.ui.key.KeySetupContent
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.graphics.Brush
import kotlin.math.roundToInt
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.customization.VersePrefsManager
import com.example.customization.WallpaperVerseRenderer
import com.example.wake.PermissionHelper
import com.example.wake.WakePrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ProfileScrollStateHolder {
    var scrollPosition: Int = 0
}

private data class MissingPermissionEntry(
    val tag: String,
    val label: String,
    val onClick: () -> Unit
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onOpenVerseEditor: () -> Unit = {},
    openTextOptionsOnLaunch: Boolean = false,
    onResetTextOptionsFlag: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenDiagnostic: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState(initial = ProfileScrollStateHolder.scrollPosition)
    var isNavVisible by remember { mutableStateOf(true) }
    var accumulatedScrollDelta by remember { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                // When close to the top of the profile, always keep navigation bar visible
                if (scrollState.value <= 15) {
                    isNavVisible = true
                    accumulatedScrollDelta = 0f
                    return Offset.Zero
                }

                if (delta < 0f) {
                    // Scrolling down towards deeper sections (hide navigation bar)
                    if (accumulatedScrollDelta > 0f) accumulatedScrollDelta = 0f
                    accumulatedScrollDelta += delta
                    if (accumulatedScrollDelta < -15f) {
                        isNavVisible = false
                    }
                } else if (delta > 0f) {
                    // Scrolling up towards top (show navigation bar)
                    if (accumulatedScrollDelta < 0f) accumulatedScrollDelta = 0f
                    accumulatedScrollDelta += delta
                    if (accumulatedScrollDelta > 15f) {
                        isNavVisible = true
                    }
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(scrollState.value) {
        ProfileScrollStateHolder.scrollPosition = scrollState.value
        if (scrollState.value <= 15) {
            isNavVisible = true
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    BackHandler { onBack() }

    val wakeState by WakePrefsManager.wakeState.collectAsStateWithLifecycle()

    // Dynamic preferences from WakePrefsManager
    var rawUserName by remember(wakeState) { mutableStateOf(WakePrefsManager.getUserName(context)) }
    var rawUserDob by remember(wakeState) { mutableStateOf(WakePrefsManager.getUserDob(context)) }
    val installDateStr by remember(wakeState) { mutableStateOf(WakePrefsManager.getFirstInstallDate(context)) }
    val prayerHistory by remember(wakeState) { mutableStateOf(WakePrefsManager.getPrayerHistory(context)) }

    var prayerTimeString by remember(wakeState) { mutableStateOf(WakePrefsManager.getPrayerTime(context)) }
    var isReminderEnabled by remember(wakeState) { mutableStateOf(WakePrefsManager.isReminderEnabled(context)) }
    var snoozeOptionsSummary by remember(wakeState) { mutableStateOf(WakePrefsManager.getSnoozeOptionsSummary(context)) }

    // Verse Preferences & States
    var isVerseSetupComplete by remember { mutableStateOf(VersePrefsManager.isVerseSetupComplete(context)) }
    var isVerseEnabled by remember { mutableStateOf(VersePrefsManager.isVerseEnabled(context)) }
    var currentVerseText by remember { mutableStateOf(VersePrefsManager.getVerseText(context)) }
    var currentVerseColor by remember { mutableIntStateOf(VersePrefsManager.getEffectiveVerseColor(context)) }
    var currentVerseStyle by remember { mutableStateOf(VersePrefsManager.getEffectiveVerseStyle(context)) }
    var currentVerseSize by remember { mutableStateOf(VersePrefsManager.getEffectiveVerseSize(context)) }
    var currentVerseScale by remember { mutableFloatStateOf(VersePrefsManager.getEffectiveVerseScale(context)) }
    var currentVerseYPct by remember { mutableFloatStateOf(VersePrefsManager.getEffectiveVerseYPct(context)) }

    val displayVerse = if (currentVerseText.trim().isNotEmpty()) {
        currentVerseText
    } else {
        "This is the day the Lord has made; let us rejoice and be glad in it."
    }

    var baseWallpaperBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // App Permissions state
    var hasMicPermission by remember { mutableStateOf(PermissionHelper.hasRecordAudioPermission(context)) }
    var hasPhoneStatePermission by remember { mutableStateOf(PermissionHelper.hasReadPhoneStatePermission(context)) }
    var hasOverlayPermission by remember { mutableStateOf(PermissionHelper.hasOverlayPermission(context)) }
    var hasNotificationPermission by remember { mutableStateOf(PermissionHelper.hasNotificationPermission(context)) }
    var hasExactAlarmPermission by remember { mutableStateOf(PermissionHelper.hasExactAlarmPermission(context)) }
    var hasApiKey by remember { mutableStateOf(com.example.api.ApiKeyProvider.hasWorkingKey(context)) }

    fun refreshAllPermissions() {
        hasMicPermission = PermissionHelper.hasRecordAudioPermission(context)
        hasPhoneStatePermission = PermissionHelper.hasReadPhoneStatePermission(context)
        hasOverlayPermission = PermissionHelper.hasOverlayPermission(context)
        hasNotificationPermission = PermissionHelper.hasNotificationPermission(context)
        hasExactAlarmPermission = PermissionHelper.hasExactAlarmPermission(context)
        hasApiKey = com.example.api.ApiKeyProvider.hasWorkingKey(context)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        refreshAllPermissions()
    }

    val phoneStatePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPhoneStatePermission = isGranted
        refreshAllPermissions()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        refreshAllPermissions()
    }

    // Bottom Sheets
    var showSetupSheet by remember { mutableStateOf(false) }
    var showTextOptionsSheet by remember { mutableStateOf(openTextOptionsOnLaunch) }
    var showKeySheet by remember { mutableStateOf(false) }

    // Secret Developer Backdoor States (Triggered by 9 taps on top fire ornament)
    var fireTapCount by remember { mutableIntStateOf(0) }
    var lastFireTapTime by remember { mutableLongStateOf(0L) }
    var showDevPasscodeDialog by remember { mutableStateOf(false) }
    var devPasscodeInput by remember { mutableStateOf("") }
    var devPasscodeError by remember { mutableStateOf<String?>(null) }
    var showDevToolsDialog by remember { mutableStateOf(false) }
    var isDeveloperModeActive by remember {
        mutableStateOf(com.example.api.ApiKeyProvider.isDeveloperMode(context))
    }

    LaunchedEffect(openTextOptionsOnLaunch) {
        if (openTextOptionsOnLaunch) {
            showTextOptionsSheet = true
            onResetTextOptionsFlag()
        }
    }

    // Refresh wallpaper and verse states on resume
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshAllPermissions()
                isVerseSetupComplete = VersePrefsManager.isVerseSetupComplete(context)
                isVerseEnabled = VersePrefsManager.isVerseEnabled(context)
                currentVerseText = VersePrefsManager.getVerseText(context)
                currentVerseColor = VersePrefsManager.getEffectiveVerseColor(context)
                currentVerseStyle = VersePrefsManager.getEffectiveVerseStyle(context)
                currentVerseSize = VersePrefsManager.getEffectiveVerseSize(context)
                currentVerseScale = VersePrefsManager.getEffectiveVerseScale(context)
                currentVerseYPct = VersePrefsManager.getEffectiveVerseYPct(context)
                coroutineScope.launch(Dispatchers.IO) {
                    val bmp = VersePrefsManager.getEffectiveWallpaperBitmap(context)
                    withContext(Dispatchers.Main) {
                        baseWallpaperBitmap = bmp
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Initial load
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val bmp = VersePrefsManager.getEffectiveWallpaperBitmap(context)
            withContext(Dispatchers.Main) {
                baseWallpaperBitmap = bmp
            }
        }
    }

    // Photo picker for setup sheet (saves to draft only)
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
                    Log.e("ProfileScreen", "Failed picking photo: ${e.message}")
                }
            }
        }
    }

    // Dialog controllers
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showSnoozeDialog by remember { mutableStateOf(false) }

    // Computed Identity Info
    val displayName = if (rawUserName.trim().isNotEmpty()) rawUserName.trim() else "Friend"
    val monogramLetter = if (rawUserName.trim().isNotEmpty()) rawUserName.trim().take(1).uppercase(Locale.US) else "F"

    val formattedInstallDate = remember(installDateStr) {
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = parser.parse(installDateStr)
            if (date != null) {
                SimpleDateFormat("MMMM yyyy", Locale.US).format(date)
            } else {
                SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())
            }
        } catch (_: Exception) {
            SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())
        }
    }

    // Computed Stats from Prayer History
    val currentStreak = remember(prayerHistory) {
        WakePrefsManager.calculateStreak(prayerHistory)
    }
    val totalPrayers = remember(prayerHistory) {
        prayerHistory.size
    }
    val longestStreak = remember(prayerHistory) {
        calculateLongestStreak(prayerHistory)
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2EFE6)) // Cream brand canvas
    ) {
        // Ambient Warm Mesh Glows for rich depth (Lollipop/Liquid glass lighting)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Top-right soft gold warm radiant aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFE8B87A).copy(alpha = 0.22f), Color(0x00E8B87A)),
                    center = Offset(w * 0.85f, h * 0.08f),
                    radius = w * 0.65f
                )
            )

            // Center-left terracotta rose subtle aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFD98A84).copy(alpha = 0.14f), Color(0x00D98A84)),
                    center = Offset(w * 0.12f, h * 0.38f),
                    radius = w * 0.70f
                )
            )

            // Bottom-right warm brown ambient grounding
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFB4574E).copy(alpha = 0.10f), Color(0x00B4574E)),
                    center = Offset(w * 0.90f, h * 0.75f),
                    radius = w * 0.60f
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .verticalScroll(scrollState)
                .padding(
                    top = topInset + 6.dp,
                    bottom = bottomInset + 84.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Tactile Circular Back Button and Centered Profile title
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                // Popped-out 3D Tactile Back Button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.CenterStart)
                        .shadow(
                            elevation = 4.dp,
                            shape = CircleShape,
                            ambientColor = Color(0x2E2C2420),
                            spotColor = Color(0x2E2C2420)
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFFFFFFFF), Color(0xFFFDFCF8), Color(0xFFF4EDE2))
                            )
                        )
                        .border(
                            BorderStroke(
                                1.dp,
                                Brush.verticalGradient(
                                    listOf(Color(0xFFFFFFFF), Color(0xFFE8E0D4))
                                )
                            ),
                            CircleShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onBack()
                        }
                        .testTag("profile_back_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = Color(0xFF2C2420),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Centered Profile Title
                Text(
                    text = "Profile",
                    style = TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFF2C2420),
                        shadow = Shadow(
                            color = Color(0x142C2420),
                            offset = Offset(0f, 2f),
                            blurRadius = 4f
                        )
                    ),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Popped Jewel Fire Logo Ornament (Secret Developer Backdoor: 9 taps)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        val now = System.currentTimeMillis()
                        if (now - lastFireTapTime > 3000L) {
                            fireTapCount = 1
                        } else {
                            fireTapCount++
                        }
                        lastFireTapTime = now

                        if (fireTapCount >= 9) {
                            fireTapCount = 0
                            devPasscodeInput = ""
                            devPasscodeError = null
                            showDevPasscodeDialog = true
                        }
                    }
                    .testTag("profile_secret_fire_trigger"),
                contentAlignment = Alignment.Center
            ) {
                // Outer subtle gloss halo
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    Color(0xFFE8B87A).copy(alpha = 0.35f),
                                    Color(0xFFB4574E).copy(alpha = 0.15f),
                                    Color(0x00F2EFE6)
                                )
                            )
                        )
                )
                Image(
                    painter = painterResource(id = R.drawable.ic_fire_logo),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ════ SECTION 1 — IDENTITY (3D LOLLIPOP / BALLOON AVATAR) ════
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .testTag("profile_identity_avatar_container"),
                contentAlignment = Alignment.Center
            ) {
                // 3D Popped Lollipop Monogram Sphere
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = CircleShape,
                            ambientColor = Color(0x40B4574E),
                            spotColor = Color(0x4DB4574E)
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFD9776C), // Lighter warm terracotta highlight
                                    Color(0xFFB4574E), // Primary terracotta
                                    Color(0xFF8A3830), // Deep rich terracotta shadow
                                    Color(0xFF5A221C)  // Rich warm brown depth
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(220f, 220f)
                            )
                        )
                        .border(
                            BorderStroke(
                                1.5.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFFFFD4CE).copy(alpha = 0.85f), // Gloss specular rim
                                        Color(0xFFB4574E).copy(alpha = 0.40f)
                                    )
                                )
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Specular light gloss arc across top-left
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.45f),
                                    Color.White.copy(alpha = 0.0f)
                                ),
                                center = Offset(w * 0.32f, h * 0.28f),
                                radius = w * 0.38f
                            )
                        )
                    }

                    Text(
                        text = monogramLetter,
                        style = TextStyle(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            color = Color(0xFFFDFCF8),
                            shadow = Shadow(
                                color = Color(0x663D1814),
                                offset = Offset(0f, 2f),
                                blurRadius = 4f
                            )
                        )
                    )
                }

                // 3D Popped Circular Edit Badge overlapping bottom-right
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.BottomEnd)
                        .shadow(
                            elevation = 5.dp,
                            shape = CircleShape,
                            ambientColor = Color(0x332C2420),
                            spotColor = Color(0x332C2420)
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFFFFFFFF), Color(0xFFFDFCF8), Color(0xFFF6EDE2))
                            )
                        )
                        .border(
                            BorderStroke(
                                1.dp,
                                Brush.verticalGradient(
                                    listOf(Color(0xFFFFFFFF), Color(0xFFE8E0D4))
                                )
                            ),
                            CircleShape
                        )
                        .clickable { showEditProfileDialog = true }
                        .testTag("btn_edit_profile"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Profile",
                        tint = Color(0xFFB4574E),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Centered User Name (Serif, 22sp, warm black with subtle depth)
            Text(
                text = displayName,
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Color(0xFF2C2420),
                    shadow = Shadow(
                        color = Color(0x142C2420),
                        offset = Offset(0f, 1.5f),
                        blurRadius = 3f
                    )
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            // Tactile Pill for Installation Date ("With First Light since ...")
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFE8E0D4).copy(alpha = 0.40f))
                    .border(0.8.dp, Color(0xFFE8E0D4), RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFB4574E))
                    )
                    Text(
                        text = "With First Light since $formattedInstallDate",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF6B5E54),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ════ SECTION 2 — YOUR JOURNEY (POPPED 3D STATS PODIUM) ════
            SectionHeaderLabel(text = "YOUR JOURNEY")
            Spacer(modifier = Modifier.height(4.dp))

            // Plush Popped-out Capsule Card for Stats
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 5.dp,
                        shape = RoundedCornerShape(18.dp),
                        ambientColor = Color(0x1F2C2420),
                        spotColor = Color(0x1F2C2420)
                    )
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFFFFFFFF), Color(0xFFFDFCF8), Color(0xFFF8F4EB))
                        )
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(Color(0xFFFFFFFF), Color(0xFFE8E0D4))
                            )
                        ),
                        RoundedCornerShape(18.dp)
                    )
                    .padding(vertical = 12.dp, horizontal = 10.dp)
                    .testTag("profile_journey_stats")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Column 1: Current streak
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                Color(0xFFE8B87A).copy(alpha = 0.35f),
                                                Color(0xFFB4574E).copy(alpha = 0.15f)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_streak_flame),
                                    contentDescription = null,
                                    tint = Color(0xFFB4574E),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Text(
                                text = "$currentStreak ${if (currentStreak == 1) "day" else "days"}",
                                style = TextStyle(
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFF2C2420)
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Current streak",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF8B7E72),
                            textAlign = TextAlign.Center
                        )
                    }

                    // Vertical soft warm hairline divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(30.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0x00E8E0D4), Color(0xFFE8E0D4), Color(0x00E8E0D4))
                                )
                            )
                    )

                    // Column 2: Total prayers
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$totalPrayers",
                            style = TextStyle(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = Color(0xFF2C2420)
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Total prayers",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF8B7E72),
                            textAlign = TextAlign.Center
                        )
                    }

                    // Vertical soft warm hairline divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(30.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0x00E8E0D4), Color(0xFFE8E0D4), Color(0x00E8E0D4))
                                )
                            )
                    )

                    // Column 3: Longest streak
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$longestStreak ${if (longestStreak == 1) "day" else "days"}",
                            style = TextStyle(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF2C2420)
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Longest streak",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF8B7E72),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ════ APP PERMISSIONS CARD (LIQUID GLASS ELEVATED CONTAINER) ════
            SectionHeaderLabel(text = "APP PERMISSIONS")
            Spacer(modifier = Modifier.height(4.dp))

            val allPermissionsGranted = hasMicPermission && hasPhoneStatePermission && hasOverlayPermission && hasNotificationPermission && hasExactAlarmPermission

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 5.dp,
                        shape = RoundedCornerShape(18.dp),
                        ambientColor = Color(0x1F2C2420),
                        spotColor = Color(0x1F2C2420)
                    )
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFFFFFFFF), Color(0xFFFDFCF8), Color(0xFFF9F5EC))
                        )
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(Color(0xFFFFFFFF), Color(0xFFE8E0D4))
                            )
                        ),
                        RoundedCornerShape(18.dp)
                    )
                    .testTag("profile_permissions_card")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (allPermissionsGranted) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                                .testTag("permission_row_all_allowed"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 3D Popped Success Emerald Icon Pebble
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .shadow(
                                        elevation = 3.dp,
                                        shape = RoundedCornerShape(11.dp),
                                        ambientColor = Color(0x266B8F5A),
                                        spotColor = Color(0x266B8F5A)
                                    )
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color(0xFF6B8F5A).copy(alpha = 0.22f),
                                                Color(0xFF6B8F5A).copy(alpha = 0.12f)
                                            )
                                        )
                                    )
                                    .border(
                                        BorderStroke(1.dp, Color(0xFF6B8F5A).copy(alpha = 0.35f)),
                                        RoundedCornerShape(11.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "All permissions allowed",
                                    tint = Color(0xFF537544),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = "All permissions allowed",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2C2420),
                                modifier = Modifier.weight(1f)
                            )

                            // Glowing Green Status Pill Indicator
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF6B8F5A).copy(alpha = 0.15f))
                                    .border(0.8.dp, Color(0xFF6B8F5A).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF6B8F5A))
                                    )
                                    Text(
                                        text = "Active",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF537544)
                                    )
                                }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val missingRows = mutableListOf<MissingPermissionEntry>()

                            if (!hasMicPermission) {
                                missingRows.add(
                                    MissingPermissionEntry(
                                        tag = "permission_row_mic_missing",
                                        label = "Microphone not allowed",
                                        onClick = {
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    )
                                )
                            }

                            if (!hasPhoneStatePermission) {
                                missingRows.add(
                                    MissingPermissionEntry(
                                        tag = "permission_row_phone_state_missing",
                                        label = "Phone state not allowed",
                                        onClick = {
                                            phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                                        }
                                    )
                                )
                            }

                            if (!hasOverlayPermission) {
                                missingRows.add(
                                    MissingPermissionEntry(
                                        tag = "permission_row_overlay_missing",
                                        label = "Display over other apps not allowed",
                                        onClick = {
                                            PermissionHelper.openOverlaySettings(context)
                                        }
                                    )
                                )
                            }

                            if (!hasNotificationPermission) {
                                missingRows.add(
                                    MissingPermissionEntry(
                                        tag = "permission_row_notifications_missing",
                                        label = "Notifications not allowed",
                                        onClick = {
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                PermissionHelper.openNotificationSettings(context)
                                            }
                                        }
                                    )
                                )
                            }

                            if (!hasExactAlarmPermission) {
                                missingRows.add(
                                    MissingPermissionEntry(
                                        tag = "permission_row_exact_alarm_missing",
                                        label = "Exact alarm not allowed",
                                        onClick = {
                                            PermissionHelper.openExactAlarmSettings(context)
                                        }
                                    )
                                )
                            }

                            missingRows.forEachIndexed { index, rowItem ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        color = Color(0xFFE8E0D4).copy(alpha = 0.7f),
                                        thickness = 1.dp,
                                        modifier = Modifier.padding(horizontal = 14.dp)
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { rowItem.onClick() }
                                        .padding(horizontal = 14.dp, vertical = 9.dp)
                                        .testTag(rowItem.tag),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(11.dp))
                                            .background(Color(0xFFB4574E).copy(alpha = 0.12f))
                                            .border(1.dp, Color(0xFFB4574E).copy(alpha = 0.25f), RoundedCornerShape(11.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Canvas(modifier = Modifier.size(16.dp)) {
                                            val w = size.width
                                            val h = size.height
                                            drawLine(
                                                color = Color(0xFFB4574E),
                                                start = Offset(w / 2f, h * 0.15f),
                                                end = Offset(w / 2f, h * 0.60f),
                                                strokeWidth = 2.2.dp.toPx(),
                                                cap = StrokeCap.Round
                                            )
                                            drawCircle(
                                                color = Color(0xFFB4574E),
                                                radius = 1.5.dp.toPx(),
                                                center = Offset(w / 2f, h * 0.82f)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Text(
                                        text = rowItem.label,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFB4574E),
                                        modifier = Modifier.weight(1f)
                                    )

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = rowItem.label,
                                        tint = Color(0xFFB4574E),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Soft Hairline Divider between Permissions and Gemini API Key
                    HorizontalDivider(
                        color = Color(0xFFE8E0D4).copy(alpha = 0.7f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )

                    // Gemini API Key Confirmation Row (3D Popped Row with Tactile Badge)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showKeySheet = true }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                            .testTag("profile_gemini_api_key_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasApiKey) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .shadow(
                                        elevation = 3.dp,
                                        shape = RoundedCornerShape(11.dp),
                                        ambientColor = Color(0x266B8F5A),
                                        spotColor = Color(0x266B8F5A)
                                    )
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color(0xFF6B8F5A).copy(alpha = 0.22f),
                                                Color(0xFF6B8F5A).copy(alpha = 0.12f)
                                            )
                                        )
                                    )
                                    .border(
                                        BorderStroke(1.dp, Color(0xFF6B8F5A).copy(alpha = 0.35f)),
                                        RoundedCornerShape(11.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = "Gemini API Key active",
                                    tint = Color(0xFF537544),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Gemini API Key",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF2C2420)
                                )
                                Text(
                                    text = "Live Voice prayer active",
                                    fontSize = 11.sp,
                                    color = Color(0xFF537544),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Glowing Green Active Pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF6B8F5A).copy(alpha = 0.15f))
                                    .border(0.8.dp, Color(0xFF6B8F5A).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF6B8F5A))
                                    )
                                    Text(
                                        text = "Ready",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF537544)
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(Color(0xFFB4574E).copy(alpha = 0.12f))
                                    .border(1.dp, Color(0xFFB4574E).copy(alpha = 0.25f), RoundedCornerShape(11.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = "Gemini API Key required",
                                    tint = Color(0xFFB4574E),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Gemini API Key",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFB4574E)
                                )
                                Text(
                                    text = "Tap to enter key & start praying",
                                    fontSize = 11.sp,
                                    color = Color(0xFF8B7E72)
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Configure Key",
                                tint = Color(0xFFB4574E),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ════ SECTION 3 — YOUR MORNING CARD (POPPED 3D SATIN CONTAINER) ════
            SectionHeaderLabel(text = "YOUR MORNING")
            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 5.dp,
                        shape = RoundedCornerShape(18.dp),
                        ambientColor = Color(0x1F2C2420),
                        spotColor = Color(0x1F2C2420)
                    )
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFFFFFFFF), Color(0xFFFDFCF8), Color(0xFFF9F5EC))
                        )
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(Color(0xFFFFFFFF), Color(0xFFE8E0D4))
                            )
                        ),
                        RoundedCornerShape(18.dp)
                    )
                    .testTag("profile_morning_card")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Row 1: Prayer Time
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val (hour, minute) = WakePrefsManager.getPrayerHourMinute(context)
                                val picker = TimePickerDialog(
                                    context,
                                    { _, selectedHour, selectedMinute ->
                                        val cal = Calendar.getInstance().apply {
                                            set(Calendar.HOUR_OF_DAY, selectedHour)
                                            set(Calendar.MINUTE, selectedMinute)
                                        }
                                        val sdf = SimpleDateFormat("hh:mm a", Locale.US)
                                        val formatted = sdf.format(cal.time)
                                        WakePrefsManager.setPrayerTime(context, formatted)
                                        prayerTimeString = formatted
                                    },
                                    hour,
                                    minute,
                                    false
                                )
                                picker.show()
                            }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 3D Popped Pebble Icon Container with Terracotta Tint
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .shadow(
                                    elevation = 3.dp,
                                    shape = RoundedCornerShape(11.dp),
                                    ambientColor = Color(0x26B4574E),
                                    spotColor = Color(0x26B4574E)
                                )
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xFFB4574E).copy(alpha = 0.20f),
                                            Color(0xFFB4574E).copy(alpha = 0.10f)
                                        )
                                    )
                                )
                                .border(
                                    BorderStroke(1.dp, Color(0xFFB4574E).copy(alpha = 0.28f)),
                                    RoundedCornerShape(11.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_clock_terracotta),
                                contentDescription = "Prayer Time",
                                tint = Color(0xFFB4574E),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "Prayer Time",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2C2420),
                            modifier = Modifier.weight(1f)
                        )

                        // Popped Terracotta Time Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFB4574E).copy(alpha = 0.12f))
                                .border(1.dp, Color(0xFFB4574E).copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = prayerTimeString,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB4574E)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Edit Time",
                            tint = Color(0xFF8B7E72),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    HorizontalDivider(
                        color = Color(0xFFE8E0D4).copy(alpha = 0.7f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )

                    // Row 2: Reminder
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 3D Popped Pebble Icon Container with Bell
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .shadow(
                                    elevation = 3.dp,
                                    shape = RoundedCornerShape(11.dp),
                                    ambientColor = Color(0x26B4574E),
                                    spotColor = Color(0x26B4574E)
                                )
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xFFB4574E).copy(alpha = 0.20f),
                                            Color(0xFFB4574E).copy(alpha = 0.10f)
                                        )
                                    )
                                )
                                .border(
                                    BorderStroke(1.dp, Color(0xFFB4574E).copy(alpha = 0.28f)),
                                    RoundedCornerShape(11.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            BellIcon(
                                tint = Color(0xFFB4574E),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reminder",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2C2420)
                            )
                            Text(
                                text = "Heads-up before your prayer time",
                                fontSize = 11.sp,
                                color = Color(0xFF8B7E72)
                            )
                        }

                        Switch(
                            checked = isReminderEnabled,
                            onCheckedChange = { checked ->
                                isReminderEnabled = checked
                                WakePrefsManager.setReminderEnabled(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFFDFCF8),
                                checkedTrackColor = Color(0xFFB4574E),
                                checkedBorderColor = Color(0xFFB4574E),
                                uncheckedThumbColor = Color(0xFF8B7E72),
                                uncheckedTrackColor = Color(0xFFE8E0D4),
                                uncheckedBorderColor = Color(0xFFE8E0D4)
                            ),
                            modifier = Modifier.testTag("profile_reminder_switch")
                        )
                    }

                    HorizontalDivider(
                        color = Color(0xFFE8E0D4).copy(alpha = 0.7f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )

                    // Row 3: Snooze Options
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSnoozeDialog = true }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 3D Popped Pebble Icon Container with Moon
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .shadow(
                                    elevation = 3.dp,
                                    shape = RoundedCornerShape(11.dp),
                                    ambientColor = Color(0x26B4574E),
                                    spotColor = Color(0x26B4574E)
                                )
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xFFB4574E).copy(alpha = 0.20f),
                                            Color(0xFFB4574E).copy(alpha = 0.10f)
                                        )
                                    )
                                )
                                .border(
                                    BorderStroke(1.dp, Color(0xFFB4574E).copy(alpha = 0.28f)),
                                    RoundedCornerShape(11.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            MoonIcon(
                                tint = Color(0xFFB4574E),
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Snooze Options",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2C2420)
                            )
                            Text(
                                text = snoozeOptionsSummary,
                                fontSize = 11.sp,
                                color = Color(0xFF8B7E72),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Edit Snooze",
                            tint = Color(0xFF8B7E72),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ════ SECTION 4 — YOUR LOCK SCREEN CARD (POPPED 3D GLOSSY CONTAINER) ════
            SectionHeaderLabel(text = "YOUR LOCK SCREEN")
            Spacer(modifier = Modifier.height(4.dp))

            val displayVerse = if (currentVerseText.trim().isNotEmpty()) {
                currentVerseText
            } else {
                "This is the day the Lord has made; let us rejoice and be glad in it."
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 5.dp,
                        shape = RoundedCornerShape(18.dp),
                        ambientColor = Color(0x1F2C2420),
                        spotColor = Color(0x1F2C2420)
                    )
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFFFFFFFF), Color(0xFFFDFCF8), Color(0xFFF9F5EC))
                        )
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(Color(0xFFFFFFFF), Color(0xFFE8E0D4))
                            )
                        ),
                        RoundedCornerShape(18.dp)
                    )
                    .testTag("profile_lock_screen_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    // Row 1: Memory verse on lock screen header + switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Memory verse on lock screen",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2C2420)
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            if (!isVerseSetupComplete) {
                                Text(
                                    text = "Not set up yet.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF8B7E72)
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isVerseEnabled) Color(0xFF6B8F5A) else Color(0xFF8B7E72)
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isVerseEnabled) "Ready" else "Turned off",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isVerseEnabled) Color(0xFF537544) else Color(0xFF8B7E72)
                                    )
                                }
                            }
                        }

                        Switch(
                            checked = isVerseSetupComplete && isVerseEnabled,
                            onCheckedChange = { checked ->
                                if (!isVerseSetupComplete) {
                                    showSetupSheet = true
                                } else {
                                    isVerseEnabled = checked
                                    VersePrefsManager.setVerseEnabled(context, checked)
                                    coroutineScope.launch(Dispatchers.IO) {
                                        WallpaperVerseRenderer.applyWallpaper(context)
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFFDFCF8),
                                checkedTrackColor = Color(0xFFB4574E),
                                checkedBorderColor = Color(0xFFB4574E),
                                uncheckedThumbColor = Color(0xFF8B7E72),
                                uncheckedTrackColor = Color(0xFFE8E0D4),
                                uncheckedBorderColor = Color(0xFFE8E0D4)
                            ),
                            modifier = Modifier.testTag("profile_verse_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (!isVerseSetupComplete) {
                        // ─── STATE A: NOT SET UP ───
                        // 3D Liquid Glass Mockup Preview Container
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .shadow(
                                    elevation = 6.dp,
                                    shape = RoundedCornerShape(16.dp),
                                    ambientColor = Color(0x33000000),
                                    spotColor = Color(0x33000000)
                                )
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        Brush.verticalGradient(
                                            listOf(Color(0x4DFFFFFF), Color(0x1AE8E0D4))
                                        )
                                    ),
                                    RoundedCornerShape(16.dp)
                                )
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF090D16))
                                    )
                                )
                                .clickable { showSetupSheet = true }
                                .testTag("preview_not_set_up")
                        ) {
                            // Specular Liquid Glass Top Rim Reflection
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val w = size.width
                                val h = size.height
                                drawRoundRect(
                                    brush = Brush.verticalGradient(
                                        listOf(Color.White.copy(alpha = 0.15f), Color.Transparent),
                                        startY = 0f,
                                        endY = h * 0.35f
                                    ),
                                    size = size,
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
                                )
                            }

                            // Mini Lock Screen Clock
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "09:41",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.85f),
                                    style = TextStyle(shadow = Shadow(color = Color.Black.copy(alpha = 0.4f), blurRadius = 4f))
                                )
                                Text(
                                    text = "Wed, 12 Aug",
                                    fontSize = 9.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }

                            // Verse Text Preview
                            Text(
                                text = "\"$displayVerse\"",
                                style = TextStyle(
                                    fontFamily = FontFamily.Serif,
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.95f),
                                    textAlign = TextAlign.Center,
                                    lineHeight = 16.sp,
                                    shadow = Shadow(color = Color.Black.copy(alpha = 0.6f), blurRadius = 6f)
                                ),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 16.dp)
                            )

                            // Bottom Hint Pill
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 8.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.22f))
                                    .border(0.8.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "Tap to set up",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFFE8E0D4).copy(alpha = 0.7f), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Setup Action Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSetupSheet = true }
                                .testTag("row_setup_verse"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFB4574E).copy(alpha = 0.12f))
                                    .border(1.dp, Color(0xFFB4574E).copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "Set up",
                                    tint = Color(0xFFB4574E),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Text(
                                text = "Set up lock screen verse",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2C2420),
                                modifier = Modifier.weight(1f)
                            )

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Set up",
                                tint = Color(0xFF8B7E72),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        // ─── STATE B: ALREADY SET UP ───
                        // 3D Liquid Glass Mockup Preview Container
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .shadow(
                                    elevation = 6.dp,
                                    shape = RoundedCornerShape(16.dp),
                                    ambientColor = Color(0x33000000),
                                    spotColor = Color(0x33000000)
                                )
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        Brush.verticalGradient(
                                            listOf(Color(0x4DFFFFFF), Color(0x1AE8E0D4))
                                        )
                                    ),
                                    RoundedCornerShape(16.dp)
                                )
                                .background(Color(0xFF0F172A))
                                .clickable { onOpenVerseEditor() }
                                .testTag("preview_already_set_up")
                        ) {
                            // Actual base wallpaper bitmap
                            baseWallpaperBitmap?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Lock screen wallpaper",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } ?: Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF090D16))
                                        )
                                    )
                            )

                            // Subtle Lock Screen Clock
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "09:41",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.85f),
                                    style = TextStyle(shadow = Shadow(color = Color.Black.copy(alpha = 0.4f), blurRadius = 4f))
                                )
                            }

                            // Dynamic Verse Text with user position, color, and style
                            val fontFam = when (currentVerseStyle) {
                                "Classic", "Serif Bold" -> FontFamily.Serif
                                "Monospace" -> FontFamily.Monospace
                                "Cursive" -> FontFamily.Cursive
                                else -> FontFamily.Default
                            }
                            val fontWt = when (currentVerseStyle) {
                                "Bold", "Serif Bold" -> FontWeight.Bold
                                "Light" -> FontWeight.Light
                                else -> FontWeight.Normal
                            }
                            val fontSty = if (currentVerseStyle == "Classic") FontStyle.Italic else FontStyle.Normal
                            val baseSizeSp = when (currentVerseSize) {
                                "Small" -> 10.sp
                                "Large" -> 14.sp
                                else -> 12.sp
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 16.dp)
                            ) {
                                Text(
                                    text = "\"$displayVerse\"",
                                    style = TextStyle(
                                        fontFamily = fontFam,
                                        fontWeight = fontWt,
                                        fontStyle = fontSty,
                                        fontSize = (baseSizeSp.value * currentVerseScale.coerceIn(0.7f, 1.8f)).sp,
                                        color = Color(currentVerseColor),
                                        textAlign = TextAlign.Center,
                                        lineHeight = (baseSizeSp.value * currentVerseScale.coerceIn(0.7f, 1.8f) * 1.35f).sp,
                                        shadow = Shadow(color = Color.Black.copy(alpha = 0.6f), blurRadius = 6f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Bottom Hint Pill (Popped Liquid Glass Pill)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 6.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.50f))
                                    .border(0.8.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Tap to adjust photo & position",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFFE8E0D4).copy(alpha = 0.7f), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        // "Customize text" row (Palette icon -> opens TextOptionsSheet directly)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTextOptionsSheet = true }
                                .testTag("row_customize_text"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFB4574E).copy(alpha = 0.12f))
                                    .border(1.dp, Color(0xFFB4574E).copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                PaletteIcon(
                                    tint = Color(0xFFB4574E),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Customize text",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF2C2420)
                                )
                                Text(
                                    text = "Colors, size, font style",
                                    fontSize = 11.sp,
                                    color = Color(0xFF8B7E72)
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Customize text",
                                tint = Color(0xFF8B7E72),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer "hope • love" in centered terracotta italic serif with gentle glow
            Text(
                text = "hope • love",
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.sp,
                    color = Color(0xFFB4574E),
                    letterSpacing = 1.5.sp,
                    shadow = Shadow(
                        color = Color(0x1AB4574E),
                        offset = Offset(0f, 1f),
                        blurRadius = 3f
                    )
                ),
                textAlign = TextAlign.Center
            )
        }

        // Floating Bottom Navigation Pill (Slides down/hides on scroll down, reappears on scroll up)
        AnimatedVisibility(
            visible = isNavVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomInset + 16.dp),
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight * 2 },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(animationSpec = tween(durationMillis = 200)),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight * 2 },
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 150))
        ) {
            com.example.ui.BottomNavPill(
                activeTab = "profile",
                onTabSelected = { tab ->
                    if (tab == "home") {
                        onBack()
                    } else if (tab == "favorites") {
                        onOpenFavorites()
                    }
                }
            )
        }

        // Edit Profile Dialog (Name + DOB onboarding-style editor)
        if (showEditProfileDialog) {
            EditProfileDialog(
                currentName = rawUserName,
                currentDob = rawUserDob,
                onDismiss = { showEditProfileDialog = false },
                onSave = { newName, newDob ->
                    WakePrefsManager.setUserName(context, newName)
                    WakePrefsManager.setUserDob(context, newDob)
                    rawUserName = newName
                    rawUserDob = newDob
                    showEditProfileDialog = false
                }
            )
        }

        // Snooze Options Dialog
        if (showSnoozeDialog) {
            SnoozeOptionsDialog(
                onDismiss = { showSnoozeDialog = false },
                onSaved = {
                    snoozeOptionsSummary = WakePrefsManager.getSnoozeOptionsSummary(context)
                    showSnoozeDialog = false
                }
            )
        }

        // ─── BOTTOM SHEET 1: LOCK SCREEN SETUP SHEET ───
        if (showSetupSheet) {
            LockScreenSetupSheet(
                onDismiss = { showSetupSheet = false },
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

        // ─── BOTTOM SHEET 2: TEXT OPTIONS SHEET ───
        if (showTextOptionsSheet) {
            TextOptionsSheet(
                initialColor = currentVerseColor,
                initialSize = currentVerseSize,
                initialStyle = currentVerseStyle,
                currentVerse = displayVerse,
                baseBitmap = baseWallpaperBitmap,
                onDismiss = { showTextOptionsSheet = false },
                onDiscard = {
                    VersePrefsManager.discardDraft(context)
                    coroutineScope.launch(Dispatchers.IO) {
                        val savedBmp = WallpaperVerseRenderer.getBaseWallpaperBitmap(context)
                        withContext(Dispatchers.Main) {
                            isVerseSetupComplete = VersePrefsManager.isVerseSetupComplete(context)
                            isVerseEnabled = VersePrefsManager.isVerseEnabled(context)
                            currentVerseColor = VersePrefsManager.getVerseColor(context)
                            currentVerseSize = VersePrefsManager.getVerseSize(context)
                            currentVerseScale = VersePrefsManager.getVerseScale(context)
                            currentVerseStyle = VersePrefsManager.getVerseStyle(context)
                            currentVerseYPct = VersePrefsManager.getVerseYPct(context)
                            baseWallpaperBitmap = savedBmp
                            showTextOptionsSheet = false
                        }
                    }
                },
                onSave = { selectedColor, selectedSize, selectedScale, selectedStyle ->
                    coroutineScope.launch(Dispatchers.IO) {
                        VersePrefsManager.setDraftColor(context, selectedColor)
                        VersePrefsManager.setDraftSize(context, selectedSize)
                        VersePrefsManager.setDraftScale(context, selectedScale)
                        VersePrefsManager.setDraftStyle(context, selectedStyle)
                        VersePrefsManager.commitDraft(context)
                        val finalBmp = WallpaperVerseRenderer.getBaseWallpaperBitmap(context)
                        withContext(Dispatchers.Main) {
                            isVerseSetupComplete = true
                            isVerseEnabled = true
                            currentVerseColor = VersePrefsManager.getVerseColor(context)
                            currentVerseSize = VersePrefsManager.getVerseSize(context)
                            currentVerseScale = VersePrefsManager.getVerseScale(context)
                            currentVerseStyle = VersePrefsManager.getVerseStyle(context)
                            currentVerseYPct = VersePrefsManager.getVerseYPct(context)
                            baseWallpaperBitmap = finalBmp
                            showTextOptionsSheet = false
                        }
                    }
                }
            )
        }

        // ─── BOTTOM SHEET 3: KEY SETUP SHEET ───
        if (showKeySheet) {
            ModalBottomSheet(
                onDismissRequest = { showKeySheet = false },
                containerColor = Color(0xFFFDFCF8)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    KeySetupContent(
                        isModalOrSheet = true,
                        onSuccess = {
                            showKeySheet = false
                            hasApiKey = com.example.api.ApiKeyProvider.hasWorkingKey(context)
                            android.widget.Toast.makeText(context, "API Key connected!", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        onSkip = {
                            showKeySheet = false
                            hasApiKey = com.example.api.ApiKeyProvider.hasWorkingKey(context)
                        }
                    )
                }
            }
        }

        // ─── DEVELOPER BACKDOOR PASSCODE DIALOG ───
        if (showDevPasscodeDialog) {
            AlertDialog(
                onDismissRequest = {
                    showDevPasscodeDialog = false
                    devPasscodeInput = ""
                    devPasscodeError = null
                },
                containerColor = Color(0xFFFDFCF8),
                shape = RoundedCornerShape(20.dp),
                title = {
                    Text(
                        text = "Developer Unlock",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = Color(0xFF2C2420)
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Enter passcode to access developer tools.",
                            fontSize = 13.sp,
                            color = Color(0xFF8B7E72),
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = devPasscodeInput,
                            onValueChange = {
                                devPasscodeInput = it
                                devPasscodeError = null
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (devPasscodeInput.trim() == "1982") {
                                        showDevPasscodeDialog = false
                                        devPasscodeInput = ""
                                        devPasscodeError = null
                                        showDevToolsDialog = true
                                    } else {
                                        devPasscodeError = "Wrong code"
                                    }
                                }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF2C2420),
                                unfocusedTextColor = Color(0xFF2C2420),
                                cursorColor = Color(0xFFB4574E),
                                focusedBorderColor = Color(0xFFB4574E),
                                unfocusedBorderColor = Color(0xFFE8E0D4),
                                focusedContainerColor = Color(0xFFF2EFE6),
                                unfocusedContainerColor = Color(0xFFF2EFE6)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dev_passcode_input")
                        )
                        if (devPasscodeError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = devPasscodeError ?: "",
                                color = Color(0xFFB4574E),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (devPasscodeInput.trim() == "1982") {
                                showDevPasscodeDialog = false
                                devPasscodeInput = ""
                                devPasscodeError = null
                                showDevToolsDialog = true
                            } else {
                                devPasscodeError = "Wrong code"
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFB4574E),
                            contentColor = Color(0xFFFDFCF8)
                        ),
                        modifier = Modifier.testTag("dev_unlock_confirm_button")
                    ) {
                        Text("Unlock", fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            showDevPasscodeDialog = false
                            devPasscodeInput = ""
                            devPasscodeError = null
                        }
                    ) {
                        Text("Cancel", color = Color(0xFF8B7E72), fontWeight = FontWeight.Medium)
                    }
                }
            )
        }

        // ─── DEVELOPER TOOLS DIALOG ───
        if (showDevToolsDialog) {
            AlertDialog(
                onDismissRequest = { showDevToolsDialog = false },
                containerColor = Color(0xFFFDFCF8),
                shape = RoundedCornerShape(20.dp),
                title = {
                    Text(
                        text = "Developer Tools",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = Color(0xFF2C2420)
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 1. Toggle: "Use built-in developer key"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF7F4EC))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Use built-in developer key",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF2C2420)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isDeveloperModeActive) "Connected (Developer Key)" else "Use pre-configured backdoor key",
                                    fontSize = 11.sp,
                                    color = if (isDeveloperModeActive) Color(0xFFB4574E) else Color(0xFF8B7E72)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            androidx.compose.material3.Switch(
                                checked = isDeveloperModeActive,
                                onCheckedChange = { enabled ->
                                    isDeveloperModeActive = enabled
                                    com.example.api.ApiKeyProvider.setDeveloperMode(context, enabled)
                                    hasApiKey = com.example.api.ApiKeyProvider.hasWorkingKey(context)
                                    android.widget.Toast.makeText(
                                        context,
                                        if (enabled) "Developer key enabled" else "Developer key disabled",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                },
                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFFDFCF8),
                                    checkedTrackColor = Color(0xFFB4574E),
                                    checkedBorderColor = Color(0xFFB4574E),
                                    uncheckedThumbColor = Color(0xFF8B7E72),
                                    uncheckedTrackColor = Color(0xFFE8E0D4),
                                    uncheckedBorderColor = Color(0xFFE8E0D4)
                                ),
                                modifier = Modifier.testTag("dev_key_toggle")
                            )
                        }

                        // 2. Button: "Open diagnostics"
                        Button(
                            onClick = {
                                showDevToolsDialog = false
                                onOpenDiagnostic()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("btn_open_diagnostics"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFB4574E),
                                contentColor = Color(0xFFFDFCF8)
                            )
                        ) {
                            Text(
                                text = "Open Diagnostics",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { showDevToolsDialog = false }
                    ) {
                        Text("Close", color = Color(0xFF8B7E72), fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
    }
}

/**
 * Small-caps taupe section label above cards
 */
@Composable
private fun SectionHeaderLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF8B7E72),
        letterSpacing = 1.2.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp)
    )
}

/**
 * Clean Canvas-drawn Vector Icons matching brand aesthetics
 */
@Composable
private fun BellIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.12f)
            cubicTo(w * 0.28f, h * 0.12f, w * 0.22f, h * 0.45f, w * 0.22f, h * 0.65f)
            lineTo(w * 0.12f, h * 0.80f)
            lineTo(w * 0.88f, h * 0.80f)
            lineTo(w * 0.78f, h * 0.65f)
            cubicTo(w * 0.78f, h * 0.45f, w * 0.72f, h * 0.12f, w * 0.5f, h * 0.12f)
            close()
        }
        drawPath(path, color = tint, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))

        // Clapper at bottom
        drawCircle(
            color = tint,
            radius = 1.8.dp.toPx(),
            center = Offset(w * 0.5f, h * 0.90f)
        )
        // Top loop
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, h * 0.04f),
            end = Offset(w * 0.5f, h * 0.12f),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun MoonIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val path = Path().apply {
            moveTo(w * 0.65f, h * 0.10f)
            cubicTo(w * 0.25f, h * 0.18f, w * 0.20f, h * 0.75f, w * 0.60f, h * 0.90f)
            cubicTo(w * 0.38f, h * 0.80f, w * 0.35f, h * 0.35f, w * 0.65f, h * 0.10f)
            close()
        }
        drawPath(path, color = tint)
    }
}

@Composable
private fun LayoutIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Outer container
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.12f, h * 0.15f),
            size = androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.70f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5.dp.toPx()),
            style = Stroke(width = 1.6.dp.toPx())
        )
        // Inner header line
        drawLine(
            color = tint,
            start = Offset(w * 0.25f, h * 0.38f),
            end = Offset(w * 0.75f, h * 0.38f),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round
        )
        // Inner body lines
        drawLine(
            color = tint,
            start = Offset(w * 0.25f, h * 0.55f),
            end = Offset(w * 0.65f, h * 0.55f),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

/**
 * Computes maximum consecutive streak length across all prayer history dates
 */
private fun calculateLongestStreak(history: Set<String>): Int {
    if (history.isEmpty()) return 0
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val parsedDates = history.mapNotNull {
        try { sdf.parse(it) } catch (_: Exception) { null }
    }.sorted()

    if (parsedDates.isEmpty()) return 0

    var maxStreak = 1
    var currentRun = 1

    val cal1 = Calendar.getInstance()
    val cal2 = Calendar.getInstance()

    for (i in 1 until parsedDates.size) {
        cal1.time = parsedDates[i - 1]
        cal2.time = parsedDates[i]

        cal1.add(Calendar.DAY_OF_YEAR, 1)
        val isConsecutive = cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)

        if (isConsecutive) {
            currentRun++
            if (currentRun > maxStreak) {
                maxStreak = currentRun
            }
        } else if (cal1.time.before(cal2.time)) {
            currentRun = 1
        }
    }
    return maxStreak
}

/**
 * Onboarding-style Dialog for editing Name and optional DOB
 */
@Composable
private fun EditProfileDialog(
    currentName: String,
    currentDob: String,
    onDismiss: () -> Unit,
    onSave: (name: String, dob: String) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val yearFocusRequester = remember { FocusRequester() }

    var nameInput by remember { mutableStateOf(currentName) }

    val parts = remember(currentDob) { currentDob.split(" ") }
    var selectedMonth by remember { mutableStateOf(parts.getOrNull(0) ?: "") }
    var selectedDay by remember { mutableStateOf(parts.getOrNull(1)?.removeSuffix(",") ?: "") }
    var yearInput by remember { mutableStateOf(parts.getOrNull(2) ?: "") }

    var showMonthDialog by remember { mutableStateOf(false) }
    var showDayDialog by remember { mutableStateOf(false) }

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
    val isNameValid = nameInput.trim().isNotEmpty()
    val isCanSave = isNameValid && isDobValid

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
        focusedContainerColor = Color(0xFFFDFCF8),
        unfocusedContainerColor = Color(0xFFFDFCF8)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFFDFCF8),
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Edit Profile",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2C2420),
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Name Field
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    placeholder = { Text("What should we call you?") },
                    label = { Text("Your Name") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFFB4574E),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    colors = unifiedTextFieldColors,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_name_field")
                )

                // Date of Birth Section
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Date of Birth (Optional)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF8B7E72)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Month Selector
                        Surface(
                            modifier = Modifier
                                .weight(1.3f)
                                .height(56.dp)
                                .clickable { showMonthDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFFDFCF8),
                            border = BorderStroke(1.dp, Color(0xFFE8E0D4))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (selectedMonth.isNotEmpty()) selectedMonth else "Month",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (selectedMonth.isNotEmpty()) Color(0xFF2C2420) else Color(0xFF8B7E72),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color(0xFF8B7E72),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Day Selector
                        Surface(
                            modifier = Modifier
                                .weight(0.9f)
                                .height(56.dp)
                                .clickable { showDayDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFFDFCF8),
                            border = BorderStroke(1.dp, Color(0xFFE8E0D4))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (selectedDay.isNotEmpty()) selectedDay else "Day",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (selectedDay.isNotEmpty()) Color(0xFF2C2420) else Color(0xFF8B7E72),
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color(0xFF8B7E72),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Year Field
                        OutlinedTextField(
                            value = yearInput,
                            onValueChange = {
                                if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                    yearInput = it
                                }
                            },
                            placeholder = { Text("Year", fontSize = 13.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }
                            ),
                            colors = unifiedTextFieldColors,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.1f)
                                .height(56.dp)
                                .focusRequester(yearFocusRequester)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val computedDob = if (isDobAllFilled) {
                        "$selectedMonth $selectedDay, $yearInput"
                    } else {
                        ""
                    }
                    onSave(nameInput.trim(), computedDob)
                },
                enabled = isCanSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB4574E),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFC8BFB3),
                    disabledContentColor = Color(0xFFF2EFE6)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF8B7E72))
            }
        }
    )

    // Month Picker Dialog
    if (showMonthDialog) {
        Dialog(onDismissRequest = { showMonthDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFFDFCF8),
                border = BorderStroke(1.dp, Color(0xFFE8E0D4)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Select Month",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF2C2420)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        months.forEach { month ->
                            val isSelected = month == selectedMonth
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFFB4574E).copy(alpha = 0.12f) else Color.Transparent)
                                    .clickable {
                                        selectedMonth = month
                                        showMonthDialog = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = month,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFFB4574E) else Color(0xFF2C2420),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Day Picker Dialog
    if (showDayDialog) {
        Dialog(onDismissRequest = { showDayDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFFDFCF8),
                border = BorderStroke(1.dp, Color(0xFFE8E0D4)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Select Day",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF2C2420)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        days.forEach { day ->
                            val isSelected = day == selectedDay
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFFB4574E).copy(alpha = 0.12f) else Color.Transparent)
                                    .clickable {
                                        selectedDay = day
                                        showDayDialog = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = day,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFFB4574E) else Color(0xFF2C2420),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Snooze options configurator dialog with elevated brand aesthetic
 */
@Composable
private fun SnoozeOptionsDialog(
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val currentOptions = remember { WakePrefsManager.getSnoozeOptions(context) }
    val availableMinutes = listOf(5, 10, 15, 20, 30, 45, 60, 90)

    var slot1 by remember { mutableIntStateOf(currentOptions.getOrNull(0) ?: 15) }
    var slot2 by remember { mutableIntStateOf(currentOptions.getOrNull(1) ?: 30) }
    var slot3 by remember { mutableIntStateOf(currentOptions.getOrNull(2) ?: 60) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFCF8)),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            border = BorderStroke(1.dp, Color(0xFFE8E0D4)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .testTag("dialog_snooze_durations")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header with Moon Icon and Serif Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFB4574E).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        MoonIcon(
                            tint = Color(0xFFB4574E),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = "Snooze Durations",
                        style = TextStyle(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color(0xFF2C2420)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Select three snooze duration options for your reminders:",
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = Color(0xFF8B7E72),
                        lineHeight = 20.sp
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Card containing the 3 option rows
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF2EFE6).copy(alpha = 0.5f))
                        .border(1.dp, Color(0xFFE8E0D4), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    DurationDropdownSelector(
                        label = "Option 1 (Default)",
                        selectedMinutes = slot1,
                        options = availableMinutes,
                        onSelected = { slot1 = it }
                    )

                    HorizontalDivider(color = Color(0xFFE8E0D4), thickness = 1.dp)

                    DurationDropdownSelector(
                        label = "Option 2",
                        selectedMinutes = slot2,
                        options = availableMinutes,
                        onSelected = { slot2 = it }
                    )

                    HorizontalDivider(color = Color(0xFFE8E0D4), thickness = 1.dp)

                    DurationDropdownSelector(
                        label = "Option 3",
                        selectedMinutes = slot3,
                        options = availableMinutes,
                        onSelected = { slot3 = it }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions: Cancel & Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .height(44.dp)
                            .padding(horizontal = 8.dp)
                            .testTag("btn_cancel_snooze")
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF8B7E72)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            WakePrefsManager.setSnoozeOptions(context, listOf(slot1, slot2, slot3))
                            WakePrefsManager.setDefaultSnoozeMinutes(context, slot1)
                            onSaved()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFB4574E),
                            contentColor = Color(0xFFFDFCF8)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("btn_save_snooze")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFFFDFCF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Save",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DurationDropdownSelector(
    label: String,
    selectedMinutes: Int,
    options: List<Int>,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF2C2420),
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFDFCF8))
                    .border(1.dp, Color(0xFFE8E0D4), RoundedCornerShape(10.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = WakePrefsManager.formatDuration(selectedMinutes),
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFB4574E),
                    fontSize = 14.sp,
                    maxLines = 1,
                    softWrap = false
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Select duration",
                    tint = Color(0xFF8B7E72),
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(Color(0xFFFDFCF8))
                    .border(1.dp, Color(0xFFE8E0D4), RoundedCornerShape(12.dp))
            ) {
                options.forEach { minutes ->
                    val isSelected = minutes == selectedMinutes
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = WakePrefsManager.formatDuration(minutes),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFFB4574E) else Color(0xFF2C2420),
                                    fontSize = 14.sp
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color(0xFFB4574E),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSelected(minutes)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Palette Icon drawn with Canvas matching brand aesthetics
 */
@Composable
private fun PaletteIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.12f)
            cubicTo(w * 0.82f, h * 0.12f, w * 0.92f, h * 0.38f, w * 0.92f, h * 0.60f)
            cubicTo(w * 0.92f, h * 0.78f, w * 0.78f, h * 0.90f, w * 0.62f, h * 0.90f)
            cubicTo(w * 0.55f, h * 0.90f, w * 0.48f, h * 0.85f, w * 0.42f, h * 0.85f)
            cubicTo(w * 0.36f, h * 0.85f, w * 0.32f, h * 0.90f, w * 0.25f, h * 0.90f)
            cubicTo(w * 0.12f, h * 0.90f, w * 0.08f, h * 0.75f, w * 0.08f, h * 0.55f)
            cubicTo(w * 0.08f, h * 0.30f, w * 0.25f, h * 0.12f, w * 0.5f, h * 0.12f)
            close()
        }
        drawPath(path, color = tint, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))

        // Small pigment wells
        drawCircle(color = tint, radius = 1.6.dp.toPx(), center = Offset(w * 0.32f, h * 0.35f))
        drawCircle(color = tint, radius = 1.6.dp.toPx(), center = Offset(w * 0.52f, h * 0.28f))
        drawCircle(color = tint, radius = 1.6.dp.toPx(), center = Offset(w * 0.70f, h * 0.38f))
        drawCircle(color = tint, radius = 1.6.dp.toPx(), center = Offset(w * 0.72f, h * 0.60f))
    }
}

/**
 * STATE A — Setup Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LockScreenSetupSheet(
    onDismiss: () -> Unit,
    onChoosePhoto: () -> Unit,
    onUseDarkDefault: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFFFDFCF8),
        scrimColor = Color(0x662C2420),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8E0D4))
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 36.dp, top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFB4574E).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = Color(0xFFB4574E),
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title
            Text(
                text = "Put your verse on your lock screen",
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF2C2420)
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Body
            Text(
                text = "Your daily verse will show on your lock screen. To do this, the app sets your lock screen wallpaper.",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF2C2420),
                    lineHeight = 20.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Warning Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF2EFE6))
                    .border(1.dp, Color(0xFFE8E0D4), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "If you continue, your current lock screen wallpaper will change. Pick your own photo, or keep our dark one.",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF8B7E72),
                        lineHeight = 18.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Button 1: [Choose my photo]
            Button(
                onClick = onChoosePhoto,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_choose_photo"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB4574E)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = Color(0xFFFDFCF8),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Choose my photo",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFDFCF8)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Button 2: [Use the dark default]
            Button(
                onClick = onUseDarkDefault,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .border(1.dp, Color(0xFFE8E0D4), RoundedCornerShape(16.dp))
                    .testTag("btn_use_dark_default"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFDFCF8)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = "Use the dark default",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2C2420)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Button 3: [Not now]
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("btn_setup_not_now")
            ) {
                Text(
                    text = "Not now",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF8B7E72)
                )
            }
        }
    }
}

/**
 * STATE B & FIRST SAVE — Text Options Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextOptionsSheet(
    initialColor: Int,
    initialSize: String,
    initialStyle: String,
    currentVerse: String,
    baseBitmap: Bitmap?,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit,
    onSave: (color: Int, size: String, scale: Float, style: String) -> Unit
) {
    val context = LocalContext.current
    var selectedColor by remember { mutableIntStateOf(initialColor) }
    var selectedSize by remember { mutableStateOf(initialSize) }
    var selectedStyle by remember { mutableStateOf(initialStyle) }
    var showCustomColorDialog by remember { mutableStateOf(false) }

    val colorOptions = listOf(
        Pair("White", 0xFFFFFFFF.toInt()),
        Pair("Dark", 0xFF2C2420.toInt()),
        Pair("Gold", 0xFFE8B87A.toInt()),
        Pair("Terracotta", 0xFFB4574E.toInt()),
        Pair("Rose", 0xFFD98A84.toInt()),
        Pair("Cream", 0xFFF2EFE6.toInt()),
        Pair("Taupe", 0xFF8B7E72.toInt()),
        Pair("Mint", 0xFF6B8F5A.toInt()),
        Pair("Slate", 0xFF5B7E91.toInt())
    )

    val sizeOptions = listOf(
        Triple("Small", 0.85f, 11.sp),
        Triple("Medium", 1.0f, 13.sp),
        Triple("Large", 1.25f, 15.sp)
    )

    val styleOptions = listOf("Modern", "Classic", "Bold", "Light")

    val selectedScale = when (selectedSize) {
        "Small" -> 0.85f
        "Large" -> 1.25f
        else -> 1.0f
    }

    if (showCustomColorDialog) {
        CustomColorDialog(
            initialColor = selectedColor,
            onDismiss = { showCustomColorDialog = false },
            onColorSelected = { newColor ->
                selectedColor = newColor
                VersePrefsManager.setDraftColor(context, newColor)
                showCustomColorDialog = false
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFFFDFCF8),
        scrimColor = Color(0x662C2420),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8E0D4))
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 36.dp, top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Customize text",
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF2C2420)
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Live Preview Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE8E0D4), RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F172A))
            ) {
                baseBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Wallpaper Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } ?: Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF090D16))
                            )
                        )
                )

                val fontFam = when (selectedStyle) {
                    "Classic", "Serif Bold" -> FontFamily.Serif
                    "Monospace" -> FontFamily.Monospace
                    "Cursive" -> FontFamily.Cursive
                    else -> FontFamily.Default
                }
                val fontWt = when (selectedStyle) {
                    "Bold", "Serif Bold" -> FontWeight.Bold
                    "Light" -> FontWeight.Light
                    else -> FontWeight.Normal
                }
                val fontSty = if (selectedStyle == "Classic") FontStyle.Italic else FontStyle.Normal
                val baseSizeSp = when (selectedSize) {
                    "Small" -> 11.sp
                    "Large" -> 15.sp
                    else -> 13.sp
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "\"$currentVerse\"",
                        style = TextStyle(
                            fontFamily = fontFam,
                            fontWeight = fontWt,
                            fontStyle = fontSty,
                            fontSize = (baseSizeSp.value * selectedScale).sp,
                            color = Color(selectedColor),
                            textAlign = TextAlign.Center,
                            lineHeight = (baseSizeSp.value * selectedScale * 1.35f).sp,
                            shadow = Shadow(
                                color = if (selectedColor == android.graphics.Color.BLACK) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.65f),
                                blurRadius = 6f
                            )
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 1. Color Selector (Horizontally Scrollable + Rainbow Picker)
            Text(
                text = "COLOR",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8B7E72),
                letterSpacing = 1.2.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(colorOptions) { (_, colInt) ->
                    val isSelected = selectedColor == colInt
                    val itemColor = Color(colInt)
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(itemColor)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) Color(0xFFB4574E) else Color(0xFFE8E0D4),
                                shape = CircleShape
                            )
                            .clickable {
                                selectedColor = colInt
                                VersePrefsManager.setDraftColor(context, colInt)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = if (itemColor == Color.White || itemColor == Color(0xFFF2EFE6)) Color(0xFF2C2420) else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Custom Rainbow Swatch
                item {
                    val isCustomColor = colorOptions.none { it.second == selectedColor }
                    val rainbowBrush = Brush.sweepGradient(
                        listOf(
                            Color(0xFFFF3B30),
                            Color(0xFFFF9500),
                            Color(0xFFFFCC00),
                            Color(0xFF34C759),
                            Color(0xFF00C7BE),
                            Color(0xFF007AFF),
                            Color(0xFF5856D6),
                            Color(0xFFAF52DE),
                            Color(0xFFFF2D55),
                            Color(0xFFFF3B30)
                        )
                    )
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(rainbowBrush)
                            .border(
                                width = if (isCustomColor) 2.5.dp else 1.dp,
                                color = if (isCustomColor) Color(0xFFB4574E) else Color(0xFFE8E0D4),
                                shape = CircleShape
                            )
                            .clickable {
                                showCustomColorDialog = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCustomColor) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(selectedColor))
                                    .border(1.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected custom color",
                                    tint = if (Color(selectedColor) == Color.White) Color(0xFF2C2420) else Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Size Selector
            Text(
                text = "SIZE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8B7E72),
                letterSpacing = 1.2.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sizeOptions.forEach { (sizeName, scaleVal, _) ->
                    val isSelected = selectedSize == sizeName
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0xFFB4574E) else Color(0xFFF2EFE6))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color(0xFFB4574E) else Color(0xFFE8E0D4),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                selectedSize = sizeName
                                VersePrefsManager.setDraftSize(context, sizeName)
                                VersePrefsManager.setDraftScale(context, scaleVal)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = sizeName,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFFFDFCF8) else Color(0xFF2C2420)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Style Selector
            Text(
                text = "STYLE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8B7E72),
                letterSpacing = 1.2.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                styleOptions.forEach { styleName ->
                    val isSelected = selectedStyle == styleName
                    val previewFontFam = when (styleName) {
                        "Classic" -> FontFamily.Serif
                        else -> FontFamily.Default
                    }
                    val previewFontWt = when (styleName) {
                        "Bold" -> FontWeight.Bold
                        "Light" -> FontWeight.Light
                        else -> FontWeight.Normal
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0xFFB4574E) else Color(0xFFF2EFE6))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color(0xFFB4574E) else Color(0xFFE8E0D4),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                selectedStyle = styleName
                                VersePrefsManager.setDraftStyle(context, styleName)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = styleName,
                            fontFamily = previewFontFam,
                            fontWeight = previewFontWt,
                            fontSize = 13.sp,
                            color = if (isSelected) Color(0xFFFDFCF8) else Color(0xFF2C2420)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Save Button (The Commit Point)
            Button(
                onClick = {
                    onSave(selectedColor, selectedSize, selectedScale, selectedStyle)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_save_text_options"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB4574E)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Save",
                    tint = Color(0xFFFDFCF8),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Save",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFDFCF8)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Discard Changes Button
            TextButton(
                onClick = onDiscard,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .testTag("btn_discard_draft")
            ) {
                Text(
                    text = "Discard changes",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF8B7E72)
                )
            }
        }
    }
}

/**
 * Custom Color Dialog with Hue, Saturation, and Brightness Sliders
 */
@Composable
private fun CustomColorDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit
) {
    val initialHsv = remember(initialColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor, hsv)
        hsv
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) } // 0..360
    var saturation by remember { mutableFloatStateOf(initialHsv[1] * 100f) } // 0..100
    var brightness by remember { mutableFloatStateOf(initialHsv[2] * 100f) } // 0..100

    val currentColorInt = remember(hue, saturation, brightness) {
        android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation / 100f, brightness / 100f))
    }

    val hexString = remember(currentColorInt) {
        String.format("#%06X", (0xFFFFFF and currentColorInt))
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFCF8)),
            border = BorderStroke(1.dp, Color(0xFFE8E0D4)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Custom Color",
                    style = TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFF2C2420)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Live Preview Circle + Hex Label
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF2EFE6))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(currentColorInt))
                                .border(1.dp, Color(0xFFE8E0D4), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = hexString,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2C2420),
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Sample Verse Text in Preview
                    Text(
                        text = "Verse Preview",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(currentColorInt),
                            shadow = Shadow(color = Color.Black.copy(alpha = 0.4f), blurRadius = 4f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 1. Hue Slider (0 - 360)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HUE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8B7E72),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${hue.roundToInt()}°",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2C2420)
                    )
                }
                Slider(
                    value = hue,
                    onValueChange = { hue = it },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFB4574E),
                        activeTrackColor = Color(0xFFB4574E),
                        inactiveTrackColor = Color(0xFFE8E0D4)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Saturation Slider (0 - 100)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SATURATION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8B7E72),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${saturation.roundToInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2C2420)
                    )
                }
                Slider(
                    value = saturation,
                    onValueChange = { saturation = it },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFB4574E),
                        activeTrackColor = Color(0xFFB4574E),
                        inactiveTrackColor = Color(0xFFE8E0D4)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Brightness Slider (0 - 100)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BRIGHTNESS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8B7E72),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${brightness.roundToInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2C2420)
                    )
                }
                Slider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFB4574E),
                        activeTrackColor = Color(0xFFB4574E),
                        inactiveTrackColor = Color(0xFFE8E0D4)
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Actions
                Button(
                    onClick = {
                        onColorSelected(currentColorInt)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB4574E))
                ) {
                    Text(
                        text = "Use this color",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFDFCF8)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF8B7E72)
                    )
                }
            }
        }
    }
}
