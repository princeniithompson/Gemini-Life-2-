package com.example.diagnostic

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.example.model.LogLevel
import com.example.model.PipelineStatusState
import com.example.model.StatusState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class TranscriptLine(
    val isUser: Boolean,
    val text: String,
    val id: Long = System.currentTimeMillis()
)

/**
 * Stream States tracked for event correlation.
 */
enum class StreamState {
    IDLE,
    CONNECTING,
    SETUP_COMPLETE,
    USER_TURN_SENT,
    RECEIVING_STREAM,
    TURN_COMPLETE,
    RECONNECTING,
    DISCONNECTED,
    STALLED
}

/**
 * Production-grade Diagnostic Engine for testing, inspecting, and analyzing the Gemini Live API stream.
 *
 * Capabilities:
 * 1. Audio Stream Inspector: Frame counting, sequence numbers, duplicate frame detection, missing/out-of-order detection,
 *    running PCM byte count, audio duration, average frame size, frame interval, and stream bitrate.
 * 2. Audio Buffer Analyzer: Virtual buffer simulation (24kHz 16-bit mono), peak buffer tracking, underflow/overflow detection,
 *    and playback starvation risk analysis.
 * 3. PCM Validation: Validates sample rate (24000 Hz), bit depth (16-bit), channels (mono), sample alignment (% 2 == 0),
 *    truncated samples, and empty frames.
 * 4. Stream Summary: Automatically logs a detailed summary block on generationFinished / turnComplete.
 * 5. Event Correlation: Correlates every log event with timestamp, ms since session start, ms since last event, turn ID, and stream state.
 * 6. Thread Safety: Uses thread-safe atomic primitives and guards to eliminate race conditions and duplicate callbacks.
 */
class GeminiLiveDiagnosticEngine {

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _pipelineStatus = MutableStateFlow(PipelineStatusState())
    val pipelineStatus: StateFlow<PipelineStatusState> = _pipelineStatus.asStateFlow()

    private val _logFlow = MutableSharedFlow<Triple<LogLevel, String, String?>>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val logFlow: SharedFlow<Triple<LogLevel, String, String?>> = _logFlow.asSharedFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _streamState = MutableStateFlow(StreamState.IDLE)
    val streamState: StateFlow<StreamState> = _streamState.asStateFlow()

    private val _connectionErrorMessage = MutableStateFlow<String?>(null)
    val connectionErrorMessage: StateFlow<String?> = _connectionErrorMessage.asStateFlow()

    private val _isGeminiSpeaking = MutableStateFlow(false)
    val isGeminiSpeaking: StateFlow<Boolean> = _isGeminiSpeaking.asStateFlow()

    private val isGeminiTurnActive = AtomicBoolean(false)

    private val _isAudioPlaying = MutableStateFlow(false)
    val isAudioPlaying: StateFlow<Boolean> = _isAudioPlaying.asStateFlow()

    private val receivedAudioMsThisTurn = AtomicLong(0L)

    private val _isMicSending = MutableStateFlow(false)
    val isMicSending: StateFlow<Boolean> = _isMicSending.asStateFlow()

    private val _micRmsLevel = MutableStateFlow(0f)
    val micRmsLevel: StateFlow<Float> = _micRmsLevel.asStateFlow()

    private val _transcriptLines = MutableStateFlow<List<TranscriptLine>>(emptyList())
    val transcriptLines: StateFlow<List<TranscriptLine>> = _transcriptLines.asStateFlow()

    private val _fullSessionTranscriptLines = MutableStateFlow<List<TranscriptLine>>(emptyList())
    val fullSessionTranscriptLines: StateFlow<List<TranscriptLine>> = _fullSessionTranscriptLines.asStateFlow()

    private val _isTranscriptAvailable = MutableStateFlow(true)
    val isTranscriptAvailable: StateFlow<Boolean> = _isTranscriptAvailable.asStateFlow()

    @Volatile private var hasReceivedAnyTranscription = false
    @Volatile var lastSpokenVerseLine: String? = null
        private set

    fun appendTranscriptText(isUser: Boolean, text: String) {
        val trimmedChunk = text.trim()
        if (trimmedChunk.isEmpty()) return
        hasReceivedAnyTranscription = true

        val cleanup: (String) -> String = { input ->
            if (isUser) {
                input.replace(Regex("\\s+([.,!?;:'’])"), "$1")
                    .replace(Regex("([a-zA-Z]{2,})\\s+(ing|ling|ly|ness|tion|ment|ed|er)\\b", RegexOption.IGNORE_CASE), "$1$2")
            } else {
                input
            }
        }

        _fullSessionTranscriptLines.update { current ->
            val last = current.lastOrNull()
            if (last != null && last.isUser == isUser) {
                val combined = (last.text + " " + trimmedChunk).trim()
                current.dropLast(1) + last.copy(text = cleanup(combined))
            } else {
                current + TranscriptLine(isUser = isUser, text = cleanup(trimmedChunk))
            }
        }
        _transcriptLines.update { current ->
            val last = current.lastOrNull()
            val updated = if (last != null && last.isUser == isUser) {
                val combined = (last.text + " " + trimmedChunk).trim()
                current.dropLast(1) + last.copy(text = cleanup(combined))
            } else {
                current + TranscriptLine(isUser = isUser, text = cleanup(trimmedChunk))
            }
            if (updated.size > 3) updated.takeLast(3) else updated
        }
    }

    private val _prayerCompletedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val prayerCompletedEvent: SharedFlow<Unit> = _prayerCompletedEvent.asSharedFlow()

    private val _lockErrorEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val lockErrorEvent: SharedFlow<String> = _lockErrorEvent.asSharedFlow()

