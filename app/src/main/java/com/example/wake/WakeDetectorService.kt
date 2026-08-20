package com.example.wake

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.R
import com.example.diagnostic.GeminiLiveDiagnosticEngine
import com.example.diagnostic.StreamState
import com.example.diagnostic.TranscriptLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

enum class OverlayPage {
    CLOSED,
    PRAYER_DOOR,
    SESSION,
    COMPLETION,
    ERROR
}

class WakeDetectorService : Service() {

    companion object {
        const val FGS_NOTIFICATION_ID = 1001
        const val ALARM_NOTIFICATION_ID = 1002
        const val FGS_CHANNEL_ID = "wake_detector_fgs_channel"
        const val ALARM_CHANNEL_ID = "wake_prayer_alarm_channel"

        val engine = GeminiLiveDiagnosticEngine()

        private var instance: WakeDetectorService? = null

        private val _overlayPage = MutableStateFlow(OverlayPage.CLOSED)
        val overlayPage: StateFlow<OverlayPage> = _overlayPage.asStateFlow()

        fun setOverlayPage(page: OverlayPage) {
            _overlayPage.value = page
        }

        fun startService(context: Context) {
            val intent = Intent(context, WakeDetectorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun simulateWakeTrigger(context: Context) {
            val msg = "[WAKE-TEST] Simulated wake trigger executed"
            Log.i("WakeDetector", msg)
            WakePrefsManager.logWakeEvent(msg)
            WakePrefsManager.setRitualPending(context, true, reason = "simulated trigger")
            WakeOverlayManager.triggerPrayerDoor(context)
        }

        fun showPrayerAlert(context: Context) {
            WakeOverlayManager.triggerPrayerDoor(context)
        }

        fun rescheduleTrigger(context: Context) {
            instance?.let { service ->
                service.lastArmedTriggerTime = -1L
                service.serviceScope.launch(Dispatchers.Default) {
                    service.tickScheduler()
                }
            }
        }

        fun startPrayerSession(context: Context) {
            startService(context)
            instance?.startForegroundNotification()
            FirstLightNotificationHelper.cancelNotification(context)
            WakePrefsManager.setRitualPending(context, false, reason = "Begin Session")
            setOverlayPage(OverlayPage.SESSION)
            val apiKey = getApiKey(context)
            val modelName = "models/gemini-2.5-flash-native-audio-preview-12-2025"
            val msg = "[WAKE] Starting service-hosted prayer session..."
            Log.i("WakeDetector", "[ENGINE] Owner: service")
            engine.log(com.example.model.LogLevel.INFO, "[ENGINE] Owner: service")
            Log.i("WakeDetector", msg)
            WakePrefsManager.logWakeEvent(msg)
            engine.startDiagnostic(apiKey = apiKey, modelName = modelName, debugMode = true, context = context)
        }

        fun endPrayerSession(context: Context) {
            val msg = "[WAKE] User ended prayer session"
            Log.i("WakeDetector", msg)
            WakePrefsManager.logWakeEvent(msg)
            FirstLightNotificationHelper.cancelNotification(context)
            WakePrefsManager.setRitualPending(context, false, reason = "prayer completion")
            engine.disconnect()
            setOverlayPage(OverlayPage.CLOSED)
            WakeOverlayManager.removeOverlay(context)
        }

        private fun getApiKey(context: Context): String {
            val prefKey = WakePrefsManager.getCustomApiKey(context)
            if (!prefKey.isNullByBlank()) return prefKey
            val buildConfigKey = BuildConfig.GEMINI_API_KEY
            if (buildConfigKey.isNotBlank()) return buildConfigKey
            return ""
        }

        private fun String?.isNullByBlank(): Boolean = this.isNullOrBlank()
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var sessionTimeoutJob: Job? = null
    private var schedulerJob: Job? = null
    private var lastArmedTriggerTime: Long = -1L

    private var telephonyManager: TelephonyManager? = null
    private var telephonyCallback: Any? = null
    private var phoneStateListener: PhoneStateListener? = null
    private val callIdleHandler = Handler(Looper.getMainLooper())
    private var callIdleRunnable: Runnable? = null

    private fun startSchedulerTicker() {
        // Ensure hardware AlarmManager exact wakeup is primed
        PrayerAlarmScheduler.scheduleNextPrayer(this)
        schedulerJob?.cancel()
        schedulerJob = serviceScope.launch(Dispatchers.Default) {
            while (true) {
                try {
                    tickScheduler()
                } catch (e: Exception) {
                    Log.e("WakeDetector", "[SCHEDULER] Error in scheduler tick: ${e.message}", e)
                }
                delay(300_000L) // 5-minute ultra-low-power sanity check (exact trigger is handled by AlarmManager)
            }
        }
    }

    private fun checkMissedPastTrigger() {
        // Obsolete auto-trigger on startup replaced by real-time scheduler evaluation
    }

    private fun tickScheduler() {
        val now = System.currentTimeMillis()
        val snoozeUntil = WakePrefsManager.getSnoozeUntil(this)
        val (hour, minute) = WakePrefsManager.getPrayerHourMinute(this)
        val isRitualPending = WakePrefsManager.isRitualPending(this)

        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        var basePrayerTime = cal.timeInMillis

        // If today's configured time is already in the past by more than 1 minute (and not snoozed),
        // look ahead to tomorrow's prayer time unless it was just edited
        if (snoozeUntil <= 0L && now > basePrayerTime + 60_000L) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            basePrayerTime = cal.timeInMillis
        }

        val triggerTime = if (snoozeUntil > 0L) snoozeUntil else basePrayerTime
        val lastDoor = WakePrefsManager.getLastDoorTriggerTime(this)

        val nowFormatted = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(java.util.Date(now))
        val targetFormatted = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(java.util.Date(triggerTime))
        val snoozeFormatted = if (snoozeUntil > 0L) java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(java.util.Date(snoozeUntil)) else "none"
        val lastDoorFormatted = if (lastDoor > 0L) java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(java.util.Date(lastDoor)) else "none"

        OvernightJournal.log(
            this,
            "SCHEDULER",
            "Heartbeat tick - now=$nowFormatted, target=$targetFormatted, ritualPending=$isRitualPending, snoozeUntil=$snoozeFormatted, lastDoor=$lastDoorFormatted"
        )

        if (triggerTime != lastArmedTriggerTime) {
            lastArmedTriggerTime = triggerTime
            val timeFormatted = java.text.SimpleDateFormat("h:mm a", Locale.US).format(java.util.Date(triggerTime))
            val armMsg = "[SCHEDULER] trigger armed for $timeFormatted"
            Log.i("WakeDetector", armMsg)
            WakePrefsManager.logWakeEvent(armMsg)
        }

        val reminderWindowStart = triggerTime - 30_000L
        val lastReminder = WakePrefsManager.getLastReminderTriggerTime(this)
        val isReminderEnabled = WakePrefsManager.isReminderEnabled(this)

        // Show 30-second reminder when within 30-second window up to base prayer time (never during snooze)
        if (snoozeUntil <= 0L && isReminderEnabled && now in reminderWindowStart until triggerTime && lastReminder != triggerTime) {
            WakePrefsManager.setLastReminderTriggerTime(this, triggerTime)
            FirstLightNotificationHelper.postState1Reminder(this)
            val remMsg = "[SCHEDULER] 30s alarm reminder posted"
            Log.i("WakeDetector", remMsg)
            WakePrefsManager.logWakeEvent(remMsg)
            OvernightJournal.log(this, "SCHEDULER", remMsg)
        }

        // Fire prayer door when trigger time arrives
        if (now >= triggerTime && lastDoor != triggerTime) {
            WakePrefsManager.setLastDoorTriggerTime(this, triggerTime)
            FirstLightNotificationHelper.cancelNotification(this)
            val doorMsg = "[SCHEDULER] door fired at trigger"
            Log.i("WakeDetector", doorMsg)
            WakePrefsManager.logWakeEvent(doorMsg)
            OvernightJournal.log(this, "SCHEDULER", doorMsg)

            acquireWakeLock(this)

            if (snoozeUntil > 0L) {
                WakePrefsManager.setSnoozeUntil(this, 0L)
            }

            WakePrefsManager.setRitualPending(this, true, reason = "alarm door")
            serviceScope.launch(Dispatchers.Main) {
                WakeOverlayManager.triggerPrayerDoor(this@WakeDetectorService)
            }
        }
    }

    private fun acquireWakeLock(context: Context) {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            @Suppress("DEPRECATION")
            val wakeLock = powerManager?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "FirstLight:MorningWakeLock"
            )
            wakeLock?.acquire(3 * 60 * 1000L) // 3 minutes
            OvernightJournal.log(context, "WAKE", "Acquired SCREEN_BRIGHT_WAKE_LOCK with ACQUIRE_CAUSES_WAKEUP (3 min)")
        } catch (e: Exception) {
            OvernightJournal.log(context, "WAKE", "Failed acquiring wakeLock: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private val wallpaperReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_WALLPAPER_CHANGED) {
                if (com.example.customization.VersePrefsManager.isVerseEnabled(context)) {
                    if (!com.example.customization.WallpaperVerseRenderer.isSelfUpdatingWallpaper) {
                        Log.i("WallpaperVerseRenderer", "[WALLPAPER] Wallpaper changed - recomposited")
                        serviceScope.launch(Dispatchers.IO) {
                            com.example.customization.WallpaperVerseRenderer.applyWallpaper(context)
                        }
                    }
                }
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val ritualPending = WakePrefsManager.isRitualPending(context)
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    val now = System.currentTimeMillis()
                    WakePrefsManager.setLastScreenOff(context, now)
                    val msg = "[WAKE] Screen OFF recorded (ritualPending=$ritualPending)"
                    Log.i("WakeDetector", msg)
                    WakePrefsManager.logWakeEvent(msg)
                    OvernightJournal.log(context, "SCHEDULER", msg)
                }
                Intent.ACTION_SCREEN_ON -> {
                    val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                    val isLocked = keyguardManager?.isKeyguardLocked ?: false
                    val isTodayCompleted = WakePrefsManager.isPrayerCompletedToday(context)
                    val isSessionActive = engine.streamState.value != StreamState.DISCONNECTED && engine.streamState.value != StreamState.IDLE
                    OvernightJournal.log(context, "SCHEDULER", "[WAKE] Screen ON event (isLocked=$isLocked, ritualPending=$ritualPending, isTodayCompleted=$isTodayCompleted, isSessionActive=$isSessionActive)")
                    if (!isLocked && (isSessionActive || ritualPending) && !WakeOverlayManager.isOverlayShowing()) {
                        if (isSessionActive) {
                            setOverlayPage(OverlayPage.SESSION)
                            WakeOverlayManager.showOverlay(context)
                        } else {
                            setOverlayPage(OverlayPage.PRAYER_DOOR)
                            WakeOverlayManager.showOverlay(context)
                        }
                        val reshowMsg = "[DOOR] re-shown on screen-on"
                        Log.i("WakeDetector", reshowMsg)
                        WakePrefsManager.logWakeEvent(reshowMsg)
                        OvernightJournal.log(context, "SCHEDULER", reshowMsg)
                    }
                    WakePrefsManager.updateWakeState(context)
                }
                Intent.ACTION_USER_PRESENT -> {
                    val isSessionActive = engine.streamState.value != StreamState.DISCONNECTED && engine.streamState.value != StreamState.IDLE
                    val isTodayCompleted = WakePrefsManager.isPrayerCompletedToday(context)
                    OvernightJournal.log(context, "SCHEDULER", "[WAKE] USER_PRESENT unlock event (ritualPending=$ritualPending, isTodayCompleted=$isTodayCompleted, isSessionActive=$isSessionActive)")
                    if (!WakeOverlayManager.isOverlayShowing()) {
                        if (isSessionActive) {
                            setOverlayPage(OverlayPage.SESSION)
                            WakeOverlayManager.showOverlay(context)
                        } else if (ritualPending) {
                            setOverlayPage(OverlayPage.PRAYER_DOOR)
                            WakeOverlayManager.showOverlay(context)
                            val catchupMsg = "[DOOR] re-shown after unlock"
                            Log.i("WakeDetector", catchupMsg)
                            WakePrefsManager.logWakeEvent(catchupMsg)
                            OvernightJournal.log(context, "SCHEDULER", catchupMsg)
                        }
                    }
                    WakePrefsManager.updateWakeState(context)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i("WakeDetector", "[ENGINE] Owner: service")
        engine.log(com.example.model.LogLevel.INFO, "[ENGINE] Owner: service")
        createNotificationChannels()
        startForegroundNotification()

        OvernightJournal.logPermissionsAudit(this, "WakeDetectorService.onCreate")

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)

        try {
            @Suppress("DEPRECATION")
            registerReceiver(wallpaperReceiver, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED))
        } catch (e: Exception) {
            Log.w("WakeDetector", "Failed registering wallpaperReceiver: ${e.message}")
        }
        
        registerTelephonyListener()
        
        val msg = "[WAKE] WakeDetectorService created & BroadcastReceiver registered"
        Log.i("WakeDetector", msg)
        WakePrefsManager.logWakeEvent(msg)
        OvernightJournal.log(this, "SCHEDULER", msg)
        WakePrefsManager.updateWakeState(this)

        startSchedulerTicker()

        // Observe prayer completion event
        serviceScope.launch {
            engine.prayerCompletedEvent.collect {
                if (overlayPage.value == OverlayPage.SESSION) {
                    val completeMsg = "[LOCK] Released - prayer complete (immediate)"
                    Log.i("WakeDetector", completeMsg)
                    WakePrefsManager.logWakeEvent(completeMsg)
                    FirstLightNotificationHelper.cancelNotification(this@WakeDetectorService)
                    WakePrefsManager.setRitualPending(this@WakeDetectorService, false, reason = "prayer completion")
                    WakePrefsManager.setLastPrayerCompleted(this@WakeDetectorService, System.currentTimeMillis())
                    setOverlayPage(OverlayPage.COMPLETION)
                }
            }
        }

        // Observe errors
        serviceScope.launch {
            engine.lockErrorEvent.collect { err ->
                if (overlayPage.value == OverlayPage.SESSION) {
                    val errorMsg = "[WAKE] Session error: $err"
                    Log.e("WakeDetector", errorMsg)
                    WakePrefsManager.logWakeEvent(errorMsg)
                    WakePrefsManager.setRitualPending(this@WakeDetectorService, true, reason = "session error")
                    setOverlayPage(OverlayPage.ERROR)
                    delay(3000)
                    engine.disconnect()
                    setOverlayPage(OverlayPage.CLOSED)
                    WakeOverlayManager.removeOverlay(this@WakeDetectorService)
                }
            }
        }
    }

