package com.example.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.diagnostic.GeminiLiveDiagnosticEngine
import com.example.diagnostic.StreamState
import com.example.model.LogEntry
import com.example.model.LogLevel
import com.example.model.PipelineStatusState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.util.Log
import androidx.activity.ComponentActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

enum class LockStatus {
    NONE,
    ACTIVE,
    RECONNECTING,
    COMPLETED,
    ERROR
}

data class LockModeState(
    val isLocked: Boolean = false,
    val status: LockStatus = LockStatus.NONE,
    val errorMessage: String? = null
)

/**
 * ViewModel for managing Gemini Live Diagnostic UI state, logs, and export logic.
 */
class DiagnosticViewModel : ViewModel() {

    private val engine = com.example.wake.WakeDetectorService.engine

    val pipelineStatus: StateFlow<PipelineStatusState> = engine.pipelineStatus
    val isConnecting: StateFlow<Boolean> = engine.isConnecting
    val streamState: StateFlow<StreamState> = engine.streamState
    val connectionErrorMessage: StateFlow<String?> = engine.connectionErrorMessage
    val isGeminiSpeaking: StateFlow<Boolean> = engine.isGeminiSpeaking
    val isAudioPlaying: StateFlow<Boolean> = engine.isAudioPlaying
    val isMicSending: StateFlow<Boolean> = engine.isMicSending
    val isWaitingForResponse: StateFlow<Boolean> = engine.isWaitingForResponse
    val wakeState: StateFlow<com.example.wake.WakeState> = com.example.wake.WakePrefsManager.wakeState

    val isLockMode = MutableStateFlow(false)
    val isAutoStartStalled = MutableStateFlow(false)
    private val _lockModeState = MutableStateFlow(LockModeState())
    val lockModeState: StateFlow<LockModeState> = _lockModeState.asStateFlow()

    private var currentActivity: java.lang.ref.WeakReference<ComponentActivity>? = null
    private var safetyTimeoutJob: Job? = null
    private var autoStartWatchdogJob: Job? = null

    private val _logEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    val logEntries: StateFlow<List<LogEntry>> = _logEntries.asStateFlow()

    // Loaded from BuildConfig if available
    val apiKeyInput = MutableStateFlow(getInitialApiKey())
    val modelNameInput = MutableStateFlow("models/gemini-2.5-flash-native-audio-preview-12-2025")

