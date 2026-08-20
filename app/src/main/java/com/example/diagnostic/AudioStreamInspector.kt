package com.example.diagnostic

import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Inspection report returned after processing an individual PCM audio frame.
 */
data class FrameInspectionReport(
    val frameNumber: Int,
    val byteSize: Int,
    val runningTotalBytes: Long,
    val durationMs: Double,
    val isDuplicate: Boolean,
    val isMissingDetected: Boolean,
    val isOutOfOrder: Boolean,
    val frameIntervalMs: Long,
    val validationResult: ValidationResult
)

/**
 * Thread-safe Inspector for Gemini Live PCM Audio Streams.
 *
 * Tracks frame counts, sequence numbers, duplicate frames, timing intervals,
 * audio duration, average frame sizes, and stream bitrates.
 */
class AudioStreamInspector(
    private val validator: PcmValidator = PcmValidator()
) {
    private val totalFrames = AtomicInteger(0)
    private val duplicateFrames = AtomicInteger(0)
    private val missingFrames = AtomicInteger(0)
    private val outOfOrderFrames = AtomicInteger(0)
    private val totalPcmBytes = AtomicLong(0L)
    private val malformedFrames = AtomicInteger(0)

    private val firstFrameTimeMs = AtomicLong(0L)
    private val lastFrameTimeMs = AtomicLong(0L)

    private val frameIntervals = ConcurrentLinkedQueue<Long>()
    private val recentFrameHashes = ConcurrentHashMap.newKeySet<Int>()
    private val maxHashHistory = 100

    private val lock = Any()

    fun reset() {
        synchronized(lock) {
            totalFrames.set(0)
            duplicateFrames.set(0)
            missingFrames.set(0)
            outOfOrderFrames.set(0)
            totalPcmBytes.set(0L)
            malformedFrames.set(0)
            firstFrameTimeMs.set(0L)
            lastFrameTimeMs.set(0L)
            frameIntervals.clear()
            recentFrameHashes.clear()
        }
    }

    /**
     * Inspects a newly arrived PCM audio frame.
     *
     * @param byteArray Raw audio bytes
     * @param nowMs Current timestamp in milliseconds
     * @param mimeType Audio MIME type string
     */
    fun inspectFrame(byteArray: ByteArray, nowMs: Long, mimeType: String = "audio/pcm;rate=24000"): FrameInspectionReport {
        val frameNum = totalFrames.incrementAndGet()
        val byteSize = byteArray.size
        val newTotalBytes = totalPcmBytes.addAndGet(byteSize.toLong())

        val validationResult = validator.validateFrame(byteArray, mimeType)
        if (!validationResult.isValid) {
            malformedFrames.incrementAndGet()
        }

        var isDup = false
        var isMissing = false
        var isOutOfOrder = false
        var intervalMs = 0L

        synchronized(lock) {
            // Check for duplicate frame
            val frameHash = Arrays.hashCode(byteArray)
            if (recentFrameHashes.contains(frameHash)) {
                isDup = true
                duplicateFrames.incrementAndGet()
            } else {
                if (recentFrameHashes.size >= maxHashHistory) {
                    recentFrameHashes.clear()
                }
                recentFrameHashes.add(frameHash)
            }

            // Timing and Interval Calculation
            if (firstFrameTimeMs.get() == 0L) {
                firstFrameTimeMs.set(nowMs)
                lastFrameTimeMs.set(nowMs)
            } else {
                val prevTime = lastFrameTimeMs.getAndSet(nowMs)
                intervalMs = nowMs - prevTime
                if (intervalMs >= 0) {
                    frameIntervals.add(intervalMs)
                } else {
                    isOutOfOrder = true
                    outOfOrderFrames.incrementAndGet()
                }

                // Check for skipped / missing frame indication (if interval is > 4x average frame duration)
                val frameDurationMs = (byteSize / 48.0) // 48 bytes per ms at 24kHz 16-bit mono
                if (intervalMs > (frameDurationMs * 4) && frameDurationMs > 5.0) {
                    isMissing = true
                    missingFrames.incrementAndGet()
                }
            }
        }

        val frameDuration = (byteSize / 48.0)

        return FrameInspectionReport(
            frameNumber = frameNum,
            byteSize = byteSize,
            runningTotalBytes = newTotalBytes,
            durationMs = frameDuration,
            isDuplicate = isDup,
            isMissingDetected = isMissing,
            isOutOfOrder = isOutOfOrder,
            frameIntervalMs = intervalMs,
            validationResult = validationResult
        )
    }

    // Getters for Thread-Safe Statistics
    fun getTotalFrames(): Int = totalFrames.get()
    fun getDuplicateFrames(): Int = duplicateFrames.get()
    fun getMissingFrames(): Int = missingFrames.get()
    fun getOutOfOrderFrames(): Int = outOfOrderFrames.get()
    fun getMalformedFrames(): Int = malformedFrames.get()
    fun getTotalPcmBytes(): Long = totalPcmBytes.get()

    /**
     * Calculates total PCM audio duration in milliseconds.
     * PCM 24,000 Hz, 16-bit Mono = 48,000 bytes/sec = 48 bytes/ms.
     */
    fun getTotalDurationMs(): Double {
        return totalPcmBytes.get() / 48.0
    }

    fun getTotalDurationSeconds(): Double {
        return getTotalDurationMs() / 1000.0
    }

    fun getAverageFrameSizeBytes(): Double {
        val count = totalFrames.get()
        return if (count > 0) totalPcmBytes.get().toDouble() / count else 0.0
    }

    fun getAverageFrameIntervalMs(): Double {
        val count = totalFrames.get()
        if (count <= 1) return 0.0
        val totalIntervalMs = lastFrameTimeMs.get() - firstFrameTimeMs.get()
        return if (totalIntervalMs > 0) totalIntervalMs.toDouble() / (count - 1) else 0.0
    }

    /**
     * Calculates streaming throughput in kilobits per second (kbps).
     */
    fun getStreamBitrateKbps(): Double {
        val streamDurationMs = lastFrameTimeMs.get() - firstFrameTimeMs.get()
        val durationSec = if (streamDurationMs > 0) streamDurationMs / 1000.0 else getTotalDurationSeconds()
        return if (durationSec > 0) (totalPcmBytes.get() * 8.0 / 1000.0) / durationSec else 0.0
    }
}
