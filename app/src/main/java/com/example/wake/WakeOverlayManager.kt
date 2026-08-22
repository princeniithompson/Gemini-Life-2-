package com.example.wake

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.favorites.FavoritesManager
import com.example.ui.key.GuideImageLoader
import com.example.ui.key.GuideImageType
import com.example.ui.session.PrayerOrb
import com.example.ui.session.SessionActions
import com.example.ui.session.SettingsSheet
import com.example.ui.session.TranscriptPanel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.diagnostic.StreamState

private class MyOverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onStart() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}

object WakeOverlayManager {

    private var overlayView: View? = null
    private var lifecycleOwner: MyOverlayLifecycleOwner? = null

    fun isOverlayShowing(): Boolean {
        return overlayView != null && overlayView?.parent != null
    }

    fun triggerPrayerDoor(context: Context) {
        WakePrefsManager.setRitualPending(context, true, reason = "door displayed")
        FirstLightNotificationHelper.cancelNotification(context)

        val canDraw = Settings.canDrawOverlays(context)
        OvernightJournal.log(context, "SCHEDULER", "triggerPrayerDoor called (canDrawOverlays=$canDraw, isOverlayShowing=${isOverlayShowing()})")

        if (canDraw) {
            if (!isOverlayShowing()) {
                WakeDetectorService.setOverlayPage(OverlayPage.PRAYER_DOOR)
                showOverlay(context)
            } else {
                Log.i("WakeDetector", "[WAKE] Overlay already attached")
                OvernightJournal.log(context, "SCHEDULER", "Overlay already attached, skipped show")
            }
        } else {
            val msg = "[WAKE] Overlay permission missing - notification fallback"
            Log.w("WakeDetector", msg)
            WakePrefsManager.logWakeEvent(msg)
            OvernightJournal.log(context, "SCHEDULER", "Overlay show attempt failed: Overlay permission missing - posting notification fallback")
            postNotificationFallback(context)
        }
    }

    fun showOverlay(context: Context) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        removeOverlay(context)