    val debugMode = MutableStateFlow(true)
    val autoScroll = MutableStateFlow(true)

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())

    init {
        viewModelScope.launch {
            engine.logFlow.collect { (level, message, details) ->
                val timeString = timeFormat.format(Date())
                val entry = LogEntry(
                    timestamp = timeString,
                    level = level,
                    message = message,
                    details = details
                )
                _logEntries.update { currentList ->
                    if (currentList.size >= 500) {
                        currentList.drop(currentList.size - 499) + entry
                    } else {
                        currentList + entry
                    }
                }
            }
        }

        viewModelScope.launch {
            engine.prayerCompletedEvent.collect {
                if (isLockMode.value) {
                    val msg = "[LOCK] Released - prayer complete (immediate)"
                    Log.i("WakeDetector", msg)
                    com.example.wake.WakePrefsManager.logWakeEvent(msg)
                    releaseLockMode("prayer complete (immediate)", isError = false)
                }
            }
        }

        viewModelScope.launch {
            engine.lockErrorEvent.collect { errorMsg ->
                if (isLockMode.value) {
                    releaseLockMode(errorMsg, isError = true)
                }
            }
        }

        viewModelScope.launch {
            engine.streamState.collect { state ->
                if (isLockMode.value) {
                    if (state == StreamState.RECONNECTING) {
                        _lockModeState.value = _lockModeState.value.copy(status = LockStatus.RECONNECTING)
                        val msg = "[LOCK] Reconnecting while locked"
                        Log.i("WakeDetector", msg)
                        com.example.wake.WakePrefsManager.logWakeEvent(msg)
                    } else if (state == StreamState.RECEIVING_STREAM || state == StreamState.SETUP_COMPLETE || state == StreamState.TURN_COMPLETE) {
                        isAutoStartStalled.value = false
                        if (_lockModeState.value.status == LockStatus.RECONNECTING) {
                            _lockModeState.value = _lockModeState.value.copy(status = LockStatus.ACTIVE)
                        }
                    } else if (state == StreamState.DISCONNECTED) {
                        val err = engine.connectionErrorMessage.value ?: "Connection lost"
                        if (_lockModeState.value.status != LockStatus.COMPLETED && _lockModeState.value.status != LockStatus.NONE) {
                            releaseLockMode(err, isError = true)
                        }
                    }
                }
            }
        }
    }

    fun attachActivity(activity: ComponentActivity) {
        currentActivity = java.lang.ref.WeakReference(activity)
    }

    fun enableLockMode(context: Context, activity: ComponentActivity? = null) {
        if (activity != null) {
            currentActivity = java.lang.ref.WeakReference(activity)
        }
        isLockMode.value = true
        isAutoStartStalled.value = false
        _lockModeState.value = LockModeState(isLocked = true, status = LockStatus.ACTIVE)

        startSafetyTimeout()
        startAutoStartWatchdog()
    }

    fun releaseLockMode(reason: String, isError: Boolean = false) {
        if (!isLockMode.value && _lockModeState.value.status == LockStatus.NONE) return
        safetyTimeoutJob?.cancel()
        safetyTimeoutJob = null
        autoStartWatchdogJob?.cancel()
        autoStartWatchdogJob = null
        isAutoStartStalled.value = false

        isLockMode.value = false
        if (isError) {
            _lockModeState.value = LockModeState(isLocked = false, status = LockStatus.ERROR, errorMessage = reason)
            val logMsg = "[LOCK] Released - connection error: $reason"
            Log.i("WakeDetector", logMsg)
            com.example.wake.WakePrefsManager.logWakeEvent(logMsg)
        } else {
            _lockModeState.value = LockModeState(isLocked = false, status = LockStatus.COMPLETED)
            val logMsg = "[LOCK] Released - $reason"
            Log.i("WakeDetector", logMsg)
            com.example.wake.WakePrefsManager.logWakeEvent(logMsg)
        }
    }

    fun dismissLockState() {
        _lockModeState.value = LockModeState(isLocked = false, status = LockStatus.NONE)
    }

    private fun startAutoStartWatchdog() {
        autoStartWatchdogJob?.cancel()
        autoStartWatchdogJob = viewModelScope.launch {
            delay(10_000L) // 10 seconds
            if (isLockMode.value && streamState.value != StreamState.SETUP_COMPLETE && streamState.value != StreamState.RECEIVING_STREAM && streamState.value != StreamState.TURN_COMPLETE) {
                isAutoStartStalled.value = true
                val msg = "[LOCK] Auto-start stalled - manual start shown"
                Log.i("WakeDetector", msg)
                com.example.wake.WakePrefsManager.logWakeEvent(msg)
            }
        }
    }

    private fun startSafetyTimeout() {
        safetyTimeoutJob?.cancel()
        safetyTimeoutJob = viewModelScope.launch {
            delay(300_000L) // 5 minutes
            if (isLockMode.value) {
                val msg = "[LOCK] Released - safety timeout (5min)"
                Log.i("WakeDetector", msg)
                com.example.wake.WakePrefsManager.logWakeEvent(msg)
                releaseLockMode("safety timeout (5min)", isError = true)
            }
        }
    }

    private fun getInitialApiKey(): String {
        return ""
    }

    fun syncApiKey(context: Context) {
        val currentKey = com.example.api.ApiKeyProvider.getApiKey(context)
        if (apiKeyInput.value.isBlank() && currentKey.isNotBlank()) {
            apiKeyInput.value = currentKey
        }
    }

    fun saveUserApiKey(context: Context, key: String) {
        apiKeyInput.value = key.trim()
        com.example.api.ApiKeyProvider.setUserApiKey(context, key)
    }

    fun setDeveloperMode(context: Context, enabled: Boolean) {
        com.example.api.ApiKeyProvider.setDeveloperMode(context, enabled)
        apiKeyInput.value = com.example.api.ApiKeyProvider.getApiKey(context)
    }

    val isMicMuted = MutableStateFlow(false)

    fun connect(context: Context) {
        val resolvedKey = apiKeyInput.value.ifBlank {
            com.example.api.ApiKeyProvider.getApiKey(context)
        }
        engine.startDiagnostic(
            apiKey = resolvedKey,
            modelName = modelNameInput.value,
            debugMode = debugMode.value,
            context = context.applicationContext
        )
    }

    fun startMicRecording(context: Context) {
        engine.startMicRecording(context.applicationContext)
    }

    fun toggleMicMute(): Boolean {
        val newMuted = engine.toggleMicMute()
        isMicMuted.value = newMuted
        return newMuted
    }

    fun disconnect() {
        engine.disconnect()
        isMicMuted.value = false
    }

    fun clearLog() {
        _logEntries.value = emptyList()
        val timeString = timeFormat.format(Date())
        _logEntries.update {
            listOf(LogEntry(timeString, LogLevel.INFO, "Log cleared by user"))
        }
    }

    fun getFullLogText(): String {
        val header = "First Light Diagnostic Log - Generated at ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n\n"
        val body = _logEntries.value.joinToString(separator = "\n") { it.toFormattedLine() }
        return header + body
    }

    fun copyLogToClipboard(context: Context): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Gemini Live Log", getFullLogText())
            clipboard.setPrimaryClip(clip)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Generates a filename formatted as GeminiLiveLog_YYYY-MM-DD_HH-MM-SS.txt
     */
    fun generateExportFilename(): String {
        val dateStr = fileDateFormat.format(Date())
        return "GeminiLiveLog_$dateStr.txt"
    }

    /**
     * Attempts to export the full log directly to the Downloads directory.
     * Returns a user-friendly status message.
     */
    fun exportLogToDownloads(context: Context): Pair<Boolean, String> {
        val filename = generateExportFilename()
        val logContent = getFullLogText()

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(logContent.toByteArray(Charsets.UTF_8))
                    }
                    Pair(true, "Saved to Downloads/$filename")
                } else {
                    Pair(false, "Failed to create MediaStore entry for $filename")
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val logFile = File(downloadsDir, filename)
                FileOutputStream(logFile).use { outputStream ->
                    outputStream.write(logContent.toByteArray(Charsets.UTF_8))
                }
                Pair(true, "Saved to ${logFile.absolutePath}")
            }
        } catch (e: Exception) {
            Pair(false, "Export error: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    /**
     * Writes log data to a user-selected SAF Document Uri.
     */
    fun writeLogToUri(context: Context, uri: Uri): Pair<Boolean, String> {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(getFullLogText().toByteArray(Charsets.UTF_8))
            }
            Pair(true, "Successfully exported log file")
        } catch (e: Exception) {
            Pair(false, "Failed writing to selected location: ${e.localizedMessage}")
        }
    }

    fun release() {
        engine.disconnect()
        isMicMuted.value = false
    }

    override fun onCleared() {
        super.onCleared()
        release()
    }
}