    private fun handleCallStateChange(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING, TelephonyManager.CALL_STATE_OFFHOOK -> {
                callIdleRunnable?.let { callIdleHandler.removeCallbacks(it) }
                val isSessionActive = engine.streamState.value != StreamState.DISCONNECTED && engine.streamState.value != StreamState.IDLE
                val stateName = if (state == TelephonyManager.CALL_STATE_RINGING) "RINGING" else "OFFHOOK"
                Log.i("WakeDetector", "[CALL] state=$stateName (isSessionActive=$isSessionActive)")
                
                WakeOverlayManager.hideForCall(this)
                
                if (isSessionActive) {
                    WakePrefsManager.setRitualPending(this, true, reason = "mid-prayer call interruption")
                    WakePrefsManager.setInterruptedByCall(this, true)
                    engine.disconnect()
                    Log.i("WakeDetector", "[CALL] active session terminated due to call interruption; ritual re-armed and marked for restart greeting")
                } else {
                    Log.i("WakeDetector", "[CALL] call state changed while not in active session (door hidden/maintained)")
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                Log.i("WakeDetector", "[CALL] state=IDLE")
                callIdleRunnable?.let { callIdleHandler.removeCallbacks(it) }
                callIdleRunnable = Runnable {
                    val isRitualPending = WakePrefsManager.isRitualPending(this)
                    val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                    val isLocked = keyguardManager?.isKeyguardLocked ?: false

                    if (isRitualPending) {
                        if (!isLocked) {
                            setOverlayPage(OverlayPage.PRAYER_DOOR)
                            WakeOverlayManager.showOverlay(this)
                            Log.i("WakeDetector", "[CALL] prayer door restored for fresh start (device unlocked)")
                        } else {
                            WakeOverlayManager.hideForCall(this)
                            Log.i("WakeDetector", "[CALL] device locked at call end; overlay cleared for unlock presentation")
                        }
                    }
                }
                callIdleHandler.postDelayed(callIdleRunnable!!, 200)
            }
        }
    }