        val owner = MyOverlayLifecycleOwner()
        owner.onCreate()
        owner.onStart()
        lifecycleOwner = owner

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)

            setContent {
                WakeOverlayRoot(context = context)
            }
        }

        overlayView = composeView

        @Suppress("DEPRECATION")
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            windowManager.addView(composeView, layoutParams)
            val msg = "[WAKE] Premium overlay shown after unlock"
            Log.i("WakeDetector", msg)
            WakePrefsManager.logWakeEvent(msg)
            OvernightJournal.log(context, "SCHEDULER", "Overlay show attempt SUCCESS: WindowManager.addView completed with TYPE_APPLICATION_OVERLAY")
        } catch (e: Exception) {
            val msg = "[WAKE] Overlay blocked by OEM security - notification fallback"
            Log.w("WakeDetector", msg, e)
            WakePrefsManager.logWakeEvent(msg)
            OvernightJournal.log(context, "SCHEDULER", "Overlay show attempt FAILED: ${e.message} - notification fallback")
            removeOverlay(context)
            postNotificationFallback(context)
        }
    }

    fun hideForCall(context: Context) {
        try {
            val currentView = overlayView
            if (currentView != null && currentView.parent != null) {
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager.removeView(currentView)
            }
        } catch (e: Exception) {
            Log.w("WakeDetector", "[WAKE] Exception removing overlay view for call: ${e.message}")
        } finally {
            lifecycleOwner?.onDestroy()
            lifecycleOwner = null
            overlayView = null
        }
    }

    fun removeOverlay(context: Context) {
        try {
            WakeDetectorService.engine.abandonAudioFocus(context)
            val currentView = overlayView
            if (currentView != null && currentView.parent != null) {
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager.removeView(currentView)
            }
        } catch (e: Exception) {
            Log.w("WakeDetector", "[WAKE] Exception removing overlay view: ${e.message}")
        } finally {
            lifecycleOwner?.onDestroy()
            lifecycleOwner = null
            overlayView = null
        }
    }

    fun postNotificationFallback(context: Context) {
        val proceedIntent = Intent(context, PrayerActionReceiver::class.java).apply {
            action = PrayerActionReceiver.ACTION_NOTIF_PROCEED
        }
        val contentPendingIntent = PendingIntent.getBroadcast(
            context,
            100,
            proceedIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, PrayerActionReceiver::class.java).apply {
            action = PrayerActionReceiver.ACTION_SNOOZE
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            101,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val skipIntent = Intent(context, PrayerActionReceiver::class.java).apply {
            action = PrayerActionReceiver.ACTION_SKIP
        }
        val skipPendingIntent = PendingIntent.getBroadcast(
            context,
            102,
            skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, WakeDetectorService.ALARM_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Morning Guided Prayer")
            .setContentText("Tap to begin your prayer session")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(alarmSound, android.media.AudioManager.STREAM_ALARM)
            .setFullScreenIntent(contentPendingIntent, true)
            .setDeleteIntent(skipPendingIntent)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(0, "Snooze 15 min", snoozePendingIntent)
            .addAction(0, "Skip today", skipPendingIntent)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(WakeDetectorService.ALARM_NOTIFICATION_ID, notification)
    }
}

@Composable
private fun WakeOverlayRoot(context: Context) {
    val page by WakeDetectorService.overlayPage.collectAsState()
    val streamState by WakeDetectorService.engine.streamState.collectAsState()
    val isSpeaking by WakeDetectorService.engine.isGeminiSpeaking.collectAsState()
    val isAudioPlaying by WakeDetectorService.engine.isAudioPlaying.collectAsState()
    val isMicSending by WakeDetectorService.engine.isMicSending.collectAsState()
    val micRms by WakeDetectorService.engine.micRmsLevel.collectAsState()
    val transcriptLines by WakeDetectorService.engine.transcriptLines.collectAsState()
    val fullTranscriptLines by WakeDetectorService.engine.fullSessionTranscriptLines.collectAsState()
    val isTranscriptAvailable by WakeDetectorService.engine.isTranscriptAvailable.collectAsState()
    val lastSpokenVerse = WakeDetectorService.engine.lastSpokenVerseLine

    val view = LocalView.current
    val animScale = try {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
    } catch (_: Exception) { 1f }
    val isReducedMotion = animScale == 0f || view.importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO

    var isFavorite by remember { mutableStateOf(false) }
    var showTranscript by remember { mutableStateOf(false) } // Default OFF at the start of EVERY session
    var isDoubleTapEnd by remember { mutableStateOf(WakePrefsManager.isDoubleTapEndEnabled(context)) }
    var isSettingsOpen by remember { mutableStateOf(false) }

    val performSavePrayer = {
        val verseText = if (!lastSpokenVerse.isNullOrBlank()) {
            lastSpokenVerse
        } else {
            val prefVerse = com.example.customization.VersePrefsManager.getVerseText(context)
            if (!prefVerse.isNullOrBlank()) prefVerse else "May the peace of God rest upon you today."
        }
        val fullTranscript = if (fullTranscriptLines.isNotEmpty()) {
            fullTranscriptLines.joinToString("\n\n") { line ->
                (if (line.isUser) "You: " else "Gemini: ") + line.text
            }
        } else if (transcriptLines.isNotEmpty()) {
            transcriptLines.joinToString("\n\n") { line ->
                (if (line.isUser) "You: " else "Gemini: ") + line.text
            }
        } else {
            "Guided Morning Prayer Session"
        }

        val verseSnippet = if (verseText.length > 30) verseText.take(30) + "..." else verseText
        android.util.Log.i("WakeDetector", "[FAVORITES] Heart clicked. Verse: $verseSnippet, Transcript length: ${fullTranscript.length}")

        FavoritesManager.savePrayer(context, verseText, fullTranscript)
        isFavorite = true
        Toast.makeText(context, "Prayer saved to Favorites.", Toast.LENGTH_SHORT).show()
    }

    var textWidth by remember { mutableStateOf(500f) }

    val infiniteTransition = rememberInfiniteTransition(label = "letterGlow")
    val sweep by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 6000
                0f at 0
                (1f at 3500) using LinearEasing   // smooth sweep, no pauses
                1f at 6000                     // hold rest
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    val x = sweep * (textWidth + 1400f) - 700f
    val animatedGlowBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF3D2B1F), Color(0xFFB4574E), Color(0xFFE8B87A),
            Color(0xFFB4574E), Color(0xFF3D2B1F)
        ),
        start = Offset(x, 0f),
        end = Offset(x + 600f, 40f)
    )

    val glowBrush = if (isReducedMotion) {
        SolidColor(Color(0xFF3D2B1F))
    } else {
        animatedGlowBrush
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE61C1917)), // Dark soft backdrop
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFFF2EFE6), // Cream background #F2EFE6
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, Color(0xFFE7E5E4))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (page) {
                    OverlayPage.PRAYER_DOOR -> {
                        // Page A — Prayer Door
                        Text(
                            text = "Guided Prayer",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1C1917)
                            ),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "A quiet space to begin your day in His presence.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF78716C)
                            ),
                            textAlign = TextAlign.Center
                        )
                        var jesusLambBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
                        var isImageLoading by remember { mutableStateOf(true) }

                        LaunchedEffect(page) {
                            val decoded = GuideImageLoader.loadGuideBitmap(context, GuideImageType.JESUS_LAMB)
                            if (decoded != null) {
                                jesusLambBitmap = decoded.asImageBitmap()
                            } else {
                                jesusLambBitmap = null
                            }
                            isImageLoading = false
                        }

                        if (isImageLoading) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color(0xFFB4574E),
                                    strokeWidth = 2.dp
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        } else if (jesusLambBitmap != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Image(
                                bitmap = jesusLambBitmap!!,
                                contentDescription = "Jesus with Lamb",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                        } else {
                            // On ANY failure or absent cached image, hide image area completely
                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        // [Begin Session]
                        Button(
                            onClick = {
                                val hasMic = PermissionHelper.hasRecordAudioPermission(context)
                                val hasOverlay = PermissionHelper.hasOverlayPermission(context)
                                if (!hasMic) {
                                    val intent = Intent(context, MainActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                        putExtra("request_mic_permission", true)
                                    }
                                    context.startActivity(intent)
                                } else if (!hasOverlay) {
                                    PermissionHelper.openOverlaySettings(context)
                                } else {
                                    WakeDetectorService.startPrayerSession(context)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFB4574E), // Terracotta
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = "Begin Session",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp,
                                    brush = glowBrush
                                ),
                                modifier = Modifier.onGloballyPositioned { textWidth = it.size.width.toFloat() }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                onClick = {
                                    val snoozeTime = System.currentTimeMillis() + 15 * 60 * 1000L
                                    WakePrefsManager.setSnoozeUntil(context, snoozeTime)
                                    WakePrefsManager.setRitualPending(context, false, reason = "Snooze")
                                    val msg = "[WAKE] Snoozed 15 min"
                                    Log.i("WakeDetector", msg)
                                    WakePrefsManager.logWakeEvent(msg)
                                    FirstLightNotificationHelper.cancelNotification(context)
                                    WakeOverlayManager.removeOverlay(context)
                                }
                            ) {
                                Text("Snooze 15 min", color = Color(0xFF78716C))
                            }
                            TextButton(
                                onClick = {
                                    WakePrefsManager.setRitualPending(context, false, reason = "Skip today")
                                    val msg = "[WAKE] Skipped today"
                                    Log.i("WakeDetector", msg)
                                    WakePrefsManager.logWakeEvent(msg)
                                    FirstLightNotificationHelper.cancelNotification(context)
                                    PrayerAlarmScheduler.scheduleNextPrayer(context)
                                    WakeOverlayManager.removeOverlay(context)
                                }
                            ) {
                                Text("Skip today", color = Color(0xFF78716C))
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    OverlayPage.SESSION -> {
                        // Page B — Active Session Page (Interface B)
                        val (statusTitle, statusSubtitle) = when {
                            streamState == StreamState.CONNECTING || streamState == StreamState.RECONNECTING ->
                                "Connecting..." to "Please wait..."
                            isSpeaking || isAudioPlaying ->
                                "Gemini is speaking…" to "Guided morning prayer"
                            isMicSending || micRms > 800f ->
                                "Listening…" to "Speak your heart"
                            else ->
                                "In prayer…" to "Guided morning prayer"
                        }

                        val animatedTitleColor by animateColorAsState(
                            targetValue = if (isSpeaking || isAudioPlaying) Color(0xFF2C2420) else Color(0xFF1C1917),
                            animationSpec = tween(500),
                            label = "statusTitleColor"
                        )

                        // 1. HEADER
                        Text(
                            text = statusTitle,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                color = animatedTitleColor
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = statusSubtitle,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF8B7E72)
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // 2. ORB (170dp)
                        PrayerOrb(
                            isGeminiSpeaking = isSpeaking,
                            isAudioPlaying = isAudioPlaying,
                            isMicSending = isMicSending,
                            micRmsLevel = micRms,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        // 3. SPACER minHeight 40dp (two-finger gap)
                        Spacer(modifier = Modifier.height(40.dp))

                        // 4. TRANSCRIPT
                        TranscriptPanel(
                            transcriptLines = transcriptLines,
                            isVisible = showTranscript,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        )

                        // 5. ACTION BAR
                        SessionActions(
                            isFavorite = isFavorite,
                            onToggleFavorite = {
                                if (!isFavorite) {
                                    performSavePrayer()
                                } else {
                                    isFavorite = false
                                }
                            },
                            onEndSession = {
                                WakeDetectorService.endPrayerSession(context)
                            },
                            onOpenSettings = {
                                isSettingsOpen = true
                            },
                            isDoubleTapEnd = isDoubleTapEnd
                        )
                    }
                    OverlayPage.COMPLETION -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (!isFavorite) {
                                        performSavePrayer()
                                    } else {
                                        Toast.makeText(context, "Already saved to Favorites.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = if (isFavorite) "Saved to Favorites" else "Save to Favorites",
                                    tint = if (isFavorite) Color(0xFFB4574E) else Color(0xFF8B7E72)
                                )
                            }
                        }

                        Text(
                            text = "Amen. You may continue your day.",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1C1917)
                            ),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (!lastSpokenVerse.isNullOrBlank()) {
                            Text(
                                text = lastSpokenVerse,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = FontFamily.Serif,
                                    fontStyle = FontStyle.Italic,
                                    color = Color(0xFF78716C)
                                ),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = "May the peace of God rest upon you today.",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = FontFamily.Serif,
                                    color = Color(0xFF78716C)
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                WakeDetectorService.endPrayerSession(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB4574E))
                        ) {
                            Text("Return to app", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    OverlayPage.ERROR -> {
                        val connectionError by WakeDetectorService.engine.connectionErrorMessage.collectAsState()
                        val errorText = when {
                            connectionError.isNullOrBlank() -> "Connection interrupted. Let's try again."
                            connectionError!!.contains("Microphone") -> connectionError!!
                            connectionError!!.contains("timed out") -> "Response timed out. Let's try again."
                            connectionError!!.contains("401") || connectionError!!.contains("403") || connectionError!!.contains("API key") || connectionError!!.contains("Auth") -> "Authentication error. Please check your API key."
                            connectionError!!.contains("429") || connectionError!!.contains("quota") || connectionError!!.contains("busy") -> "Service is temporarily busy. Let's try again."
                            else -> "Connection interrupted. Let's try again."
                        }

                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Serif,
                                color = Color(0xFF2C2420)
                            ),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                WakeDetectorService.startPrayerSession(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB4574E))
                        ) {
                            Text("Try Again", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    else -> {}
                }
            }
        }

        // 6. SETTINGS SHEET OVERLAY
        SettingsSheet(
            isVisible = isSettingsOpen,
            showTranscript = showTranscript,
            onToggleShowTranscript = { enabled ->
                showTranscript = enabled
                val msg = "[SESSION] transcript toggled ${if (enabled) "on" else "off"} (session-scoped)"
                Log.i("WakeDetector", msg)
                WakePrefsManager.logWakeEvent(msg)
            },
            isDoubleTapEnd = isDoubleTapEnd,
            onToggleDoubleTapEnd = { enabled ->
                isDoubleTapEnd = enabled
                WakePrefsManager.setDoubleTapEndEnabled(context, enabled)
            },
            onDismiss = { isSettingsOpen = false }
        )
    }
}

@Composable
private fun WaveformVisualizer(isGeminiActive: Boolean, isMicSending: Boolean, micRms: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val waveAnim by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave"
    )

    val barColor = if (isGeminiActive) Color(0xFF3730A3) else Color(0xFFB4574E)

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(60.dp)
    ) {
        val baseEnergy = if (isGeminiActive) {
            0.85f
        } else if (isMicSending && micRms > 100f) {
            (micRms / 3000f).coerceIn(0.25f, 1.0f)
        } else {
            0.15f
        }

        val heights = listOf(
            20.dp * (baseEnergy * waveAnim).coerceAtLeast(0.2f),
            40.dp * (baseEnergy * (1.2f - waveAnim)).coerceAtLeast(0.3f),
            56.dp * (baseEnergy * waveAnim).coerceAtLeast(0.4f),
            36.dp * (baseEnergy * (1.1f - waveAnim)).coerceAtLeast(0.25f),
            24.dp * (baseEnergy * waveAnim).coerceAtLeast(0.2f)
        )

        heights.forEach { height ->
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(height)
                    .background(barColor, shape = RoundedCornerShape(4.dp))
            )
        }
    }
}
