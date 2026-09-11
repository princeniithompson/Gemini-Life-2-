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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.R
import com.example.diagnostic.GeminiLiveDiagnosticEngine
import com.example.diagnostic.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

        fun stopService(context: Context) {
            try {
                instance?.stopSelf()
                context.stopService(Intent(context, WakeDetectorService::class.java))
            } catch (e: Exception) {
                Log.w("WakeDetector", "[WAKE] Error stopping service: ${e.message}")
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
            PrayerAlarmScheduler.scheduleNextPrayer(context)
        }

        fun startPrayerSession(context: Context) {
            startService(context)
            instance?.startForegroundNotification()
            FirstLightNotificationHelper.cancelNotification(context)
            WakePrefsManager.setRitualPending(context, false, reason = "Begin Session")
            setOverlayPage(OverlayPage.SESSION)
            engine.disconnect()
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
            WakeOverlayManager.resetSessionLock(context)
            WakeOverlayManager.removeOverlay(context)
            
            // Stop foreground service immediately to restore 0 background RAM & CPU
            instance?.stopForeground(true)
            instance?.stopSelf()
        }

        private fun getApiKey(context: Context): String {
            return com.example.api.ApiKeyProvider.getApiKey(context)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private var telephonyManager: TelephonyManager? = null
    private var telephonyCallback: Any? = null
    private var phoneStateListener: PhoneStateListener? = null
    private val callIdleHandler = Handler(Looper.getMainLooper())
    private var callIdleRunnable: Runnable? = null

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
                    val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
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

        OvernightJournal.logPermissionsAudit(this, "WakeDetectorService.onCreate (on-demand)")

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
        
        registerTelephonyListener()
        
        val msg = "[WAKE] WakeDetectorService started on-demand for active session"
        Log.i("WakeDetector", msg)
        WakePrefsManager.logWakeEvent(msg)
        OvernightJournal.log(this, "SCHEDULER", msg)
        WakePrefsManager.updateWakeState(this)

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
                    engine.disconnect()
                    setOverlayPage(OverlayPage.ERROR)
                }
            }
        }

        serviceScope.launch {
            engine.connectionErrorMessage.collect { err ->
                if (!err.isNullOrBlank() && overlayPage.value == OverlayPage.SESSION) {
                    val errorMsg = "[WAKE] Connection error: $err"
                    Log.e("WakeDetector", errorMsg)
                    WakePrefsManager.logWakeEvent(errorMsg)
                    WakePrefsManager.setRitualPending(this@WakeDetectorService, true, reason = "connection error")
                    engine.disconnect()
                    setOverlayPage(OverlayPage.ERROR)
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
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
        engine.disconnect()
        WakeOverlayManager.resetSessionLock(this)
        WakeOverlayManager.removeOverlay(this)
        unregisterTelephonyListener()
        callIdleRunnable?.let { callIdleHandler.removeCallbacks(it) }
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            // Ignored if already unregistered
        }
        val msg = "[WAKE] WakeDetectorService destroyed (idle state restored)"
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
                description = "Active during live morning prayer session"
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
            .setContentTitle("First Light Active")
            .setContentText("Morning prayer in progress")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            val hasMicPermission = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            var started = false
            if (hasMicPermission) {
                try {
                    startForeground(
                        FGS_NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                    started = true
                } catch (e: Exception) {
                    Log.w("WakeDetector", "startForeground with SPECIAL_USE|MICROPHONE failed: ${e.message}")
                }
            }

            if (!started) {
                try {
                    startForeground(
                        FGS_NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } catch (e: Exception) {
                    Log.e("WakeDetector", "startForeground with SPECIAL_USE failed: ${e.message}")
                    try {
                        startForeground(FGS_NOTIFICATION_ID, notification)
                    } catch (e2: Exception) {
                        Log.e("WakeDetector", "startForeground fallback failed: ${e2.message}")
                    }
                }
            }
        } else {
            try {
                startForeground(FGS_NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                Log.e("WakeDetector", "startForeground failed: ${e.message}")
            }
        }
    }
}