    private fun registerTelephonyListener() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return
        }
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        telephonyManager?.let { tm ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleCallStateChange(state)
                    }
                }
                tm.registerTelephonyCallback(mainExecutor, telephonyCallback as TelephonyCallback)
            } else {
                @Suppress("DEPRECATION")
                phoneStateListener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleCallStateChange(state)
                    }
                }
                @Suppress("DEPRECATION")
                tm.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
            }
        }
    }

    private fun unregisterTelephonyListener() {
        telephonyManager?.let { tm ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let { tm.unregisterTelephonyCallback(it as TelephonyCallback) }
            } else {
                @Suppress("DEPRECATION")
                phoneStateListener?.let { tm.listen(it, PhoneStateListener.LISTEN_NONE) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        WakePrefsManager.updateWakeState(this)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
        schedulerJob?.cancel()
        engine.disconnect()
        WakeOverlayManager.removeOverlay(this)
        unregisterTelephonyListener()
        callIdleRunnable?.let { callIdleHandler.removeCallbacks(it) }
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            // Ignored if already unregistered
        }
        try {
            unregisterReceiver(wallpaperReceiver)
        } catch (e: Exception) {
            // Ignored if already unregistered
        }
        val msg = "[WAKE] WakeDetectorService destroyed"
        Log.i("WakeDetector", msg)
        WakePrefsManager.logWakeEvent(msg)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val fgsChannel = NotificationChannel(
                FGS_CHANNEL_ID,
                "Wake Detector Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors screen unlock events for morning prayer"
            }
            nm.createNotificationChannel(fgsChannel)

            val alarmChannel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "Morning Prayer Alarm",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarm notification for morning prayer prompt"
            }
            nm.createNotificationChannel(alarmChannel)
        }
    }

    fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, FGS_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Wake Detector Active")
            .setContentText("Morning prayer watch active")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            val hasMicPermission = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            val fgsType = if (hasMicPermission) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            try {
                startForeground(FGS_NOTIFICATION_ID, notification, fgsType)
            } catch (e: Exception) {
                Log.e("WakeDetector", "Failed startForeground with type $fgsType, falling back to specialUse: ${e.message}")
                try {
                    startForeground(FGS_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } catch (e2: Exception) {
                    Log.e("WakeDetector", "Fallback startForeground failed: ${e2.message}")
                }
            }
        } else {
            startForeground(FGS_NOTIFICATION_ID, notification)
        }
    }
}
