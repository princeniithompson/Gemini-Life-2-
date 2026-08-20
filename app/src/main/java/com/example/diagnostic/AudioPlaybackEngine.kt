package com.example.diagnostic

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import com.example.model.LogLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private sealed class PlaybackCommand {
    data class PlayChunk(val byteData: ByteArray, val parentFrameNumber: Int) : PlaybackCommand()
    data class Stop(val reason: String) : PlaybackCommand()
    data class SetVolume(val volume: Float) : PlaybackCommand()
    object Flush : PlaybackCommand()
    object ResumeFocus : PlaybackCommand()
    object PauseFocus : PlaybackCommand()
    object PauseAndFlushCall : PlaybackCommand()
    object ResumeFromCall : PlaybackCommand()
}

/**
 * Robust Direct Streaming Audio Playback Engine for real-time PCM streaming via AudioTrack.
 *
 * Uses standard Android Media Playback configuration (USAGE_MEDIA, STREAM_MUSIC, 24kHz 16-bit Mono)
 * with single-actor serialization to guarantee rock-solid stability and loud speaker audio.
 */
class AudioPlaybackEngine(
    private val logger: (LogLevel, String, String?) -> Unit
) {
    // Single-threaded supervisor scope for total serialization of AudioTrack operations
    private val playbackDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val supervisorScope = CoroutineScope(SupervisorJob() + playbackDispatcher)
    private var supervisorJob: Job? = null
    private var drainMonitorJob: Job? = null

    private var activeAudioTrack: AudioTrack? = null
    private val isInitialized = AtomicBoolean(false)
    private val isPlayingFlag = AtomicBoolean(false)
    private val currentVolume = AtomicReference(1.0f)

    var onPlaybackDrained: (() -> Unit)? = null

    @Volatile private var lastHeadPosition = 0L
    @Volatile private var lastHeadMoveTime = System.currentTimeMillis()
    private val chunksWrittenInCurrentTurn = AtomicInteger(0)

    // Serialized command channel (UNLIMITED capacity to prevent backpressure drops)
    private val commandChannel = Channel<PlaybackCommand>(Channel.UNLIMITED)

    // 1-Byte Carryover Buffer for 16-bit PCM sample alignment
    private var leftoverByte: Byte? = null

    private val isResumeAfterInterruption = AtomicBoolean(false)
    private val isTurnComplete = AtomicBoolean(false)

    // Metrics tracking
    private val bytesWrittenTotal = AtomicLong(0L)
    private val framesWrittenTotal = AtomicLong(0L)
    private val writeFailuresTotal = AtomicInteger(0)
    private val underrunsTotal = AtomicInteger(0)
    private val droppedFramesTotal = AtomicInteger(0)
    private var lastUnderrunCount = 0

    // Timing metrics
    private var totalWriteDurationMs = 0L
    private var maxWriteDurationMs = 0L
    private var writeCount = 0L

    private val sampleRate = 24000
    private val bytesPerSample = 2 // 16-bit mono = 2 bytes/sample
    private val bytesPerMs = 48.0 // 24000 * 2 / 1000

    init {
        ensureSupervisorRunning()
        startDrainMonitor()
    }

    private fun resetCounters() {
        bytesWrittenTotal.set(0L)
        framesWrittenTotal.set(0L)
        lastHeadPosition = 0L
        lastHeadMoveTime = System.currentTimeMillis()
        isPlayingFlag.set(false)
    }

    /**
     * Ensures the persistent background supervisor loop is active.
     */
    private fun ensureSupervisorRunning() {
        if (supervisorJob?.isActive == true) return
        supervisorJob = supervisorScope.launch {
            log(LogLevel.INFO, "[AudioPlayer] Serialized supervisor actor STARTED")
            while (isActive) {
                try {
                    when (val command = commandChannel.receive()) {
                        is PlaybackCommand.PlayChunk -> {
                            handlePlayChunk(command.byteData, command.parentFrameNumber)
                        }
                        is PlaybackCommand.Stop -> {
                            handleStop(command.reason)
                        }
                        is PlaybackCommand.SetVolume -> {
                            handleSetVolume(command.volume)
                        }
                        is PlaybackCommand.Flush -> {
                            handleFlush()
                        }
                        is PlaybackCommand.PauseFocus -> {
                            handlePauseFocus()
                        }
                        is PlaybackCommand.ResumeFocus -> {
                            handleResumeFocus()
                        }
                        is PlaybackCommand.PauseAndFlushCall -> {
                            handlePauseAndFlushCall()
                        }
                        is PlaybackCommand.ResumeFromCall -> {
                            handleResumeFromCall()
                        }
                    }
                } catch (e: CancellationException) {
                    log(LogLevel.INFO, "[AudioPlayer] Supervisor actor loop cancelled")
                    break
                } catch (e: Exception) {
                    log(LogLevel.WARN, "[AudioPlayer] Supervisor actor caught exception (${e.javaClass.simpleName}): ${e.localizedMessage}")
                    safelyReleaseTrackInternal()
                }
            }
        }
    }

    /**
     * Handles incoming PCM chunks serialized on the worker coroutine.
     */
    private fun handlePlayChunk(byteData: ByteArray, parentFrameNumber: Int) {
        var track = activeAudioTrack
        if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
            track = createAndStartAudioTrackInternal()
            if (track == null) {
                log(LogLevel.ERROR, "[AudioPlayer] Failed to instantiate AudioTrack for Chunk #$parentFrameNumber")
                return
            }
        }

        var writeResult = writeChunkToTrack(track, byteData, parentFrameNumber)

        // Instantaneous error recovery on dead track / negative error code
        if (writeResult < 0) {
            log(LogLevel.WARN, "[AudioPlayer] Instantaneous self-heal: AudioTrack write failed ($writeResult). Recreating hardware track...")
            safelyReleaseTrackInternal()
            val freshTrack = createAndStartAudioTrackInternal()
            if (freshTrack != null) {
                writeResult = writeChunkToTrack(freshTrack, byteData, parentFrameNumber)
                if (writeResult > 0) {
                    log(LogLevel.SUCCESS, "[AudioPlayer] Self-heal SUCCESS: Chunk #$parentFrameNumber recovered ($writeResult bytes written)")
                }
            }
        }
    }

    private fun handleStop(reason: String) {
        if (reason.contains("interrupted", ignoreCase = true) || reason.contains("barge-in", ignoreCase = true)) {
            isResumeAfterInterruption.set(true)
        }
        safelyReleaseTrackInternal()
        resetCounters()
        leftoverByte = null
        log(LogLevel.INFO, "[AudioPlayer] Playback stopped ($reason)")
    }

    private fun handleSetVolume(volume: Float) {
        currentVolume.set(volume)
        val track = activeAudioTrack
        if (track != null && track.state == AudioTrack.STATE_INITIALIZED) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    track.setVolume(volume)
                } else {
                    @Suppress("DEPRECATION")
                    track.setStereoVolume(volume, volume)
                }
                log(LogLevel.INFO, "[AudioPlayer] AudioTrack hardware volume set to $volume")
            } catch (e: Exception) {
                log(LogLevel.WARN, "[AudioPlayer] Exception setting volume: ${e.localizedMessage}")
            }
        }
    }

    private fun handleFlush() {
        val track = activeAudioTrack
        if (track != null && track.state == AudioTrack.STATE_INITIALIZED) {
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                }
                track.flush()
                track.play()
                log(LogLevel.INFO, "[AudioPlayer] AudioTrack buffer flushed and resumed")
            } catch (e: Exception) {
                log(LogLevel.WARN, "[AudioPlayer] Exception flushing AudioTrack: ${e.localizedMessage}")
            }
        }
    }

    private fun handlePauseFocus() {
        val track = activeAudioTrack
        if (track != null && track.state == AudioTrack.STATE_INITIALIZED) {
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    isPlayingFlag.set(false)
                    log(LogLevel.WARN, "[AudioPlayer] AudioTrack paused for audio focus loss")
                }
            } catch (e: Exception) {
                log(LogLevel.WARN, "[AudioPlayer] Exception pausing for audio focus: ${e.localizedMessage}")
            }
        }
    }

    private fun handleResumeFocus() {
        val track = activeAudioTrack
        if (track != null && track.state == AudioTrack.STATE_INITIALIZED) {
            try {
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    track.play()
                    isPlayingFlag.set(true)
                    lastHeadMoveTime = System.currentTimeMillis()
                    log(LogLevel.SUCCESS, "[AudioPlayer] AudioTrack resumed after audio focus regained")
                }
            } catch (e: Exception) {
                log(LogLevel.WARN, "[AudioPlayer] Exception resuming for audio focus: ${e.localizedMessage}")
            }
        }
    }

    private fun handlePauseAndFlushCall() {
        val track = activeAudioTrack
        if (track != null && track.state == AudioTrack.STATE_INITIALIZED) {
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                }
                track.flush()
                isPlayingFlag.set(false)
                log(LogLevel.WARN, "[AudioPlayer] AudioTrack paused & flushed for incoming call (instant silence)")
            } catch (e: Exception) {
                log(LogLevel.WARN, "[AudioPlayer] Exception pausing & flushing AudioTrack for call: ${e.localizedMessage}")
            }
        }
    }

    private fun handleResumeFromCall() {
        val track = activeAudioTrack
        if (track != null && track.state == AudioTrack.STATE_INITIALIZED) {
            try {
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    track.play()
                    isPlayingFlag.set(true)
                    lastHeadMoveTime = System.currentTimeMillis()
                    log(LogLevel.SUCCESS, "[AudioPlayer] AudioTrack resumed after call disconnected")
                }
            } catch (e: Exception) {
                log(LogLevel.WARN, "[AudioPlayer] Exception resuming AudioTrack after call: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Creates and starts a new AudioTrack instance with USAGE_ASSISTANT playback attributes.
     */
    private fun createAndStartAudioTrackInternal(): AudioTrack? {
        safelyReleaseTrackInternal()
        log(LogLevel.INFO, "[AudioPlayer] Initializing AudioTrack (USAGE_ASSISTANT, 24kHz Mono)...")
        return try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (minBufferSize <= 0) {
                log(LogLevel.ERROR, "[AudioPlayer] AudioTrack.getMinBufferSize failed with code: $minBufferSize")
                return null
            }

            val safeBufferSize = maxOf(minBufferSize * 8, 96000)

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            val track = AudioTrack(
                attributes,
                format,
                safeBufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                log(LogLevel.ERROR, "[AudioPlayer] AudioTrack creation failed (State: ${track.state})")
                track.release()
                return null
            }

            val vol = currentVolume.get()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                track.setVolume(vol)
            } else {
                @Suppress("DEPRECATION")
                track.setStereoVolume(vol, vol)
            }

            track.play()
            activeAudioTrack = track
            isInitialized.set(true)
            isPlayingFlag.set(true)

            log(
                LogLevel.SUCCESS,
                "[AudioPlayer] AudioTrack created & started\nSample Rate: $sampleRate Hz | Usage: USAGE_ASSISTANT | Safe Buffer: $safeBufferSize bytes"
            )
            track
        } catch (e: Exception) {
            log(LogLevel.ERROR, "[AudioPlayer] Exception during AudioTrack creation: ${e.localizedMessage}")
            null
        }
    }

    /**
     * Safely releases the active AudioTrack instance.
     */
    private fun safelyReleaseTrackInternal() {
        val track = activeAudioTrack
        activeAudioTrack = null
        if (track != null) {
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                }
                track.flush()
                track.release()
            } catch (e: Exception) {
                log(LogLevel.WARN, "[AudioPlayer] Exception safely releasing AudioTrack: ${e.localizedMessage}")
            }
        }
        isInitialized.set(false)
        isPlayingFlag.set(false)
    }

    /**
     * Writes PCM bytes directly to AudioTrack STREAM mode buffer.
     */
    private fun writeChunkToTrack(track: AudioTrack, byteData: ByteArray, parentFrameNumber: Int): Int {
        if (track.state != AudioTrack.STATE_INITIALIZED) return -1

        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
            try {
                track.play()
                isPlayingFlag.set(true)
            } catch (e: Exception) {
                log(LogLevel.WARN, "[AudioPlayer] Exception starting play() before write: ${e.localizedMessage}")
            }
        }

        val bytesToWrite = byteData.size
        val writeStartTime = System.currentTimeMillis()

        val writeResult = track.write(byteData, 0, bytesToWrite, AudioTrack.WRITE_BLOCKING)
        val writeDurationMs = System.currentTimeMillis() - writeStartTime

        synchronized(this) {
            writeCount++
            totalWriteDurationMs += writeDurationMs
            maxWriteDurationMs = maxOf(maxWriteDurationMs, writeDurationMs)
        }

        if (writeResult > 0) {
            val now = System.currentTimeMillis()
            lastHeadMoveTime = now
            bytesWrittenTotal.addAndGet(writeResult.toLong())
            framesWrittenTotal.addAndGet((writeResult / bytesPerSample).toLong())
            val currentChunks = chunksWrittenInCurrentTurn.incrementAndGet()
            if (currentChunks == 1) {
                log(LogLevel.SUCCESS, "[AudioPlayer] First write result of turn: $writeResult bytes written")
            }
            isPlayingFlag.set(true)

            if (writeResult < bytesToWrite) {
                writeFailuresTotal.incrementAndGet()
                log(LogLevel.WARN, "[AudioPlayer] Short write for Chunk #$parentFrameNumber: $writeResult / $bytesToWrite bytes written")
            }
        } else {
            writeFailuresTotal.incrementAndGet()
            log(LogLevel.ERROR, "[AudioPlayer] AudioTrack write failed for Chunk #$parentFrameNumber with code: $writeResult")
            return writeResult
        }

        // Diagnostics logging
        val headPosFrames = try { track.playbackHeadPosition.toLong() and 0xFFFFFFFFL } catch (_: Exception) { 0L }
        val playedBytes = headPosFrames * bytesPerSample
        val trackBufferedBytes = (bytesWrittenTotal.get() - playedBytes).coerceAtLeast(0L)
        val totalLatencyMs = trackBufferedBytes / bytesPerMs

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val currentUnderruns = track.underrunCount
                if (currentUnderruns > lastUnderrunCount) {
                    val delta = currentUnderruns - lastUnderrunCount
                    underrunsTotal.addAndGet(delta)
                    lastUnderrunCount = currentUnderruns
                    log(LogLevel.WARN, "[AudioPlayer] Underrun detected (Count: $currentUnderruns)")
                }
            } catch (_: Exception) {}
        }

        log(
            LogLevel.INFO,
            "[AudioPlayer] Chunk #$parentFrameNumber written | Bytes: $bytesToWrite | Head: $headPosFrames frames | Buffer Latency: ${totalLatencyMs.toInt()}ms"
        )
        return writeResult
    }

    /**
     * Enqueues incoming PCM audio chunk into channel.
     */
    fun playChunk(rawBytes: ByteArray, parentFrameNumber: Int) {
        if (rawBytes.isEmpty()) return

        val combinedBytes: ByteArray
        synchronized(this) {
            val prev = leftoverByte
            if (prev != null) {
                leftoverByte = null
                combinedBytes = ByteArray(rawBytes.size + 1)
                combinedBytes[0] = prev
                System.arraycopy(rawBytes, 0, combinedBytes, 1, rawBytes.size)
            } else {
                combinedBytes = rawBytes
            }

            val remainder = combinedBytes.size % bytesPerSample
            val alignedLength = combinedBytes.size - remainder

            if (remainder != 0) {
                leftoverByte = combinedBytes[combinedBytes.size - 1]
            }

            if (alignedLength > 0) {
                val alignedData = if (alignedLength == combinedBytes.size) combinedBytes else combinedBytes.copyOf(alignedLength)
                commandChannel.trySend(PlaybackCommand.PlayChunk(alignedData, parentFrameNumber))
            }
        }
        ensureSupervisorRunning()
    }

    /**
     * Hardware drain monitor: Detects when written audio for the turn has fully completed rendering.
     * When idle (no audio playing), suspends with a 1-second interval to eliminate CPU wakeups.
     */
    private fun startDrainMonitor() {
        if (drainMonitorJob?.isActive == true) return
        drainMonitorJob = supervisorScope.launch {
            while (isActive) {
                val track = activeAudioTrack
                if (track != null && track.state == AudioTrack.STATE_INITIALIZED && isPlayingFlag.get()) {
                    val head = try { track.playbackHeadPosition.toLong() and 0xFFFFFFFFL } catch (_: Exception) { lastHeadPosition }
                    if (head != lastHeadPosition) {
                        lastHeadPosition = head
                        lastHeadMoveTime = System.currentTimeMillis()
                    } else if (isTurnComplete.get() && head >= framesWrittenTotal.get()) {
                        isPlayingFlag.set(false)
                        log(LogLevel.INFO, "[AudioPlayer] Turn playback fully drained ($head frames rendered)")
                        try {
                            onPlaybackDrained?.invoke()
                        } catch (e: Exception) {
                            log(LogLevel.WARN, "[AudioPlayer] Exception in onPlaybackDrained callback: ${e.localizedMessage}")
                        }
                    }
                    delay(100)
                } else {
                    // Zero CPU overhead when idle
                    delay(1000)
                }
            }
        }
    }

    fun getTrackBufferedBytes(): Long {
        val track = activeAudioTrack ?: return 0L
        if (track.state != AudioTrack.STATE_INITIALIZED) return 0L
        val headPosFrames = try { track.playbackHeadPosition.toLong() and 0xFFFFFFFFL } catch (_: Exception) { 0L }
        val playedBytes = headPosFrames * bytesPerSample
        return (bytesWrittenTotal.get() - playedBytes).coerceAtLeast(0L)
    }

    fun getTotalBufferedBytes(): Long = getTrackBufferedBytes()

    fun notifyInterrupted() {
        isResumeAfterInterruption.set(true)
    }

    fun notifyTurnStarted() {
        isTurnComplete.set(false)
        chunksWrittenInCurrentTurn.set(0)
        lastHeadPosition = 0L
        lastHeadMoveTime = System.currentTimeMillis()
        val isResume = isResumeAfterInterruption.getAndSet(false)
        if (isResume) {
            log(LogLevel.INFO, "[AudioPlayer] Model turn resumed after interruption")
        } else {
            log(LogLevel.INFO, "[AudioPlayer] Model turn started")
        }
        ensureSupervisorRunning()
    }

    fun notifyTurnCompleted() {
        isTurnComplete.set(true)
        log(LogLevel.INFO, "[AudioPlayer] Model turn complete - AudioTrack direct hardware drain active")
        ensureSupervisorRunning()
    }

    fun prepare() {
        ensureSupervisorRunning()
    }

    fun isPlaying(): Boolean = isPlayingFlag.get()

    fun hasWrittenAudioInCurrentTurn(): Boolean = chunksWrittenInCurrentTurn.get() > 0

    fun setVolume(volume: Float) {
        commandChannel.trySend(PlaybackCommand.SetVolume(volume))
    }

    fun forceStop() {
        stopPlaybackImmediate("Force stop requested")
    }

    fun reset() {
        // Reset counters without abruptly releasing track mid-flight
        synchronized(this) {
            leftoverByte = null
            writeFailuresTotal.set(0)
            underrunsTotal.set(0)
            droppedFramesTotal.set(0)
            lastUnderrunCount = 0
            totalWriteDurationMs = 0L
            maxWriteDurationMs = 0L
            writeCount = 0L
            chunksWrittenInCurrentTurn.set(0)
            isResumeAfterInterruption.set(false)
            isTurnComplete.set(false)
            resetCounters()
        }
        ensureSupervisorRunning()
    }

    fun flushForReconnection() {
        commandChannel.trySend(PlaybackCommand.Flush)
        log(LogLevel.INFO, "[AudioPlayer] Reconnection complete. Audio buffer flushed. Ready for fresh audio stream.")
    }

    fun pauseForAudioFocus() {
        commandChannel.trySend(PlaybackCommand.PauseFocus)
    }

    fun resumeFromAudioFocus() {
        commandChannel.trySend(PlaybackCommand.ResumeFocus)
    }

    fun pauseForPhoneCall() {
        commandChannel.trySend(PlaybackCommand.PauseAndFlushCall)
    }

    fun resumeFromPhoneCall() {
        commandChannel.trySend(PlaybackCommand.ResumeFromCall)
    }

    /**
     * Flushes channel and safely stops AudioTrack.
     */
    fun stopPlaybackImmediate(reason: String) {
        while (commandChannel.tryReceive().isSuccess) {
            // Drain pending commands
        }
        commandChannel.trySend(PlaybackCommand.Stop(reason))
    }

    /**
     * Releases all engine resources and cancels supervisor scope.
     */
    fun release() {
        stopPlaybackImmediate("Engine release")
        try {
            drainMonitorJob?.cancel()
            supervisorJob?.cancel()
            commandChannel.close()
            log(LogLevel.SUCCESS, "[AudioPlayer] AudioPlaybackEngine released cleanly")
        } catch (e: Exception) {
            log(LogLevel.WARN, "[AudioPlayer] Exception during AudioPlaybackEngine release: ${e.localizedMessage}")
        }
    }

    private fun log(level: LogLevel, message: String, details: String? = null) {
        logger(level, message, details)
    }
}