    private fun playTurnBeep() {
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    toneGenerator.release()
                } catch (e: Exception) {
                    // Ignore
                }
            }, 300)
        } catch (e: Exception) {
            log(LogLevel.WARN, "[TurnBeep] ToneGenerator exception: ${e.localizedMessage}")
        }
    }

    private val isUserDisconnecting = AtomicBoolean(false)
    @Volatile private var isInputAudioTranscriptionEnabled = true
    private val reconnectAttemptCount = AtomicInteger(0)
    private val maxReconnectAttempts = 6
    private val reconnectDelaysMs = listOf(500L, 1000L, 2000L, 4000L, 8000L, 8000L)
    private val reconnectStartTimeMs = AtomicLong(0L)
    private val isReconnectedSession = AtomicBoolean(false)
    private var reconnectJob: Job? = null

    private val _isWaitingForResponse = MutableStateFlow(false)
    val isWaitingForResponse: StateFlow<Boolean> = _isWaitingForResponse.asStateFlow()

    private var responseWatchdogJob: Job? = null
    @Volatile private var userSpeechSentTimeMs = 0L

    fun onUserSpeechSent() {
        if (_isGeminiSpeaking.value) return
        userSpeechSentTimeMs = System.currentTimeMillis()
        responseWatchdogJob?.cancel()
        responseWatchdogJob = scope.launch {
            delay(15_000)
            if (!_isGeminiSpeaking.value && currentStreamState.get() != StreamState.RECEIVING_STREAM) {
                _isWaitingForResponse.value = true
                setStreamState(StreamState.STALLED)
                val stallMsg = "Response timed out. Let's try again."
                log(LogLevel.WARN, "[TURN] No model response 15s after user speech - session stalled")
                _connectionErrorMessage.value = stallMsg
                _lockErrorEvent.tryEmit(stallMsg)
                disconnectInternal("15s response watchdog timeout")
            }
        }
    }

    private fun isPrayerCompletedTurn(): Boolean {
        val userSpoke = userSpeechSentTimeMs > 0L
        if (!userSpoke) {
            // The initial greeting turn ("Good morning and welcome...") MUST NOT finish the session before the user speaks!
            return false
        }
        val audioMs = receivedAudioMsThisTurn.get()
        val hasVerse = lastSpokenVerseLine != null
        val lastTranscript = _transcriptLines.value.lastOrNull { !it.isUser }?.text?.lowercase() ?: ""
        val containsAmen = lastTranscript.contains("amen") ||
                           lastTranscript.contains("god bless you") ||
                           lastTranscript.contains("carry this with you") ||
                           lastTranscript.contains("peace of god") ||
                           lastTranscript.contains("in jesus' name")
        return hasVerse || containsAmen || audioMs >= 10_000L
    }

    private var lastApiKey: String = ""
    private var lastModelName: String = ""
    private var lastDebugMode: Boolean = true
    private var lastContext: Context? = null

    private var activeWebSocket: WebSocket? = null
    private var okHttpClient: OkHttpClient? = null

    // Single-threaded dispatcher to ensure strict sequential processing of WebSocket packets in FIFO order
    private val wsDispatcher = Dispatchers.IO.limitedParallelism(1)

    // Audio Focus management
    private var audioFocusRequest: android.media.AudioFocusRequest? = null

    // Stubborn-Media Detection v4 (Single-Funnel + Concurrency Guard)
    private val stubbornMediaHandler = Handler(Looper.getMainLooper())
    private val promptedThisSession = AtomicBoolean(false)
    private var audioPlaybackCallback: AudioManager.AudioPlaybackCallback? = null

    private val pendingCheck = Runnable {
        val context = lastContext ?: return@Runnable
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return@Runnable
            val currentConfigs = try {
                audioManager.activePlaybackConfigurations
            } catch (e: Exception) {
                null
            }

            if (currentConfigs.isNullOrEmpty()) {
                val privacyMsg = "[AUDIO FOCUS] hidden by privacy"
                log(LogLevel.INFO, privacyMsg)
                android.util.Log.i("WakeDetector", privacyMsg)
                return@Runnable
            }

            val externalConfigs = filterExternalMedia(currentConfigs)
            if (externalConfigs.isNotEmpty()) {
                if (promptedThisSession.compareAndSet(false, true)) {
                    val promptMsg = "[AUDIO FOCUS] prompted"
                    log(LogLevel.INFO, promptMsg)
                    android.util.Log.i("WakeDetector", promptMsg)
                    com.example.wake.WakePrefsManager.logWakeEvent(promptMsg)
                    com.example.wake.FirstLightNotificationHelper.postStubbornMediaNotification(context)
                }
            }
        }
    }

    private fun evaluate(configs: List<android.media.AudioPlaybackConfiguration>?) {
        stubbornMediaHandler.removeCallbacks(pendingCheck)

        if (configs == null || configs.isEmpty()) {
            val evalMsg = "[AUDIO FOCUS] evaluate external=false"
            log(LogLevel.INFO, evalMsg)
            android.util.Log.i("WakeDetector", evalMsg)
            val privacyMsg = "[AUDIO FOCUS] hidden by privacy"
            log(LogLevel.INFO, privacyMsg)
            android.util.Log.i("WakeDetector", privacyMsg)
            val cancelMsg = "[AUDIO FOCUS] check cancelled"
            log(LogLevel.INFO, cancelMsg)
            android.util.Log.i("WakeDetector", cancelMsg)
            return
        }

        val externalConfigs = filterExternalMedia(configs)
        val hasExternal = externalConfigs.isNotEmpty()
        val evalMsg = "[AUDIO FOCUS] evaluate external=$hasExternal"
        log(LogLevel.INFO, evalMsg)
        android.util.Log.i("WakeDetector", evalMsg)

        if (hasExternal) {
            stubbornMediaHandler.postDelayed(pendingCheck, 1000L)
            val postedMsg = "[AUDIO FOCUS] check posted"
            log(LogLevel.INFO, postedMsg)
            android.util.Log.i("WakeDetector", postedMsg)
        } else {
            val cancelMsg = "[AUDIO FOCUS] check cancelled"
            log(LogLevel.INFO, cancelMsg)
            android.util.Log.i("WakeDetector", cancelMsg)
        }
    }

    private fun filterExternalMedia(configs: List<android.media.AudioPlaybackConfiguration>): List<android.media.AudioPlaybackConfiguration> {
        val ourUid = android.os.Process.myUid()
        return configs.filter { config ->
            val clientUid = getClientUidSafe(config)
            val isNotUs = clientUid != -1 && clientUid != ourUid
            val isMediaOnly = config.audioAttributes?.usage == android.media.AudioAttributes.USAGE_MEDIA
            val notSuspended = !isSuspended(config)
            isNotUs && isMediaOnly && notSuspended
        }
    }

    private fun getClientUidSafe(config: android.media.AudioPlaybackConfiguration): Int {
        return try {
            val method = config.javaClass.getMethod("getClientUid")
            method.invoke(config) as? Int ?: -1
        } catch (_: Throwable) {
            try {
                val field = config.javaClass.getDeclaredField("mClientUid")
                field.isAccessible = true
                field.getInt(config)
            } catch (_: Throwable) {
                -1
            }
        }
    }

    private fun isSuspended(config: android.media.AudioPlaybackConfiguration): Boolean {
        return try {
            val method = config.javaClass.getMethod("isSuspended")
            (method.invoke(config) as? Boolean) == true
        } catch (_: Throwable) {
            try {
                val isActiveMethod = config.javaClass.getMethod("isActive")
                val isActive = isActiveMethod.invoke(config) as? Boolean ?: true
                !isActive
            } catch (_: Throwable) {
                false
            }
        }
    }

    private fun startStubbornMediaDetection(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

            stopStubbornMediaDetection(context)
            promptedThisSession.set(false)

            val callback = object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<android.media.AudioPlaybackConfiguration>?) {
                    evaluate(configs)
                }
            }
            audioPlaybackCallback = callback

            // 1. Snapshot first (posted to the same handler)
            val snapshot = try {
                audioManager.activePlaybackConfigurations
            } catch (e: Exception) {
                null
            }
            stubbornMediaHandler.post {
                evaluate(snapshot)
            }

            // 2. Register callback with the same handler
            try {
                audioManager.registerAudioPlaybackCallback(callback, stubbornMediaHandler)
            } catch (e: Exception) {
                log(LogLevel.WARN, "[AUDIO FOCUS] Error registering AudioPlaybackCallback: ${e.localizedMessage}")
            }
        }
    }

    private fun stopStubbornMediaDetection(context: Context?) {
        val ctx = context ?: lastContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val callback = audioPlaybackCallback
            if (callback != null && ctx != null) {
                val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                try {
                    audioManager?.unregisterAudioPlaybackCallback(callback)
                } catch (e: Exception) {
                    log(LogLevel.WARN, "[AUDIO FOCUS] Error unregistering AudioPlaybackCallback: ${e.localizedMessage}")
                }
                audioPlaybackCallback = null
            }
        }
        stubbornMediaHandler.removeCallbacksAndMessages(null)
        promptedThisSession.set(false)
        ctx?.let { com.example.wake.FirstLightNotificationHelper.cancelStubbornMediaNotification(it) }
    }

    private val audioFocusChangeListener = android.media.AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            android.media.AudioManager.AUDIOFOCUS_LOSS -> {
                val lossMsg = "[AUDIO FOCUS] Lost (permanent) - ending session gracefully"
                log(LogLevel.WARN, lossMsg)
                android.util.Log.w("WakeDetector", lossMsg)
                com.example.wake.WakePrefsManager.logWakeEvent(lossMsg)
                try {
                    audioPlaybackEngine.pauseForAudioFocus()
                    disconnectInternal("Permanent audio focus loss")
                } catch (e: Exception) {
                    log(LogLevel.WARN, "[AUDIO FOCUS] Exception handling permanent loss: ${e.localizedMessage}")
                }
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                val transMsg = "[AUDIO FOCUS] Lost (transient) - pausing playback"
                log(LogLevel.WARN, transMsg)
                android.util.Log.w("WakeDetector", transMsg)
                com.example.wake.WakePrefsManager.logWakeEvent(transMsg)
                try {
                    audioPlaybackEngine.pauseForAudioFocus()
                } catch (e: Exception) {
                    log(LogLevel.WARN, "[AUDIO FOCUS] Exception handling transient loss: ${e.localizedMessage}")
                }
            }
            android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                val gainMsg = "[AUDIO FOCUS] Gained/Regained - resuming playback"
                log(LogLevel.SUCCESS, gainMsg)
                android.util.Log.i("WakeDetector", gainMsg)
                com.example.wake.WakePrefsManager.logWakeEvent(gainMsg)
                try {
                    audioPlaybackEngine.setVolume(1.0f)
                    audioPlaybackEngine.resumeFromAudioFocus()
                } catch (e: Exception) {
                    log(LogLevel.WARN, "[AUDIO FOCUS] Exception resuming playback on focus gain: ${e.localizedMessage}")
                }
            }
        }
    }

    fun requestAudioFocus(context: android.content.Context): Boolean {
        startStubbornMediaDetection(context)
        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return false
        val reqMsg = "[AUDIO FOCUS] requested"
        log(LogLevel.INFO, reqMsg)
        android.util.Log.i("WakeDetector", reqMsg)
        com.example.wake.WakePrefsManager.logWakeEvent(reqMsg)
        com.example.wake.OvernightJournal.log(context, "AUDIO_FOCUS", "requested")

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val playbackAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                // Primary attempt: AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                val exclusiveRequest = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()

                var res = audioManager.requestAudioFocus(exclusiveRequest)
                if (res == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    audioFocusRequest = exclusiveRequest
                    val grantMsg = "[AUDIO FOCUS] granted"
                    log(LogLevel.SUCCESS, grantMsg)
                    android.util.Log.i("WakeDetector", grantMsg)
                    com.example.wake.WakePrefsManager.logWakeEvent(grantMsg)
                    com.example.wake.OvernightJournal.log(context, "AUDIO_FOCUS", "granted")
                    return true
                }

                // Fallback attempt: AUDIOFOCUS_GAIN_TRANSIENT if exclusive is denied
                val transientRequest = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()

                res = audioManager.requestAudioFocus(transientRequest)
                if (res == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    audioFocusRequest = transientRequest
                    val grantMsg = "[AUDIO FOCUS] granted"
                    log(LogLevel.SUCCESS, grantMsg)
                    android.util.Log.i("WakeDetector", grantMsg)
                    com.example.wake.WakePrefsManager.logWakeEvent(grantMsg)
                    com.example.wake.OvernightJournal.log(context, "AUDIO_FOCUS", "granted")
                    return true
                } else {
                    val denyMsg = "[AUDIO FOCUS] denied"
                    log(LogLevel.WARN, "$denyMsg - proceeding anyway (code $res)")
                    android.util.Log.w("WakeDetector", denyMsg)
                    com.example.wake.WakePrefsManager.logWakeEvent(denyMsg)
                    com.example.wake.OvernightJournal.log(context, "AUDIO_FOCUS", "denied")
                    return false
                }
            } else {
                @Suppress("DEPRECATION")
                var res = audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    android.media.AudioManager.STREAM_MUSIC,
                    android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
                if (res != android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    @Suppress("DEPRECATION")
                    res = audioManager.requestAudioFocus(
                        audioFocusChangeListener,
                        android.media.AudioManager.STREAM_MUSIC,
                        android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                    )
                }

                if (res == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    val grantMsg = "[AUDIO FOCUS] granted"
                    log(LogLevel.SUCCESS, grantMsg)
                    android.util.Log.i("WakeDetector", grantMsg)
                    com.example.wake.WakePrefsManager.logWakeEvent(grantMsg)
                    com.example.wake.OvernightJournal.log(context, "AUDIO_FOCUS", "granted")
                    return true
                } else {
                    val denyMsg = "[AUDIO FOCUS] denied"
                    log(LogLevel.WARN, "$denyMsg - proceeding anyway (<O code $res)")
                    android.util.Log.w("WakeDetector", denyMsg)
                    com.example.wake.WakePrefsManager.logWakeEvent(denyMsg)
                    com.example.wake.OvernightJournal.log(context, "AUDIO_FOCUS", "denied")
                    return false
                }
            }
        } catch (e: Exception) {
            val denyMsg = "[AUDIO FOCUS] denied"
            log(LogLevel.WARN, "$denyMsg - exception requesting focus: ${e.localizedMessage}")
            android.util.Log.w("WakeDetector", "$denyMsg: ${e.localizedMessage}")
            com.example.wake.WakePrefsManager.logWakeEvent(denyMsg)
            return false
        }
    }

    fun abandonAudioFocus(context: android.content.Context?) {
        val ctx = context ?: lastContext ?: return
        stopStubbornMediaDetection(ctx)
        val audioManager = ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val req = audioFocusRequest
                if (req != null) {
                    audioManager.abandonAudioFocusRequest(req)
                    audioFocusRequest = null
                    val logMsg = "[AUDIO FOCUS] abandoned"
                    log(LogLevel.INFO, logMsg)
                    android.util.Log.i("WakeDetector", logMsg)
                    com.example.wake.WakePrefsManager.logWakeEvent(logMsg)
                    com.example.wake.OvernightJournal.log(ctx, "AUDIO_FOCUS", "abandoned")
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(audioFocusChangeListener)
                val logMsg = "[AUDIO FOCUS] abandoned"
                log(LogLevel.INFO, logMsg)
                android.util.Log.i("WakeDetector", logMsg)
                com.example.wake.WakePrefsManager.logWakeEvent(logMsg)
                com.example.wake.OvernightJournal.log(ctx, "AUDIO_FOCUS", "abandoned")
            }
        } catch (e: Exception) {
            log(LogLevel.WARN, "[AUDIO FOCUS] Exception abandoning audio focus: ${e.localizedMessage}")
        }
    }
    // Event Correlation & Session Timestamps
    private val sessionStartTimeMs = AtomicLong(0L)
    private val lastEventTimeMs = AtomicLong(0L)
    private val currentTurnId = AtomicInteger(1)
    private val currentStreamState = AtomicReference(StreamState.IDLE)

    // Thread-safe state flags
    private val diagnosticPromptSent = AtomicBoolean(false)
    private val generationStartedLogged = AtomicBoolean(false)
    private val summaryLogged = AtomicBoolean(false)
    private val wasInterruptedSession = AtomicBoolean(false)

    private val validator = PcmValidator(targetSampleRate = 24000, targetBitDepth = 16, targetChannels = 1)
    private val inspector = AudioStreamInspector(validator)
    private val bufferAnalyzer = AudioBufferAnalyzer()
    private val audioPlaybackEngine = AudioPlaybackEngine { level, msg, details ->
        log(level, msg, details)
    }
    private var audioRecordingEngine: AudioRecordingEngine? = null

    private var activityTimeoutJob: Job? = null
    private var watchdogJob: Job? = null
    private var statusMonitorJob: Job? = null
    @Volatile private var lastAudioChunkReceivedTimeMs = 0L
    @Volatile private var lastServerAudioMs = 0L
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private fun setStreamState(newState: StreamState) {
        currentStreamState?.set(newState)
        _streamState.value = newState
    }

    private fun startStatusMonitor() {
        statusMonitorJob?.cancel()
        statusMonitorJob = scope.launch {
            while (isActive) {
                val state = _streamState.value
                if (state == StreamState.DISCONNECTED || state == StreamState.IDLE) {
                    break
                }
                val audioPlaying = audioPlaybackEngine.isPlaying()
                val geminiSpeaking = _isGeminiSpeaking.value
                _isAudioPlaying.value = audioPlaying
                _isMicSending.value = !geminiSpeaking && !audioPlaying
                delay(250)
            }
        }
    }

    /**
     * Turn Complete Watchdog to prevent permanent freezes if audio stream or turnComplete JSON stalls.
     * Fires only if isGeminiSpeaking == true AND (now - lastServerAudioMs) > 10000.
     */
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                val state = _streamState.value
                if (state == StreamState.DISCONNECTED || state == StreamState.IDLE) {
                    break
                }
                delay(1000)
                if (_isGeminiSpeaking.value) {
                    val now = System.currentTimeMillis()
                    val lastTime = lastServerAudioMs
                    if (lastTime > 0 && (now - lastTime) > 10000L) {
                        log(LogLevel.WARN, "Watchdog: unmuted mic after 10s audio silence")
                        setStreamState(StreamState.TURN_COMPLETE)
                        generationStartedLogged.set(false)
                        audioPlaybackEngine.notifyTurnCompleted()
                        if (!audioPlaybackEngine.isPlaying()) {
                            _isGeminiSpeaking.value = false
                            playTurnBeep()
                        }
                    }
                }
            }
        }
    }

    /**
     * Executes the full Gemini Live API diagnostic, audio analysis, and Full Duplex microphone capture flow.
     */
    fun startDiagnostic(
        apiKey: String,
        modelName: String,
        debugMode: Boolean = true,
        context: Context? = null
    ) {
        if (_isConnecting.value && _streamState.value != StreamState.RECONNECTING && _streamState.value != StreamState.STALLED && _streamState.value != StreamState.DISCONNECTED && _streamState.value != StreamState.IDLE) {
            log(LogLevel.WARN, "Diagnostic is already running. Please disconnect first.")
            return
        }

        reconnectJob?.cancel()
        reconnectJob = null
        responseWatchdogJob?.cancel()
        responseWatchdogJob = null

        isUserDisconnecting.set(false)
        reconnectAttemptCount.set(0)
        _connectionErrorMessage.value = null

        lastApiKey = apiKey
        lastModelName = modelName
        lastDebugMode = debugMode
        lastContext = context

        // Request Audio Focus BEFORE AudioTrack playback and AudioRecord start
        if (context != null) {
            requestAudioFocus(context)
        }

        _isConnecting.value = true
        _isGeminiSpeaking.value = false
        isGeminiTurnActive.set(false)
        _pipelineStatus.value = PipelineStatusState()

        // Reset analysis & engine state
        inspector.reset()
        bufferAnalyzer.reset()
        audioPlaybackEngine.reset()
        audioPlaybackEngine.prepare()
        audioPlaybackEngine.onPlaybackDrained = {
            _isGeminiSpeaking.value = false
            isGeminiTurnActive.set(false)
            playTurnBeep()
            if (isPrayerCompletedTurn()) {
                lastContext?.let { ctx ->
                    val now = System.currentTimeMillis()
                    com.example.wake.WakePrefsManager.setLastPrayerCompleted(ctx, now)
                    val wakeMsg = "[LOCK] Released - prayer complete (immediate)"
                    log(LogLevel.SUCCESS, wakeMsg)
                    com.example.wake.WakePrefsManager.logWakeEvent(wakeMsg)

                    val aiOutputText = _transcriptLines.value.filter { !it.isUser }.joinToString(" ") { it.text }
                    scope.launch {
                        com.example.customization.VerseExtractor.onPrayerCompleted(ctx, aiOutputText)
                    }
                }
                abandonAudioFocus(lastContext)
                _prayerCompletedEvent.tryEmit(Unit)
            } else {
                log(LogLevel.INFO, "[TURN] Greeting turn complete. Unmuting mic for user response.")
                audioRecordingEngine?.setMuted(false)
            }
        }

        if (context != null) {
            audioRecordingEngine?.reset()
            audioRecordingEngine = AudioRecordingEngine(
                context = context,
                logger = { level, msg, details -> log(level, msg, details) },
                onBargeInDetected = {
                    generationStartedLogged.set(false)
                    setStreamState(StreamState.USER_TURN_SENT)
                    audioPlaybackEngine.stopPlaybackImmediate("User barge-in speech detected")
                    audioPlaybackEngine.notifyInterrupted()
                    onUserSpeechSent()
                },
                onUserSpeechDetected = {
                    onUserSpeechSent()
                },
                onRecordingError = { errorMsg ->
                    log(LogLevel.ERROR, "[MIC RECORD] $errorMsg")
                    _connectionErrorMessage.value = errorMsg
                    _lockErrorEvent.tryEmit(errorMsg)
                }
            ).apply {
                onRmsUpdated = { rms -> _micRmsLevel.value = rms }
            }
        }

        _transcriptLines.value = emptyList()
        _fullSessionTranscriptLines.value = emptyList()
        _isTranscriptAvailable.value = true
        hasReceivedAnyTranscription = false
        lastSpokenVerseLine = null
        userSpeechSentTimeMs = 0L

        sessionStartTimeMs.set(System.currentTimeMillis())
        lastEventTimeMs.set(System.currentTimeMillis())
        currentTurnId.set(1)
        setStreamState(StreamState.CONNECTING)

        diagnosticPromptSent.set(false)
        generationStartedLogged.set(false)
        summaryLogged.set(false)

        startWatchdog()
        startStatusMonitor()

        scope.launch {
            try {
                log(LogLevel.INFO, "==========================================")
                log(LogLevel.INFO, "Application started: Gemini Live Audio Stream Inspector & Analyzer")
                log(LogLevel.INFO, "Debug Mode: $debugMode")
                log(LogLevel.INFO, "Target Model: $modelName")

                // STEP 1: SDK Initialization & Environment Check
                _pipelineStatus.update { it.copy(sdkStatus = StatusState.IN_PROGRESS) }
                log(LogLevel.INFO, "SDK initialized")
                log(LogLevel.DEBUG, "Android OS Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                log(LogLevel.DEBUG, "Device Model: ${Build.MANUFACTURER} ${Build.MODEL}")
                log(LogLevel.DEBUG, "Thread / Coroutine Dispatcher: ${Thread.currentThread().name}")

                _pipelineStatus.update { it.copy(sdkStatus = StatusState.SUCCESS) }
                log(LogLevel.SUCCESS, "SDK check passed")

                // STEP 2: Loading & Validating API Key
                _pipelineStatus.update { it.copy(apiKeyStatus = StatusState.IN_PROGRESS) }
                log(LogLevel.INFO, "Loading API key")

                val cleanKey = apiKey.trim()
                if (cleanKey.isEmpty()) {
                    val errMsg = "API key is missing or empty"
                    log(LogLevel.ERROR, errMsg, "Provide a valid Gemini API key via Secrets or UI text field.")
                    _pipelineStatus.update { it.copy(apiKeyStatus = StatusState.FAILED) }
                    _isConnecting.value = false
                    setStreamState(StreamState.DISCONNECTED)
                    return@launch
                }

                if (cleanKey == "MY_GEMINI_API_KEY" || cleanKey.contains("YOUR_API_KEY")) {
                    val errMsg = "API key is set to a placeholder value: '$cleanKey'"
                    log(LogLevel.ERROR, errMsg, "Update your Gemini API key in Secrets or the input field.")
                    _pipelineStatus.update { it.copy(apiKeyStatus = StatusState.FAILED) }
                    _isConnecting.value = false
                    setStreamState(StreamState.DISCONNECTED)
                    return@launch
                }

                val redactedKey = if (cleanKey.length > 8) {
                    "${cleanKey.take(4)}...${cleanKey.takeLast(4)}"
                } else {
                    "***"
                }

                log(LogLevel.INFO, "API key loaded ($redactedKey, length: ${cleanKey.length})")
                _pipelineStatus.update { it.copy(apiKeyStatus = StatusState.SUCCESS) }
                log(LogLevel.SUCCESS, "API key validated")

                // STEP 3: Creating Live Client
                _pipelineStatus.update { it.copy(liveClientStatus = StatusState.IN_PROGRESS) }
                log(LogLevel.INFO, "Creating Live client")

                val clientBuilder = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(90, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .pingInterval(30, TimeUnit.SECONDS)

                okHttpClient = clientBuilder.build()
                log(LogLevel.DEBUG, "OkHttpClient instantiated with 60s connect/write, 90s read timeout & 30s ping interval")

                _pipelineStatus.update { it.copy(liveClientStatus = StatusState.SUCCESS) }
                log(LogLevel.SUCCESS, "Live client created successfully")

                // STEP 4 & 5: Opening WebSocket & Establishing Session
                _pipelineStatus.update { it.copy(webSocketStatus = StatusState.IN_PROGRESS) }

                connectWebSocket(isReconnectingAttempt = false)

            } catch (e: Exception) {
                logException("Exception during pipeline initialization", e)
                _pipelineStatus.update { current ->
                    current.copy(
                        sdkStatus = if (current.sdkStatus == StatusState.IN_PROGRESS) StatusState.FAILED else current.sdkStatus,
                        apiKeyStatus = if (current.apiKeyStatus == StatusState.IN_PROGRESS) StatusState.FAILED else current.apiKeyStatus,
                        liveClientStatus = if (current.liveClientStatus == StatusState.IN_PROGRESS) StatusState.FAILED else current.liveClientStatus
                    )
                }
                setStreamState(StreamState.DISCONNECTED)
                _isConnecting.value = false
            }
        }
    }

    private fun connectWebSocket(isReconnectingAttempt: Boolean) {
        if (isUserDisconnecting.get()) return

        val cleanKey = lastApiKey.trim()
        val redactedKey = if (cleanKey.length > 8) "${cleanKey.take(4)}...${cleanKey.takeLast(4)}" else "***"
        val wsUrl = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$cleanKey"
        val displayUrl = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$redactedKey"

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("User-Agent", "GeminiLiveDiagnostic/1.0 (Android)")
            .build()

        if (!isReconnectingAttempt) {
            isInputAudioTranscriptionEnabled = true
            log(LogLevel.INFO, "Opening WebSocket")
            log(LogLevel.DEBUG, "Connecting to: $displayUrl")
        } else {
            log(LogLevel.INFO, "Reconnecting WebSocket...")
            log(LogLevel.DEBUG, "Reconnecting to: $displayUrl")
        }

        val client = okHttpClient ?: return

        activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                scope.launch {
                    if (isUserDisconnecting.get()) return@launch

                    log(LogLevel.SUCCESS, if (isReconnectingAttempt) "Reconnected WebSocket established" else "Connected")
                    log(LogLevel.INFO, "WebSocket connection opened")
                    log(LogLevel.DEBUG, "Handshake Response Code: ${response.code} ${response.message}")

                    _pipelineStatus.update {
                        it.copy(
                            webSocketStatus = StatusState.SUCCESS,
                            sessionStatus = StatusState.IN_PROGRESS
                        )
                    }
                    log(LogLevel.INFO, "Waiting for connection / Session setup...")

                    resetActivityTimeout(webSocket)

                    sendSetupFrame(webSocket, lastModelName)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch(wsDispatcher) {
                    resetActivityTimeout(webSocket)
                    processJsonPayload(webSocket, text)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                scope.launch(wsDispatcher) {
                    resetActivityTimeout(webSocket)
                    val receptionTimeMs = System.currentTimeMillis()

                    val utf8Text = try { bytes.utf8() } catch (e: Exception) { null }
                    var parsedAsJson = false
                    if (!utf8Text.isNullOrBlank() && (utf8Text.trim().startsWith("{") || utf8Text.trim().startsWith("["))) {
                        try {
                            processJsonPayload(webSocket, utf8Text)
                            parsedAsJson = true
                        } catch (e: Exception) {
                            parsedAsJson = false
                        }
                    }

                    if (!parsedAsJson) {
                        setStreamState(StreamState.RECEIVING_STREAM)
                        val rawBytes = bytes.toByteArray()
                        processAndLogAudioChunk(rawBytes, receptionTimeMs, "audio/pcm;rate=24000")
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                scope.launch {
                    log(LogLevel.WARN, "Connection closing (Close Code: $code, Reason: '$reason')")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                scope.launch {
                    log(LogLevel.INFO, "[WS] closed: code=$code reason=$reason")
                    log(LogLevel.INFO, "Connection closed (Close Code: $code, Reason: '$reason')")
                    cancelActivityTimeout()

                    if (isUserDisconnecting.get()) {
                        setStreamState(StreamState.DISCONNECTED)
                        _isConnecting.value = false
                        log(LogLevel.INFO, "Disconnected")
                        emitStreamSummaryIfNeeded("WebSocket Closed")
                    } else if (code == 1007 && isInputAudioTranscriptionEnabled) {
                        log(LogLevel.WARN, "[TRANSCRIPT] 1007 with input transcription - reconnecting without it")
                        android.util.Log.w("WakeDetector", "[TRANSCRIPT] 1007 with input transcription - reconnecting without it")
                        isInputAudioTranscriptionEnabled = false
                        handleConnectionFailure("1007 inputAudioTranscription fallback")
                    } else if (code != 1000) {
                        val trimmedReason = reason.ifBlank { "Protocol rejection" }
                        val reasonFirst80 = trimmedReason.take(80)
                        val rejectionText = "Session rejected (code $code): $reasonFirst80"

                        log(LogLevel.ERROR, "[WS] Error card shown with reason: $code $trimmedReason")
                        android.util.Log.e("WakeDetector", "[WS] Error card shown with reason: $code $trimmedReason")

                        _connectionErrorMessage.value = rejectionText
                        _lockErrorEvent.tryEmit(rejectionText)

                        setStreamState(StreamState.DISCONNECTED)
                        _isConnecting.value = false
                        _pipelineStatus.update { current ->
                            current.copy(
                                webSocketStatus = StatusState.FAILED,
                                sessionStatus = StatusState.FAILED,
                                readyStatus = StatusState.FAILED
                            )
                        }
                        emitStreamSummaryIfNeeded("WebSocket Closed $code")
                    } else {
                        handleConnectionFailure("WebSocket Closed ($code: $reason)")
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scope.launch {
                    cancelActivityTimeout()
                    val code = response?.code ?: -1
                    val reason = response?.message?.ifEmpty { null } ?: t.localizedMessage ?: t.javaClass.simpleName
                    val is1007 = code == 1007 || reason.contains("1007") || (t.localizedMessage ?: "").contains("1007")
                    log(LogLevel.ERROR, "[WS] closed: code=$code reason=$reason")
                    log(LogLevel.ERROR, "WebSocket Failure / Error encountered: ${t.localizedMessage ?: t.javaClass.simpleName}")

                    if (is1007 && isInputAudioTranscriptionEnabled && !isUserDisconnecting.get()) {
                        log(LogLevel.WARN, "[TRANSCRIPT] 1007 with input transcription - reconnecting without it")
                        android.util.Log.w("WakeDetector", "[TRANSCRIPT] 1007 with input transcription - reconnecting without it")
                        isInputAudioTranscriptionEnabled = false
                        handleConnectionFailure("1007 inputAudioTranscription fallback")
                    } else if (response != null) {
                        if (code == 400 || code == 401 || code == 403 || reason.contains("401") || reason.contains("403") || reason.contains("API_KEY") || reason.contains("UNAUTHENTICATED") || reason.contains("PERMISSION_DENIED")) {
                            com.example.api.ApiKeyProvider.notifyAuthError()
                        }
                        log(LogLevel.ERROR, "Network Response Code: ${response.code} ${response.message}")
                        val rawReason = response.message.ifBlank { t.localizedMessage ?: "HTTP $code" }
                        val reasonFirst80 = rawReason.take(80)
                        val rejectionText = "Session rejected (code $code): $reasonFirst80"

                        log(LogLevel.ERROR, "[WS] Error card shown with reason: $code $rawReason")
                        android.util.Log.e("WakeDetector", "[WS] Error card shown with reason: $code $rawReason")

                        _connectionErrorMessage.value = rejectionText
                        _lockErrorEvent.tryEmit(rejectionText)

                        setStreamState(StreamState.DISCONNECTED)
                        _isConnecting.value = false
                        _pipelineStatus.update { current ->
                            current.copy(
                                webSocketStatus = StatusState.FAILED,
                                sessionStatus = StatusState.FAILED,
                                readyStatus = StatusState.FAILED
                            )
                        }
                        emitStreamSummaryIfNeeded("WebSocket Failure $code")
                    } else {
                        logException("WebSocket Failure Exception Details", t)

                        if (isUserDisconnecting.get()) {
                            _pipelineStatus.update { current ->
                                current.copy(
                                    webSocketStatus = if (current.webSocketStatus == StatusState.IN_PROGRESS) StatusState.FAILED else current.webSocketStatus,
                                    sessionStatus = if (current.sessionStatus == StatusState.IN_PROGRESS) StatusState.FAILED else current.sessionStatus,
                                    readyStatus = if (current.readyStatus == StatusState.IN_PROGRESS) StatusState.FAILED else current.readyStatus
                                )
                            }
                            setStreamState(StreamState.DISCONNECTED)
                            _isConnecting.value = false
                            log(LogLevel.INFO, "Disconnected")
                            emitStreamSummaryIfNeeded("WebSocket Failure")
                        } else {
                            val networkMsg = "Could not complete prayer session. Please check your network."
                            _connectionErrorMessage.value = networkMsg
                            handleConnectionFailure("WebSocket Failure: ${t.localizedMessage ?: t.javaClass.simpleName}")
                        }
                    }
                }
            }
        })
    }

    private fun handleConnectionFailure(reason: String) {
        if (isUserDisconnecting.get()) return

        val currentAttempt = reconnectAttemptCount.incrementAndGet()
        if (currentAttempt <= maxReconnectAttempts) {
            val delayMs = reconnectDelaysMs.getOrElse(currentAttempt - 1) { 8000L }
            if (currentAttempt == 1) {
                reconnectStartTimeMs.set(System.currentTimeMillis())
            }
            isReconnectedSession.set(true)

            setStreamState(StreamState.RECONNECTING)
            log(LogLevel.WARN, "[WS] Reconnect attempt $currentAttempt after ${delayMs}ms")
            log(
                LogLevel.WARN,
                "Connection unstable – reconnecting… (Attempt $currentAttempt/$maxReconnectAttempts in ${delayMs}ms) [Reason: $reason]"
            )

            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(delayMs)
                if (isUserDisconnecting.get()) return@launch

                log(LogLevel.INFO, "Executing reconnection attempt $currentAttempt/$maxReconnectAttempts...")
                try {
                    activeWebSocket?.close(1000, "Reconnecting")
                } catch (e: Exception) {
                    // Ignore
                }
                activeWebSocket = null

                connectWebSocket(isReconnectingAttempt = true)
            }
        } else {
            val failureMsg = "Connection failed after $maxReconnectAttempts attempts. Click Reconnect to restore session."
            log(LogLevel.ERROR, failureMsg)
            _connectionErrorMessage.value = failureMsg
            _lockErrorEvent.tryEmit(failureMsg)
            _pipelineStatus.update { current ->
                current.copy(
                    webSocketStatus = StatusState.FAILED,
                    sessionStatus = StatusState.FAILED,
                    readyStatus = StatusState.FAILED
                )
            }
            setStreamState(StreamState.DISCONNECTED)
            _isConnecting.value = false
            reconnectAttemptCount.set(0)
            log(LogLevel.INFO, "Disconnected")
            emitStreamSummaryIfNeeded("Reconnection Failed")
        }
    }

    private fun sendSetupFrame(webSocket: WebSocket, modelName: String) {
        try {
            val modalities = listOf("AUDIO")
            val voiceName = "Puck"

            val rawUserName = lastContext?.let { com.example.wake.WakePrefsManager.getUserName(it) }?.trim() ?: ""
            val userDob = lastContext?.let { com.example.wake.WakePrefsManager.getUserDob(it) }?.trim() ?: ""

            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)

            val isInterrupted = lastContext?.let { com.example.wake.WakePrefsManager.isInterruptedByCall(it) } ?: false
            wasInterruptedSession.set(isInterrupted)

            val (bucket, greetingText) = if (isInterrupted) {
                val text = "I'm glad you're back. No worries about the interruption—let's begin our prayer fresh before the Lord. How are you feeling right now?"
                val timeBucket = when (hour) {
                    in 5..11 -> "morning"
                    in 12..16 -> "afternoon"
                    in 17..20 -> "evening"
                    else -> "night"
                }
                timeBucket to text
            } else {
                when (hour) {
                    in 5..11 -> {
                        val text = if (rawUserName.isNotBlank()) {
                            "Good morning, $rawUserName — welcome to today's prayer session. How are you feeling this morning?"
                        } else {
                            "Good morning and welcome to today's prayer session. How are you feeling this morning?"
                        }
                        "morning" to text
                    }
                    in 12..16 -> {
                        val text = if (rawUserName.isNotBlank()) {
                            "Good afternoon, $rawUserName — welcome to today's prayer session. How are you feeling this afternoon?"
                        } else {
                            "Good afternoon and welcome to today's prayer session. How are you feeling this afternoon?"
                        }
                        "afternoon" to text
                    }
                    in 17..20 -> {
                        val text = if (rawUserName.isNotBlank()) {
                            "Good evening, $rawUserName — welcome to tonight's prayer session. How are you feeling this evening?"
                        } else {
                            "Good evening and welcome to tonight's prayer session. How are you feeling this evening?"
                        }
                        "evening" to text
                    }
                    else -> {
                        val text = if (rawUserName.isNotBlank()) {
                            "Good night, $rawUserName — welcome to tonight's prayer session. How are you feeling tonight?"
                        } else {
                            "Good night and welcome to tonight's prayer session. How are you feeling tonight?"
                        }
                        "night" to text
                    }
                }
            }

            val greetingLog = "[GREETING] bucket=$bucket"
            log(LogLevel.INFO, greetingLog)
            android.util.Log.i("GeminiLive", greetingLog)
            com.example.wake.WakePrefsManager.logWakeEvent(greetingLog)
            lastContext?.let { com.example.wake.OvernightJournal.log(it, "GREETING", "bucket=$bucket") }

            val prayerTextLog = "[PRAYER TEXT] period=$bucket"
            log(LogLevel.INFO, prayerTextLog)
            android.util.Log.i("GeminiLive", prayerTextLog)
            com.example.wake.WakePrefsManager.logWakeEvent(prayerTextLog)
            lastContext?.let { com.example.wake.OvernightJournal.log(it, "PRAYER TEXT", "period=$bucket") }

            val thisPeriodPhrase = when (bucket) {
                "morning" -> "this morning"
                "afternoon" -> "this afternoon"
                "evening" -> "this evening"
                "night" -> "tonight"
                else -> "this morning"
            }

            val thisExactPeriodPhrase = when (bucket) {
                "morning" -> "this exact morning"
                "afternoon" -> "this exact afternoon"
                "evening" -> "this exact evening"
                "night" -> "tonight"
                else -> "this exact morning"
            }

            val userProfileText = if (rawUserName.isNotBlank()) {
                val dobPart = if (userDob.isNotBlank()) " The user was born on $userDob. Never announce the user's age; use this context only when naturally relevant (e.g., life season, wisdom)." else ""
                "\n\nUSER PROFILE: The user's name is $rawUserName.$dobPart Use their name naturally and sparingly — once in the greeting, at most once more in the prayer. Never mention that a profile exists."
            } else ""

            val systemInstructionText = """
                You are an empathetic, professional, and patient spiritual guide and pastor. Your voice is warm, calm, steady, and powerful. You never rush, and you never sound robotic.
                It is currently $bucket. Use only time-appropriate language: 'as the day begins' (morning), 'as the day unfolds' (afternoon), 'as the day winds down' (evening), 'as night descends' (night). Never say 'morning' outside 05:00-11:59.

                PHASE 1, INITIALIZATION:
                Begin by saying exactly: $greetingText

                PHASE 2, LISTEN AND ACKNOWLEDGE:
                Wait for the user's answer. Listen carefully to their emotional state, even if they just say "I'm fine", "I'm feeling okay", or "just normal". Acknowledge them warmly and naturally in one or two sentences. If they share a struggle (like a lost pet, stress, or pain), offer deep comfort. If they share joy, celebrate with them. If they are neutral or tired, offer gentle, grounded encouragement.
                CONFUSED-USER PIVOT RULE: If the user asks an operational or clarification question instead of sharing a feeling (for example: Who is this? Can you hear me? My phone is lagging), answer concisely in one sentence, then gently ask if they are ready to begin today's prayer. Only proceed when the user is ready or has shared a feeling.
                After acknowledging them, say: Let us pray $thisPeriodPhrase.

                PHASE 3, CONTINUOUS PRAYER (CRITICAL RULE):
                Immediately after saying "Let us pray $thisPeriodPhrase", you MUST continue in the EXACT SAME TURN and deliver the prayer. Do NOT stop speaking. Do NOT wait for the user to reply.
                Generate a fresh, unique prayer specifically for this user and $thisExactPeriodPhrase, based on what they shared. Do NOT recite famous pre-written prayers (like the Our Father) unless the user specifically asks for them. Use the brevity, rhythm, and reverence of classic short prayers only as structural inspiration.
                The prayer must be short (about 30 to 45 seconds of slow speaking) to keep the voice natural and human. Keep the language authentic, grounded, and sincere — avoid overly flowery or fabricated religious jargon.
                Structure:
                1. Open with: In the name of the Father, and of the Son, and of the Holy Spirit. Amen.
                2. A short, personalized address to God that weaves in what the user just shared.
                3. A brief surrender or dedication of the day.
                4. Close with: In the name of the Father, and of the Son, and of the Holy Spirit. Amen.

                PHASE 4, CLOSING:
                Then say one warm closing sentence, such as: May the peace of God rest upon you today. God bless you.
                Then add ONE short scripture line to carry into the day (vary it each morning), for example: Carry this with you today: "This is the day the Lord has made; let us rejoice and be glad in it."
                Then stop speaking completely. Do NOT ask any follow-up questions. The session is complete.$userProfileText

                VOICE AND TONE GUIDELINES:
                Always address the user directly as 'you'. Never refer to them in third person by their name inside the prayer (say 'the energy you feel', never 'the energy Prince feels').
                Separate your reply into short paragraphs with a blank line between them: (1) a 1-2 sentence acknowledgment of what the user shared, (2) the prayer itself (max 4-6 sentences), (3) the closing verse ALONE on its own line, beginning with 'Carry this with you:'.
                Speak slowly, deliberately, and naturally. Use intentional pauses. Do not use markdown, lists, or bullet points. Your output is fed directly to audio.
                Speak with the natural warmth and cadence of a close friend sitting across the table, not a narrator reading a script.
                REMEMBER: The acknowledgment, the transition, and the entire prayer MUST be spoken in ONE single, continuous response.
            """.trimIndent()

            val setupJson = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", modelName)
                    if (isInputAudioTranscriptionEnabled) {
                        put("inputAudioTranscription", JSONObject())
                    }
                    put("outputAudioTranscription", JSONObject())
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", systemInstructionText)
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().apply {
                            modalities.forEach { put(it) }
                        })
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put("prebuiltVoiceConfig", JSONObject().apply {
                                    put("voiceName", voiceName)
                                })
                            })
                        })
                    })
                })
            }

            val payloadString = setupJson.toString()
            log(LogLevel.INFO, "Supported response modalities selected: $modalities")
            if (isInputAudioTranscriptionEnabled) {
                log(LogLevel.INFO, "Transcript configuration: inputAudioTranscription and outputAudioTranscription enabled")
            } else {
                log(LogLevel.INFO, "Transcript configuration: outputAudioTranscription enabled (inputAudioTranscription disabled due to close code 1007)")
            }
            log(LogLevel.INFO, "Voice configuration enabled/disabled: Enabled (Prebuilt Voice: $voiceName)")

            log(LogLevel.INFO, "Sending session setup frame...")
            log(LogLevel.DEBUG, "Outbound Setup Frame: $payloadString")

            val sent = webSocket.send(payloadString)
            if (sent) {
                log(LogLevel.INFO, "Setup frame sent to Gemini Live API")
            } else {
                log(LogLevel.WARN, "webSocket.send() returned false")
            }

            // Consume interrupted flag only after setup frame is constructed and sent
            if (isInterrupted) {
                lastContext?.let { com.example.wake.WakePrefsManager.setInterruptedByCall(it, false) }
            }

        } catch (e: Exception) {
            logException("Failed to construct or send Live setup frame", e)
            _pipelineStatus.update { it.copy(sessionStatus = StatusState.FAILED) }
            disconnectInternal("Setup frame error")
        }
    }

    /**
     * Parses and decodes JSON events received over the WebSocket connection.
     */
    private fun processJsonPayload(webSocket: WebSocket, jsonText: String) {
        try {
            val jsonResponse = JSONObject(jsonText)

            // 1. Check for setupComplete
            if (jsonResponse.has("setupComplete")) {
                val attempts = reconnectAttemptCount.getAndSet(0)
                val startTime = reconnectStartTimeMs.getAndSet(0L)
                val wasReconnect = isReconnectedSession.getAndSet(false) || attempts > 0 || startTime > 0

                if (wasReconnect) {
                    val totalMs = if (startTime > 0) System.currentTimeMillis() - startTime else 0L
                    log(LogLevel.SUCCESS, "[WS] reconnected after ${totalMs}ms total")
                    log(LogLevel.SUCCESS, "Reconnected successfully after $attempts attempts")
                    audioPlaybackEngine.flushForReconnection()
                    playTurnBeep()
                } else {
                    audioPlaybackEngine.prepare()
                }
                _connectionErrorMessage.value = null

                setStreamState(StreamState.SETUP_COMPLETE)
                log(LogLevel.SUCCESS, "[setupComplete] setupComplete received - Gemini Live session ready")

                // Ensure audio focus is active
                if (lastContext != null && audioFocusRequest == null) {
                    requestAudioFocus(lastContext!!)
                }

                _pipelineStatus.update {
                    it.copy(
                        sessionStatus = StatusState.SUCCESS,
                        readyStatus = StatusState.SUCCESS
                    )
                }

                // Start continuous Full Duplex microphone capture if permission is granted
                val recEngine = audioRecordingEngine
                if (recEngine != null) {
                    if (recEngine.hasRecordAudioPermission()) {
                        val started = recEngine.startRecording(webSocket) { _isGeminiSpeaking.value || audioPlaybackEngine.isPlaying() || isGeminiTurnActive.get() }
                        if (!started) {
                            val err = "Microphone unavailable. Tap to retry."
                            log(LogLevel.ERROR, "[MIC RECORD] startRecording failed on setupComplete")
                            _connectionErrorMessage.value = err
                            _lockErrorEvent.tryEmit(err)
                        }
                    } else {
                        val err = "Microphone permission unavailable. Tap to retry."
                        log(LogLevel.WARN, "[MIC RECORD] RECORD_AUDIO permission not granted. Request runtime permission to enable Full Duplex microphone input.")
                        _connectionErrorMessage.value = err
                        _lockErrorEvent.tryEmit(err)
                    }
                }

                if (wasReconnect) {
                    sendReGreetTrigger(webSocket)
                } else if (diagnosticPromptSent.compareAndSet(false, true)) {
                    sendDiagnosticTurn(webSocket)
                }
            }

            // 2. Check for serverContent
            if (jsonResponse.has("serverContent")) {
                val serverContent = jsonResponse.getJSONObject("serverContent")

                if (serverContent.has("inputTranscription")) {
                    val text = serverContent.optJSONObject("inputTranscription")?.optString("text", "") ?: ""
                    if (text.isNotBlank()) {
                        log(LogLevel.SUCCESS, "[transcript] Input transcript: \"$text\"")
                        appendTranscriptText(isUser = true, text = text)
                    }
                }

                if (serverContent.has("outputTranscription")) {
                    val text = serverContent.optJSONObject("outputTranscription")?.optString("text", "") ?: ""
                    if (text.isNotBlank()) {
                        appendTranscriptText(isUser = false, text = text)
                        if (text.contains("Carry this") || text.contains("Psalm") || text.contains("\"")) {
                            lastSpokenVerseLine = text
                        }
                    }
                }

                if (serverContent.has("clientContent")) {
                    log(LogLevel.INFO, "[clientAcknowledgement] Server acknowledged client content turn")
                }

                if (serverContent.has("modelTurn")) {
                    _isGeminiSpeaking.value = true
                    isGeminiTurnActive.set(true)
                    lastServerAudioMs = System.currentTimeMillis()
                    if (generationStartedLogged.compareAndSet(false, true)) {
                        setStreamState(StreamState.RECEIVING_STREAM)
                        audioPlaybackEngine.notifyTurnStarted()
                        receivedAudioMsThisTurn.set(0L)
                        log(LogLevel.SUCCESS, "[generationStarted] Model audio response stream started")

                        if (_isWaitingForResponse.value) {
                            _isWaitingForResponse.value = false
                            val elapsedSec = if (userSpeechSentTimeMs > 0) (System.currentTimeMillis() - userSpeechSentTimeMs) / 1000L else 15L
                            log(LogLevel.SUCCESS, "[TURN] Model response resumed after ${elapsedSec}s")
                        }
                        responseWatchdogJob?.cancel()
                        responseWatchdogJob = null
                    }

                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    if (modelTurn.has("parts")) {
                        val parts = modelTurn.getJSONArray("parts")
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)

                            // TRANSCRIPT
                            val isThought = part.optBoolean("thought", false) || part.has("thought")
                            if (part.has("text") && !isThought) {
                                val responseText = part.getString("text")
                                log(LogLevel.SUCCESS, "[transcript] Transcript: \"$responseText\"")
                                appendTranscriptText(isUser = false, responseText)
                                if (responseText.contains("Carry this") || responseText.contains("\"")) {
                                    lastSpokenVerseLine = responseText
                                }
                            }

                            // AUDIO RESPONSE INLINE DATA
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val mimeType = inlineData.optString("mimeType", "audio/pcm;rate=24000")
                                val base64Data = inlineData.optString("data", "")

                                if (base64Data.isNotEmpty()) {
                                    val nowMs = System.currentTimeMillis()
                                    try {
                                        val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                        processAndLogAudioChunk(decodedBytes, nowMs, mimeType)
                                    } catch (e: Exception) {
                                        log(LogLevel.WARN, "Base64 audio decode error: ${e.localizedMessage}")
                                    }
                                }
                            }
                        }
                    }
                }

                // TURN COMPLETION
                if (serverContent.optBoolean("turnComplete", false)) {
                    lastServerAudioMs = System.currentTimeMillis()
                    setStreamState(StreamState.TURN_COMPLETE)
                    generationStartedLogged.set(false)
                    audioPlaybackEngine.notifyTurnCompleted()

                    if (!hasReceivedAnyTranscription && _isTranscriptAvailable.value) {
                        _isTranscriptAvailable.value = false
                        log(LogLevel.INFO, "[UI] Transcription unavailable - transcript hidden")
                    }

                    log(LogLevel.SUCCESS, "[turnComplete] turnComplete received")
                    log(LogLevel.SUCCESS, "[generationFinished] generationFinished - Gemini completed response")

                    if (!audioPlaybackEngine.hasWrittenAudioInCurrentTurn()) {
                        _isGeminiSpeaking.value = false
                        isGeminiTurnActive.set(false)
                        playTurnBeep()
                        if (isPrayerCompletedTurn()) {
                            lastContext?.let { ctx ->
                                val now = System.currentTimeMillis()
                                com.example.wake.WakePrefsManager.setLastPrayerCompleted(ctx, now)
                                val wakeMsg = "[LOCK] Released - prayer complete (immediate)"
                                log(LogLevel.SUCCESS, wakeMsg)
                                com.example.wake.WakePrefsManager.logWakeEvent(wakeMsg)

                                val aiOutputText = _transcriptLines.value.filter { !it.isUser }.joinToString(" ") { it.text }
                                scope.launch {
                                    com.example.customization.VerseExtractor.onPrayerCompleted(ctx, aiOutputText)
                                }
                            }
                            _prayerCompletedEvent.tryEmit(Unit)
                        } else {
                            audioRecordingEngine?.setMuted(false)
                        }
                    } else {
                        // Hard 5-second timeout on "FINISHING" drain state
                        scope.launch {
                            delay(5000)
                            if (audioPlaybackEngine.isPlaying()) {
                                log(LogLevel.WARN, "Watchdog: forced end of draining state after 5s")
                                audioPlaybackEngine.stopPlaybackImmediate("5s drain timeout")
                                _isGeminiSpeaking.value = false
                                isGeminiTurnActive.set(false)
                                playTurnBeep()
                            }
                        }
                    }

                    emitStreamSummaryIfNeeded("turnComplete")
                    
                    log(LogLevel.INFO, "[SESSION IDLE] Session idle waiting for next interaction")
                }

                if (serverContent.optBoolean("interrupted", false)) {
                    setStreamState(StreamState.TURN_COMPLETE)
                    generationStartedLogged.set(false)
                    _isGeminiSpeaking.value = false
                    isGeminiTurnActive.set(false)
                    audioPlaybackEngine.notifyTurnCompleted()
                    log(LogLevel.WARN, "[interrupted] Model turn interrupted by server")
                    audioPlaybackEngine.stopPlaybackImmediate("Model turn interrupted by server VAD")
                    audioPlaybackEngine.notifyInterrupted()
                    log(LogLevel.INFO, "[SESSION IDLE] Session idle waiting for next interaction after interruption")
                }
            }

            // 3. Check for sessionUpdate
            if (jsonResponse.has("sessionUpdate")) {
                log(LogLevel.INFO, "[sessionUpdate] sessionUpdate event received")
            }

            // 4. Check for toolCall
            if (jsonResponse.has("toolCall")) {
                log(LogLevel.INFO, "[toolCall] toolCall event received")
            }

            // 5. Check for error
            if (jsonResponse.has("error")) {
                val errObj = jsonResponse.get("error")
                log(LogLevel.ERROR, "[error] Error event received from server: $errObj")
            }

            // 6. Unknown event
            if (!jsonResponse.has("setupComplete") &&
                !jsonResponse.has("serverContent") &&
                !jsonResponse.has("sessionUpdate") &&
                !jsonResponse.has("toolCall") &&
                !jsonResponse.has("error")
            ) {
                log(LogLevel.INFO, "[unknown event] Unhandled event frame received")
            }

        } catch (e: Exception) {
            log(LogLevel.WARN, "Non-JSON or unparseable event: ${e.localizedMessage}")
        }
    }

    /**
     * Inspects, validates, analyzes buffer, and logs details for an incoming audio chunk.
     */
    private fun processAndLogAudioChunk(rawBytes: ByteArray, receptionTimeMs: Long, mimeType: String) {
        val nowMs = System.currentTimeMillis()
        lastServerAudioMs = nowMs
        lastAudioChunkReceivedTimeMs = nowMs

        val chunkMs = (rawBytes.size * 1000L) / 48000L
        receivedAudioMsThisTurn.addAndGet(chunkMs)

        val report = inspector.inspectFrame(rawBytes, nowMs, mimeType)
        val actualBufferedBytes = audioPlaybackEngine.getTotalBufferedBytes()
        val bufferReport = bufferAnalyzer.processFrameBuffer(rawBytes.size, receptionTimeMs, nowMs, actualBufferedBytes)

        val hexPreview = rawBytes.take(16).joinToString(" ") { "%02X".format(it) }

        val details = buildString {
            append("Frame #: ").append(report.frameNumber).append("\n")
            append("Byte Size: ").append(report.byteSize).append(" bytes\n")
            append("Frame Duration: ").append(String.format(Locale.US, "%.1f ms", report.durationMs)).append("\n")
            append("Running Total: ").append(report.runningTotalBytes).append(" bytes (")
            append(String.format(Locale.US, "%.2f s", inspector.getTotalDurationSeconds())).append(")\n")
            append("Interval Since Last Frame: ").append(report.frameIntervalMs).append(" ms\n")
            append("Buffer Level: ").append(bufferReport.currentBufferBytes).append(" bytes (")
            append(String.format(Locale.US, "%.1f ms", bufferReport.bufferDurationMs)).append(")\n")
            append("Peak Buffer: ").append(bufferReport.peakBufferBytes).append(" bytes\n")
            append("Processing Latency: ").append(bufferReport.latencyMs).append(" ms\n")
            append("PCM Alignment: ").append(if (report.validationResult.isValid) "VALID (16-bit Mono @ 24kHz)" else "INVALID / TRUNCATED").append("\n")
            if (report.isDuplicate) append("⚠️ DUPLICATE FRAME DETECTED\n")
            if (report.isMissingDetected) append("⚠️ POTENTIAL SKIPPED FRAME GAP DETECTED\n")
            if (report.isOutOfOrder) append("⚠️ OUT-OF-ORDER ARRIVAL DETECTED\n")
            bufferReport.warnings.forEach { append("⚠️ ").append(it).append("\n") }
            report.validationResult.warnings.forEach { append("⚠️ ").append(it).append("\n") }
            append("Hex Preview (first 16B): ").append(hexPreview)
        }

        val warningFlag = if (!report.validationResult.isValid || report.isDuplicate || report.isMissingDetected || bufferReport.isUnderflowDetected || bufferReport.isStarvationRisk) " [WARNING]" else ""

        log(
            LogLevel.INFO,
            "[audioChunk] Chunk #${report.frameNumber} | Size: ${report.byteSize}B | Duration: ${String.format(Locale.US, "%.1fms", report.durationMs)} | Total: ${report.runningTotalBytes}B | Buffer: ${bufferReport.currentBufferBytes}B${warningFlag}",
            details
        )

        // Real-time PCM audio playback via AudioTrack (integrated after validation)
        if (report.validationResult.isValid) {
            audioPlaybackEngine.playChunk(rawBytes, report.frameNumber)
        } else {
            log(LogLevel.WARN, "[AudioPlayer] Chunk #${report.frameNumber} skipped from playback due to PCM validation failure")
        }
    }

    /**
     * Emits the comprehensive Audio Stream Summary if frames were received and summary hasn't been emitted yet.
     */
    private fun emitStreamSummaryIfNeeded(triggerReason: String) {
        if (summaryLogged.compareAndSet(false, true)) {
            val totalFrames = inspector.getTotalFrames()
            if (totalFrames > 0) {
                val summaryText = AudioStreamSummaryBuilder.buildSummaryString(inspector, bufferAnalyzer)
                log(LogLevel.SUCCESS, summaryText, "Audio Stream Summary generated automatically on $triggerReason")
            } else {
                log(LogLevel.WARN, "No audio frames were received during session. Stream summary skipped.")
            }
        }
    }

    /**
     * Sends the diagnostic user conversation turn ("Hello from Gemini Live.").
     */
    private fun sendDiagnosticTurn(webSocket: WebSocket) {
        try {
            val isInterrupted = wasInterruptedSession.get()
            val promptText = if (isInterrupted) {
                "I am back from a phone call interruption. Please give your welcome-back greeting and begin our prayer fresh."
            } else {
                "Hello from Gemini Live."
            }
            val nowTimestamp = timeFormat.format(Date())

            setStreamState(StreamState.USER_TURN_SENT)
            log(LogLevel.INFO, "User turn created: \"$promptText\"")

            val promptJson = JSONObject().apply {
                put("clientContent", JSONObject().apply {
                    put("turns", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", promptText)
                                })
                            })
                        })
                    })
                    put("turnComplete", true)
                })
            }

            val payloadString = promptJson.toString()
            log(LogLevel.INFO, "User turn serialized: $payloadString")

            val sent = webSocket.send(payloadString)
            if (sent) {
                log(LogLevel.SUCCESS, "User turn sent successfully [Timestamp: $nowTimestamp, Payload Size: ${payloadString.length} bytes]")
            } else {
                log(LogLevel.WARN, "webSocket.send() returned false when sending user turn")
            }
            onUserSpeechSent()
        } catch (e: Exception) {
            logException("Failed to send diagnostic user turn", e)
        }
    }

    /**
     * Sends the re-greet prompt after a WebSocket reconnection.
     */
    private fun sendReGreetTrigger(webSocket: WebSocket) {
        try {
            val promptText = "The connection was restored. Please greet me again and continue the prayer session."
            setStreamState(StreamState.USER_TURN_SENT)
            log(LogLevel.INFO, "Re-greet trigger turn created: \"$promptText\"")

            val promptJson = JSONObject().apply {
                put("clientContent", JSONObject().apply {
                    put("turns", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", promptText)
                                })
                            })
                        })
                    })
                    put("turnComplete", true)
                })
            }

            val payloadString = promptJson.toString()
            val sent = webSocket.send(payloadString)
            if (sent) {
                log(LogLevel.SUCCESS, "[WS] Re-greet trigger sent")
            } else {
                log(LogLevel.WARN, "webSocket.send() returned false when sending re-greet trigger")
            }
            onUserSpeechSent()
        } catch (e: Exception) {
            logException("Failed to send re-greet trigger", e)
        }
    }

    /**
     * Resets the 60-second activity timeout timer.
     */
    private fun resetActivityTimeout(webSocket: WebSocket) {
        activityTimeoutJob?.cancel()
        activityTimeoutJob = scope.launch {
            delay(60_000)
            log(LogLevel.WARN, "Timeout: 60 seconds passed without activity.")
            _lockErrorEvent.tryEmit("60s inactivity timeout reached")
            disconnectInternal("60s inactivity timeout reached")
        }
    }

    private fun cancelActivityTimeout() {
        activityTimeoutJob?.cancel()
        activityTimeoutJob = null
    }

    /**
     * Starts full duplex microphone recording manually if connection is active.
     */
    fun startMicRecording(context: Context) {
        val ws = activeWebSocket
        if (ws == null) {
            log(LogLevel.WARN, "Cannot start microphone recording: Live WebSocket is not connected.")
            return
        }
        if (audioRecordingEngine == null) {
            audioRecordingEngine = AudioRecordingEngine(
                context = context,
                logger = { level, msg, details -> log(level, msg, details) },
                onBargeInDetected = {
                    generationStartedLogged.set(false)
                    setStreamState(StreamState.USER_TURN_SENT)
                    audioPlaybackEngine.stopPlaybackImmediate("User barge-in speech detected")
                    audioPlaybackEngine.notifyInterrupted()
                    onUserSpeechSent()
                },
                onUserSpeechDetected = {
                    onUserSpeechSent()
                },
                onRecordingError = { errorMsg ->
                    log(LogLevel.ERROR, "[MIC RECORD] $errorMsg")
                    _connectionErrorMessage.value = errorMsg
                    _lockErrorEvent.tryEmit(errorMsg)
                }
            )
        }
        val started = audioRecordingEngine?.startRecording(ws) { _isGeminiSpeaking.value || audioPlaybackEngine.isPlaying() || isGeminiTurnActive.get() }
        if (started == false) {
            val err = "Microphone unavailable. Tap to retry."
            _connectionErrorMessage.value = err
            _lockErrorEvent.tryEmit(err)
        }
    }

    /**
     * Stops microphone recording.
     */
    fun stopMicRecording() {
        audioRecordingEngine?.stopRecording()
    }

    /**
     * Toggles microphone mute state.
     */
    fun toggleMicMute(): Boolean {
        val engine = audioRecordingEngine ?: return false
        val newMuteState = !engine.isMuted()
        engine.setMuted(newMuteState)
        return newMuteState
    }

    fun isMicMuted(): Boolean = audioRecordingEngine?.isMuted() ?: false
    fun isMicRecording(): Boolean = audioRecordingEngine?.isRecording() ?: false

    fun pauseSessionForCall() {
        log(LogLevel.WARN, "[CALL] Session paused for incoming call - mic stopped & audio flushed")
        android.util.Log.w("GeminiLive", "[CALL] Session paused for incoming call - mic stopped & audio flushed")
        audioRecordingEngine?.stopRecording()
        audioPlaybackEngine.pauseForPhoneCall()
    }

    fun resumeSessionFromCall(context: Context) {
        log(LogLevel.INFO, "[CALL] Session resuming from call")
        android.util.Log.i("GeminiLive", "[CALL] Session resuming from call")
        startMicRecording(context)
        audioPlaybackEngine.resumeFromPhoneCall()
    }

    /**
     * Explicitly releases engine, watchdog coroutine, audio playback hardware, and microphone recording hardware.
     */
    fun release() {
        watchdogJob?.cancel()
        watchdogJob = null
        statusMonitorJob?.cancel()
        statusMonitorJob = null
        disconnectInternal("Application lifecycle release")
        audioPlaybackEngine.release()
        audioRecordingEngine?.stopRecording()
        audioRecordingEngine?.reset()
        audioRecordingEngine = null
    }

    /**
     * Disconnects the active Live session and closes the WebSocket connection.
     */
    fun disconnect() {
        scope.launch {
            log(LogLevel.INFO, "User requested disconnect")
            disconnectInternal("User triggered disconnect")
        }
    }

    private fun disconnectInternal(reason: String) {
        isUserDisconnecting.set(true)
        _isGeminiSpeaking.value = false
        isGeminiTurnActive.set(false)
        _isAudioPlaying.value = false
        _isMicSending.value = false
        _isWaitingForResponse.value = false
        responseWatchdogJob?.cancel()
        responseWatchdogJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        statusMonitorJob?.cancel()
        statusMonitorJob = null
        reconnectAttemptCount.set(0)
        _connectionErrorMessage.value = null

        cancelActivityTimeout()
        abandonAudioFocus(lastContext)
        audioPlaybackEngine.stopPlaybackImmediate("Disconnect ($reason)")

        val recEngine = audioRecordingEngine
        if (recEngine != null) {
            recEngine.stopRecording()
            val summary = recEngine.getSummary()
            log(LogLevel.INFO, summary)
            recEngine.reset()
            audioRecordingEngine = null
        }

        try {
            emitStreamSummaryIfNeeded("Disconnect ($reason)")
            activeWebSocket?.close(1000, reason)
            activeWebSocket = null
            log(LogLevel.INFO, "Connection closed ($reason)")
        } catch (e: Exception) {
            logException("Exception while closing WebSocket", e)
        } finally {
            setStreamState(StreamState.DISCONNECTED)
            _isConnecting.value = false
            log(LogLevel.INFO, "Disconnected")
        }
    }

    /**
     * Logs a diagnostic message with event correlation metadata (Timestamp, Elapsed ms, Delta ms, Turn ID, Stream State).
     */
    fun log(level: LogLevel, message: String, details: String? = null) {
        val now = System.currentTimeMillis()
        val start = sessionStartTimeMs?.get() ?: 0L
        val elapsedMs = if (start > 0) now - start else 0L

        val prev = lastEventTimeMs?.getAndSet(now) ?: 0L
        val deltaMs = if (prev > 0) now - prev else 0L

        val turnId = currentTurnId?.get() ?: 1
        val state = currentStreamState?.get() ?: StreamState.IDLE

        val correlatedMessage = "[+${elapsedMs}ms | Δ${deltaMs}ms | Turn #$turnId | State: $state] $message"

        _logFlow.tryEmit(Triple(level, correlatedMessage, details))
    }

    private fun logException(contextMessage: String, t: Throwable) {
        val details = buildString {
            append("Exception Type: ").append(t.javaClass.name).append("\n")
            append("Message: ").append(t.message ?: "(none)").append("\n")
            append("Cause: ").append(t.cause?.toString() ?: "(none)").append("\n")
            append("\n--- FULL STACK TRACE ---\n")
            append(t.stackTraceToString())
        }
        log(LogLevel.ERROR, "$contextMessage: ${t.localizedMessage ?: t.javaClass.simpleName}", details)
    }
}
