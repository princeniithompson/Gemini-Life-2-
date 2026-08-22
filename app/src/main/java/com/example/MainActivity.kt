package com.example

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
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
import androidx.compose.foundation.layout.Box
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
        WakeDetectorService.startService(this)
        com.example.wake.PrayerAlarmScheduler.scheduleNextPrayer(this)
        com.example.ui.key.GuideImageLoader.preload(this)

        if (VersePrefsManager.isVerseEnabled(this)) {
            lifecycleScope.launch(Dispatchers.IO) {
                WallpaperVerseRenderer.applyWallpaper(applicationContext)
            }
        }

        setContent {
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
