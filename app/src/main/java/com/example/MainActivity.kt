package com.example

import android.Manifest
import android.app.NotificationManager
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.customization.CustomizationScreen
import com.example.customization.VerseEditorScreen
import com.example.customization.VersePrefsManager
import com.example.customization.WallpaperVerseRenderer
import com.example.ui.DiagnosticScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.DiagnosticViewModel
import com.example.wake.PermissionHelper
import com.example.wake.WakeDetectorService
import com.example.wake.WakeOverlayManager
import com.example.wake.WakePrefsManager

class MainActivity : ComponentActivity() {

    private val diagnosticViewModel: DiagnosticViewModel by viewModels()

    var isInPipMode by androidx.compose.runtime.mutableStateOf(false)
        private set

    var isVideoGuideActive by androidx.compose.runtime.mutableStateOf(false)
    var lastVideoPosition by androidx.compose.runtime.mutableIntStateOf(0)

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
    }

    private fun getSafePipRational(width: Int, height: Int): Rational {
        if (width <= 0 || height <= 0) {
            return Rational(9, 16)
        }
        val ratio = width.toFloat() / height.toFloat()
        return when {
            ratio < 0.418410f -> Rational(1000, 2390)
            ratio > 2.390000f -> Rational(2390, 1000)
            else -> Rational(width, height)
        }
    }

    fun updatePipParams(videoFile: java.io.File? = null, autoEnter: Boolean = true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                    val fileToUse = videoFile ?: com.example.ui.key.GuideVideoLoader.getCachedVideoFile(this)
                    val (w, h) = com.example.ui.key.GuideVideoLoader.getVideoDimensions(fileToUse)
                    val rational = getSafePipRational(w, h)
                    val builder = PictureInPictureParams.Builder()
                        .setAspectRatio(rational)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        builder.setAutoEnterEnabled(autoEnter)
                        builder.setSeamlessResizeEnabled(true)
                    }
                    setPictureInPictureParams(builder.build())
                    Log.d("MainActivity", "[PIP] updatePipParams: autoEnter=$autoEnter, ratio=$rational")
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "[PIP] Failed updating PiP params: ${e.message}")
            }
        }
    }

    fun requestPipMode(videoFile: java.io.File? = null): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                    Log.w("MainActivity", "[PIP] Device does not support Picture-in-Picture feature")
                    return false
                }
                if (!PermissionHelper.hasPipPermission(this)) {
                    Log.w("MainActivity", "[PIP] Picture-in-Picture permission is not granted")
                    PermissionHelper.openPipSettings(this)
                    return false
                }
                val fileToUse = videoFile ?: com.example.ui.key.GuideVideoLoader.getCachedVideoFile(this)
                val (w, h) = com.example.ui.key.GuideVideoLoader.getVideoDimensions(fileToUse)
                val rational = getSafePipRational(w, h)
                Log.d("MainActivity", "[PIP] Entering PiP mode with ratio $rational")
                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(rational)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setAutoEnterEnabled(true)
                    builder.setSeamlessResizeEnabled(true)
                }
                val params = builder.build()
                setPictureInPictureParams(params)
                val success = enterPictureInPictureMode(params)
                Log.i("MainActivity", "[PIP] enterPictureInPictureMode returned $success")
                return success
            } catch (e: Exception) {
                Log.w("MainActivity", "[PIP] Failed to enter Picture-in-Picture: ${e.message}")
            }
        }
        return false
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isVideoGuideActive && PermissionHelper.hasPipPermission(this)) {
            requestPipMode()
        }
    }

    private enum class FirstRunStep {
        IDLE,
        MIC,
        PHONE_STATE,
        OVERLAY,
        NOTIFICATIONS,
        DONE
    }

    private var firstRunStep = FirstRunStep.IDLE

    private val recordAudioLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val msg = "[PERMS] RECORD_AUDIO result: $isGranted"
        Log.i("WakeDetector", msg)
        WakePrefsManager.logWakeEvent(msg)
        if (firstRunStep == FirstRunStep.MIC) {
            requestStep2PhoneState()
        }
    }

    private val phoneStateLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val msg = "[PERMS] READ_PHONE_STATE result: $isGranted"
        Log.i("WakeDetector", msg)
        WakePrefsManager.logWakeEvent(msg)
        if (firstRunStep == FirstRunStep.PHONE_STATE) {
            requestStep3Overlay()
        }
    }

    private val overlaySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = PermissionHelper.hasOverlayPermission(this)
        val msg = "[PERMS] Overlay settings return -> granted: $granted"
        Log.i("WakeDetector", msg)
        WakePrefsManager.logWakeEvent(msg)
        if (firstRunStep == FirstRunStep.OVERLAY) {
            requestStep4Notifications()
        }
    }

    private val postNotificationsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val msg = "[PERMS] POST_NOTIFICATIONS result: $isGranted"
        Log.i("WakeDetector", msg)
        WakePrefsManager.logWakeEvent(msg)
        if (firstRunStep == FirstRunStep.NOTIFICATIONS) {
            finishFirstRunPermissions()
        }
    }

    private val exactAlarmSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = PermissionHelper.hasExactAlarmPermission(this)
        val msg = "[PERMS] Exact alarm settings return -> granted: $granted"
        Log.i("WakeDetector", msg)
        WakePrefsManager.logWakeEvent(msg)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        diagnosticViewModel.attachActivity(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (diagnosticViewModel.isLockMode.value) {
                    val msg = "[LOCK] Back blocked"
                    Log.i("WakeDetector", msg)
                    WakePrefsManager.logWakeEvent(msg)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        checkAndRequestFirstRunPermissions()
        com.example.wake.OvernightJournal.logPermissionsAudit(this, "MainActivity.onCreate")
        com.example.wake.PrayerAlarmScheduler.scheduleNextPrayer(this)
        com.example.ui.key.GuideImageLoader.preload(this)

        if (VersePrefsManager.isVerseEnabled(this)) {
            lifecycleScope.launch(Dispatchers.IO) {
                WallpaperVerseRenderer.applyWallpaper(applicationContext)
            }
        }

        setContent {
            if (isInPipMode) {
                // Zero-bezel root layout bypass for Picture-in-Picture mode
                val context = androidx.compose.ui.platform.LocalContext.current
                val cachedVideo = com.example.ui.key.GuideVideoLoader.getCachedVideoFile(context)
                var videoViewRef by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<android.widget.VideoView?>(null) }
                var showPipControls by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable { showPipControls = !showPipControls },
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            android.widget.VideoView(ctx).apply {
                                if (cachedVideo.exists()) {
                                    setVideoPath(cachedVideo.absolutePath)
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = true
                                        if (lastVideoPosition > 0) {
                                            seekTo(lastVideoPosition)
                                        }
                                        start()
                                    }
                                }
                                videoViewRef = this
                            }
                        },
                        update = { vv ->
                            videoViewRef = vv
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (showPipControls) {
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color(0x88000000), androidx.compose.foundation.shape.CircleShape)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            androidx.compose.material3.Text(
                                text = "-5s",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    videoViewRef?.let { vv ->
                                        val pos = (vv.currentPosition - 5000).coerceAtLeast(0)
                                        vv.seekTo(pos)
                                        lastVideoPosition = pos
                                    }
                                }
                            )
                            androidx.compose.material3.Text(
                                text = "+5s",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    videoViewRef?.let { vv ->
                                        val pos = (vv.currentPosition + 5000)
                                        vv.seekTo(pos)
                                        lastVideoPosition = pos
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                MyApplicationTheme {
                val context = androidx.compose.ui.platform.LocalContext.current
                var currentScreen by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf("home")
                }
                var isOnboardingComplete by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(WakePrefsManager.isOnboardingComplete(context))
                }

                var openTextOptionsForFirstSave by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(false)
                }
                var previousScreenBeforeEditor by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf("profile")
                }

                var showReauthKeySheet by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(false)
                }

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    com.example.api.ApiKeyProvider.authErrorEvent.collect {
                        showReauthKeySheet = true
                    }
                }

                androidx.compose.runtime.LaunchedEffect(isOnboardingComplete, openTextOptionsForFirstSave, currentScreen) {
                    if (isOnboardingComplete && 
                        !openTextOptionsForFirstSave && 
                        currentScreen != "verse_editor" && 
                        !com.example.api.ApiKeyProvider.hasWorkingKey(context)) {
                        showReauthKeySheet = true
                    }
                }

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    val currentKey = com.example.api.ApiKeyProvider.getUserApiKey(context)
                    if (currentKey.isNotBlank()) {
                        val result = com.example.api.ApiKeyProvider.validateKeyLive(currentKey)
                        if (result.isFailure) {
                            com.example.api.ApiKeyProvider.notifyAuthError()
                        }
                    }
                }

                if (!isOnboardingComplete && currentScreen != "verse_editor") {
                    com.example.ui.OnboardingScreen(
                        onOnboardingComplete = {
                            WakePrefsManager.setOnboardingComplete(context, true)
                            isOnboardingComplete = true
                            if (com.example.api.ApiKeyProvider.hasWorkingKey(context)) {
                                android.widget.Toast.makeText(context, "You're ready!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        onOpenVerseEditor = {
                            previousScreenBeforeEditor = "home"
                            currentScreen = "verse_editor"
                        }
                    )
                } else {
                    when (currentScreen) {
                        "home" -> com.example.ui.HomeScreen(
                            onOpenProfile = { currentScreen = "profile" },
                            onOpenFavorites = { currentScreen = "favorites" }
                        )
                        "favorites" -> com.example.ui.FavoritesScreen(
                            onBack = { currentScreen = "home" },
                            onOpenHome = { currentScreen = "home" },
                            onOpenProfile = { currentScreen = "profile" }
                        )
                        "profile" -> com.example.ui.ProfileScreen(
                            onBack = { currentScreen = "home" },
                            onOpenVerseEditor = {
                                previousScreenBeforeEditor = "profile"
                                currentScreen = "verse_editor"
                            },
                            openTextOptionsOnLaunch = openTextOptionsForFirstSave,
                            onResetTextOptionsFlag = { 
                                openTextOptionsForFirstSave = false
                            },
                            onOpenFavorites = { currentScreen = "favorites" },
                            onOpenDiagnostic = { currentScreen = "diagnostic" }
                        )
                        "customization" -> CustomizationScreen(
                            onBack = { currentScreen = "profile" },
                            onOpenVerseEditor = {
                                previousScreenBeforeEditor = "customization"
                                currentScreen = "verse_editor"
                            }
                        )
                        "verse_editor" -> VerseEditorScreen(
                            onBack = { isFirstSave ->
                                if (!isOnboardingComplete) {
                                    WakePrefsManager.setOnboardingComplete(context, true)
                                    isOnboardingComplete = true
                                }
                                if (isFirstSave) {
                                    openTextOptionsForFirstSave = true
                                    currentScreen = "profile"
                                } else {
                                    currentScreen = previousScreenBeforeEditor
                                }
                            }
                        )
                        "diagnostic" -> DiagnosticScreen(
                            viewModel = diagnosticViewModel,
                            onBack = { currentScreen = "profile" }
                        )
                    }
                }

                if (showReauthKeySheet && !openTextOptionsForFirstSave && currentScreen != "verse_editor") {
                    ModalBottomSheet(
                        onDismissRequest = { showReauthKeySheet = false },
                        containerColor = Color(0xFFFDFCF8)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                        ) {
                            com.example.ui.key.KeySetupContent(
                                isModalOrSheet = true,
                                onSuccess = {
                                    showReauthKeySheet = false
                                    android.widget.Toast.makeText(context, "You're ready!", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                onSkip = {
                                    showReauthKeySheet = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val autoStart = intent?.getBooleanExtra("auto_start_prayer", false) == true
        val lockMode = intent?.getBooleanExtra("lock_mode", false) == true
        val requestMic = intent?.getBooleanExtra("request_mic_permission", false) == true
        val requestOverlay = intent?.getBooleanExtra("request_overlay_permission", false) == true

        if (requestMic && !PermissionHelper.hasRecordAudioPermission(this)) {
            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else if (requestOverlay && !PermissionHelper.hasOverlayPermission(this)) {
            PermissionHelper.openOverlaySettings(this)
        }

        if (autoStart || lockMode) {
            val msg = "[WAKE] MainActivity launched with auto_start_prayer/lock_mode -> starting Gemini Live connection"
            Log.i("WakeDetector", msg)
            WakePrefsManager.logWakeEvent(msg)
            if (lockMode) {
                diagnosticViewModel.enableLockMode(this, this)
            }
            diagnosticViewModel.connect(this)
        }
    }

    private fun checkAndRequestFirstRunPermissions() {
        if (WakePrefsManager.hasPromptedFirstRunPermissions(this)) {
            return
        }
        requestStep1Microphone()
    }

    private fun requestStep1Microphone() {
        if (!PermissionHelper.hasRecordAudioPermission(this)) {
            firstRunStep = FirstRunStep.MIC
            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            requestStep2PhoneState()
        }
    }

    private fun requestStep2PhoneState() {
        if (!PermissionHelper.hasReadPhoneStatePermission(this)) {
            firstRunStep = FirstRunStep.PHONE_STATE
            phoneStateLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        } else {
            requestStep3Overlay()
        }
    }

    private fun requestStep3Overlay() {
        if (!PermissionHelper.hasOverlayPermission(this)) {
            firstRunStep = FirstRunStep.OVERLAY
            try {
                val intent = PermissionHelper.getOverlaySettingsIntent(this, newTask = false)
                overlaySettingsLauncher.launch(intent)
            } catch (e: Exception) {
                Log.w("WakeDetector", "[PERMS] Failed launching overlay settings: ${e.message}")
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val genericIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        overlaySettingsLauncher.launch(genericIntent)
                    } else {
                        requestStep4Notifications()
                    }
                } catch (_: Exception) {
                    requestStep4Notifications()
                }
            }
        } else {
            requestStep4Notifications()
        }
    }

    private fun requestStep4Notifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !PermissionHelper.hasNotificationPermission(this)) {
            firstRunStep = FirstRunStep.NOTIFICATIONS
            postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            finishFirstRunPermissions()
        }
    }

    private fun finishFirstRunPermissions() {
        firstRunStep = FirstRunStep.DONE
        WakePrefsManager.setPromptedFirstRunPermissions(this, true)
        val msg = "[PERMS] First-run permissions sequence finished"
        Log.i("WakeDetector", msg)
        WakePrefsManager.logWakeEvent(msg)
    }

    override fun onResume() {
        super.onResume()
        val overlayGranted = PermissionHelper.hasOverlayPermission(this)
        val msg = "[WAKE] onResume -> overlay permission granted: $overlayGranted"
        Log.i("WakeDetector", msg)
        WakePrefsManager.logWakeEvent(msg)

        // Reschedule alarm on coming to foreground (defense against OEM reschedule loss)
        com.example.wake.PrayerAlarmScheduler.scheduleNextPrayer(this)

        if (firstRunStep == FirstRunStep.OVERLAY) {
            requestStep4Notifications()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
    }

    override fun onDestroy() {
        super.onDestroy()
        diagnosticViewModel.release()
    }
}

fun Context.findMainActivity(): MainActivity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is MainActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
